package app.beacon.core.parser

import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SubscriptionParserTest {
    private val parser = SubscriptionParser(ProfileInputParser(clock = { 42L }))

    private val nl = "vless://11111111-1111-1111-1111-111111111111@nl1.example.com:443" +
        "?security=reality&sni=apple.com&fp=chrome&pbk=pubkey&sid=ab#NL-1"
    private val de = "vless://11111111-1111-1111-1111-111111111111@de1.example.com:443" +
        "?security=reality&sni=apple.com&fp=chrome&pbk=pubkey&sid=cd#DE-1"

    @Test
    fun parsesPlainTextBody() {
        val servers = parser.parse("$nl\n$de\n")

        assertEquals(2, servers.size)
        assertEquals(listOf("nl1.example.com", "de1.example.com"), servers.map { it.host })
    }

    @Test
    fun parsesBase64Body() {
        val encoded = Base64.getEncoder().encodeToString("$nl\n$de".toByteArray())

        val servers = parser.parse(encoded)

        assertEquals(2, servers.size)
    }

    @Test
    fun skipsUnsupportedAndJunkLines() {
        val body = """
            # comment line
            ss://junk@host:1234#Other
            $nl
            not-a-link
            vmess://ey000
            $de
        """.trimIndent()

        val servers = parser.parse(body)

        assertEquals(2, servers.size)
    }

    @Test
    fun dropsDuplicateServers() {
        val servers = parser.parse("$nl\n$nl\n$de")

        assertEquals(2, servers.size)
    }

    @Test
    fun emptyBodyYieldsNoServers() {
        assertTrue(parser.parse("   \n  ").isEmpty())
    }
}
