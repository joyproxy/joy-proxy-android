package com.joyproxy.app.config

enum class DnsProvider(
    val label: String,
    val dohUrl: String,
    val plainDns: String,
) {
    GOOGLE("Google", "https://dns.google/dns-query", "8.8.8.8"),
    CLOUDFLARE("Cloudflare", "https://cloudflare-dns.com/dns-query", "1.1.1.1"),
    OPENDNS("OpenDNS", "https://doh.opendns.com/dns-query", "208.67.222.222"),
    QUAD9("Quad9", "https://dns.quad9.net/dns-query", "9.9.9.9"),
    ALIDNS("阿里 DNS", "https://dns.alidns.com/dns-query", "223.5.5.5"),
    ;

    companion object {
        fun fromId(id: String?): DnsProvider =
            entries.find { it.name == id } ?: GOOGLE

        fun fromDohUrl(url: String): DnsProvider =
            entries.find { it.dohUrl == url } ?: GOOGLE

        fun fromPlainDns(dns: String): DnsProvider =
            entries.find { it.plainDns == dns } ?: GOOGLE
    }
}
