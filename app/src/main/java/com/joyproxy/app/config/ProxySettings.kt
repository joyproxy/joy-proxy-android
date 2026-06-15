package com.joyproxy.app.config

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
    val dnsMode: DnsMode = DnsMode.FAKE_IP,
    val customDns: String = "223.5.5.5",
    val dohUrl: String = "https://dns.google/dns-query",
    val connected: Boolean = false,
) {
    fun isValid(): Boolean = host.isNotBlank() && port in 1..65535
}
