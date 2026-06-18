package com.joyproxy.app.config

import kotlinx.serialization.Serializable

@Serializable
enum class ProxyProtocol {
    SOCKS5,
    HTTP,
}

enum class ProxyScope {
    GLOBAL,
    WHITELIST,
    BLACKLIST,
}

enum class DnsMode {
    FAKE_IP,
    DOH,
    CUSTOM,
    SYSTEM,
}

data class ProxySettings(
    val protocol: ProxyProtocol = ProxyProtocol.SOCKS5,
    val host: String = "",
    val port: Int = 1080,
    val username: String = "",
    val password: String = "",
    val scope: ProxyScope = ProxyScope.GLOBAL,
    val selectedApps: Set<String> = emptySet(),
    val dnsMode: DnsMode = DnsMode.SYSTEM,
    val dnsProvider: DnsProvider = DnsProvider.GOOGLE,
    val customDns: String = DnsProvider.GOOGLE.plainDns,
    val dohUrl: String = DnsProvider.GOOGLE.dohUrl,
    val connected: Boolean = false,
) {
    fun isValid(): Boolean = host.isNotBlank() && port in 1..65535

    fun resolvedDohUrl(): String = dnsProvider.dohUrl

    fun resolvedCustomDns(): String = dnsProvider.plainDns
}
