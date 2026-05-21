package app.beacon

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.beacon.core.model.DnsMode
import app.beacon.core.model.ProxyProfile
import app.beacon.core.parser.ProfileInputParser
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(
    application: Application,
    private val repository: ProfileRepository = SharedPrefsProfileRepository(application),
    private val vpnGateway: VpnGateway = SingBoxVpnGateway(application),
    private val parser: ProfileInputParser = ProfileInputParser(),
    private val configBuilder: SingBoxConfigBuilder = SingBoxConfigBuilder()
) : AndroidViewModel(application) {
    private val selectedTab = MutableStateFlow(BeaconTab.Home)
    private val draftKey = MutableStateFlow("")
    private val lastError = MutableStateFlow<String?>(null)
    private val busy = MutableStateFlow(false)

    private val profileSnapshot = combine(
        repository.observeProfiles(),
        repository.observeActiveProfileId(),
        repository.observeSettings()
    ) { profiles, activeProfileId, settings ->
        val active = profiles.firstOrNull { it.id == activeProfileId } ?: profiles.firstOrNull()
        ProfileSnapshot(
            profiles = profiles,
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

    val state: StateFlow<BeaconUiState> = combine(profileSnapshot, uiInputs) { snapshot, inputs ->
        BeaconUiState(
            selectedTab = inputs.selectedTab,
            profiles = snapshot.profiles,
            activeProfile = snapshot.activeProfile,
            draftKey = inputs.draftKey,
            status = inputs.vpnState.status,
            statusText = ConnectionStatusText.from(inputs.vpnState.status),
            lastError = inputs.lastError ?: inputs.vpnState.error,
            settings = snapshot.settings,
            isBusy = inputs.isBusy
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
}
