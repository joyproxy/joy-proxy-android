package com.joyproxy.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.joyproxy.app.config.DnsMode
import com.joyproxy.app.config.ProxyProtocol
import com.joyproxy.app.config.ProxyScope
import com.joyproxy.app.config.ProxySettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsRepository(private val context: Context) {
    private object Keys {
        val PROTOCOL = stringPreferencesKey("protocol")
        val HOST = stringPreferencesKey("host")
        val PORT = intPreferencesKey("port")
        val USERNAME = stringPreferencesKey("username")
        val PASSWORD = stringPreferencesKey("password")
        val SCOPE = stringPreferencesKey("scope")
        val SELECTED_APPS = stringSetPreferencesKey("selected_apps")
        val DNS_MODE = stringPreferencesKey("dns_mode")
        val CUSTOM_DNS = stringPreferencesKey("custom_dns")
        val DOH_URL = stringPreferencesKey("doh_url")
        val CONNECTED = booleanPreferencesKey("connected")
    }

    val settings: Flow<ProxySettings> = context.appPreferencesStore.data.map { prefs ->
        ProxySettings(
            protocol = runCatching { ProxyProtocol.valueOf(prefs[Keys.PROTOCOL] ?: ProxyProtocol.SOCKS5.name) }
                .getOrDefault(ProxyProtocol.SOCKS5),
            host = prefs[Keys.HOST] ?: "",
            port = prefs[Keys.PORT] ?: 1080,
            username = prefs[Keys.USERNAME] ?: "",
            password = prefs[Keys.PASSWORD] ?: "",
            scope = runCatching { ProxyScope.valueOf(prefs[Keys.SCOPE] ?: ProxyScope.GLOBAL.name) }
                .getOrDefault(ProxyScope.GLOBAL),
            selectedApps = prefs[Keys.SELECTED_APPS] ?: emptySet(),
            dnsMode = runCatching { DnsMode.valueOf(prefs[Keys.DNS_MODE] ?: DnsMode.FAKE_IP.name) }
                .getOrDefault(DnsMode.FAKE_IP),
            customDns = prefs[Keys.CUSTOM_DNS] ?: "223.5.5.5",
            dohUrl = prefs[Keys.DOH_URL] ?: "https://dns.alidns.com/dns-query",
            connected = prefs[Keys.CONNECTED] ?: false,
        )
    }

    suspend fun save(settings: ProxySettings) {
        context.appPreferencesStore.edit { prefs ->
            prefs[Keys.PROTOCOL] = settings.protocol.name
            prefs[Keys.HOST] = settings.host
            prefs[Keys.PORT] = settings.port
            prefs[Keys.USERNAME] = settings.username
            prefs[Keys.PASSWORD] = settings.password
            prefs[Keys.SCOPE] = settings.scope.name
            prefs[Keys.SELECTED_APPS] = settings.selectedApps
            prefs[Keys.DNS_MODE] = settings.dnsMode.name
            prefs[Keys.CUSTOM_DNS] = settings.customDns
            prefs[Keys.DOH_URL] = settings.dohUrl
            prefs[Keys.CONNECTED] = settings.connected
        }
    }

    suspend fun setConnected(connected: Boolean) {
        context.appPreferencesStore.edit { prefs ->
            prefs[Keys.CONNECTED] = connected
        }
    }
}
