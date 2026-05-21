package app.beacon.desktop

import java.nio.file.Path
import kotlin.io.path.createDirectories

object DesktopPaths {
    val appDir: Path by lazy {
        val base = System.getenv("APPDATA")
            ?.takeIf { it.isNotBlank() }
            ?.let(Path::of)
            ?: Path.of(System.getProperty("user.home"), ".beacon")

        base.resolve("Beacon").also { it.createDirectories() }
    }

    val profilesFile: Path by lazy { appDir.resolve("profiles.json") }
    val configFile: Path by lazy { appDir.resolve("sing-box.json") }
    val logFile: Path by lazy { appDir.resolve("sing-box.log") }
}
