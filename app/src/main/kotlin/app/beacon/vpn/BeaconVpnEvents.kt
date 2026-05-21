package app.beacon.vpn

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object BeaconVpnEvents {
    private val mutableState = MutableStateFlow(VpnConnectionState())
    val state: StateFlow<VpnConnectionState> = mutableState

    fun update(value: VpnConnectionState) {
        mutableState.value = value
    }
}
