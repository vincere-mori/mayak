package app.beacon.core.singbox

import app.beacon.core.model.DnsMode
import app.beacon.core.model.ProxyProfile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class SingBoxConfigBuilder(
    private val json: Json = Json {
        prettyPrint = true
        explicitNulls = false
    }
) {
    fun build(profile: ProxyProfile, settings: SingBoxConfigSettings = SingBoxConfigSettings()): String {
        val vless = profile.vless ?: error("vless profile missing")
        val config = buildJsonObject {
            put("log", buildJsonObject {
                put("level", "info")
                put("timestamp", true)
            })
            put("dns", dns(settings, vless.server))
            if (settings.warpEnabled && settings.warpPrivateKey.isNotBlank()) {
                put("endpoints", buildJsonArray {
                    add(warpEndpoint(settings))
                })
            }
            put("inbounds", buildJsonArray {
                add(
                    when (settings.inboundMode) {
                        InboundMode.Tun -> tun(settings)
                        InboundMode.Mixed -> mixed(settings)
                    }
                )
            })
            put("outbounds", buildJsonArray {
                add(vlessOutbound(profile))
                add(taggedOutbound("direct", "direct"))
            })
            put("route", route(settings))
            put("experimental", buildJsonObject {
                put("clash_api", buildJsonObject {
                    put("external_controller", "127.0.0.1:${settings.clashApiPort}")
                    put("default_mode", "rule")
                })
            })
        }

        return json.encodeToString(JsonObject.serializer(), config)
    }

    private fun dns(settings: SingBoxConfigSettings, proxyServer: String): JsonObject {
        return buildJsonObject {
            put("strategy", if (settings.ipv6Enabled) "prefer_ipv4" else "ipv4_only")
            put("servers", buildJsonArray {
                // Bootstrap: plain UDP for resolving the proxy/DoH server hostname
                add(buildJsonObject {
                    put("type", "udp")
                    put("tag", "bootstrap")
                    put("server", "1.1.1.1")
                })
                // Remote: DoH through the VLESS proxy — resolves everything else
                add(buildJsonObject {
                    put("type", "https")
                    put("tag", "remote")
                    put("server", settings.dnsMode.server)
                    put("path", settings.dnsMode.path)
                    if (settings.dnsMode.needsDomainResolver) {
                        put("domain_resolver", "bootstrap")
                    }
                    put("detour", "proxy")
                })
                // Note: we intentionally do NOT add a warp-dns server here.
                // WireGuard endpoints are L3 tunnels and cannot be used as DNS detour
                // (which requires an L4 connection). Google domains are resolved via
                // the normal "remote" server; the route rule then sends the actual
                // traffic through the WARP endpoint. DNS and routing are independent.
            })
            put("rules", buildJsonArray {
                if (!proxyServer.isIpAddress()) {
                    add(buildJsonObject {
                        put("domain", JsonArray(listOf(JsonPrimitive(proxyServer))))
                        put("server", "bootstrap")
                    })
                }
            })
            put("final", "remote")
        }
    }

    private fun warpDomains() = listOf(
        "google.com", "googleapis.com", "googleusercontent.com",
        "gstatic.com", "ggpht.com", "gvt1.com", "gvt2.com", "gvt3.com",
        "recaptcha.net", "youtube.com", "youtubei.googleapis.com",
        "googlevideo.com", "ytimg.com", "ai.google.dev"
    )

    private fun tun(settings: SingBoxConfigSettings): JsonObject {
        val address = if (settings.ipv6Enabled) {
            listOf("172.19.0.1/30", "fdfe:dcba:9876::1/126")
        } else {
            listOf("172.19.0.1/30")
        }

        return buildJsonObject {
            put("type", "tun")
            put("tag", "tun-in")
            put("address", JsonArray(address.map(::JsonPrimitive)))
            put("auto_route", true)
            put("strict_route", true)
            put("stack", "mixed")
        }
    }

    private fun mixed(settings: SingBoxConfigSettings): JsonObject {
        return buildJsonObject {
            put("type", "mixed")
            put("tag", "mixed-in")
            put("listen", settings.mixedListenHost)
            put("listen_port", settings.mixedListenPort)
        }
    }

    private fun vlessOutbound(profile: ProxyProfile): JsonObject {
        val vless = profile.vless ?: error("vless profile missing")
        return buildJsonObject {
            put("type", "vless")
            put("tag", "proxy")
            put("server", vless.server)
            put("server_port", vless.port)
            put("uuid", vless.uuid)
            if (!vless.server.isIpAddress()) {
                put("domain_resolver", "bootstrap")
            }
            vless.flow?.let { put("flow", it) }
            put("network", "tcp")
            put("tls", buildJsonObject {
                put("enabled", true)
                put("server_name", vless.serverName)
                put("utls", buildJsonObject {
                    put("enabled", true)
                    put("fingerprint", vless.fingerprint)
                })
                put("reality", buildJsonObject {
                    put("enabled", true)
                    put("public_key", vless.publicKey)
                    vless.shortId?.let { put("short_id", it) }
                })
            })
        }
    }

    private fun taggedOutbound(type: String, tag: String): JsonObject {
        return buildJsonObject {
            put("type", type)
            put("tag", tag)
        }
    }

    private fun warpEndpoint(settings: SingBoxConfigSettings): JsonObject {
        val (epHost, epPort) = parseEndpoint(settings.warpEndpoint)
        return buildJsonObject {
            put("type", "wireguard")
            put("tag", "warp")
            put("detour", "proxy")
            // "system" is NOT a valid field for endpoints[] in sing-box 1.11+
            // (it was only for the old outbounds[] wireguard type — removed to avoid parse issues)
            put("mtu", 1280)
            put("address", buildJsonArray {
                add(JsonPrimitive("${settings.warpLocalAddressV4}/32"))
                if (settings.warpLocalAddressV6.isNotBlank()) {
                    add(JsonPrimitive("${settings.warpLocalAddressV6}/128"))
                }
            })
            put("private_key", settings.warpPrivateKey)
            put("peers", buildJsonArray {
                add(buildJsonObject {
                    put("address", epHost)
                    put("port", epPort)
                    put("public_key", settings.warpPeerPublicKey)
                    put("allowed_ips", buildJsonArray {
                        add(JsonPrimitive("0.0.0.0/0"))
                        add(JsonPrimitive("::/0"))
                    })
                    put("persistent_keepalive_interval", 30)
                    put("reserved", JsonArray(normalizedWarpReserved(settings).map(::JsonPrimitive)))
                })
            })
        }
    }

    private fun normalizedWarpReserved(settings: SingBoxConfigSettings): List<Int> {
        return (settings.warpReserved.map { it.coerceIn(0, 255) } + listOf(0, 0, 0)).take(3)
    }

    private fun parseEndpoint(endpoint: String): Pair<String, Int> {
        val text = endpoint.trim()
        if (text.startsWith("[")) {
            val host = text.substringAfter("[").substringBefore("]")
            val port = text.substringAfter("]:", "").toIntOrNull() ?: 2408
            return host to port
        }

        val host = text.substringBeforeLast(":", text)
        val port = text.substringAfterLast(":", "").toIntOrNull() ?: 2408
        return host to port
    }

    private fun route(settings: SingBoxConfigSettings = SingBoxConfigSettings()): JsonObject {
        return buildJsonObject {
            put("auto_detect_interface", true)
            put("default_domain_resolver", "remote")
            put("rules", buildJsonArray {
                add(buildJsonObject { put("action", "sniff") })
                add(buildJsonObject { put("protocol", "dns"); put("action", "hijack-dns") })
                add(buildJsonObject { put("ip_is_private", true); put("action", "route"); put("outbound", "direct") })
                if (settings.warpEnabled && settings.warpPrivateKey.isNotBlank()) {
                    add(buildJsonObject {
                        put("domain_suffix", buildJsonArray {
                            warpDomains().forEach { add(JsonPrimitive(it)) }
                        })
                        put("action", "route")
                        put("outbound", "warp")
                    })
                }
            })
            put("final", "proxy")
        }
    }

    private fun String.isIpAddress(): Boolean {
        return matches(Regex("""^\d{1,3}(\.\d{1,3}){3}$""")) || contains(":")
    }
}

data class SingBoxConfigSettings(
    val dnsMode: DnsMode = DnsMode.Cloudflare,
    val ipv6Enabled: Boolean = false,
    val inboundMode: InboundMode = InboundMode.Tun,
    val mixedListenHost: String = "127.0.0.1",
    val mixedListenPort: Int = 2080,
    val clashApiPort: Int = 9095,
    val warpEnabled: Boolean = false,
    val warpPrivateKey: String = "",
    val warpLocalAddressV4: String = "",
    val warpLocalAddressV6: String = "",
    val warpPeerPublicKey: String = "",
    val warpEndpoint: String = "",
    val warpReserved: List<Int> = listOf(0, 0, 0)
)
