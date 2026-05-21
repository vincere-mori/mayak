package app.beacon.desktop

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.RoundRectangle2D
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants
import javax.swing.border.EmptyBorder

object BeaconTheme {

    val BG_TOP = Color(15, 21, 53)
    val BG_BOT = Color(8, 12, 34)
    val CARD = Color(22, 32, 70, 235)
    val CARD_SOLID = Color(22, 32, 70)
    val BG_INPUT = Color(15, 22, 54)
    val BORDER = Color(42, 58, 110)
    val BORDER_SOFT = Color(35, 48, 92)
    val TEXT = Color(225, 232, 250)
    val TEXT_DIM = Color(180, 195, 230)
    val MUTED = Color(120, 140, 190)
    val ACCENT = Color(60, 110, 230)
    val ACCENT_HOVER = Color(80, 130, 245)
    val ACCENT_LIGHT = Color(130, 180, 255)
    val SUCCESS = Color(52, 211, 153)
    val WARN = Color(251, 191, 36)
    val DANGER = Color(248, 113, 113)

    fun accentButton(text: String) = JButton(text).apply {
        isOpaque = true
        background = ACCENT
        foreground = Color.WHITE
        font = font.deriveFont(Font.BOLD, 13f)
        isFocusPainted = false
        border = BorderFactory.createEmptyBorder(0, 18, 0, 18)
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        putClientProperty("Button.arc", 10)
        addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent) { background = ACCENT_HOVER; repaint() }
            override fun mouseExited(e: MouseEvent) { background = ACCENT; repaint() }
        })
    }

    fun ghostButton(text: String) = JButton(text).apply {
        isOpaque = true
        background = CARD_SOLID
        foreground = TEXT_DIM
        font = font.deriveFont(Font.PLAIN, 12f)
        isFocusPainted = false
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER, 1, true),
            BorderFactory.createEmptyBorder(6, 12, 6, 12)
        )
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        putClientProperty("Button.arc", 8)
        addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent) { foreground = TEXT; repaint() }
            override fun mouseExited(e: MouseEvent) { foreground = TEXT_DIM; repaint() }
        })
    }

    fun iconButton(glyph: String, tip: String) = JButton(glyph).apply {
        isOpaque = false
        isContentAreaFilled = false
        foreground = TEXT_DIM
        font = font.deriveFont(Font.PLAIN, 18f)
        isFocusPainted = false
        border = EmptyBorder(8, 10, 8, 10)
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        toolTipText = tip
        addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent) { foreground = TEXT; repaint() }
            override fun mouseExited(e: MouseEvent) { foreground = TEXT_DIM; repaint() }
        })
    }

    fun card(radius: Float = 14f): JPanel = object : JPanel() {
        override fun paintComponent(g: Graphics) {
            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color = CARD
            g2.fill(RoundRectangle2D.Float(0f, 0f, width.toFloat(), height.toFloat(), radius, radius))
            g2.color = BORDER_SOFT
            g2.stroke = BasicStroke(1f)
            g2.draw(RoundRectangle2D.Float(0.5f, 0.5f, width - 1f, height - 1f, radius, radius))
        }
    }.apply {
        isOpaque = false
        border = EmptyBorder(14, 16, 14, 16)
    }

    fun pillTag(text: String, color: Color) = JLabel(text).apply {
        isOpaque = false
        font = font.deriveFont(Font.BOLD, 11f)
        foreground = color
        horizontalAlignment = SwingConstants.CENTER
        border = EmptyBorder(4, 10, 4, 10)
    }

    fun JComponent.preferred(w: Int, h: Int) = apply {
        preferredSize = Dimension(w, h)
        minimumSize = Dimension(w, h)
        maximumSize = Dimension(w, h)
    }
}
