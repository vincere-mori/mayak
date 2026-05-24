package app.beacon.desktop

class WindowsProxy(
    private val commandRunner: (List<String>) -> String = ::runCommand
) : SystemProxy {
    private var previous: ProxySnapshot? = null

    override fun enable(port: Int) {
        if (previous == null) previous = readSnapshot()

        commandRunner(
            listOf(
                "reg", "add", KEY, "/v", "ProxyEnable",
                "/t", "REG_DWORD", "/d", "1", "/f"
            )
        )
        commandRunner(
            listOf(
                "reg", "add", KEY, "/v", "ProxyServer",
                "/t", "REG_SZ", "/d", "127.0.0.1:$port", "/f"
            )
        )
        commandRunner(
            listOf(
                "reg", "add", KEY, "/v", "ProxyOverride",
                "/t", "REG_SZ", "/d", "<local>", "/f"
            )
        )
    }

    override fun restore() {
        val snapshot = previous ?: return

        commandRunner(
            listOf(
                "reg", "add", KEY, "/v", "ProxyEnable",
                "/t", "REG_DWORD", "/d", snapshot.enabled.toString(), "/f"
            )
        )
        snapshot.server?.let {
            commandRunner(listOf("reg", "add", KEY, "/v", "ProxyServer", "/t", "REG_SZ", "/d", it, "/f"))
        } ?: commandRunner(listOf("reg", "delete", KEY, "/v", "ProxyServer", "/f"))

        snapshot.override?.let {
            commandRunner(listOf("reg", "add", KEY, "/v", "ProxyOverride", "/t", "REG_SZ", "/d", it, "/f"))
        } ?: commandRunner(listOf("reg", "delete", KEY, "/v", "ProxyOverride", "/f"))

        previous = null
    }

    private fun readSnapshot(): ProxySnapshot {
        return ProxySnapshot(
            enabled = parseDword(readValue("ProxyEnable")) ?: 0,
            server = readValue("ProxyServer")?.substringAfter("REG_SZ")?.trim(),
            override = readValue("ProxyOverride")?.substringAfter("REG_SZ")?.trim()
        )
    }

    private fun parseDword(value: String?): Int? {
        val raw = value?.substringAfter("REG_DWORD")?.trim() ?: return null
        return if (raw.startsWith("0x", ignoreCase = true)) {
            raw.removePrefix("0x").removePrefix("0X").toIntOrNull(16)
        } else {
            raw.toIntOrNull()
        }
    }

    private fun readValue(name: String): String? {
        return runCatching { commandRunner(listOf("reg", "query", KEY, "/v", name)) }
            .getOrNull()
            ?.lineSequence()
            ?.firstOrNull { it.contains(name) }
    }

    private data class ProxySnapshot(
        val enabled: Int,
        val server: String?,
        val override: String?
    )

    private companion object {
        const val KEY = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings"

        fun runCommand(command: List<String>): String {
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            if (!process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly()
            }
            return output
        }
    }
}
