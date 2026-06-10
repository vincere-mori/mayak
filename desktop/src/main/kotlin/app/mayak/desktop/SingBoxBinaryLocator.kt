package app.mayak.desktop

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists

class SingBoxBinaryLocator(
    private val launchDir: Path = Path.of("").toAbsolutePath(),
    private val appDir: Path = appBinaryDir()
) {
    private val binaryName = if (Platform.isWindows) "sing-box.exe" else "sing-box"

    fun locate(): Path? {
        return listOf(
            launchDir.resolve(binaryName),
            appDir.resolve(binaryName),
            appDir.parent?.resolve("bin")?.resolve(binaryName),
            appDir.parent?.resolve(binaryName)
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
