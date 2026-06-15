package com.joyproxy.app.vpn

import android.annotation.SuppressLint
import android.net.IpPrefix
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Process
import android.system.OsConstants
import androidx.annotation.RequiresApi
import io.nekohasekai.libbox.ConnectionOwner
import io.nekohasekai.libbox.InterfaceUpdateListener
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.LocalDNSTransport
import io.nekohasekai.libbox.NetworkInterfaceIterator
import io.nekohasekai.libbox.PlatformInterface
import io.nekohasekai.libbox.RoutePrefix
import io.nekohasekai.libbox.StringIterator
import io.nekohasekai.libbox.TunOptions
import io.nekohasekai.libbox.WIFIState
import com.joyproxy.app.JoyProxyApp
import java.net.Inet6Address
import java.net.InetSocketAddress
import java.net.InterfaceAddress
import java.net.NetworkInterface
import java.security.KeyStore
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import io.nekohasekai.libbox.NetworkInterface as LibboxNetworkInterface

interface PlatformInterfaceWrapper : PlatformInterface {
    override fun usePlatformAutoDetectInterfaceControl(): Boolean = true

    override fun autoDetectInterfaceControl(fd: Int) {}

    override fun openTun(options: TunOptions): Int {
        error("invalid argument")
    }

    override fun useProcFS(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun findConnectionOwner(
        ipProtocol: Int,
        sourceAddress: String,
        sourcePort: Int,
        destinationAddress: String,
        destinationPort: Int,
    ): ConnectionOwner {
        val uid =
            JoyProxyApp.connectivity.getConnectionOwnerUid(
                ipProtocol,
                InetSocketAddress(sourceAddress, sourcePort),
                InetSocketAddress(destinationAddress, destinationPort),
            )
        if (uid == Process.INVALID_UID) error("android: connection owner not found")
        val packages = JoyProxyApp.packageManager.getPackagesForUid(uid)
        val owner = ConnectionOwner()
        owner.userId = uid
        owner.userName = packages?.firstOrNull() ?: ""
        owner.setAndroidPackageNames(StringArray(packages?.iterator() ?: emptyList<String>().iterator()))
        return owner
    }

    override fun startDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {
        DefaultNetworkMonitor.setListener(listener)
    }

    override fun closeDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {
        DefaultNetworkMonitor.setListener(null)
    }

    override fun getInterfaces(): NetworkInterfaceIterator {
        return try {
            buildInterfaces()
        } catch (t: Throwable) {
            InterfaceArray(emptyList<LibboxNetworkInterface>().iterator())
        }
    }

    private fun buildInterfaces(): NetworkInterfaceIterator {
        val networks = JoyProxyApp.connectivity.allNetworks
        val networkInterfaces = NetworkInterface.getNetworkInterfaces()?.toList() ?: emptyList()
        val interfaces = mutableListOf<LibboxNetworkInterface>()
        for (network in networks) {
            val boxInterface = LibboxNetworkInterface()
            val linkProperties = JoyProxyApp.connectivity.getLinkProperties(network) ?: continue
            val networkCapabilities =
                JoyProxyApp.connectivity.getNetworkCapabilities(network) ?: continue
            boxInterface.name = linkProperties.interfaceName
            val networkInterface =
                networkInterfaces.find { it.name == boxInterface.name } ?: continue
            boxInterface.dnsServer =
                StringArray(linkProperties.dnsServers.mapNotNull { it.hostAddress }.iterator())
            boxInterface.type =
                when {
                    networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ->
                        Libbox.InterfaceTypeWIFI
                    networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ->
                        Libbox.InterfaceTypeCellular
                    networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ->
                        Libbox.InterfaceTypeEthernet
                    else -> Libbox.InterfaceTypeOther
                }
            boxInterface.index = networkInterface.index
            runCatching { boxInterface.mtu = networkInterface.mtu }
            boxInterface.addresses =
                StringArray(
                    networkInterface.interfaceAddresses.mapTo(mutableListOf()) { it.toPrefix() }
                        .iterator(),
                )
            var dumpFlags = 0
            if (networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                dumpFlags = OsConstants.IFF_UP or OsConstants.IFF_RUNNING
            }
            if (networkInterface.isLoopback) {
                dumpFlags = dumpFlags or OsConstants.IFF_LOOPBACK
            }
            if (networkInterface.isPointToPoint) {
                dumpFlags = dumpFlags or OsConstants.IFF_POINTOPOINT
            }
            if (networkInterface.supportsMulticast()) {
                dumpFlags = dumpFlags or OsConstants.IFF_MULTICAST
            }
            boxInterface.flags = dumpFlags
            boxInterface.metered =
                !networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            interfaces.add(boxInterface)
        }
        return InterfaceArray(interfaces.iterator())
    }

    override fun underNetworkExtension(): Boolean = false

    override fun includeAllNetworks(): Boolean = false

    override fun clearDNSCache() {}

    // libbox 的 ReadWIFIState() 在 Go 接口里不返回 error，任何抛出的异常都会让整个进程 abort。
    // Android 12+ 无定位权限时 connectionInfo / ssid 可能抛异常或为 null，必须全部吞掉返回 null。
    override fun readWIFIState(): WIFIState? {
        return try {
            @Suppress("DEPRECATION")
            val wifiInfo = JoyProxyApp.wifiManager.connectionInfo ?: return null
            var ssid = wifiInfo.ssid ?: return WIFIState("", "")
            if (ssid == " " || ssid == "<unknown ssid>") return WIFIState("", "")
            if (ssid.startsWith("\"") && ssid.endsWith("\"")) {
                ssid = ssid.substring(1, ssid.length - 1)
            }
            WIFIState(ssid, wifiInfo.bssid ?: "")
        } catch (t: Throwable) {
            null
        }
    }

    override fun localDNSTransport(): LocalDNSTransport? = LocalResolver

    // SystemCertificates() 同样不返回 error，必须保证不抛异常。
    @OptIn(ExperimentalEncodingApi::class)
    override fun systemCertificates(): StringIterator {
        return try {
            val certificates = mutableListOf<String>()
            val keyStore = KeyStore.getInstance("AndroidCAStore")
            keyStore.load(null, null)
            val aliases = keyStore.aliases()
            while (aliases.hasMoreElements()) {
                val cert = keyStore.getCertificate(aliases.nextElement()) ?: continue
                certificates.add(
                    "-----BEGIN CERTIFICATE-----\n" + Base64.encode(cert.encoded) + "\n-----END CERTIFICATE-----",
                )
            }
            StringArray(certificates.iterator())
        } catch (t: Throwable) {
            StringArray(emptyList<String>().iterator())
        }
    }

    private class InterfaceArray(private val iterator: Iterator<LibboxNetworkInterface>) : NetworkInterfaceIterator {
        override fun hasNext(): Boolean = iterator.hasNext()
        override fun next(): LibboxNetworkInterface = iterator.next()
    }

    class StringArray(source: Iterator<String>) : StringIterator {
        private val values = source.asSequence().toList()
        private val iterator = values.iterator()
        override fun len(): Int = values.size
        override fun hasNext(): Boolean = iterator.hasNext()
        override fun next(): String = iterator.next()
    }

    private fun InterfaceAddress.toPrefix(): String =
        if (address is Inet6Address) {
            "${Inet6Address.getByAddress(address.address).hostAddress}/$networkPrefixLength"
        } else {
            "${address.hostAddress}/$networkPrefixLength"
        }
}

fun RoutePrefix.toIpPrefix(): IpPrefix =
    IpPrefix(java.net.InetAddress.getByName(address()), prefix())
