package app.beacon.desktop

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class SingBoxBinaryLocatorTest {
    @Test
    fun findsLocalSingBoxExeFirst() {
        val dir = Files.createTempDirectory("beacon-sing-box")
        val exe = dir.resolve("sing-box.exe")
        Files.writeString(exe, "")

        val locator = SingBoxBinaryLocator(
            launchDir = dir,
            appDir = Files.createTempDirectory("beacon-app")
        )

        assertEquals(exe, locator.locate())
    }

    @Test
    fun findsBundledSingBoxInDistributionBin() {
        val appHome = Files.createTempDirectory("beacon-app-home")
        val appLib = Files.createDirectories(appHome.resolve("lib"))
        val appBin = Files.createDirectories(appHome.resolve("bin"))
        val exe = appBin.resolve("sing-box.exe")
        Files.writeString(exe, "")

        val locator = SingBoxBinaryLocator(
            launchDir = Files.createTempDirectory("beacon-launch"),
            appDir = appLib
        )

        assertEquals(exe, locator.locate())
    }
}
