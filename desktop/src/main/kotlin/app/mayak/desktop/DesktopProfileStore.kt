package app.mayak.desktop

import app.mayak.core.model.ProxyProfile
import app.mayak.core.model.Subscription
import app.mayak.core.model.DnsMode
import app.mayak.core.model.RoutingSettings
import app.mayak.core.parser.restoreTransport
import app.mayak.core.singbox.InboundMode
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.writeText

class DesktopProfileStore(
    private val file: Path = DesktopPaths.profilesFile,
    private val secretBox: DesktopSecretBox = DesktopSecretBox(),
    private val json: Json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        prettyPrint = true
    }
) {
    fun load(): DesktopProfileState {
        if (!file.exists()) return DesktopProfileState()
        return runCatching { decode(Files.readString(file)) }.getOrElse {
            val backup = file.resolveSibling(file.fileName.toString() + ".bak")
            if (backup.exists()) runCatching { decode(Files.readString(backup)) }.getOrNull()?.let { return it }
            throw IllegalStateException("Не удалось прочитать настройки. Исходный файл сохранён и не будет заменён пустыми настройками.", it)
        }
    }

    private fun decode(encoded: String) = json.decodeFromString<DesktopProfileState>(secretBox.unprotect(encoded)).migrated()

    fun save(state: DesktopProfileState) {
        file.parent?.let { Files.createDirectories(it) }
        val tmp = file.resolveSibling(file.fileName.toString() + ".tmp")
        tmp.writeText(secretBox.protect(json.encodeToString(state)))
        if (file.exists()) {
            val old = Files.readString(file)
            val suffix = if (runCatching { decode(old) }.isSuccess) ".bak" else ".damaged"
            Files.copy(file, file.resolveSibling(file.fileName.toString() + suffix), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        }
        Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE)
    }
}

@Serializable
data class DesktopProfileState(
    val profiles: List<ProxyProfile> = emptyList(),
    val subscriptions: List<Subscription> = emptyList(),
    val activeProfileId: String? = null,
    val dnsMode: DnsMode = DnsMode.Cloudflare,
    val ipv6Enabled: Boolean = false,
    val inboundMode: InboundMode = InboundMode.Mixed,
    val warpEnabled: Boolean = false,
    val warpCredentials: WarpCredentials? = null,
    val routing: RoutingSettings = RoutingSettings.defaults(),
    val trayEnabled: Boolean = true,
    val language: AppLanguage = AppLanguage.RU,
    val trayNoticeShown: Boolean = false
) {
    /** Every server the user has — standalone keys and subscription servers. */
    val allProfiles: List<ProxyProfile>
        get() = profiles + subscriptions.flatMap { it.profiles }

    val activeProfile: ProxyProfile?
        get() = allProfiles.let { all -> all.firstOrNull { it.id == activeProfileId } ?: all.firstOrNull() }

    fun migrated(): DesktopProfileState = copy(
        routing = routing.ensureDefaults(),
        profiles = profiles.map { it.restoreTransport() },
        subscriptions = subscriptions.map { it.copy(profiles = it.profiles.map { profile -> profile.restoreTransport() }) }
    )
}
