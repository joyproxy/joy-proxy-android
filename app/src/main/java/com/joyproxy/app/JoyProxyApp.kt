package com.joyproxy.app

import android.app.Application
import android.app.NotificationManager
import android.content.ClipboardManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.PowerManager
import android.util.Log
import androidx.core.content.getSystemService
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.SetupOptions
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class JoyProxyApp : Application() {
    val libboxReady = CompletableDeferred<Unit>()

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        instance = this
    }

    override fun onCreate() {
        super.onCreate()
        installCrashLogger()
        Libbox.setLocale(Locale.getDefault().toLanguageTag().replace("-", "_"))
        GlobalScope.launch(Dispatchers.IO) {
            try {
                initializeLibbox()
                libboxReady.complete(Unit)
            } catch (e: Exception) {
                libboxReady.completeExceptionally(e)
            }
        }
    }

    // 把任何线程上未捕获的异常完整写入 crash.log，方便定位崩溃根因（路径见日志）。
    private fun installCrashLogger() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val dir = getExternalFilesDir(null) ?: filesDir
                val file = File(dir, "crash.log")
                val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
                file.appendText(
                    "==== $ts thread=${thread.name} ====\n" +
                        Log.getStackTraceString(throwable) + "\n\n",
                )
                Log.e(TAG, "Uncaught exception on ${thread.name}", throwable)
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun initializeLibbox() {
        val baseDir = filesDir
        baseDir.mkdirs()
        val workingDir = getExternalFilesDir(null) ?: filesDir
        workingDir.mkdirs()
        val tempDir = cacheDir
        tempDir.mkdirs()
        Libbox.setup(
            SetupOptions().also {
                it.basePath = baseDir.path
                it.workingPath = workingDir.path
                it.tempPath = tempDir.path
                it.fixAndroidStack = true
                it.logMaxLines = 3000
                it.debug = BuildConfig.DEBUG
            },
        )
        Libbox.redirectStderr(File(workingDir, "stderr.log").path)
    }

    companion object {
        private const val TAG = "JoyProxyApp"

        lateinit var instance: JoyProxyApp
            private set

        val notification by lazy { instance.getSystemService<NotificationManager>()!! }
        val connectivity by lazy { instance.getSystemService<ConnectivityManager>()!! }
        val packageManager by lazy { instance.packageManager }
        val powerManager by lazy { instance.getSystemService<PowerManager>()!! }
        val wifiManager by lazy { instance.getSystemService<WifiManager>()!! }
        val clipboard by lazy { instance.getSystemService<ClipboardManager>()!! }
    }
}
