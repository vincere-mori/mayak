package app.mayak.core.singbox

import app.mayak.core.parser.ProfileInputParser
import app.mayak.core.xray.XrayConfigBuilder
import kotlinx.serialization.json.*
import kotlin.test.*

class XhttpBridgeTest {
    private val profile = ProfileInputParser().parse("vless://11111111-1111-1111-1111-111111111111@vpn.example:8443?security=reality&pbk=key&sni=example.org&type=xhttp&path=%2Froute&mode=packet-up&fp=firefox#Home")

    @Test fun unsupportedCoreCannotConnectAsPlainTcp() {
        assertFailsWith<IllegalArgumentException> { SingBoxConfigBuilder().build(profile) }
    }

    @Test fun bridgeKeepsDnsAndRoutingOnTheProxy() {
        val config = Json.parseToJsonElement(SingBoxConfigBuilder().build(profile, SingBoxConfigSettings(localProxyPort = 10891))).jsonObject
        val outbound = config["outbounds"]!!.jsonArray.first().jsonObject
        assertEquals("socks", outbound["type"]!!.jsonPrimitive.content)
        assertEquals("127.0.0.1", outbound["server"]!!.jsonPrimitive.content)
        assertEquals("proxy", config["route"]!!.jsonObject["final"]!!.jsonPrimitive.content)
        val remoteDns = config["dns"]!!.jsonObject["servers"]!!.jsonArray[1].jsonObject
        assertEquals("proxy", remoteDns["detour"]!!.jsonPrimitive.content)
        val xray = Json.parseToJsonElement(XrayConfigBuilder().build(profile, 10891, "Ethernet")).jsonObject
        val stream = xray["outbounds"]!!.jsonArray.first().jsonObject["streamSettings"]!!.jsonObject
        assertEquals("packet-up", stream["xhttpSettings"]!!.jsonObject["mode"]!!.jsonPrimitive.content)
        assertEquals("firefox", stream["realitySettings"]!!.jsonObject["fingerprint"]!!.jsonPrimitive.content)
        assertEquals("Ethernet", stream["sockopt"]!!.jsonObject["interface"]!!.jsonPrimitive.content)
    }
}
