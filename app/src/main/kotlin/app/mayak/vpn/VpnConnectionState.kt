package app.mayak.vpn

data class VpnConnectionState(
    val status: VpnStatus = VpnStatus.Disconnected,
    val error: String? = null
)

enum class VpnStatus {
    Connected,
    Connecting,
    Disconnecting,
    Disconnected
}
