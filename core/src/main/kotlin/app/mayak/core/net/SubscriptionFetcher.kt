package app.mayak.core.net

import java.net.HttpURLConnection
import java.net.URI
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/**
 * Downloads the raw body of a subscription URL. Blocking — call off the UI thread.
 *
 * Follows http→https redirects manually (HttpURLConnection refuses to follow
 * cross-protocol hops on its own) and sends a common client User-Agent because
 * some panels gate the response on it.
 *
 * TLS validation is intentionally disabled: VPN subscription servers routinely use
 * self-signed or expired certificates; the URL itself is the trust anchor (the user
 * pasted it explicitly). This matches behaviour of Hiddify, Clash, sing-box, etc.
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
                    in 200..299 -> return conn.inputStream.use { it.readBytes() }
                        .toString(Charsets.UTF_8)

                    in 300..399 -> {
                        val location = conn.getHeaderField("Location")
                            ?: throw IllegalStateException("подписка вернула редирект без адреса")
                        current = URI(current).resolve(location).toString()
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
        if (conn is HttpsURLConnection) {
            conn.sslSocketFactory = TRUST_ALL_SSL_CTX.socketFactory
            conn.hostnameVerifier = HostnameVerifier { _, _ -> true }
        }
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
        const val USER_AGENT = "Mayak/1.0 (clash; sing-box)"

        val TRUST_ALL_SSL_CTX: SSLContext = SSLContext.getInstance("TLS").also { ctx ->
            ctx.init(null, arrayOf(object : X509TrustManager {
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit
            }), SecureRandom())
        }
    }
}
