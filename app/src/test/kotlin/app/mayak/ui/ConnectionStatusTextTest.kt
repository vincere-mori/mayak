package app.mayak.ui

import app.mayak.vpn.VpnStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class ConnectionStatusTextTest {
    @Test
    fun mapsStatusToShortRussianText() {
        assertEquals("Подключено", ConnectionStatusText.from(VpnStatus.Connected))
        assertEquals("Подключение", ConnectionStatusText.from(VpnStatus.Connecting))
        assertEquals("Отключение", ConnectionStatusText.from(VpnStatus.Disconnecting))
        assertEquals("Отключено", ConnectionStatusText.from(VpnStatus.Disconnected))
    }
}
