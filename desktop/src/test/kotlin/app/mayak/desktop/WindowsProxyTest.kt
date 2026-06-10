package app.mayak.desktop

import kotlin.test.Test
import kotlin.test.assertTrue

class WindowsProxyTest {
    @Test
    fun restoresHexProxyEnableValue() {
        val commands = mutableListOf<List<String>>()
        val proxy = WindowsProxy { command ->
            commands += command
            when {
                command.contains("ProxyEnable") && command.contains("query") ->
                    "ProxyEnable    REG_DWORD    0x1"
                command.contains("ProxyServer") && command.contains("query") ->
                    "ProxyServer    REG_SZ    old.proxy:8080"
                else -> ""
            }
        }

        proxy.enable(2080)
        proxy.restore()

        assertTrue(commands.any { it.contains("ProxyEnable") && it.contains("/d") && it.contains("1") })
        assertTrue(commands.any { it.contains("ProxyServer") && it.contains("old.proxy:8080") })
    }
}
