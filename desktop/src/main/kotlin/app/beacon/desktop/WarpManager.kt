package app.beacon.desktop

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.net.InetAddress
import java.net.URL
import java.security.KeyPairGenerator
import java.security.interfaces.XECPrivateKey
import java.security.interfaces.XECPublicKey
import java.security.spec.NamedParameterSpec
import java.util.Base64
import java.util.UUID
import javax.net.ssl.HttpsURLConnection

@Serializable
data class WarpCredentials(
    val privateKey: String,
    val localAddressV4: String,
    val localAddressV6: String,
    val peerPublicKey: String,
    val endpoint: String,
    val reserved: List<Int> = listOf(0, 0, 0)
)

/**
 * Registers a new Cloudflare WARP device via the public mobile API,
 * returning WireGuard credentials that sing-box can use as an outbound.
 *
 * Key generation: standard JDK XDH / X25519 — no external deps required.
 * The Curve25519 key format matches WireGuard (32-byte little-endian scalars).
 */
object WarpManager {

    private val json = Json { ignoreUnknownKeys = true }

    fun register(): WarpCredentials {
        val (privateKeyB64, publicKeyB64) = generateKeyPair()

        val body = buildJsonObject {
            put("key", publicKeyB64)
            put("install_id", UUID.randomUUID().toString())
            put("fcm_token", "")
            put("referrer", "")
            put("warp_enabled", true)
            put("tos", "2024-01-01T00:00:00.000Z")
            put("type", "Android")
            put("locale", "en_US")
        }.toString().toByteArray(Charsets.UTF_8)

        val url = URL("https://api.cloudflareclient.com/v0a2158/reg")
        val conn = url.openConnection() as HttpsURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        conn.setRequestProperty("User-Agent", "okhttp/3.12.1")
        conn.connectTimeout = 15_000
        conn.readTimeout  = 15_000
        conn.doOutput = true
        conn.outputStream.use { it.write(body) }

        val code = conn.responseCode
        if (code != 200) {
            conn.disconnect()
            throw Exception("WARP API error: HTTP $code")
        }

        val resp = conn.inputStream.use { it.readBytes() }.toString(Charsets.UTF_8)
        conn.disconnect()

        val root   = json.parseToJsonElement(resp).jsonObject
        val config = root["config"]!!.jsonObject
        val peer   = config["peers"]!!.jsonArray[0].jsonObject
        val iface  = config["interface"]!!.jsonObject["addresses"]!!.jsonObject

        return WarpCredentials(
            privateKey    = privateKeyB64,
            localAddressV4 = iface["v4"]!!.jsonPrimitive.content,
            localAddressV6 = iface["v6"]?.jsonPrimitive?.content ?: "",
            peerPublicKey  = peer["public_key"]!!.jsonPrimitive.content,
            endpoint       = resolveEndpoint(peer["endpoint"]!!.jsonObject["host"]!!.jsonPrimitive.content),
            reserved       = reservedBytes(config["client_id"]?.jsonPrimitive?.content)
        )
    }

    /**
     * Cloudflare returns `endpoint.host` as a hostname (e.g. engage.cloudflareclient.com:2408).
     * sing-box's WireGuard peer expects an IP literal — resolve here so the handshake works.
     * Falls back to the raw value if resolution fails (better than nothing).
     */
    fun resolveEndpoint(raw: String): String {
        val text = raw.trim()
        val (host, portStr) = when {
            text.startsWith("[") -> {
                val h = text.substringAfter("[").substringBefore("]")
                val p = text.substringAfter("]:", "")
                h to p
            }
            else -> {
                val h = text.substringBeforeLast(":", text)
                val p = text.substringAfterLast(":", "")
                h to p
            }
        }
        val port = portStr.toIntOrNull() ?: 2408
        // Already an IPv4 literal? keep it.
        if (host.matches(Regex("""^\d{1,3}(\.\d{1,3}){3}$"""))) return "$host:$port"
        // Already an IPv6 literal? keep it bracketed.
        if (host.contains(":")) return "[$host]:$port"
        return runCatching {
            val ip = InetAddress.getByName(host).hostAddress
            if (ip.contains(":")) "[$ip]:$port" else "$ip:$port"
        }.getOrElse { "$host:$port" }
    }

    private fun reservedBytes(value: String?): List<Int> {
        if (value.isNullOrBlank()) return listOf(0, 0, 0)
        val padded = value + "=".repeat((4 - value.length % 4) % 4)
        val decoded = runCatching { Base64.getDecoder().decode(padded) }
            .getOrElse { Base64.getUrlDecoder().decode(padded) }
        return (decoded.take(3).map { it.toInt() and 0xff } + listOf(0, 0, 0)).take(3)
    }

    /**
     * Generates a WireGuard-compatible X25519 key pair using the standard JDK XDH provider.
     * Returns (privateKeyBase64, publicKeyBase64) in WireGuard's little-endian format.
     */
    private fun generateKeyPair(): Pair<String, String> {
        val kpg = KeyPairGenerator.getInstance("XDH")
        kpg.initialize(NamedParameterSpec.X25519)
        val kp = kpg.generateKeyPair()

        // Private key scalar — Java returns it in little-endian (RFC 7748) already
        val privScalar = (kp.private as XECPrivateKey).scalar
            .orElseThrow { IllegalStateException("XEC private key scalar unavailable") }

        // Public key u-coordinate — Java BigInteger is big-endian; WireGuard needs LE
        val pubU  = (kp.public as XECPublicKey).u
        val pubBE = pubU.toByteArray()   // may have leading sign byte
        val pubLE = ByteArray(32).also { out ->
            // Take the 32 least-significant bytes and reverse for LE
            val src = if (pubBE.size > 32) pubBE.copyOfRange(pubBE.size - 32, pubBE.size) else pubBE
            src.copyInto(out, destinationOffset = 32 - src.size)
            out.reverse()
        }

        val enc = Base64.getEncoder()
        return enc.encodeToString(privScalar) to enc.encodeToString(pubLE)
    }
}
