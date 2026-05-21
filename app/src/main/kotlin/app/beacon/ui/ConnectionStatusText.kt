package app.beacon.ui

import app.beacon.vpn.VpnStatus

object ConnectionStatusText {
    fun from(status: VpnStatus): String {
        return when (status) {
            VpnStatus.Connected -> "Подключено"
            VpnStatus.Connecting -> "Подключение"
            VpnStatus.Disconnecting -> "Отключение"
            VpnStatus.Disconnected -> "Отключено"
        }
    }
}
