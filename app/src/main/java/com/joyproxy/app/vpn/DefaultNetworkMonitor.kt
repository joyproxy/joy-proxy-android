package com.joyproxy.app.vpn

import android.net.Network
import android.os.Build
import io.nekohasekai.libbox.InterfaceUpdateListener
import com.joyproxy.app.JoyProxyApp
import java.net.NetworkInterface

object DefaultNetworkMonitor {
    var defaultNetwork: Network? = null
    private var listener: InterfaceUpdateListener? = null

    suspend fun start() {
        DefaultNetworkListener.start(this) {
            defaultNetwork = it
            checkDefaultInterfaceUpdate(it)
        }
        defaultNetwork =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                JoyProxyApp.connectivity.activeNetwork
            } else {
                DefaultNetworkListener.get()
            }
    }

    suspend fun stop() {
        DefaultNetworkListener.stop(this)
    }

    fun setListener(listener: InterfaceUpdateListener?) {
        this.listener = listener
        checkDefaultInterfaceUpdate(defaultNetwork)
    }

    private fun checkDefaultInterfaceUpdate(newNetwork: Network?) {
        val currentListener = listener ?: return
        if (newNetwork != null) {
            for (i in 0 until 10) {
                val linkProperties = JoyProxyApp.connectivity.getLinkProperties(newNetwork)
                if (linkProperties == null) {
                    Thread.sleep(100)
                    continue
                }
                val interfaceIndex =
                    try {
                        NetworkInterface.getByName(linkProperties.interfaceName).index
                    } catch (e: Exception) {
                        Thread.sleep(100)
                        continue
                    }
                currentListener.updateDefaultInterface(linkProperties.interfaceName, interfaceIndex, false, false)
                return
            }
        } else {
            currentListener.updateDefaultInterface("", -1, false, false)
        }
    }
}
