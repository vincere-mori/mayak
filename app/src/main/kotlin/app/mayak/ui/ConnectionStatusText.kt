package app.mayak.ui

import app.mayak.vpn.VpnStatus

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
