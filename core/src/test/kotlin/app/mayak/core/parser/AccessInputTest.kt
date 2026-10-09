package app.mayak.core.parser

import kotlin.test.*

class AccessInputTest {
    @Test fun readsSharedSubscriptionWithoutChangingItsToken() {
        val link = "https://vpn.example/sub/ab+cd?token=a%2Fb"
        val shared = "mayak://import?url=" + java.net.URLEncoder.encode(link, "UTF-8")
        assertEquals(link, AccessInput.normalize(shared))
        assertTrue(AccessInput.isSubscription(shared))
        assertFalse(AccessInput.isSubscription("https://user:password@vpn.example/sub"))
        assertFalse(AccessInput.isSubscription("file:///etc/passwd"))
    }

    @Test fun preservesXhttpParametersAndLiteralPlusInName() {
        val profile = ProfileInputParser().parse("vless://11111111-1111-1111-1111-111111111111@vpn.example:8443?security=reality&pbk=key&sni=example.org&type=xhttp&path=%2Froute&mode=packet-up&fp=firefox#Home+Phone")
        assertEquals("Home+Phone", profile.name)
        assertEquals("xhttp", profile.vless?.transport)
        assertEquals("/route", profile.vless?.transportPath)
        assertEquals("packet-up", profile.vless?.transportMode)
        assertEquals("firefox", profile.vless?.fingerprint)
        val old = profile.copy(vless = profile.vless!!.copy(transport = "tcp"), name = "Custom name")
        assertEquals("xhttp", old.restoreTransport().vless?.transport)
        assertEquals("Custom name", old.restoreTransport().name)
        assertEquals(old.id, old.restoreTransport().id)
    }

    @Test fun rejectsUnsupportedTransportInsteadOfSilentlyUsingTcp() {
        assertFailsWith<ProfileParseException> {
            ProfileInputParser().parse("vless://11111111-1111-1111-1111-111111111111@vpn.example:443?security=reality&pbk=key&sni=example.org&type=quic")
        }
    }
}
