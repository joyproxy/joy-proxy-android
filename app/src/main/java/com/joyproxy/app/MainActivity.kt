package com.joyproxy.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import com.joyproxy.app.ui.AppPickerScreen
import com.joyproxy.app.ui.HomeScreen
import com.joyproxy.app.ui.MainViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    private val vpnPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                viewModel.startVpn()
            }
        }

    private val appPickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
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
                            Intent(this, AppPickerActivity::class.java).apply {
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

class AppPickerActivity : ComponentActivity() {
    companion object {
        const val EXTRA_SELECTED_APPS = "selected_apps"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initial = intent.getStringArrayListExtra(EXTRA_SELECTED_APPS)?.toSet() ?: emptySet()
        setContent {
            MaterialTheme {
                AppPickerScreen(
                    initialSelection = initial,
                    onDone = { selected ->
                        setResult(
                            Activity.RESULT_OK,
                            Intent().putStringArrayListExtra(EXTRA_SELECTED_APPS, ArrayList(selected)),
                        )
                        finish()
                    },
                    onCancel = { finish() },
                )
            }
        }
    }
}
