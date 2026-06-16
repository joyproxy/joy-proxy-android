package com.joyproxy.app

import android.Manifest
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.joyproxy.app.ui.theme.JoyProxyTheme
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.joyproxy.app.ui.AppPickerActivity
import com.joyproxy.app.ui.HomeScreen
import com.joyproxy.app.ui.MainViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    private val vpnPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                requestNotificationPermissionThenStart()
            } else {
                viewModel.showMessage("需要系统授权才能建立代理连接")
            }
        }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            viewModel.startVpn()
        }

    private val appPickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val apps = result.data?.getStringArrayListExtra(AppPickerActivity.EXTRA_SELECTED_APPS)
                if (apps != null) {
                    viewModel.setSelectedApps(apps.toSet())
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            JoyProxyTheme {
                HomeScreen(
                    viewModel = viewModel,
                    onConnect = ::beginConnect,
                    onPickApps = {
                        appPickerLauncher.launch(
                            android.content.Intent(this, AppPickerActivity::class.java).apply {
                                putStringArrayListExtra(
                                    AppPickerActivity.EXTRA_SELECTED_APPS,
                                    ArrayList(viewModel.settings.value.selectedApps),
                                )
                            },
                        )
                    },
                )
            }
        }
    }

    private fun beginConnect() {
        lifecycleScope.launch {
            viewModel.flushSettings()
            requestVpnPermission()
        }
    }

    private fun requestVpnPermission() {
        val prepareIntent = VpnService.prepare(this)
        if (prepareIntent != null) {
            vpnPermissionLauncher.launch(prepareIntent)
        } else {
            requestNotificationPermissionThenStart()
        }
    }

    private fun requestNotificationPermissionThenStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted =
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        viewModel.startVpn()
    }
}
