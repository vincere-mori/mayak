package app.beacon.desktop

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.writeText

class SingBoxProcess(
    private val binaryLocator: SingBoxBinaryLocator = SingBoxBinaryLocator(),
    private val configFile: Path = DesktopPaths.configFile,
    private val logFile: Path = DesktopPaths.logFile
) {
    @Volatile private var process: Process? = null

    init {
        Runtime.getRuntime().addShutdownHook(Thread { stop() })
    }

    val running: Boolean
        get() = process?.isAlive == true

    fun start(configJson: String): Result<Unit> {
        if (running) stop()

        val binary = binaryLocator.locate()
            ?: return Result.failure(IllegalStateException("sing-box.exe не найден"))

        configFile.parent?.let { Files.createDirectories(it) }
        deleteConfig()
        configFile.writeText(configJson)

        process = ProcessBuilder(binary.toString(), "run", "-c", configFile.toString())
            .redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.appendTo(logFile.toFile()))
            .start()

        Thread.sleep(600)
        return if (running) {
            Result.success(Unit)
        } else {
            process = null
            deleteConfig()
            Result.failure(IllegalStateException("sing-box не запустился, смотри лог: $logFile"))
        }
    }

    fun stop() {
        val current = process
        if (current != null) {
            current.destroy()
            if (!current.waitFor(2, TimeUnit.SECONDS)) {
                current.destroyForcibly()
            }
        }
        process = null
        deleteConfig()
    }

    private fun deleteConfig() {
        runCatching { Files.deleteIfExists(configFile) }
    }
}
