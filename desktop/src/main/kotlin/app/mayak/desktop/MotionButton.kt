package app.mayak.desktop

import java.awt.*
import java.awt.event.*
import javax.swing.*
import javax.swing.border.EmptyBorder

enum class ButtonTone { PRIMARY, SECONDARY, SUCCESS, WARNING }

class MotionButton(text: String, initialTone: ButtonTone = ButtonTone.SECONDARY) : JButton(text) {
    var tone = initialTone
        set(value) { field = value; foreground = if (value == ButtonTone.SECONDARY) MayakTheme.TEXT_DIM else Color.WHITE; repaint() }
    private val motion = ButtonMotion(this) { tone }
    init {
        motion.install()
        tone = initialTone
    }
    override fun paintComponent(g: Graphics) {
        val graphics = motion.graphics(g)
        try { motion.paint(graphics); motion.paintLabel(graphics) } finally { graphics.dispose() }
    }
}

class MotionToggle(text: String) : JToggleButton(text) {
    private val motion = ButtonMotion(this) { if (isSelected) ButtonTone.PRIMARY else ButtonTone.SECONDARY }
    init {
        motion.install()
        addChangeListener { foreground = if (isSelected) Color.WHITE else MayakTheme.TEXT_DIM; repaint() }
    }
    override fun paintComponent(g: Graphics) {
        val graphics = motion.graphics(g)
        try { motion.paint(graphics); motion.paintLabel(graphics) } finally { graphics.dispose() }
    }
}

private class ButtonMotion(private val button: AbstractButton, private val tone: () -> ButtonTone) {
    private var hover = 0f
    private var press = 0f
    private val hoverAnimator = HoverAnimator(button, 140) { hover = it }
    private val pressAnimator = HoverAnimator(button, 100) { press = it }

    fun install() {
        button.isOpaque = false
        button.isContentAreaFilled = false
        button.isBorderPainted = false
        button.isFocusPainted = false
        button.isRolloverEnabled = true
        button.font = button.font.deriveFont(Font.BOLD, 12f)
        button.foreground = MayakTheme.TEXT_DIM
        button.border = EmptyBorder(11, 16, 11, 16)
        button.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        button.addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent) { if (button.isEnabled) hoverAnimator.setTarget(1f) }
            override fun mouseExited(e: MouseEvent) { hoverAnimator.setTarget(0f); pressAnimator.setTarget(0f) }
            override fun mousePressed(e: MouseEvent) { if (button.isEnabled && SwingUtilities.isLeftMouseButton(e)) pressAnimator.setTarget(1f) }
            override fun mouseReleased(e: MouseEvent) { pressAnimator.setTarget(0f) }
        })
        button.addFocusListener(object : FocusAdapter() {
            override fun focusGained(e: FocusEvent) = button.repaint()
            override fun focusLost(e: FocusEvent) = button.repaint()
        })
        button.addPropertyChangeListener("enabled") {
            if (!button.isEnabled) { hoverAnimator.setTarget(0f); pressAnimator.setTarget(0f) }
            button.cursor = Cursor.getPredefinedCursor(if (button.isEnabled) Cursor.HAND_CURSOR else Cursor.DEFAULT_CURSOR)
            button.repaint()
        }
    }

    fun graphics(original: Graphics): Graphics2D = (original.create() as Graphics2D).apply {
        setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        val scale = 1.0 - 0.018 * press
        translate(button.width * (1 - scale) / 2, button.height * (1 - scale) / 2)
        scale(scale, scale)
        if (!button.isEnabled) composite = AlphaComposite.SrcOver.derive(0.42f)
    }

    fun paint(g: Graphics2D) {
        val base = when (tone()) {
            ButtonTone.PRIMARY -> Color(53, 104, 223)
            ButtonTone.SUCCESS -> Color(23, 145, 115)
            ButtonTone.WARNING -> Color(166, 116, 40)
            ButtonTone.SECONDARY -> MayakTheme.BG_INPUT
        }
        val top = mix(base, if (tone() == ButtonTone.SECONDARY) Color(42, 57, 96) else base.brighter(), 0.24f + hover * 0.30f)
        val bottom = mix(base, Color(0, 0, 0), press * 0.10f)
        val w = button.width - 5
        val h = button.height - 6
        g.color = Color(0, 0, 0, 26 + (hover * 18).toInt())
        g.fillRoundRect(3, 5, w, h, 20, 20)
        g.paint = GradientPaint(0f, 2f, top, 0f, h.toFloat(), bottom)
        g.fillRoundRect(2, 2, w, h, 18, 18)
        g.color = mix(MayakTheme.BORDER_SOFT, MayakTheme.ACCENT_LIGHT, hover * 0.36f)
        g.stroke = BasicStroke(1f)
        g.drawRoundRect(2, 2, w, h, 18, 18)
        if (button.isFocusOwner) {
            g.color = MayakTheme.ACCENT_LIGHT
            g.stroke = BasicStroke(1.5f)
            g.drawRoundRect(0, 0, button.width - 2, button.height - 2, 21, 21)
        }
    }

    fun paintLabel(g: Graphics2D) {
        g.composite = AlphaComposite.SrcOver.derive(if (button.isEnabled) 1f else 0.72f)
        g.font = button.font
        g.color = if (button.isEnabled) button.foreground else MayakTheme.TEXT_DIM
        val insets = button.insets
        val view = Rectangle(insets.left, insets.top, (button.width - insets.left - insets.right).coerceAtLeast(0), (button.height - insets.top - insets.bottom).coerceAtLeast(0))
        val iconRect = Rectangle()
        val textRect = Rectangle()
        val text = SwingUtilities.layoutCompoundLabel(button, g.fontMetrics, button.text.orEmpty(), button.icon,
            button.verticalAlignment, button.horizontalAlignment, button.verticalTextPosition, button.horizontalTextPosition,
            view, iconRect, textRect, button.iconTextGap)
        button.icon?.paintIcon(button, g, iconRect.x, iconRect.y)
        javax.swing.plaf.basic.BasicGraphicsUtils.drawStringUnderlineCharAt(g, text, button.displayedMnemonicIndex, textRect.x, textRect.y + g.fontMetrics.ascent)
    }

    private fun mix(a: Color, b: Color, value: Float) = Color(
        (a.red + (b.red - a.red) * value).toInt().coerceIn(0, 255),
        (a.green + (b.green - a.green) * value).toInt().coerceIn(0, 255),
        (a.blue + (b.blue - a.blue) * value).toInt().coerceIn(0, 255)
    )
}
