package com.joyproxy.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.joyproxy.app.config.ProxySettings
import com.joyproxy.app.config.SavedProxy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ProxyHistoryRepository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }

    private object Keys {
        val HISTORY = stringPreferencesKey("proxy_history")
    }

    val history: Flow<List<SavedProxy>> =
        context.appPreferencesStore.data.map { prefs ->
            decode(prefs[Keys.HISTORY])
        }

    suspend fun upsert(settings: ProxySettings) {
        val entry = SavedProxy.fromSettings(settings) ?: return
        context.appPreferencesStore.edit { prefs ->
            val current = decode(prefs[Keys.HISTORY])
            prefs[Keys.HISTORY] = json.encodeToString(SavedProxy.mergeHistory(current, entry))
        }
    }

    suspend fun delete(id: String) {
        context.appPreferencesStore.edit { prefs ->
            val current = decode(prefs[Keys.HISTORY]).filterNot { it.id == id }
            prefs[Keys.HISTORY] = json.encodeToString(current)
        }
    }

    private fun decode(raw: String?): List<SavedProxy> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<SavedProxy>>(raw) }.getOrDefault(emptyList())
    }
}
