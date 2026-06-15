package com.joyproxy.app.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.core.content.ContextCompat
import com.joyproxy.app.config.ProxySettings

object VpnController {
    fun start(context: Context, settings: ProxySettings) {
        val intent = Intent(context, ProxyVpnService::class.java)
        ContextCompat.startForegroundService(context, intent)
    }

    fun stop(context: Context) {
        val intent = Intent(context, ProxyVpnService::class.java).apply {
            action = ProxyVpnService.ACTION_STOP
        }
        context.startService(intent)
    }

    fun prepare(context: Context): Intent? = VpnService.prepare(context)
}
