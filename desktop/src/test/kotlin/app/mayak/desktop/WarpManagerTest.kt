package app.mayak.desktop

import kotlin.test.Test
import kotlin.test.assertEquals

class WarpManagerTest {
    @Test
    fun registrationUsesIpv4EndpointAndHostPort() {
        val credentials = WarpManager.parseRegistrationResponse(
            response = """
                {
                  "config": {
                    "client_id": "AQID",
                    "interface": {
                      "addresses": {
                        "v4": "172.16.0.2",
                        "v6": "2606:4700:110::2"
                      }
                    },
                    "peers": [{
                      "public_key": "peer",
                      "endpoint": {
                        "v4": "162.159.192.9:0",
                        "host": "engage.cloudflareclient.com:2408",
                        "ports": [2408, 500]
                      }
                    }]
                  }
                }
            """.trimIndent(),
            privateKey = "private"
        )

        assertEquals("162.159.192.9:2408", credentials.endpoint)
        assertEquals(listOf(1, 2, 3), credentials.reserved)
    }
}
