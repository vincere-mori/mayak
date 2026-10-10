package app.mayak.desktop

import java.awt.Container
import javax.swing.*
import kotlin.test.*

class SettingsLayoutTest {
    @Test fun controlsFitAtMinimumAndDefaultWidthWithoutChangingValues() {
        SwingUtilities.invokeAndWait {
            for (lang in AppLanguage.entries) {
                L.lang = lang
                var state = DesktopProfileState(language = lang, ipv6Enabled = true)
                var changes = 0
                val panel = SettingsPanel({ state }, { state = it; changes++ }, {}, {}, {}, {}, {})
                for (width in listOf(500, 560, 700)) {
                    panel.setSize(width, 650)
                    layout(panel)
                    panel.sync(state)
                    layout(panel)
                    for (control in descendants(panel).filter { (it is AbstractButton || it is JComboBox<*>) && it.isVisible && it.parent.isVisible }) {
                        assertTrue(control.width > 0, "zero width: $control")
                        assertTrue(control.x >= 0 && control.x + control.width <= control.parent.width, "clipped control: $control")
                    }
                }
                assertEquals(0, changes)
                assertTrue(state.ipv6Enabled)
            }
            L.lang = AppLanguage.RU
        }
    }

    private fun layout(container: Container) { container.doLayout(); container.components.filterIsInstance<Container>().forEach(::layout) }
    private fun descendants(container: Container): List<java.awt.Component> = container.components.flatMap { listOf(it) + if (it is Container) descendants(it) else emptyList() }
}
