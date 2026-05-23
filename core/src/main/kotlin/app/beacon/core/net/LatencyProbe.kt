package app.beacon.core.net

import java.net.InetSocketAddress
import java.net.Socket

/** Measures TCP connect latency to a server. Returns null when unreachable. */
class LatencyProbe {
    fun tcpLatencyMs(host: String, port: Int, timeoutMs: Int = 2500): Long? = runCatching {
        Socket().use { socket ->
            val start = System.nanoTime()
            socket.connect(InetSocketAddress(host, port), timeoutMs)
            (System.nanoTime() - start) / 1_000_000
        }
    }.getOrNull()
}
