package app.mayak.vpn

import kotlinx.coroutines.flow.Flow

interface VpnGateway {
    fun observeState(): Flow<VpnConnectionState>
    suspend fun connect(configJson: String)
    suspend fun disconnect()
}
