package com.joyproxy.app.vpn

import android.content.Intent
import android.net.VpnService
import android.os.IBinder
import android.util.Log
import io.nekohasekai.libbox.Notification
import io.nekohasekai.libbox.TunOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class ProxyVpnService : VpnService(), PlatformInterfaceWrapper {
  companion object {
    private const val TAG = "ProxyVpnService"
    const val ACTION_STOP = "com.joyproxy.app.STOP_VPN"
  }

  private val boxService = BoxService(this, this)

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (intent?.action == ACTION_STOP) {
      boxService.stopService()
      return START_NOT_STICKY
    }
    return boxService.onStartCommand()
  }

  override fun onBind(intent: Intent): IBinder? {
    val binder = super.onBind(intent)
  if (binder != null) return binder
    return boxService.onBind()
  }

  override fun onDestroy() {
    boxService.onDestroy()
    super.onDestroy()
  }

  override fun onRevoke() {
    runBlocking {
      withContext(Dispatchers.Main) {
        boxService.onRevoke()
      }
    }
  }

  override fun autoDetectInterfaceControl(fd: Int) {
    protect(fd)
  }

  override fun openTun(options: TunOptions): Int = boxService.openTunFromService(options)

  override fun sendNotification(notification: Notification) {
    boxService.sendNotification(notification)
  }
}
