package app.mayak.desktop

import app.mayak.core.parser.ProfileParseException
import javax.net.ssl.SSLException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

object UserFacingError {
    fun describe(error: Throwable): String = when (error) {
        is SSLException -> "Не удалось проверить безопасность ссылки. Попросите владельца VPN обновить сертификат."
        is SocketTimeoutException, is UnknownHostException -> "Сервер не отвечает. Проверьте интернет и попробуйте ещё раз."
        is ProfileParseException -> "Не удалось прочитать доступ. Скопируйте ссылку целиком или попросите новую у владельца VPN."
        else -> when {
            error.message?.contains("HTTP 401") == true || error.message?.contains("HTTP 403") == true -> "Доступ отклонён. Попросите владельца VPN проверить вашу ссылку."
            error.message?.contains("HTTP 404") == true -> "Ссылка больше не доступна. Попросите новую у владельца VPN."
            error.message?.contains("XHTTP") == true -> error.message!!
            error.message?.startsWith("Сервер не отвечает") == true -> error.message!!
            error.message?.startsWith("Ядро подключения") == true -> error.message!!
            else -> "Не удалось завершить действие. Проверьте интернет и ссылку доступа."
        }
    }
}
