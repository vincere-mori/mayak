package app.mayak.core.parser

import app.mayak.core.model.ProfileKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class ProfileInputParserTest {
    private val parser = ProfileInputParser(clock = { 42L })

    @Test
    fun parsesVlessRealityLink() {
        val profile = parser.parse(
            """
            vless://11111111-1111-1111-1111-111111111111@example.com:443
            ?security=reality&sni=apple.com&fp=chrome&pbk=pubkey&sid=abcd
            &spx=%2F&pqv=verify-token&flow=xtls-rprx-vision#Main
            """.trimIndent()
        )

        assertEquals(ProfileKind.VlessReality, profile.kind)
        assertEquals("Main", profile.name)
        assertEquals("example.com", profile.host)
        assertEquals(443, profile.port)
        assertEquals(42L, profile.createdAtMillis)

        val vless = assertNotNull(profile.vless)
        assertEquals("apple.com", vless.serverName)
        assertEquals("pubkey", vless.publicKey)
        assertEquals("abcd", vless.shortId)
        assertEquals("xtls-rprx-vision", vless.flow)
        assertEquals("/", vless.spiderX)
        assertEquals("verify-token", vless.postQuantumVerify)
    }

    @Test
    fun rejectsNonRealityVless() {
        val error = assertFailsWith<ProfileParseException> {
            parser.parse("vless://11111111-1111-1111-1111-111111111111@example.com:443?security=tls#Main")
        }

        assertEquals("нужен VLESS Reality ключ", error.message)
    }

    @Test
    fun parsesLongRealityLinkFromPanel() {
        val profile = parser.parse(
            "vless://11111111-1111-1111-1111-111111111111@203.0.113.10:443" +
                "?type=tcp&encryption=none&security=reality&pbk=pubkey&fp=chrome" +
                "&sni=www.microsoft.com&sid=2670&spx=%2F&pqv=${"a".repeat(4096)}" +
                "&flow=xtls-rprx-vision#default-test"
        )

        assertEquals("default-test", profile.name)
        assertEquals("203.0.113.10", profile.host)
        assertEquals("2670", profile.vless?.shortId)
        assertEquals(4096, profile.vless?.postQuantumVerify?.length)
    }

    @Test
    fun rejectsRawSingBoxJson() {
        val error = assertFailsWith<ProfileParseException> {
            parser.parse("""{"inbounds":[],"outbounds":[]}""")
        }

        assertEquals("поддерживается только vless:// Reality ключ", error.message)
    }
}
