package app.beacon.desktop

import java.net.InetSocketAddress
import java.net.Socket
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.writeText

class SingBoxProcess(
    private val binaryLocator: SingBoxBinaryLocator = SingBoxBinaryLocator(),
    private val configFile: Path = DesktopPaths.configFile,
    private val logFile: Path = DesktopPaths.logFile,
    private val readyProbeHost: String = "127.0.0.1",
    private val readyProbePort: Int = 9095,
    private val readyTimeoutMs: Long = 4000L
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

        return if (waitUntilReady()) {
            Result.success(Unit)
        } else {
            stop()
            Result.failure(IllegalStateException("sing-box не запустился, смотри лог: $logFile"))
        }
    }

    /**
     * Poll the clash-api port until sing-box accepts a TCP connect, or until the
     * process dies / the timeout elapses. Replaces a fixed Thread.sleep(600) so
     * slow disks / AV scans don't get reported as a failed start.
     */
    private fun waitUntilReady(): Boolean {
        val deadline = System.currentTimeMillis() + readyTimeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (process?.isAlive != true) return false
            try {
                Socket().use { s ->
                    s.connect(InetSocketAddress(readyProbeHost, readyProbePort), 250)
                    return true
                }
            } catch (_: Exception) {
                Thread.sleep(80)
            }
        }
        return false
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
