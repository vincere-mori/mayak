package app.mayak.desktop

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

data class TrafficSample(val up: Long, val down: Long)

/**
 * Streams traffic stats from sing-box clash API (/traffic SSE-ish endpoint).
 * Each line is "{"up":N,"down":N}". Emits per second even at 0/0.
 *
 * Uses HttpURLConnection (not java.net.http.HttpClient) because the latter
 * sometimes buffers / waits for HTTP/2 framing which doesn't play well with
 * sing-box's chunked text response.
 */
class TrafficMonitor(private val controllerPort: Int) {
    private val running = AtomicBoolean(false)
    private val lastUp = AtomicLong(0)
    private val lastDown = AtomicLong(0)
    @Volatile private var thread: Thread? = null

    val sample: TrafficSample
        get() = TrafficSample(lastUp.get(), lastDown.get())

    fun start(onSample: (TrafficSample) -> Unit) {
        if (running.getAndSet(true)) return

        thread = Thread {
            // give sing-box a moment to bring up its API
            Thread.sleep(800)
            var attempts = 0
            while (running.get()) {
                val ok = runCatching { stream(onSample) }
                    .onFailure { System.err.println("[Mayak] traffic stream error: ${it.message}") }
                    .isSuccess
                if (!running.get()) break
                attempts++
                if (attempts > 40) {
                    System.err.println("[Mayak] traffic monitor giving up after $attempts attempts")
                    return@Thread
                }
                Thread.sleep(if (ok) 500 else 1500)
            }
        }.apply {
            name = "mayak-traffic-monitor"
            isDaemon = true
            start()
        }
    }

    private fun stream(onSample: (TrafficSample) -> Unit) {
        val url = URL("http://127.0.0.1:$controllerPort/traffic")
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 1500
        conn.readTimeout = 0
        conn.requestMethod = "GET"
        conn.setRequestProperty("Accept", "application/json")
        conn.connect()

        if (conn.responseCode != 200) {
            System.err.println("[Mayak] clash /traffic returned ${conn.responseCode}")
            conn.disconnect()
            return
        }

        BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { reader ->
            while (running.get()) {
                val line = try { reader.readLine() } catch (_: Exception) { null } ?: break
                if (line.isBlank()) continue
                val s = parse(line) ?: continue
                lastUp.set(s.up)
                lastDown.set(s.down)
                onSample(s)
            }
        }
        conn.disconnect()
    }

    fun stop() {
        running.set(false)
        thread?.interrupt()
        thread = null
        lastUp.set(0)
        lastDown.set(0)
    }

    private fun parse(line: String): TrafficSample? {
        val up = num(line, "\"up\"") ?: return null
        val down = num(line, "\"down\"") ?: return null
        return TrafficSample(up, down)
    }

    private fun num(line: String, key: String): Long? {
        val idx = line.indexOf(key)
        if (idx < 0) return null
        var i = idx + key.length
        while (i < line.length && (line[i] == ':' || line[i].isWhitespace())) i++
        val start = i
        while (i < line.length && (line[i].isDigit() || line[i] == '-')) i++
        if (i == start) return null
        return line.substring(start, i).toLongOrNull()
    }
}

fun formatBytesPerSec(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B/s"
    bytes < 1024 * 1024 -> "%.1f KB/s".format(bytes / 1024.0)
    bytes < 1024L * 1024 * 1024 -> "%.1f MB/s".format(bytes / (1024.0 * 1024))
    else -> "%.2f GB/s".format(bytes / (1024.0 * 1024 * 1024))
}
