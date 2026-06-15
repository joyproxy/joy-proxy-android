package com.joyproxy.app.vpn

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.joyproxy.app.JoyProxyApp
import com.joyproxy.app.MainActivity
import com.joyproxy.app.R
import com.joyproxy.app.config.ConfigBuilder
import com.joyproxy.app.config.ProxyScope
import com.joyproxy.app.config.ProxySettings
import com.joyproxy.app.data.SettingsRepository
import io.nekohasekai.libbox.CommandServer
import io.nekohasekai.libbox.CommandServerHandler
import io.nekohasekai.libbox.Notification
import io.nekohasekai.libbox.OverrideOptions
import io.nekohasekai.libbox.PlatformInterface
import io.nekohasekai.libbox.SystemProxyStatus
import io.nekohasekai.libbox.TunOptions
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class BoxService(
    private val service: Service,
    private val platformInterface: PlatformInterface,
) : CommandServerHandler {
    companion object {
        private const val TAG = "BoxService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "joyproxy_vpn"
    }

    enum class Status { Stopped, Starting, Started, Stopping }

    var fileDescriptor: ParcelFileDescriptor? = null
    var status: Status = Status.Stopped
        private set

    private val binder = VpnBinder()
    private lateinit var commandServer: CommandServer
    private val settingsRepository = SettingsRepository(service)
    private var pendingSettings: ProxySettings? = null

    fun onStartCommand(intent: Intent?): Int {
        if (status != Status.Stopped) return Service.START_NOT_STICKY
        status = Status.Starting
        pendingSettings = intent?.readProxySettings()
        VpnStatusBus.emit(VpnStatusBus.Event.Connecting)
        showNotification(service.getString(R.string.vpn_connecting), false)

        GlobalScope.launch(Dispatchers.IO) {
            try {
                JoyProxyApp.instance.libboxReady.await()
                startCommandServer()
                startVpn()
            } catch (e: Exception) {
                Log.e(TAG, "start failed", e)
                failToStart(friendlyError(e))
            }
        }
        return Service.START_STICKY
    }

    private fun startCommandServer() {
        commandServer = CommandServer(this, platformInterface)
        commandServer.start()
    }

    private suspend fun startVpn() {
        val settings = pendingSettings ?: settingsRepository.settings.first()
        if (!settings.isValid()) {
            failToStart("代理配置无效，请重新填写后连接")
            return
        }

        settingsRepository.save(settings.copy(connected = false))

        val config = ConfigBuilder.build(settings)
        DefaultNetworkMonitor.start()

        try {
            commandServer.startOrReloadService(config, buildOverrideOptions(settings))
        } catch (e: Exception) {
            Log.e(TAG, "create service failed", e)
            failToStart(friendlyError(e))
            return
        }

        status = Status.Started
        settingsRepository.save(settings.copy(connected = true))
        VpnStatusBus.emit(VpnStatusBus.Event.Connected)
        withContext(Dispatchers.Main) {
            showNotification(service.getString(R.string.vpn_notification_title), true)
        }
    }

    private suspend fun failToStart(message: String) {
        VpnStatusBus.emit(VpnStatusBus.Event.Failed(message))
        settingsRepository.setConnected(false)
        stopService()
    }

    private fun friendlyError(error: Exception): String {
        val message = error.message.orEmpty()
        return when {
            message.contains("missing vpn permission", ignoreCase = true) ->
                "未获得 VPN 授权，请允许建立 VPN 连接"
            message.contains("vpn establish failed", ignoreCase = true) ->
                "VPN 隧道建立失败，请重试"
            message.isBlank() -> "VPN 启动失败，请稍后重试"
            else -> "VPN 启动失败：${message.take(120)}"
        }
    }

    private fun buildOverrideOptions(settings: ProxySettings): OverrideOptions {
        return OverrideOptions().apply {
            autoRedirect = false
            val selfPackage = service.packageName
            when (settings.scope) {
                ProxyScope.WHITELIST -> {
                    val apps = (settings.selectedApps + selfPackage).toList()
                    includePackage = PlatformInterfaceWrapper.StringArray(apps.iterator())
                }
                ProxyScope.BLACKLIST -> {
                    val apps = (settings.selectedApps - selfPackage).toList()
                    excludePackage = PlatformInterfaceWrapper.StringArray(apps.iterator())
                }
                ProxyScope.GLOBAL -> {
                    excludePackage = PlatformInterfaceWrapper.StringArray(listOf(selfPackage).iterator())
                }
            }
        }
    }

    fun stopService() {
        if (status == Status.Stopped || status == Status.Stopping) return
        status = Status.Stopping

        GlobalScope.launch(Dispatchers.IO) {
            runCatching {
                fileDescriptor?.close()
                fileDescriptor = null
                DefaultNetworkMonitor.stop()
                if (::commandServer.isInitialized) {
                    runCatching { commandServer.closeService() }
                    commandServer.close()
                }
            }
            settingsRepository.setConnected(false)
            status = Status.Stopped
            VpnStatusBus.emit(VpnStatusBus.Event.Disconnected)
            withContext(Dispatchers.Main) {
                service.stopForeground(Service.STOP_FOREGROUND_REMOVE)
                service.stopSelf()
            }
        }
    }

    fun onBind(): IBinder = binder

    fun onDestroy() {}

    fun onRevoke() {
        stopService()
    }

    override fun serviceStop() {
        stopService()
    }

    override fun serviceReload() {
        runBlocking {
            if (::commandServer.isInitialized) {
                val settings = pendingSettings ?: settingsRepository.settings.first()
                val config = ConfigBuilder.build(settings)
                commandServer.startOrReloadService(config, buildOverrideOptions(settings))
            }
        }
    }

    override fun getSystemProxyStatus(): SystemProxyStatus? = null

    override fun setSystemProxyEnabled(isEnabled: Boolean) {
        serviceReload()
    }

    override fun writeDebugMessage(message: String?) {
        Log.d("sing-box", message ?: "")
    }

    fun sendNotification(notification: Notification) {}

    fun openTunFromService(options: TunOptions): Int {
        val vpnService = service as VpnService
        if (VpnService.prepare(vpnService) != null) {
            error("android: missing vpn permission")
        }

        val builder =
            vpnService.Builder()
                .setSession("JoyProxy")
                .setMtu(options.mtu)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
        }

        val inet4Address = options.inet4Address
        while (inet4Address.hasNext()) {
            val address = inet4Address.next()
            builder.addAddress(address.address(), address.prefix())
        }

        val inet6Address = options.inet6Address
        while (inet6Address.hasNext()) {
            val address = inet6Address.next()
            builder.addAddress(address.address(), address.prefix())
        }

        if (options.autoRoute) {
            builder.addDnsServer(options.dnsServerAddress.value)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val inet4RouteAddress = options.inet4RouteAddress
                if (inet4RouteAddress.hasNext()) {
                    while (inet4RouteAddress.hasNext()) {
                        builder.addRoute(inet4RouteAddress.next().toIpPrefix())
                    }
                } else if (options.inet4Address.hasNext()) {
                    builder.addRoute("0.0.0.0", 0)
                }

                val inet6RouteAddress = options.inet6RouteAddress
                if (inet6RouteAddress.hasNext()) {
                    while (inet6RouteAddress.hasNext()) {
                        builder.addRoute(inet6RouteAddress.next().toIpPrefix())
                    }
                } else if (options.inet6Address.hasNext()) {
                    builder.addRoute("::", 0)
                }
            } else {
                val inet4RouteAddress = options.inet4RouteRange
                if (inet4RouteAddress.hasNext()) {
                    while (inet4RouteAddress.hasNext()) {
                        val address = inet4RouteAddress.next()
                        builder.addRoute(address.address(), address.prefix())
                    }
                }
                val inet6RouteAddress = options.inet6RouteRange
                if (inet6RouteAddress.hasNext()) {
                    while (inet6RouteAddress.hasNext()) {
                        val address = inet6RouteAddress.next()
                        builder.addRoute(address.address(), address.prefix())
                    }
                }
            }
        }

        val includePackage = options.includePackage
        while (includePackage.hasNext()) {
            runCatching { builder.addAllowedApplication(includePackage.next()) }
        }

        val excludePackage = options.excludePackage
        while (excludePackage.hasNext()) {
            runCatching { builder.addDisallowedApplication(excludePackage.next()) }
        }

        val pfd = builder.establish() ?: error("android: vpn establish failed")
        fileDescriptor = pfd
        return pfd.fd
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun showNotification(text: String, ongoing: Boolean) {
        val manager = service.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    service.getString(R.string.vpn_notification_channel),
                    NotificationManager.IMPORTANCE_LOW,
                )
            manager.createNotificationChannel(channel)
        }

        val pendingIntent =
            PendingIntent.getActivity(
                service,
                0,
                Intent(service, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE,
            )

        val notification =
            NotificationCompat.Builder(service, CHANNEL_ID)
                .setContentTitle(service.getString(R.string.app_name))
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_vpn)
                .setContentIntent(pendingIntent)
                .setOngoing(ongoing)
                .build()

        runCatching { service.startForeground(NOTIFICATION_ID, notification) }
            .onFailure { Log.e(TAG, "startForeground failed", it) }
    }
}
