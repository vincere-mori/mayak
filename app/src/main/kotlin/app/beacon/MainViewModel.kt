package app.beacon

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.beacon.core.model.DnsMode
import app.beacon.core.model.ProxyProfile
import app.beacon.core.model.Subscription
import app.beacon.core.net.LatencyProbe
import app.beacon.core.net.SubscriptionFetcher
import app.beacon.core.parser.ProfileInputParser
import app.beacon.core.parser.SubscriptionParser
import app.beacon.core.singbox.SingBoxConfigBuilder
import app.beacon.core.singbox.SingBoxConfigSettings
import app.beacon.data.BeaconSettings
import app.beacon.data.ProfileRepository
import app.beacon.data.SharedPrefsProfileRepository
import app.beacon.ui.BeaconTab
import app.beacon.ui.BeaconUiState
import app.beacon.ui.ConnectionStatusText
import app.beacon.vpn.SingBoxVpnGateway
import app.beacon.vpn.VpnGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URI
import java.util.UUID

class MainViewModel(
    application: Application,
    private val repository: ProfileRepository = SharedPrefsProfileRepository(application),
    private val vpnGateway: VpnGateway = SingBoxVpnGateway(application),
    private val parser: ProfileInputParser = ProfileInputParser(),
    private val configBuilder: SingBoxConfigBuilder = SingBoxConfigBuilder(),
    private val subscriptionFetcher: SubscriptionFetcher = SubscriptionFetcher(),
    private val subscriptionParser: SubscriptionParser = SubscriptionParser(),
    private val latencyProbe: LatencyProbe = LatencyProbe()
) : AndroidViewModel(application) {
    private val selectedTab = MutableStateFlow(BeaconTab.Home)
    private val draftKey = MutableStateFlow("")
    private val lastError = MutableStateFlow<String?>(null)
    private val busy = MutableStateFlow(false)

    private val pingResults = MutableStateFlow<Map<String, Long?>>(emptyMap())
    private val pingingIds = MutableStateFlow<Set<String>>(emptySet())

    private val profileSnapshot = combine(
        repository.observeProfiles(),
        repository.observeSubscriptions(),
        repository.observeActiveProfileId(),
        repository.observeSettings()
    ) { profiles, subscriptions, activeProfileId, settings ->
        val all = profiles + subscriptions.flatMap { it.profiles }
        val active = all.firstOrNull { it.id == activeProfileId } ?: all.firstOrNull()
        ProfileSnapshot(
            profiles = profiles,
            subscriptions = subscriptions,
            activeProfile = active,
            settings = settings
        )
    }

    private val uiInputs = combine(
        vpnGateway.observeState(),
        selectedTab,
        draftKey,
        lastError,
        busy
    ) { vpnState, tab, draft, error, isBusy ->
        UiInputs(
            vpnState = vpnState,
            selectedTab = tab,
            draftKey = draft,
            lastError = error,
            isBusy = isBusy
        )
    }

    private val pingSnapshot = combine(pingResults, pingingIds) { results, pinging ->
        PingSnapshot(results, pinging)
    }

    val state: StateFlow<BeaconUiState> = combine(profileSnapshot, uiInputs, pingSnapshot) { snapshot, inputs, ping ->
        BeaconUiState(
            selectedTab = inputs.selectedTab,
            profiles = snapshot.profiles,
            subscriptions = snapshot.subscriptions,
            activeProfile = snapshot.activeProfile,
            draftKey = inputs.draftKey,
            status = inputs.vpnState.status,
            statusText = ConnectionStatusText.from(inputs.vpnState.status),
            lastError = inputs.lastError ?: inputs.vpnState.error,
            settings = snapshot.settings,
            isBusy = inputs.isBusy,
            pingResults = ping.results,
            pingingIds = ping.pinging
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BeaconUiState()
    )

    fun selectTab(tab: BeaconTab) {
        selectedTab.value = tab
    }

    fun setDraftKey(value: String) {
        draftKey.value = value
        lastError.value = null
    }

    fun saveDraftProfile() {
        runAction {
            val profile = parser.parse(draftKey.value)
            repository.saveProfile(profile)
            repository.setActiveProfile(profile.id)
            draftKey.value = ""
            selectedTab.value = BeaconTab.Home
        }
    }

    fun selectProfile(profileId: String) {
        runAction {
            repository.setActiveProfile(profileId)
        }
    }

    fun deleteProfile(profileId: String) {
        runAction {
            repository.deleteProfile(profileId)
        }
    }

    fun addSubscription(url: String) {
        val trimmed = url.trim()
        runAction {
            if (!trimmed.startsWith("http://", true) && !trimmed.startsWith("https://", true)) {
                throw IllegalArgumentException("ссылка подписки должна начинаться с http:// или https://")
            }
            val servers = withContext(Dispatchers.IO) {
                subscriptionParser.parse(subscriptionFetcher.fetch(trimmed))
            }
            if (servers.isEmpty()) throw IllegalStateException("в подписке нет VLESS Reality серверов")
            repository.saveSubscription(
                Subscription(
                    id = UUID.randomUUID().toString().take(12),
                    name = subscriptionName(trimmed),
                    url = trimmed,
                    profiles = servers,
                    updatedAtMillis = System.currentTimeMillis()
                )
            )
        }
    }

    fun refreshSubscription(subscription: Subscription) {
        runAction {
            val servers = withContext(Dispatchers.IO) {
                subscriptionParser.parse(subscriptionFetcher.fetch(subscription.url))
            }
            if (servers.isEmpty()) throw IllegalStateException("в подписке нет серверов")
            repository.saveSubscription(
                subscription.copy(profiles = servers, updatedAtMillis = System.currentTimeMillis())
            )
        }
    }

    fun deleteSubscription(subscriptionId: String) {
        runAction {
            repository.deleteSubscription(subscriptionId)
        }
    }

    fun pingSubscription(subscription: Subscription) {
        subscription.profiles.forEach { pingServer(it) }
    }

    fun pingServer(profile: ProxyProfile) {
        if (profile.id in pingingIds.value) return
        pingingIds.value = pingingIds.value + profile.id
        viewModelScope.launch {
            val ms = withContext(Dispatchers.IO) {
                latencyProbe.tcpLatencyMs(profile.host, profile.port)
            }
            pingResults.value = pingResults.value + (profile.id to ms)
            pingingIds.value = pingingIds.value - profile.id
        }
    }

    fun setDnsMode(mode: DnsMode) {
        runAction {
            repository.updateSettings(repository.currentSettings().copy(dnsMode = mode))
        }
    }

    fun setIpv6Enabled(enabled: Boolean) {
        runAction {
            repository.updateSettings(repository.currentSettings().copy(ipv6Enabled = enabled))
        }
    }

    fun connect() {
        runAction {
            val profile = repository.currentActiveProfile()
                ?: throw IllegalStateException("сначала добавь ключ")
            val settings = repository.currentSettings()
            val config = configBuilder.build(
                profile = profile,
                settings = SingBoxConfigSettings(
                    dnsMode = settings.dnsMode,
                    ipv6Enabled = settings.ipv6Enabled
                )
            )
            vpnGateway.connect(config)
        }
    }

    fun disconnect() {
        runAction {
            vpnGateway.disconnect()
        }
    }

    private fun subscriptionName(url: String): String {
        val host = runCatching { URI(url).host }.getOrNull()?.takeIf { it.isNotBlank() }
        return host ?: "Подписка"
    }

    private fun runAction(block: suspend () -> Unit) {
        viewModelScope.launch {
            busy.value = true
            lastError.value = null
            runCatching { block() }
                .onFailure { lastError.value = it.message ?: "ошибка" }
            busy.value = false
        }
    }

    private data class ProfileSnapshot(
        val profiles: List<ProxyProfile>,
        val subscriptions: List<Subscription>,
        val activeProfile: ProxyProfile?,
        val settings: BeaconSettings
    )

    private data class UiInputs(
        val vpnState: app.beacon.vpn.VpnConnectionState,
        val selectedTab: BeaconTab,
        val draftKey: String,
        val lastError: String?,
        val isBusy: Boolean
    )

    private data class PingSnapshot(
        val results: Map<String, Long?>,
        val pinging: Set<String>
    )
}
