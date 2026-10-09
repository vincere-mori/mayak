package app.mayak.core.net

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlin.test.*

class SubscriptionFetcherTest {
    @Test fun rejectsNonHttpRedirectsAndOversizedBodies() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/redirect") {
            it.responseHeaders.add("Location", "file:///etc/passwd")
            it.sendResponseHeaders(302, -1); it.close()
        }
        server.createContext("/large") {
            val body = ByteArray(2_000_001)
            it.sendResponseHeaders(200, body.size.toLong())
            it.responseBody.use { out -> out.write(body) }
        }
        server.start()
        try {
            val base = "http://127.0.0.1:${server.address.port}"
            assertFailsWith<IllegalArgumentException> { SubscriptionFetcher().fetch("$base/redirect") }
            assertFailsWith<IllegalArgumentException> { SubscriptionFetcher().fetch("$base/large") }
        } finally { server.stop(0) }
    }
}
