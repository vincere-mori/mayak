package app.beacon.desktop

import java.util.concurrent.TimeUnit

class MacProxy(
    private val exec: (List<String>) -> String = ::runCommand
) : SystemProxy {
    private val enabledServices = linkedSetOf<String>()

    override fun enable(port: Int) {
        networkServices().forEach { service ->
            runCatching {
                exec(listOf("networksetup", "-setwebproxy", service, "127.0.0.1", port.toString()))
                exec(listOf("networksetup", "-setsecurewebproxy", service, "127.0.0.1", port.toString()))
                exec(listOf("networksetup", "-setsocksfirewallproxy", service, "127.0.0.1", port.toString()))
                exec(listOf("networksetup", "-setproxybypassdomains", service, "localhost", "127.0.0.1", "::1"))
                exec(listOf("networksetup", "-setwebproxystate", service, "on"))
                exec(listOf("networksetup", "-setsecurewebproxystate", service, "on"))
                exec(listOf("networksetup", "-setsocksfirewallproxystate", service, "on"))
                enabledServices += service
            }
        }
    }

    override fun restore() {
        val services = enabledServices.ifEmpty { networkServices() }
        services.forEach { service ->
            runCatching {
                exec(listOf("networksetup", "-setwebproxystate", service, "off"))
                exec(listOf("networksetup", "-setsecurewebproxystate", service, "off"))
                exec(listOf("networksetup", "-setsocksfirewallproxystate", service, "off"))
            }
        }
        enabledServices.clear()
    }

    private fun networkServices(): List<String> {
        return exec(listOf("networksetup", "-listallnetworkservices"))
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filterNot { it.startsWith("*") || it.startsWith("An asterisk") }
            .toList()
    }
}

private fun runCommand(command: List<String>): String {
    val process = ProcessBuilder(command).redirectErrorStream(true).start()
    process.waitFor(4, TimeUnit.SECONDS)
    return process.inputStream.bufferedReader().readText()
}
