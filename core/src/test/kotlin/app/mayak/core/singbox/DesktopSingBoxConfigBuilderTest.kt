package app.mayak.core.singbox

import app.mayak.core.parser.ProfileInputParser
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopSingBoxConfigBuilderTest {
    @Test
    fun buildsMixedInboundForDesktop() {
        val profile = ProfileInputParser(clock = { 42L }).parse(
            "vless://11111111-1111-1111-1111-111111111111@example.com:443" +
                "?security=reality&sni=apple.com&fp=chrome&pbk=pubkey#Main"
        )
        val config = SingBoxConfigBuilder().build(
            profile = profile,
            settings = SingBoxConfigSettings(
                inboundMode = InboundMode.Mixed,
                mixedListenPort = 2080
            )
        )

        val inbound = Json.parseToJsonElement(config)
            .jsonObject["inbounds"]!!
            .jsonArray.first()
            .jsonObject

        assertEquals("mixed", inbound["type"]!!.jsonPrimitive.content)
        assertEquals("127.0.0.1", inbound["listen"]!!.jsonPrimitive.content)
        assertEquals("2080", inbound["listen_port"]!!.jsonPrimitive.content)
    }
}
