package app.mayak.desktop

import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopSecretBoxTest {
    @Test
    fun keepsOldPlainJsonReadable() {
        val json = """{"profiles":[]}"""

        assertEquals(json, DesktopSecretBox().unprotect(json))
    }

    @Test
    fun readsLegacyPlainPrefix() {
        val json = """{"profiles":[]}"""
        val saved = "plain:" + Base64.getEncoder().encodeToString(json.toByteArray())

        assertEquals(json, DesktopSecretBox().unprotect(saved))
    }

    @Test
    fun protectsWithDpapiOnWindows() {
        val json = """{"profiles":[{"name":"x"}]}"""
        val box = DesktopSecretBox(
            osName = "Windows 11",
            dpapiProtect = { ("locked:" + String(it, Charsets.UTF_8)).toByteArray() },
            dpapiUnprotect = { String(it, Charsets.UTF_8).removePrefix("locked:").toByteArray() }
        )

        val saved = box.protect(json)

        assertTrue(saved.startsWith("dpapi:"))
        assertFalse(saved.contains(json))
        assertEquals(json, box.unprotect(saved))
    }
}
