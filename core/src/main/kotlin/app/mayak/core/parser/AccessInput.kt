package app.mayak.core.parser

import java.net.URI
import java.net.URLDecoder

object AccessInput {
    fun normalize(input: String): String {
        val text = input.trim()
        if (!text.startsWith("mayak://", true)) return text
        val uri = runCatching { URI(text) }.getOrElse { throw ProfileParseException("Ссылка повреждена. Скопируйте её целиком ещё раз.") }
        require(uri.host == "import") { "Эта ссылка не предназначена для добавления VPN." }
        val encoded = uri.rawQuery.orEmpty().split('&').firstOrNull { it.startsWith("url=") }
            ?.substringAfter('=') ?: throw ProfileParseException("В ссылке нет данных подключения.")
        val result = URLDecoder.decode(encoded, "UTF-8")
        require(!result.startsWith("mayak://", true)) { "Ссылка повреждена." }
        return result
    }

    fun isSubscription(input: String): Boolean {
        val uri = runCatching { URI(normalize(input)) }.getOrNull() ?: return false
        return uri.scheme?.lowercase() in setOf("http", "https") && !uri.host.isNullOrBlank() && uri.userInfo == null
    }
}
