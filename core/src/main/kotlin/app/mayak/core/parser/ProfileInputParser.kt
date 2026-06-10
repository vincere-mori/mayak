package app.mayak.core.parser

import app.mayak.core.model.ProfileKind
import app.mayak.core.model.ProxyProfile
import app.mayak.core.model.VlessRealityProfile
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID

class ProfileInputParser(
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    fun parse(input: String): ProxyProfile {
        val text = input.trim().replace(Regex("""\s+"""), "")
        if (text.isBlank()) throw ProfileParseException("ключ пустой")

        return when {
            text.startsWith("vless://", ignoreCase = true) -> parseVlessReality(text)
            text.startsWith("{") -> throw ProfileParseException("поддерживается только vless:// Reality ключ")
            text.startsWith("http://", ignoreCase = true) ||
                text.startsWith("https://", ignoreCase = true) ->
                throw ProfileParseException("subscription добавим следующим шагом")
            else -> throw ProfileParseException("поддерживается только vless:// Reality ключ")
        }
    }

    private fun parseVlessReality(text: String): ProxyProfile {
        val uri = runCatching { URI(text) }
            .getOrElse { throw ProfileParseException("ключ vless битый") }

        if (!uri.scheme.equals("vless", ignoreCase = true)) {
            throw ProfileParseException("ожидался vless:// ключ")
        }

        val uuid = uri.userInfo?.trim().orEmpty()
        runCatching { UUID.fromString(uuid) }
            .getOrElse { throw ProfileParseException("uuid в ключе битый") }

        val host = uri.host ?: throw ProfileParseException("не найден сервер")
        val port = uri.port.takeIf { it > 0 } ?: throw ProfileParseException("не найден порт")
        val query = parseQuery(uri.rawQuery.orEmpty())
        val security = query["security"].orEmpty()

        if (!security.equals("reality", ignoreCase = true)) {
            throw ProfileParseException("нужен VLESS Reality ключ")
        }

        val publicKey = query["pbk"] ?: query["publicKey"]
            ?: throw ProfileParseException("не найден reality public key")
        val serverName = query["sni"] ?: query["serverName"] ?: query["server_name"]
            ?: throw ProfileParseException("не найден SNI")
        val displayName = decode(uri.rawFragment).ifBlank { host }

        val vless = VlessRealityProfile(
            uuid = uuid,
            server = host,
            port = port,
            serverName = serverName,
            publicKey = publicKey,
            shortId = query["sid"] ?: query["shortId"],
            fingerprint = query["fp"]?.ifBlank { null } ?: "chrome",
            flow = query["flow"]?.ifBlank { null },
            spiderX = query["spx"]?.ifBlank { null },
            postQuantumVerify = query["pqv"]?.ifBlank { null },
            displayName = displayName
        )

        return ProxyProfile(
            id = stableId(text),
            name = displayName,
            kind = ProfileKind.VlessReality,
            source = text,
            host = host,
            port = port,
            createdAtMillis = clock(),
            vless = vless
        )
    }

    private fun parseQuery(rawQuery: String): Map<String, String> {
        if (rawQuery.isBlank()) return emptyMap()

        return rawQuery.split("&")
            .mapNotNull { part ->
                val index = part.indexOf("=")
                if (index <= 0) return@mapNotNull null
                decode(part.substring(0, index)) to decode(part.substring(index + 1))
            }
            .toMap()
    }

    private fun decode(value: String?): String {
        if (value.isNullOrBlank()) return ""
        return URLDecoder.decode(value, StandardCharsets.UTF_8.name())
    }

    private fun stableId(value: String): String {
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return hash.take(16)
    }
}
