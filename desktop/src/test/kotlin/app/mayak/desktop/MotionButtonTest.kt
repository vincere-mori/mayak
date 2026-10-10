package app.mayak.desktop

import java.awt.event.MouseEvent
import javax.swing.SwingUtilities
import kotlin.test.*

class MotionButtonTest {
    @Test fun rightClickDoesNotActivateAndDisabledButtonCannotActivate() {
        SwingUtilities.invokeAndWait {
            val button = MotionButton("Подключить")
            button.setSize(180, 44)
            var clicks = 0
            button.addActionListener { clicks++ }
            fun click(mouse: Int) {
                button.dispatchEvent(MouseEvent(button, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), 0, 30, 20, 1, mouse == MouseEvent.BUTTON3, mouse))
                button.dispatchEvent(MouseEvent(button, MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(), 0, 30, 20, 1, mouse == MouseEvent.BUTTON3, mouse))
            }
            click(MouseEvent.BUTTON3)
            assertEquals(0, clicks)
            click(MouseEvent.BUTTON1)
            assertEquals(1, clicks)
            button.isEnabled = false
            click(MouseEvent.BUTTON1)
            assertEquals(1, clicks)
        }
    }
}
