package com.joyproxy.app.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme

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
