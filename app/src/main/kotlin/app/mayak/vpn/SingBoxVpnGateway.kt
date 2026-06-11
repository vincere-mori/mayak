package app.mayak.vpn

import android.content.Context
import androidx.core.content.ContextCompat
import app.mayak.log.AppJournal
import kotlinx.coroutines.flow.Flow

class SingBoxVpnGateway(context: Context) : VpnGateway {
    private val appContext = context.applicationContext

    override fun observeState(): Flow<VpnConnectionState> {
        return MayakVpnEvents.state
    }

    override suspend fun connect(configJson: String) {
        MayakVpnEvents.update(VpnConnectionState(VpnStatus.Connecting))
        AppJournal.info("gateway", "startForegroundService CONNECT")
        ContextCompat.startForegroundService(
            appContext,
            MayakVpnService.connectIntent(appContext, configJson)
        )
    }

    override suspend fun disconnect() {
        MayakVpnEvents.update(VpnConnectionState(VpnStatus.Disconnecting))
        AppJournal.info("gateway", "startService DISCONNECT")
        val result = appContext.startService(
            MayakVpnService.disconnectIntent(appContext)
        )
        AppJournal.info("gateway", "disconnect service dispatch result=${result?.className ?: "null"}")
    }
}
