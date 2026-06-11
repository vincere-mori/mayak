package app.mayak.core.net

import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LatencyProbeTest {
    @Test
    fun localTcpLatencyNeverRoundsDownToZero() {
        ServerSocket(0).use { server ->
            val acceptor = thread(start = true) {
                server.accept().use { }
            }

            val latency = LatencyProbe().tcpLatencyMs("127.0.0.1", server.localPort)

            acceptor.join()
            assertTrue(latency != null && latency >= 1)
        }
    }

    @Test
    fun readsProxyLatencyFromClashApi() {
        ServerSocket(0).use { server ->
            val responder = thread(start = true) {
                server.accept().use { socket ->
                    val reader = socket.getInputStream().bufferedReader()
                    while (!reader.readLine().isNullOrEmpty()) {
                        // consume request headers
                    }
                    val body = """{"delay":87}"""
                    val response = buildString {
                        append("HTTP/1.1 200 OK\r\n")
                        append("Content-Type: application/json\r\n")
                        append("Content-Length: ${body.toByteArray(StandardCharsets.UTF_8).size}\r\n")
                        append("Connection: close\r\n\r\n")
                        append(body)
                    }
                    socket.getOutputStream().write(response.toByteArray(StandardCharsets.UTF_8))
                }
            }

            val latency = LatencyProbe().proxyLatencyMs(server.localPort)

            responder.join()
            assertEquals(87, latency)
        }
    }
}
