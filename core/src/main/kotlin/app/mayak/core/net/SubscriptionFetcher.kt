package app.mayak.core.net

import java.net.HttpURLConnection
import java.net.URI

/**
 * Downloads the raw body of a subscription URL. Blocking — call off the UI thread.
 *
 * Follows http→https redirects manually (HttpURLConnection refuses to follow
 * cross-protocol hops on its own) and sends a common client User-Agent because
 * some panels gate the response on it.
 *
 * TLS certificates are validated by the platform trust store.
 */
class SubscriptionFetcher {

    fun fetch(url: String): String {
        var current = url.trim()
        if (!current.startsWith("http://", true) && !current.startsWith("https://", true)) {
            throw IllegalArgumentException("ссылка подписки должна начинаться с http:// или https://")
        }

        repeat(MAX_REDIRECTS) {
            val conn = open(current)
            try {
                when (val code = conn.responseCode) {
                    in 200..299 -> return conn.inputStream.use {
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        var total = 0
                        while (true) {
                            val count = it.read(buffer)
                            if (count < 0) break
                            total += count
                            require(total <= MAX_BYTES) { "Подписка слишком большая." }
                            output.write(buffer, 0, count)
                        }
                        output.toString("UTF-8")
                    }

                    in 300..399 -> {
                        val location = conn.getHeaderField("Location")
                            ?: throw IllegalStateException("подписка вернула редирект без адреса")
                        val next = URI(current).resolve(location)
                        require(next.scheme?.lowercase() in setOf("http", "https") && next.host != null && next.userInfo == null) { "Небезопасное перенаправление подписки." }
                        require(!current.startsWith("https://", true) || next.scheme.equals("https", true)) { "Ссылка перенаправляет на незащищённое соединение." }
                        current = next.toString()
                    }

                    else -> throw IllegalStateException("подписка вернула HTTP $code")
                }
            } finally {
                conn.disconnect()
            }
        }
        throw IllegalStateException("слишком много редиректов у подписки")
    }

    private fun open(url: String): HttpURLConnection {
        val conn = URI(url).toURL().openConnection() as HttpURLConnection
        return conn.apply {
            connectTimeout = 12_000
            readTimeout = 12_000
            instanceFollowRedirects = false
            requestMethod = "GET"
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "*/*")
        }
    }

    private companion object {
        const val MAX_REDIRECTS = 5
        const val MAX_BYTES = 2_000_000
        const val USER_AGENT = "Mayak/1.0 (clash; sing-box)"

    }
}
