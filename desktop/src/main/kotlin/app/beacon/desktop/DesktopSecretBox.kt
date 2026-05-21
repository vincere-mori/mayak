package app.beacon.desktop

import com.sun.jna.platform.win32.Crypt32Util
import java.util.Base64

class DesktopSecretBox(
    private val osName: String = System.getProperty("os.name"),
    private val dpapiProtect: (ByteArray) -> ByteArray = { Crypt32Util.cryptProtectData(it) },
    private val dpapiUnprotect: (ByteArray) -> ByteArray = { Crypt32Util.cryptUnprotectData(it) }
) {
    fun protect(value: String): String {
        val raw = value.toByteArray(Charsets.UTF_8)
        if (isWindows()) {
            return DPAPI_PREFIX + Base64.getEncoder().encodeToString(dpapiProtect(raw))
        }
        return PLAIN_PREFIX + Base64.getEncoder().encodeToString(raw)
    }

    fun unprotect(value: String): String {
        if (value.startsWith(PLAIN_PREFIX)) {
            val raw = value.substringAfter(":")
            return runCatching {
                String(Base64.getDecoder().decode(raw), Charsets.UTF_8)
            }.getOrDefault(value)
        }
        if (value.startsWith(DPAPI_PREFIX) && isWindows()) {
            val raw = value.substringAfter(":")
            return runCatching {
                val bytes = Base64.getDecoder().decode(raw)
                String(dpapiUnprotect(bytes), Charsets.UTF_8)
            }.getOrElse { value }
        }
        return value
    }

    private fun isWindows(): Boolean =
        osName.contains("Windows", ignoreCase = true)

    private companion object {
        const val PLAIN_PREFIX = "plain:"
        const val DPAPI_PREFIX = "dpapi:"
    }
}
