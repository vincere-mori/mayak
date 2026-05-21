package app.beacon.vpn

import android.content.Context
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.Flow

class SingBoxVpnGateway(context: Context) : VpnGateway {
    private val appContext = context.applicationContext

    override fun observeState(): Flow<VpnConnectionState> {
        return BeaconVpnEvents.state
    }

    override suspend fun connect(configJson: String) {
        BeaconVpnEvents.update(VpnConnectionState(VpnStatus.Connecting))
        ContextCompat.startForegroundService(
            appContext,
            BeaconVpnService.connectIntent(appContext, configJson)
        )
    }

    override suspend fun disconnect() {
        BeaconVpnEvents.update(VpnConnectionState(VpnStatus.Disconnecting))
        appContext.startService(
            BeaconVpnService.disconnectIntent(appContext)
        )
    }
}
