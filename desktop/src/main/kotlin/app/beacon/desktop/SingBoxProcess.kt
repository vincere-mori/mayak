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
    private val readyTimeoutMs: Long = 4_000L,
    // TUN-режим инициализирует виртуальный сетевой адаптер через WinTun, что под
    // антивирусом / на медленном диске легко уходит за 10+ секунд. Логи показывают
    // "open interface take too much time to finish" с таймаутом 15с в sing-box.
    private val tunReadyTimeoutMs: Long = 25_000L,
    private val gracefulStopMs: Long = 6_000L
) {
    @Volatile private var process: Process? = null

    init {
        Runtime.getRuntime().addShutdownHook(Thread { stop() })
    }

    val running: Boolean
        get() = process?.isAlive == true

    fun start(configJson: String, tunMode: Boolean = false): Result<Unit> {
        if (running) stop()

        val binary = binaryLocator.locate()
            ?: return Result.failure(IllegalStateException("sing-box.exe не найден"))

        runCatching {
            val escapedBinary = binary.toString().replace("'", "''")
            ProcessBuilder("powershell", "-NoProfile", "-Command",
                "Get-Process -Name 'sing-box' -ErrorAction SilentlyContinue | " +
                "Where-Object { \$_.Path -eq '$escapedBinary' } | " +
                "Stop-Process -Force"
            ).start().waitFor(3, TimeUnit.SECONDS)
        }

        configFile.parent?.let { Files.createDirectories(it) }

        val timeout = if (tunMode) tunReadyTimeoutMs else readyTimeoutMs
        val attempts = if (tunMode) 3 else 1

        var lastError: Throwable? = null
        repeat(attempts) { attempt ->
            if (attempt > 0) {
                // Прошлый запуск sing-box упал на "configure tun interface:
                // Cannot create a file when that file already exists" — WinTun
                // удержал виртуальный адаптер tun0. Циклим его, ждём и пробуем ещё раз.
                cycleStaleTunAdapter()
                Thread.sleep(1_200)
            }

            // Перезаписываем конфиг каждый раз — abortLastProcess() его удаляет,
            // чтобы между запусками не оставалось файлов с ключами на диске.
            configFile.writeText(configJson)

            process = ProcessBuilder(binary.toString(), "run", "-c", configFile.toString())
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(logFile.toFile()))
                .start()

            if (waitUntilReady(timeout)) return Result.success(Unit)

            lastError = IllegalStateException(
                "sing-box не запустился (попытка ${attempt + 1}/$attempts). Лог: $logFile"
            )
            abortLastProcess()
        }

        deleteConfig()
        return Result.failure(lastError ?: IllegalStateException("sing-box не запустился"))
    }

    private fun waitUntilReady(timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
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
        abortLastProcess()
        deleteConfig()
    }

    private fun abortLastProcess() {
        val current = process
        if (current != null) {
            current.destroy()
            // Sing-box должен снять TUN-адаптер при graceful shutdown; force-kill
            // оставляет stale tun0, после которого следующий старт падает.
            if (!current.waitFor(gracefulStopMs, TimeUnit.MILLISECONDS)) {
                current.destroyForcibly()
                current.waitFor(2, TimeUnit.SECONDS)
            }
        }
        process = null
    }

    private fun cycleStaleTunAdapter() {
        runCatching {
            ProcessBuilder(
                "powershell", "-NoProfile", "-Command",
                "Get-NetAdapter -Name 'tun0' -ErrorAction SilentlyContinue | " +
                    "ForEach-Object { Disable-NetAdapter -Name \$_.Name -Confirm:\$false; " +
                    "Enable-NetAdapter -Name \$_.Name -Confirm:\$false }"
            )
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(logFile.toFile()))
                .start()
                .waitFor(5, TimeUnit.SECONDS)
        }
    }

    private fun deleteConfig() {
        runCatching { Files.deleteIfExists(configFile) }
    }
}
