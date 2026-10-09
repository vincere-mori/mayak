package app.mayak.core.xray

import app.mayak.core.model.ProxyProfile
import kotlinx.serialization.json.*

class XrayConfigBuilder {
    private val json = Json { prettyPrint = true }
    fun build(profile: ProxyProfile, port: Int, interfaceName: String? = null): String {
        val v = requireNotNull(profile.vless)
        val config = buildJsonObject {
            put("log", buildJsonObject { put("loglevel", "warning") })
            put("inbounds", buildJsonArray { add(buildJsonObject {
                put("listen", "127.0.0.1"); put("port", port); put("protocol", "socks")
                put("settings", buildJsonObject { put("auth", "noauth"); put("udp", true) })
            }) })
            put("outbounds", buildJsonArray { add(buildJsonObject {
                put("protocol", "vless")
                put("settings", buildJsonObject { put("vnext", buildJsonArray { add(buildJsonObject {
                    put("address", v.server); put("port", v.port)
                    put("users", buildJsonArray { add(buildJsonObject {
                        put("id", v.uuid); put("encryption", "none")
                        v.flow?.let { put("flow", it) }
                    }) })
                }) }) })
                put("streamSettings", buildJsonObject {
                    put("network", v.transport); put("security", "reality")
                    put("realitySettings", buildJsonObject {
                        put("serverName", v.serverName); put("fingerprint", v.fingerprint)
                        put("publicKey", v.publicKey)
                        v.shortId?.let { put("shortId", it) }
                        v.spiderX?.let { put("spiderX", it) }
                        v.postQuantumVerify?.let { put("mldsa65Verify", it) }
                    })
                    if (v.transport == "xhttp") put("xhttpSettings", buildJsonObject {
                        put("path", v.transportPath); put("mode", v.transportMode)
                        if (v.transportHost.isNotBlank()) put("host", v.transportHost)
                        v.transportExtra?.let { put("extra", Json.parseToJsonElement(it).jsonObject) }
                    })
                    interfaceName?.let { put("sockopt", buildJsonObject { put("interface", it) }) }
                })
            }) })
        }
        return json.encodeToString(JsonObject.serializer(), config)
    }
}
