package com.joyproxy.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import com.joyproxy.app.ui.AppPickerActivity
import com.joyproxy.app.ui.HomeScreen
import com.joyproxy.app.ui.MainViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    private val vpnPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                viewModel.startVpn()
            }
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
            MaterialTheme {
                HomeScreen(
                    viewModel = viewModel,
                    onConnect = {
                        val intent = com.joyproxy.app.vpn.VpnController.prepare(this)
                        if (intent != null) {
                            vpnPermissionLauncher.launch(intent)
                        } else {
                            viewModel.startVpn()
                        }
                    },
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
}
