package app.mayak.desktop

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.net.InetAddress
import java.net.Inet4Address
import java.net.URL
import java.security.KeyPairGenerator
import java.security.interfaces.XECPrivateKey
import java.security.interfaces.XECPublicKey
import java.security.spec.NamedParameterSpec
import java.time.Instant
import java.util.Base64
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
            put("install_id", "")
            put("fcm_token", "")
            put("warp_enabled", true)
            put("tos", Instant.now().toString())
            put("type", "Android")
            put("model", "PC")
            put("locale", "en_US")
        }.toString().toByteArray(Charsets.UTF_8)

        val failures = mutableListOf<String>()
        for (api in REGISTRATION_APIS) {
            val response = runCatching { requestRegistration(api, body) }
            if (response.isSuccess) {
                return parseRegistrationResponse(response.getOrThrow(), privateKeyB64)
            }
            failures += "${api.version}: ${response.exceptionOrNull()?.message ?: "unknown error"}"
        }
        throw IllegalStateException("WARP registration failed: ${failures.joinToString("; ")}")
    }

    private fun requestRegistration(api: RegistrationApi, body: ByteArray): String {
        val url = URL("https://api.cloudflareclient.com/${api.version}/reg")
        val conn = url.openConnection() as HttpsURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        conn.setRequestProperty("User-Agent", "okhttp/3.12.1")
        conn.setRequestProperty("CF-Client-Version", api.clientVersion)
        conn.connectTimeout = 8_000
        conn.readTimeout = 8_000
        conn.doOutput = true

        return try {
            conn.outputStream.use { it.write(body) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val response = stream?.use { it.readBytes() }?.toString(Charsets.UTF_8).orEmpty()
            if (code !in 200..299) {
                val detail = response.take(240).ifBlank { "empty response" }
                throw IllegalStateException("HTTP $code: $detail")
            }
            response
        } finally {
            conn.disconnect()
        }
    }

    internal fun parseRegistrationResponse(response: String, privateKey: String): WarpCredentials {
        val root   = json.parseToJsonElement(response).jsonObject
        val config = root["config"]?.jsonObject
            ?: throw IllegalStateException("WARP response has no config")
        val peer   = config["peers"]?.jsonArray?.getOrNull(0)?.jsonObject
            ?: throw IllegalStateException("WARP response has no peers")
        val iface  = config["interface"]?.jsonObject?.get("addresses")?.jsonObject
            ?: throw IllegalStateException("WARP response has no interface.addresses")

        return WarpCredentials(
            privateKey     = privateKey,
            localAddressV4 = iface["v4"]?.jsonPrimitive?.content
                ?: throw IllegalStateException("WARP response has no IPv4 address"),
            localAddressV6 = iface["v6"]?.jsonPrimitive?.content ?: "",
            peerPublicKey  = peer["public_key"]?.jsonPrimitive?.content
                ?: throw IllegalStateException("WARP response has no peer public key"),
            endpoint       = endpointFrom(peer),
            reserved       = reservedBytes(config["client_id"]?.jsonPrimitive?.content)
        )
    }

    private fun endpointFrom(peer: JsonObject): String {
        val endpoint = peer["endpoint"]?.jsonObject
            ?: throw IllegalStateException("WARP response has no peer endpoint")
        val hostAddress = endpoint["host"]?.jsonPrimitive?.content
            ?: throw IllegalStateException("WARP response has no endpoint.host")
        val hostPort = parseHostPort(hostAddress)
        val port = hostPort.port.takeIf { it > 0 }
            ?: endpoint["ports"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.content?.toIntOrNull()
            ?: 2408

        val ipv4 = endpoint["v4"]?.jsonPrimitive?.content
            ?.let(::parseHostPort)
            ?.host
            ?.takeIf { it.matches(IPV4_PATTERN) }
        return if (ipv4 != null) "$ipv4:$port" else resolveEndpoint(formatHostPort(hostPort.host, port))
    }

    /**
     * Cloudflare returns `endpoint.host` as a hostname (e.g. engage.cloudflareclient.com:2408).
     * sing-box's WireGuard peer expects an IP literal — resolve here so the handshake works.
     * Falls back to the raw value if resolution fails (better than nothing).
     */
    fun resolveEndpoint(raw: String): String {
        val parsed = parseHostPort(raw)
        val host = parsed.host
        val port = parsed.port.takeIf { it > 0 } ?: 2408
        // Already an IPv4 literal? keep it.
        if (host.matches(IPV4_PATTERN)) return "$host:$port"
        // Already an IPv6 literal? keep it bracketed.
        if (host.contains(":")) return "[$host]:$port"
        return runCatching {
            val addresses = InetAddress.getAllByName(host)
            val ip = (addresses.firstOrNull { it is Inet4Address } ?: addresses.first()).hostAddress
            if (ip.contains(":")) "[$ip]:$port" else "$ip:$port"
        }.getOrElse { formatHostPort(host, port) }
    }

    private fun reservedBytes(value: String?): List<Int> {
        if (value.isNullOrBlank()) throw IllegalStateException("WARP response has no client_id")
        val padded = value + "=".repeat((4 - value.length % 4) % 4)
        val decoded = runCatching { Base64.getDecoder().decode(padded) }
            .recoverCatching { Base64.getUrlDecoder().decode(padded) }
            .getOrElse { throw IllegalStateException("WARP client_id is invalid", it) }
        if (decoded.size < 3) throw IllegalStateException("WARP client_id is too short")
        return decoded.take(3).map { it.toInt() and 0xff }
    }

    private fun parseHostPort(raw: String): HostPort {
        val text = raw.trim()
        if (text.startsWith("[")) {
            val host = text.substringAfter("[").substringBefore("]")
            val port = text.substringAfter("]:", "").toIntOrNull() ?: 0
            return HostPort(host, port)
        }
        val lastColon = text.lastIndexOf(':')
        if (lastColon > 0 && text.indexOf(':') == lastColon) {
            return HostPort(text.substring(0, lastColon), text.substring(lastColon + 1).toIntOrNull() ?: 0)
        }
        return HostPort(text, 0)
    }

    private fun formatHostPort(host: String, port: Int): String =
        if (host.contains(":")) "[$host]:$port" else "$host:$port"

    /**
     * Generates a WireGuard-compatible X25519 key pair using the standard JDK XDH provider.
     * Returns (privateKeyBase64, publicKeyBase64) in WireGuard's little-endian format.
     */
    private fun generateKeyPair(): Pair<String, String> {
        val kpg = KeyPairGenerator.getInstance("XDH")
        kpg.initialize(NamedParameterSpec.X25519)
        val kp = kpg.generateKeyPair()

        // Private key scalar — Java returns it in little-endian (RFC 7748) already.
        // Pad to exactly 32 bytes: JDK may omit leading zero bytes.
        val rawScalar = (kp.private as XECPrivateKey).scalar
            .orElseThrow { IllegalStateException("XEC private key scalar unavailable") }
        val privScalar = if (rawScalar.size < 32) {
            ByteArray(32).also { rawScalar.copyInto(it) }
        } else rawScalar

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

    private data class RegistrationApi(val version: String, val clientVersion: String)
    private data class HostPort(val host: String, val port: Int)

    private val REGISTRATION_APIS = listOf(
        RegistrationApi("v0a4005", "a-6.30-3596"),
        RegistrationApi("v0a2158", "a-6.10-2158")
    )
    private val IPV4_PATTERN = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")
}
