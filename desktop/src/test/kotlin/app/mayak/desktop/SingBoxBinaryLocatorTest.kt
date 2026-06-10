package app.mayak.desktop

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class SingBoxBinaryLocatorTest {
    private val binaryName = if (Platform.isWindows) "sing-box.exe" else "sing-box"

    @Test
    fun findsLocalSingBoxExeFirst() {
        val dir = Files.createTempDirectory("mayak-sing-box")
        val exe = dir.resolve(binaryName)
        Files.writeString(exe, "")

        val locator = SingBoxBinaryLocator(
            launchDir = dir,
            appDir = Files.createTempDirectory("mayak-app")
        )

        assertEquals(exe, locator.locate())
    }

    @Test
    fun findsBundledSingBoxInDistributionBin() {
        val appHome = Files.createTempDirectory("mayak-app-home")
        val appLib = Files.createDirectories(appHome.resolve("lib"))
        val appBin = Files.createDirectories(appHome.resolve("bin"))
        val exe = appBin.resolve(binaryName)
        Files.writeString(exe, "")

        val locator = SingBoxBinaryLocator(
            launchDir = Files.createTempDirectory("mayak-launch"),
            appDir = appLib
        )

        assertEquals(exe, locator.locate())
    }
}
