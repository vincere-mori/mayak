package app.beacon.desktop

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists

class SingBoxBinaryLocator(
    private val launchDir: Path = Path.of("").toAbsolutePath(),
    private val appDir: Path = appBinaryDir()
) {
    fun locate(): Path? {
        return listOf(
            launchDir.resolve("sing-box.exe"),
            appDir.resolve("sing-box.exe"),
            appDir.parent?.resolve("bin")?.resolve("sing-box.exe"),
            appDir.parent?.resolve("sing-box.exe")
        ).filterNotNull().firstOrNull { it.exists() }
    }

    companion object {
        fun appBinaryDir(): Path {
            val location = SingBoxBinaryLocator::class.java.protectionDomain.codeSource?.location?.toURI()
            val path = location?.let(Path::of) ?: Path.of("").toAbsolutePath()
            return if (Files.isRegularFile(path)) path.parent else path
        }
    }
}
