package app.beacon.desktop

import java.nio.file.Path
import kotlin.io.path.createDirectories

object DesktopPaths {
    val appDir: Path by lazy {
        val base = when {
            Platform.isWindows -> System.getenv("APPDATA")
                ?.takeIf { it.isNotBlank() }
                ?.let(Path::of)
                ?: Path.of(System.getProperty("user.home"), ".config")
            else -> System.getenv("XDG_CONFIG_HOME")
                ?.takeIf { it.isNotBlank() }
                ?.let(Path::of)
                ?: Path.of(System.getProperty("user.home"), ".config")
        }

        // Windows keeps legacy capitalised "Beacon"; Linux follows XDG lowercase.
        val dirName = if (Platform.isWindows) "Beacon" else "beacon"
        base.resolve(dirName).also { it.createDirectories() }
    }

    val profilesFile: Path by lazy { appDir.resolve("profiles.json") }
    val configFile: Path by lazy { appDir.resolve("sing-box.json") }
    val logFile: Path by lazy { appDir.resolve("sing-box.log") }
}
