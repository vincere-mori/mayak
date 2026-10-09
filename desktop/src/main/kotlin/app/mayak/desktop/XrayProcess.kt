package app.mayak.desktop

import app.mayak.core.model.ProxyProfile
import app.mayak.core.xray.XrayConfigBuilder
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

class XrayProcess {
    @Volatile private var process: Process? = null
    private val config = DesktopPaths.appDir.resolve("xray.json")
    private val log = DesktopPaths.appDir.resolve("xray.log")
    private val pidFile = DesktopPaths.appDir.resolve("xray.pid")
    val running get() = process?.isAlive == true
    init { Runtime.getRuntime().addShutdownHook(Thread { runCatching { stop() } }) }

    @Synchronized
    fun start(profile: ProxyProfile): Result<Unit> = runCatching {
        stop()
        val name = if (Platform.isWindows) "xray.exe" else "xray"
        val app = SingBoxBinaryLocator.appBinaryDir()
        val binary = listOf(Path.of(name).toAbsolutePath(), app.resolve(name), app.parent?.resolve(name), app.parent?.resolve("bin")?.resolve(name))
            .filterNotNull().firstOrNull(Files::isRegularFile)
            ?: error("Ядро подключения отсутствует. Переустановите Маяк из полной сборки.")
        stopPrevious(binary)
        val network = if (Platform.isWindows) physicalInterface() else null
        Files.writeString(config, XrayConfigBuilder().build(profile, PORT, network))
        if (Files.exists(log) && Files.size(log) > 2_000_000) Files.delete(log)
        process = ProcessBuilder(binary.toString(), "run", "-c", config.toString())
            .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile())).start()
        Files.writeString(pidFile, process!!.pid().toString())
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8)
        while (running && System.nanoTime() < deadline) {
            val ready = runCatching { Socket().use { it.connect(InetSocketAddress("127.0.0.1", PORT), 100) } }.isSuccess
            if (ready) return@runCatching
            Thread.sleep(30)
        }
        stop()
        error("Не удалось запустить подключение. Проверьте ссылку или переустановите Маяк.")
    }.onFailure { stop() }

    @Synchronized
    fun stop() {
        process?.let { p ->
            p.destroy()
            if (!p.waitFor(2, TimeUnit.SECONDS)) { p.destroyForcibly(); p.waitFor(2, TimeUnit.SECONDS) }
            Files.deleteIfExists(pidFile)
        }
        process = null
        Files.deleteIfExists(config)
    }

    private fun stopPrevious(binary: Path) {
        if (!Files.exists(pidFile)) return
        val pid = Files.readString(pidFile).trim().toLongOrNull() ?: return
        val handle = ProcessHandle.of(pid).orElse(null) ?: return
        val command = handle.info().command().orElse("")
        val args = handle.info().arguments().orElse(emptyArray())
        if (command.equals(binary.toAbsolutePath().toString(), ignoreCase = Platform.isWindows) && args.any { it == config.toString() }) {
            handle.destroy()
            runCatching { handle.onExit().get(2, TimeUnit.SECONDS) }.onFailure { handle.destroyForcibly() }
        }
    }

    private fun physicalInterface(): String? = runCatching {
        val command = "[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new(); Get-NetRoute -AddressFamily IPv4 -DestinationPrefix '0.0.0.0/0' | Where-Object { \$_.InterfaceAlias -notmatch 'tun|Happ|Mayak|Wintun|VPN' } | Sort-Object RouteMetric | Select-Object -First 1 -ExpandProperty InterfaceAlias"
        val p = ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", command).start()
        if (!p.waitFor(4, TimeUnit.SECONDS)) { p.destroyForcibly(); return@runCatching null }
        p.inputStream.bufferedReader().readText().trim().takeIf { p.exitValue() == 0 && it.isNotBlank() }
    }.getOrNull()

    companion object { const val PORT = 10891 }
}
