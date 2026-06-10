package app.mayak.core.parser

import app.mayak.core.model.ProxyProfile
import java.util.Base64

/**
 * Turns a raw subscription body into the list of servers it carries.
 *
 * A subscription body is either plain text with one `vless://` link per line,
 * or that same text encoded as a single base64 blob. Lines that are not a
 * supported VLESS Reality key (other protocols, comments, junk) are skipped
 * rather than failing the whole import.
 */
class SubscriptionParser(
    private val keyParser: ProfileInputParser = ProfileInputParser()
) {
    fun parse(body: String): List<ProxyProfile> {
        val text = decodeIfBase64(body.trim())
        return text.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("vless://", ignoreCase = true) }
            .mapNotNull { line -> runCatching { keyParser.parse(line) }.getOrNull() }
            .distinctBy { it.id }
            .toList()
    }

    private fun decodeIfBase64(body: String): String {
        if (body.contains("://")) return body
        val compact = body.filterNot { it.isWhitespace() }
        if (compact.isEmpty()) return body

        val decoder = when {
            compact.contains('-') || compact.contains('_') -> Base64.getUrlDecoder()
            else -> Base64.getMimeDecoder()
        }
        val decoded = runCatching {
            String(decoder.decode(compact), Charsets.UTF_8)
        }.getOrNull()

        return if (decoded != null && decoded.contains("://")) decoded else body
    }
}
