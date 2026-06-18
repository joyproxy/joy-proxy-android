package com.joyproxy.app.debug

import com.joyproxy.app.JoyProxyApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object DebugSessionLog {
    private const val SESSION_ID = "32ffd8"
    private const val LOG_NAME = "debug-32ffd8.log"
    private const val INGEST = "http://127.0.0.1:7937/ingest/0b809961-5e9a-4844-a502-13ed50edeeab"

    fun log(
        hypothesisId: String,
        location: String,
        message: String,
        data: Map<String, Any?> = emptyMap(),
        runId: String = "pre-fix",
    ) {
        val payload =
            JSONObject()
                .put("sessionId", SESSION_ID)
                .put("hypothesisId", hypothesisId)
                .put("location", location)
                .put("message", message)
                .put("runId", runId)
                .put("timestamp", System.currentTimeMillis())
                .put("data", JSONObject(data))
        val line = payload.toString()
        appendLocal(line)
        postAsync(line)
    }

    private fun appendLocal(line: String) {
        runCatching {
            val dir = JoyProxyApp.instance.getExternalFilesDir(null) ?: JoyProxyApp.instance.filesDir
            File(dir, LOG_NAME).appendText(line + "\n")
        }
    }

    private fun postAsync(line: String) {
        GlobalScope.launch(Dispatchers.IO) {
            runCatching {
                val conn = URL(INGEST).openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("X-Debug-Session-Id", SESSION_ID)
                conn.doOutput = true
                conn.outputStream.use { it.write(line.toByteArray()) }
                conn.responseCode
                conn.disconnect()
            }
        }
    }
}
