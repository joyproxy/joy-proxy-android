package com.joyproxy.app.config

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

object ConfigBuilder {
    private val json = Json { prettyPrint = false }

    fun build(settings: ProxySettings): String {
        val config = buildJsonObject {
            putJsonObject("log") {
                put("level", "info")
            }
            put("dns", buildDns(settings))
            putJsonArray("inbounds") {
                add(
                    buildJsonObject {
                        put("type", "tun")
                        put("tag", "tun-in")
                        put("interface_name", "tun0")
                        putJsonArray("address") {
                            add(JsonPrimitive("172.19.0.1/30"))
                        }
                        put("mtu", 9000)
                        put("auto_route", true)
                        put("strict_route", true)
                        put("stack", "system")
                    },
                )
            }
            putJsonArray("outbounds") {
                add(buildProxyOutbound(settings))
                add(
                    buildJsonObject {
                        put("type", "direct")
                        put("tag", "direct")
                    },
                )
                add(
                    buildJsonObject {
                        put("type", "block")
                        put("tag", "block")
                    },
                )
            }
            putJsonObject("route") {
                putJsonArray("rules") {
                    add(
                        buildJsonObject {
                            put("action", "sniff")
                        },
                    )
                    add(
                        buildJsonObject {
                            put("protocol", "dns")
                            put("action", "hijack-dns")
                        },
                    )
                    add(
                        buildJsonObject {
                            put("protocol", "udp")
                            put("port", 443)
                            put("action", "route")
                            put("outbound", "block")
                        },
                    )
                }
                put("final", "proxy")
                put("auto_detect_interface", true)
            }
        }
        return json.encodeToString(JsonObject.serializer(), config)
    }

    private fun buildDns(settings: ProxySettings): JsonObject {
        return when (settings.dnsMode) {
            DnsMode.FAKE_IP -> buildJsonObject {
                putJsonArray("servers") {
                    add(
                        buildJsonObject {
                            put("tag", "dns-remote")
                            put("address", settings.dohUrl)
                            put("detour", "proxy")
                        },
                    )
                    add(
                        buildJsonObject {
                            put("tag", "dns-fakeip")
                            put("address", "fakeip")
                        },
                    )
                    add(
                        buildJsonObject {
                            put("tag", "dns-local")
                            put("address", "local")
                            put("detour", "direct")
                        },
                    )
                }
                putJsonArray("rules") {
                    add(
                        buildJsonObject {
                            putJsonArray("query_type") {
                                add(JsonPrimitive("A"))
                                add(JsonPrimitive("AAAA"))
                            }
                            put("server", "dns-fakeip")
                        },
                    )
                }
                putJsonObject("fakeip") {
                    put("enabled", true)
                    put("inet4_range", "198.18.0.0/15")
                }
                put("final", "dns-remote")
                put("strategy", "prefer_ipv4")
                put("independent_cache", true)
            }
            DnsMode.DOH -> buildJsonObject {
                putJsonArray("servers") {
                    add(
                        buildJsonObject {
                            put("tag", "dns-doh")
                            put("address", settings.dohUrl)
                            put("detour", "proxy")
                        },
                    )
                    add(
                        buildJsonObject {
                            put("tag", "dns-local")
                            put("address", "local")
                            put("detour", "direct")
                        },
                    )
                }
                put("final", "dns-doh")
                put("strategy", "prefer_ipv4")
            }
            DnsMode.CUSTOM -> buildJsonObject {
                putJsonArray("servers") {
                    add(
                        buildJsonObject {
                            put("tag", "dns-custom")
                            put("address", "tcp://${settings.customDns}")
                            put("detour", "proxy")
                        },
                    )
                    add(
                        buildJsonObject {
                            put("tag", "dns-local")
                            put("address", "local")
                            put("detour", "direct")
                        },
                    )
                }
                put("final", "dns-custom")
                put("strategy", "prefer_ipv4")
            }
            DnsMode.SYSTEM -> buildJsonObject {
                putJsonArray("servers") {
                    add(
                        buildJsonObject {
                            put("tag", "dns-local")
                            put("address", "local")
                            put("detour", "direct")
                        },
                    )
                }
                put("final", "dns-local")
                put("strategy", "prefer_ipv4")
            }
        }
    }

    private fun buildProxyOutbound(settings: ProxySettings): JsonObject {
        return buildJsonObject {
            put("tag", "proxy")
            when (settings.protocol) {
                ProxyProtocol.SOCKS5 -> {
                    put("type", "socks")
                    put("server", settings.host)
                    put("server_port", settings.port)
                    put("version", "5")
                    if (settings.username.isNotBlank()) {
                        put("username", settings.username)
                        put("password", settings.password)
                    }
                }
                ProxyProtocol.HTTP -> {
                    put("type", "http")
                    put("server", settings.host)
                    put("server_port", settings.port)
                    if (settings.username.isNotBlank()) {
                        put("username", settings.username)
                        put("password", settings.password)
                    }
                }
            }
        }
    }
}
