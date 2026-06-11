package app.mayak.core.net

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI

/** Measures TCP connect latency to a server. Returns null when unreachable. */
class LatencyProbe {
    fun tcpLatencyMs(host: String, port: Int, timeoutMs: Int = 2500): Long? = runCatching {
        Socket().use { socket ->
            val start = System.nanoTime()
            socket.connect(InetSocketAddress(host, port), timeoutMs)
            ((System.nanoTime() - start + 999_999) / 1_000_000).coerceAtLeast(1)
        }
    }.getOrNull()

    fun proxyLatencyMs(
        controllerPort: Int,
        proxyTag: String = "proxy",
        testUrl: String = "http://www.gstatic.com/generate_204",
        timeoutMs: Int = 2500
    ): Long? = runCatching {
        val endpoint = URI(
            "http",
            null,
            "127.0.0.1",
            controllerPort,
            "/proxies/$proxyTag/delay",
            "timeout=$timeoutMs&url=$testUrl",
            null
        ).toURL()
        val connection = endpoint.openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 800
            connection.readTimeout = timeoutMs + 500
            connection.requestMethod = "GET"
            if (connection.responseCode !in 200..299) return@runCatching null
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            Json.parseToJsonElement(body)
                .jsonObject["delay"]
                ?.jsonPrimitive
                ?.content
                ?.toLongOrNull()
                ?.takeIf { it > 0 }
        } finally {
            connection.disconnect()
        }
    }.getOrNull()
}
