package com.joyproxy.app.config

import kotlinx.serialization.Serializable

@Serializable
data class SavedProxy(
    val id: String,
    val protocol: ProxyProtocol,
    val host: String,
    val port: Int,
    val username: String = "",
    val password: String = "",
) {
    fun displayLabel(): String {
        val auth = if (username.isNotBlank()) " · $username" else ""
        return "${protocol.name} $host:$port$auth"
    }

    companion object {
        private const val MAX_HISTORY = 20

        fun fromSettings(settings: ProxySettings): SavedProxy? {
            if (!settings.isValid()) return null
            val host = settings.host.trim()
            val username = settings.username.trim()
            return SavedProxy(
                id = fingerprint(settings.protocol, host, settings.port, username),
                protocol = settings.protocol,
                host = host,
                port = settings.port,
                username = username,
                password = settings.password,
            )
        }

        fun fingerprint(
            protocol: ProxyProtocol,
            host: String,
            port: Int,
            username: String,
        ): String = "${protocol.name}|${host.lowercase()}|$port|$username"

        fun mergeHistory(existing: List<SavedProxy>, entry: SavedProxy): List<SavedProxy> {
            val updated = listOf(entry) + existing.filterNot { it.id == entry.id }
            return updated.take(MAX_HISTORY)
        }
    }
}
