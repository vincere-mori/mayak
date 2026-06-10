package app.mayak.ui

import app.mayak.core.model.ProxyProfile
import app.mayak.core.model.Subscription
import app.mayak.data.MayakSettings
import app.mayak.vpn.VpnStatus

data class MayakUiState(
    val selectedTab: MayakTab = MayakTab.Home,
    val profiles: List<ProxyProfile> = emptyList(),
    val subscriptions: List<Subscription> = emptyList(),
    val activeProfile: ProxyProfile? = null,
    val draftKey: String = "",
    val status: VpnStatus = VpnStatus.Disconnected,
    val statusText: String = ConnectionStatusText.from(VpnStatus.Disconnected),
    val lastError: String? = null,
    val settings: MayakSettings = MayakSettings(),
    val isBusy: Boolean = false,
    val trafficUpBytesPerSec: Long = 0,
    val trafficDownBytesPerSec: Long = 0,
    val pingResults: Map<String, Long?> = emptyMap(),
    val pingingIds: Set<String> = emptySet()
)
