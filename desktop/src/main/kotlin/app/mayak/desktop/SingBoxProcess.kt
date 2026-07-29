package app.mayak.desktop

import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.concurrent.TimeUnit
import kotlin.io.path.writeText

class SingBoxProcess(
    private val binaryLocator: SingBoxBinaryLocator = SingBoxBinaryLocator(),
    private val configFile: Path = DesktopPaths.configFile,
    private val logFile: Path = DesktopPaths.logFile,
    private val readyProbeHost: String = "127.0.0.1",
    private val readyProbePort: Int = 9095,
    private val readyTimeoutMs: Long = 4_000L,
    // TUN-режим инициализирует виртуальный сетевой адаптер через WinTun/tun(4), что под
    // антивирусом / на медленном диске легко уходит за 10+ секунд.
    private val tunReadyTimeoutMs: Long = 25_000L,
    private val gracefulStopMs: Long = 6_000L,
    private val maxLogBytes: Long = 4_000_000L
) {
    @Volatile private var process: Process? = null
    // Поднимается, когда пользователь отменяет подключение во время старта, чтобы
    // start() не докручивал ретраи и не оставлял осиротевший sing-box.
    @Volatile private var stopRequested = false
    // Чистка tun0 после force-kill едет фоном на дисконнекте, чтобы не держать UI.
    // start() дожидается её, если пользователь переподключился сразу.
    @Volatile private var tunCleanup: Thread? = null

    // Метка «TUN поднимался, адаптер не чищен». Переживает краш Маяка, поэтому
    // после падения следующий start() знает, что tun0 надо циклить.
    private val tunDirtyFile: Path get() = configFile.resolveSibling("tun.dirty")

    init {
        Runtime.getRuntime().addShutdownHook(Thread { stop(); awaitTunCleanup() })
    }

    val running: Boolean
        get() = process?.isAlive == true

    fun start(configJson: String, tunMode: Boolean = false): Result<Unit> {
        if (running) stop()
        stopRequested = false
        val phases = PhaseLog(tunMode)

        val binary = binaryLocator.locate()
            ?: return Result.failure(IllegalStateException("sing-box не найден"))

        // Фоновая чистка адаптера с прошлого дисконнекта могла ещё не закончиться.
        phases.measure("await-cleanup") { awaitTunCleanup() }
        phases.measure("kill-stale") { killStaleSingBox(binary) }

        phases.measure("write-config") {
            configFile.parent?.let { Files.createDirectories(it) }
            rotateLogIfLarge()
            writeConfig(configJson)
        }
        // Раньше тут гонялся отдельный `sing-box check` на каждый запуск — это холодный
        // старт 44МБ бинаря (под антивирусом легко 2-5с). `run` валидирует конфиг сам и
        // пишет причину в лог, поэтому проверяем конфиг только если запуск реально упал.

        // На Windows наш stop() жёстко убивает sing-box (destroy() = TerminateProcess,
        // graceful-сигнала нет), и WinTun удерживает адаптер tun0. Обычно его снимает
        // фоновая чистка на дисконнекте; сюда попадаем только если прошлый сеанс
        // оборвался вместе с Маяком и метка осталась на диске.
        if (tunMode && Platform.isWindows) {
            if (Files.exists(tunDirtyFile) && hasTunAdapter()) {
                phases.measure("cycle-tun") { cycleStaleTunAdapter() }
            }
            markTunDirty()
        }

        val timeout = if (tunMode) tunReadyTimeoutMs else readyTimeoutMs
        // On Linux, the kernel releases the TUN fd on process exit, so stale adapters
        // are not possible. One retry on Windows is enough as a fallback to the
        // up-front adapter cleanup above.
        val attempts = if (tunMode && Platform.isWindows) 2 else 1

        var lastError: Throwable? = null
        repeat(attempts) { attempt ->
            if (stopRequested) {
                deleteConfig()
                return Result.failure(IllegalStateException("запуск отменён"))
            }
            if (attempt > 0) {
                // Запуск всё равно упал на stale tun0 — циклим адаптер ещё раз и пробуем.
                phases.measure("retry-cycle-tun") {
                    cycleStaleTunAdapter()
                    Thread.sleep(250)
                }
            }

            phases.measure("spawn") {
                process = ProcessBuilder(binary.toString(), "run", "-c", configFile.toString())
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.appendTo(logFile.toFile()))
                    .start()
            }

            val ready = phases.measure("ready") { waitUntilReady(timeout) }
            if (ready) {
                appendLog(phases.render())
                return Result.success(Unit)
            }

            lastError = IllegalStateException(
                "sing-box не запустился (попытка ${attempt + 1}/$attempts): " +
                    "${lastSingBoxError() ?: "нет ответа от API"}. Лог: $logFile"
            )
            abortLastProcess()
        }
        appendLog(phases.render())

        // Запуск упал, а в логе нет явной FATAL/ERROR — спросим у `check`, что не так
        // с конфигом (он ещё на диске), чтобы показать пользователю точную причину.
        val enriched = if (lastSingBoxError() == null) checkConfig(binary) ?: lastError else lastError
        deleteConfig()
        return Result.failure(enriched ?: IllegalStateException("sing-box не запустился"))
    }

    private fun waitUntilReady(timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (process?.isAlive != true) return false
            try {
                Socket().use { s ->
                    s.connect(InetSocketAddress(readyProbeHost, readyProbePort), 60)
                    return true
                }
            } catch (_: Exception) {
                Thread.sleep(20)
            }
        }
        return false
    }

    private fun writeConfig(configJson: String) {
        configFile.writeText(configJson)
    }

    private fun rotateLogIfLarge() {
        runCatching {
            if (Files.exists(logFile) && Files.size(logFile) > maxLogBytes) {
                val rotated = logFile.resolveSibling("${logFile.fileName}.old")
                Files.deleteIfExists(rotated)
                Files.move(logFile, rotated, StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }

    private fun checkConfig(binary: Path): Throwable? {
        return runCatching {
            val checker = ProcessBuilder(binary.toString(), "check", "-c", configFile.toString())
                .redirectErrorStream(true)
                .start()
            val finished = checker.waitFor(8, TimeUnit.SECONDS)

            if (!finished) {
                checker.destroyForcibly()
                checker.waitFor(2, TimeUnit.SECONDS)
                appendLog("sing-box check timed out")
                return@runCatching IllegalStateException("sing-box check timed out. Лог: $logFile")
            }

            val output = checker.inputStream.bufferedReader(StandardCharsets.UTF_8).readText()
            appendLog(output)

            if (checker.exitValue() == 0) {
                null
            } else {
                IllegalStateException("sing-box config invalid: ${lastErrorLine(output)}. Лог: $logFile")
            }
        }.getOrElse {
            IllegalStateException("sing-box check failed: ${it.message ?: it.javaClass.simpleName}. Лог: $logFile")
        }
    }

    private fun appendLog(text: String) {
        if (text.isBlank()) return
        logFile.parent?.let { Files.createDirectories(it) }
        val suffix = if (text.endsWith("\n") || text.endsWith("\r")) "" else System.lineSeparator()
        Files.writeString(
            logFile,
            text + suffix,
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND
        )
    }

    private fun lastSingBoxError(): String? {
        return runCatching {
            Files.readString(logFile, StandardCharsets.UTF_8)
                .lineSequence()
                .map { stripAnsi(it).trim() }
                .filter { it.contains("FATAL") || it.contains("ERROR") }
                .lastOrNull()
        }.getOrNull()
    }

    private fun lastErrorLine(output: String): String {
        return output.lineSequence()
            .map { stripAnsi(it).trim() }
            .filter { it.isNotEmpty() }
            .lastOrNull()
            ?: "unknown error"
    }

    private fun stripAnsi(text: String): String {
        return text.replace(Regex("\u001B\\[[;\\d]*m"), "")
    }

    fun stop() {
        stopRequested = true
        val tunLeftBehind = Platform.isWindows && Files.exists(tunDirtyFile)
        abortLastProcess()
        deleteConfig()
        // Снимаем tun0 здесь, а не на следующем старте: дисконнект уже отрисован,
        // и эти пара секунд никто не ждёт, в отличие от подключения.
        if (tunLeftBehind) scheduleTunCleanup()
    }

    private fun scheduleTunCleanup() {
        awaitTunCleanup()
        tunCleanup = Thread {
            cycleStaleTunAdapter()
            runCatching { Files.deleteIfExists(tunDirtyFile) }
        }.apply { isDaemon = true; start() }
    }

    private fun awaitTunCleanup() {
        val worker = tunCleanup ?: return
        runCatching { worker.join(TUN_CLEANUP_JOIN_MS) }
        tunCleanup = null
    }

    private fun markTunDirty() {
        runCatching {
            tunDirtyFile.parent?.let { Files.createDirectories(it) }
            Files.writeString(tunDirtyFile, "", StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
        }
    }

    private fun abortLastProcess() {
        val current = process
        if (current != null) {
            current.destroy()
            // Sing-box должен снять TUN-адаптер при graceful shutdown; force-kill
            // оставляет stale tun0 на Windows, после которого следующий старт падает.
            if (!current.waitFor(gracefulStopMs, TimeUnit.MILLISECONDS)) {
                current.destroyForcibly()
                current.waitFor(2, TimeUnit.SECONDS)
            }
        }
        process = null
    }

    private fun killStaleSingBox(binary: Path) {
        val target = binary.toAbsolutePath().normalize()
        runCatching {
            ProcessHandle.allProcesses().use { processes ->
                processes
                    .filter { handle ->
                        handle.info().command().orElse(null)?.let { command ->
                            runCatching {
                                Path.of(command).toAbsolutePath().normalize() == target
                            }.getOrDefault(false)
                        } == true
                    }
                    .forEach { handle ->
                        handle.destroyForcibly()
                        runCatching { handle.onExit().get(2, TimeUnit.SECONDS) }
                    }
            }
        }
    }

    private fun hasTunAdapter(): Boolean = runCatching {
        NetworkInterface.getNetworkInterfaces()?.toList()?.any {
            it.name.equals("tun0", ignoreCase = true) ||
                it.displayName.equals("tun0", ignoreCase = true)
        } == true
    }.getOrDefault(false)

    // netsh вместо powershell: делают они одно и то же, но холодный старт powershell
    // с автозагрузкой модуля NetAdapter — это ~2с против ~0.3с у netsh.
    private fun cycleStaleTunAdapter() {
        if (!Platform.isWindows) return
        setTunAdapterAdmin("disabled")
        setTunAdapterAdmin("enabled")
    }

    private fun setTunAdapterAdmin(admin: String) {
        runCatching {
            ProcessBuilder("netsh", "interface", "set", "interface", "name=tun0", "admin=$admin")
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(logFile.toFile()))
                .start()
                .waitFor(5, TimeUnit.SECONDS)
        }
    }

    private fun deleteConfig() {
        runCatching { Files.deleteIfExists(configFile) }
    }

    // Сколько на какую фазу ушло — одной строкой в конце, чтобы не мешать выводу sing-box.
    private class PhaseLog(private val tunMode: Boolean) {
        private val startedAt = System.currentTimeMillis()
        private val phases = mutableListOf<String>()

        fun <T> measure(name: String, block: () -> T): T {
            val t0 = System.currentTimeMillis()
            try {
                return block()
            } finally {
                phases += "$name=${System.currentTimeMillis() - t0}"
            }
        }

        fun render(): String =
            "[Mayak] start tun=$tunMode total=${System.currentTimeMillis() - startedAt}ms " +
                phases.joinToString(" ")
    }

    private companion object {
        const val TUN_CLEANUP_JOIN_MS = 8_000L
    }
}
