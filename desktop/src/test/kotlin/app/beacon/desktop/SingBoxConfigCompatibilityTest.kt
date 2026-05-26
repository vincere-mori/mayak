package app.beacon.desktop

import app.beacon.core.parser.ProfileInputParser
import app.beacon.core.singbox.InboundMode
import app.beacon.core.singbox.SingBoxConfigBuilder
import app.beacon.core.singbox.SingBoxConfigSettings
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class SingBoxConfigCompatibilityTest {
    private val parser = ProfileInputParser(clock = { 42L })
    private val builder = SingBoxConfigBuilder()

    @Test
    fun generatedDesktopConfigsPassSingBoxCheck() {
        val binary = findSingBoxBinary() ?: return
        val profile = parser.parse(
            "vless://11111111-1111-1111-1111-111111111111@example.com:443" +
                "?security=reality&sni=apple.com&fp=chrome&pbk=$REALITY_PUBLIC_KEY&sid=abcd#Main"
        )
        val configs = mapOf(
            "proxy" to builder.build(profile, SingBoxConfigSettings(inboundMode = InboundMode.Mixed)),
            "tun" to builder.build(profile, SingBoxConfigSettings(inboundMode = InboundMode.Tun)),
            "tun-warp" to builder.build(
                profile,
                SingBoxConfigSettings(
                    inboundMode = InboundMode.Tun,
                    warpEnabled = true,
                    warpPrivateKey = WARP_PRIVATE_KEY,
                    warpLocalAddressV4 = "172.16.0.2",
                    warpPeerPublicKey = WARP_PEER_PUBLIC_KEY,
                    warpEndpoint = "162.159.192.1:2408",
                    warpReserved = listOf(1, 2, 3)
                )
            )
        )

        configs.forEach { (name, config) ->
            assertConfigAccepted(binary, name, config)
        }
    }

    private fun assertConfigAccepted(binary: Path, name: String, config: String) {
        val configFile = Files.createTempFile("beacon-$name-", ".json")
        try {
            Files.writeString(configFile, config)
            val process = ProcessBuilder(binary.toString(), "check", "-c", configFile.toString())
                .redirectErrorStream(true)
                .start()
            val finished = process.waitFor(10, TimeUnit.SECONDS)

            if (!finished) {
                process.destroyForcibly()
                fail("sing-box check timed out for $name")
            }

            val output = process.inputStream.bufferedReader().readText()
            assertEquals(0, process.exitValue(), "sing-box rejected $name config:\n$output")
        } finally {
            Files.deleteIfExists(configFile)
        }
    }

    private fun findSingBoxBinary(): Path? {
        val root = repoRoot()
        val name = if (Platform.isWindows) "sing-box.exe" else "sing-box"
        return sequenceOf(root.resolve(name), root.resolve("build").resolve("linux-package").resolve("input").resolve(name))
            .firstOrNull { it.exists() }
    }

    private fun repoRoot(): Path {
        var dir = Path.of("").toAbsolutePath()
        while (!dir.resolve("settings.gradle.kts").exists()) {
            dir = dir.parent ?: return Path.of("").toAbsolutePath()
        }
        return dir
    }

    private companion object {
        const val REALITY_PUBLIC_KEY = "C32jMOdE0tDhw8lWuBOYpIOEpyhPb4brJ3gNe4fwLHo"
        const val WARP_PRIVATE_KEY = "1bgFEYzAZTCffIHOzFL6iDiT6nJrKgg99uAN+gi2JWY="
        const val WARP_PEER_PUBLIC_KEY = "MfDBSU0crryEpkKRgOLOUpB9y5AiKF6ePyDyRih419U="
    }
}
