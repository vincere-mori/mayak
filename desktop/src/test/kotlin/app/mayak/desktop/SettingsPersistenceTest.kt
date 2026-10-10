package app.mayak.desktop

import app.mayak.core.model.DnsMode
import app.mayak.core.model.RoutingSettings
import app.mayak.core.singbox.InboundMode
import java.nio.file.Files
import kotlin.test.*

class SettingsPersistenceTest {
    private fun fixture(file: java.nio.file.Path) = DesktopProfileStore(file, DesktopSecretBox(osName = "Linux"))

    @Test fun updatingOneSettingKeepsTheOthersAndMakesABackup() {
        val file = Files.createTempDirectory("mayak-settings").resolve("profiles.json")
        val store = fixture(file)
        val old = DesktopProfileState(
            dnsMode = DnsMode.Google, ipv6Enabled = true, inboundMode = InboundMode.Tun,
            trayEnabled = false, language = AppLanguage.EN,
            routing = RoutingSettings.defaults().copy(exceptionDomains = listOf("my.example"))
        )
        store.save(old)
        store.save(store.load().copy(trayEnabled = true))
        val restored = store.load()
        assertEquals(DnsMode.Google, restored.dnsMode)
        assertTrue(restored.ipv6Enabled)
        assertEquals(InboundMode.Tun, restored.inboundMode)
        assertEquals(AppLanguage.EN, restored.language)
        assertEquals(listOf("my.example"), restored.routing.exceptionDomains)
        assertTrue(restored.trayEnabled)
        assertTrue(Files.exists(file.resolveSibling("profiles.json.bak")))
    }

    @Test fun damagedSettingsRecoverFromBackupOrFailWithoutOverwriting() {
        val file = Files.createTempDirectory("mayak-recovery").resolve("profiles.json")
        val store = fixture(file)
        store.save(DesktopProfileState(dnsMode = DnsMode.Google))
        store.save(store.load().copy(ipv6Enabled = true))
        Files.writeString(file, "damaged")
        assertEquals(DnsMode.Google, store.load().dnsMode)
        assertEquals("damaged", Files.readString(file))
        Files.writeString(file.resolveSibling("profiles.json.bak"), "damaged too")
        assertFailsWith<IllegalStateException> { store.load() }
        assertEquals("damaged", Files.readString(file))
    }
}
