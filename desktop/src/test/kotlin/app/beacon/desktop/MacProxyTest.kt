package app.beacon.desktop

import kotlin.test.Test
import kotlin.test.assertTrue

class MacProxyTest {
    @Test
    fun enablesAndRestoresActiveNetworkServices() {
        val commands = mutableListOf<List<String>>()
        val proxy = MacProxy { command ->
            commands += command
            if (command == listOf("networksetup", "-listallnetworkservices")) {
                """
                An asterisk (*) denotes that a network service is disabled.
                Wi-Fi
                *Old Ethernet
                USB 10/100/1000 LAN
                """.trimIndent()
            } else {
                ""
            }
        }

        proxy.enable(2080)
        proxy.restore()

        assertTrue(commands.any { it == listOf("networksetup", "-setwebproxy", "Wi-Fi", "127.0.0.1", "2080") })
        assertTrue(commands.any { it == listOf("networksetup", "-setsocksfirewallproxy", "USB 10/100/1000 LAN", "127.0.0.1", "2080") })
        assertTrue(commands.any { it == listOf("networksetup", "-setwebproxystate", "Wi-Fi", "off") })
        assertTrue(commands.none { it.contains("Old Ethernet") })
    }
}
