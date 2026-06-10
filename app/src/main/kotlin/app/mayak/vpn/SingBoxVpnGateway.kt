package app.mayak.vpn

import android.content.Context
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.Flow

class SingBoxVpnGateway(context: Context) : VpnGateway {
    private val appContext = context.applicationContext

    override fun observeState(): Flow<VpnConnectionState> {
        return MayakVpnEvents.state
    }

    override suspend fun connect(configJson: String) {
        MayakVpnEvents.update(VpnConnectionState(VpnStatus.Connecting))
        ContextCompat.startForegroundService(
            appContext,
            MayakVpnService.connectIntent(appContext, configJson)
        )
    }

    override suspend fun disconnect() {
        MayakVpnEvents.update(VpnConnectionState(VpnStatus.Disconnecting))
        appContext.startService(
            MayakVpnService.disconnectIntent(appContext)
        )
    }
}
