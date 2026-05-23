package app.beacon.desktop

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Ellipse2D
import java.awt.geom.RoundRectangle2D

private typealias Painter = (Graphics2D, Int, Int, Int, Int) -> Unit

/**
 * Draws small country flags. Windows Swing cannot render regional-indicator
 * emoji flags, so flags are painted from simple geometry. Countries without a
 * painted flag fall back to a two-letter code badge.
 */
object FlagIcon {

    fun paint(g2: Graphics2D, code: String?, x: Int, y: Int, w: Int, h: Int) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        val iso = code?.uppercase()
        val flag = iso?.let { FLAGS[it] }
        if (flag == null) {
            paintBadge(g2, iso, x, y, w, h)
            return
        }
        val frame = RoundRectangle2D.Float(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), 3f, 3f)
        val oldClip = g2.clip
        g2.clip(frame)
        flag(g2, x, y, w, h)
        g2.clip = oldClip
        g2.color = Color(0, 0, 0, 80)
        g2.stroke = BasicStroke(1f)
        g2.draw(frame)
    }

    private fun paintBadge(g2: Graphics2D, code: String?, x: Int, y: Int, w: Int, h: Int) {
        g2.color = Color(54, 70, 108)
        g2.fillRoundRect(x, y, w, h, 4, 4)
        g2.color = Color(90, 112, 158)
        g2.drawRoundRect(x, y, w - 1, h - 1, 4, 4)
        val text = code?.take(2) ?: "??"
        g2.color = Color(210, 222, 245)
        g2.font = g2.font.deriveFont(Font.BOLD, (h * 0.62f).coerceAtLeast(8f))
        val fm = g2.fontMetrics
        g2.drawString(text, x + (w - fm.stringWidth(text)) / 2, y + (h + fm.ascent - fm.descent) / 2)
    }

    // ── flag builders ────────────────────────────────────────────────────────

    private fun hbands(vararg c: Color): Painter = { g, x, y, w, h ->
        for (i in c.indices) {
            g.color = c[i]
            val y0 = y + h * i / c.size
            val y1 = y + h * (i + 1) / c.size
            g.fillRect(x, y0, w, y1 - y0)
        }
    }

    private fun vbands(vararg c: Color): Painter = { g, x, y, w, h ->
        for (i in c.indices) {
            g.color = c[i]
            val x0 = x + w * i / c.size
            val x1 = x + w * (i + 1) / c.size
            g.fillRect(x0, y, x1 - x0, h)
        }
    }

    /** Off-centre Scandinavian cross. */
    private fun nordic(bg: Color, cross: Color): Painter = { g, x, y, w, h ->
        g.color = bg
        g.fillRect(x, y, w, h)
        val arm = (h * 0.22f).toInt().coerceAtLeast(2)
        val vx = x + (w * 0.34f).toInt()
        val hy = y + (h - arm) / 2
        g.color = cross
        g.fillRect(vx, y, arm, h)
        g.fillRect(x, hy, w, arm)
    }

    /** Centred plus (Switzerland). */
    private fun centerCross(bg: Color, cross: Color): Painter = { g, x, y, w, h ->
        g.color = bg
        g.fillRect(x, y, w, h)
        val arm = (h * 0.22f).toInt().coerceAtLeast(2)
        val len = (h * 0.62f).toInt()
        g.color = cross
        g.fillRect(x + (w - arm) / 2, y + (h - len) / 2, arm, len)
        g.fillRect(x + (w - len) / 2, y + (h - arm) / 2, len, arm)
    }

    /** Solid field with a centred disc (Japan). */
    private fun disc(bg: Color, dot: Color): Painter = { g, x, y, w, h ->
        g.color = bg
        g.fillRect(x, y, w, h)
        val d = (h * 0.6f)
        g.color = dot
        g.fill(Ellipse2D.Float(x + (w - d) / 2f, y + (h - d) / 2f, d, d))
    }

    private val usFlag: Painter = { g, x, y, w, h ->
        val stripes = 7
        for (i in 0 until stripes) {
            g.color = if (i % 2 == 0) RED else WHITE
            val y0 = y + h * (2 * i) / (2 * stripes - 1)
            val y1 = y + h * (2 * i + 2) / (2 * stripes - 1)
            g.fillRect(x, y0, w, y1 - y0)
        }
        g.color = NAVY
        g.fillRect(x, y, (w * 0.42f).toInt(), (h * 0.54f).toInt())
    }

    private val gbFlag: Painter = { g, x, y, w, h ->
        g.color = NAVY
        g.fillRect(x, y, w, h)
        g.stroke = BasicStroke((h * 0.28f).coerceAtLeast(2f))
        g.color = WHITE
        g.drawLine(x, y, x + w, y + h)
        g.drawLine(x + w, y, x, y + h)
        g.stroke = BasicStroke((h * 0.14f).coerceAtLeast(1f))
        g.color = RED
        g.drawLine(x, y, x + w, y + h)
        g.drawLine(x + w, y, x, y + h)
        val arm = (h * 0.34f).toInt().coerceAtLeast(2)
        g.color = WHITE
        g.fillRect(x + (w - arm) / 2, y, arm, h)
        g.fillRect(x, y + (h - arm) / 2, w, arm)
        val red = (arm * 0.55f).toInt().coerceAtLeast(1)
        g.color = RED
        g.fillRect(x + (w - red) / 2, y, red, h)
        g.fillRect(x, y + (h - red) / 2, w, red)
    }

    private val RED = Color(0xC8, 0x10, 0x2E)
    private val WHITE = Color(0xF4, 0xF5, 0xF8)
    private val NAVY = Color(0x10, 0x2A, 0x6E)
    private val BLUE = Color(0x21, 0x46, 0xC7)
    private val LIGHTBLUE = Color(0x2D, 0x8C, 0xD6)
    private val BLACK = Color(0x1A, 0x1A, 0x1E)
    private val GOLD = Color(0xF4, 0xC4, 0x2C)
    private val GREEN = Color(0x18, 0x8A, 0x4A)
    private val ORANGE = Color(0xE8, 0x73, 0x22)
    private val MAROON = Color(0x8A, 0x1B, 0x2E)

    private val FLAGS: Map<String, Painter> = mapOf(
        "NL" to hbands(RED, WHITE, BLUE),
        "DE" to hbands(BLACK, RED, GOLD),
        "RU" to hbands(WHITE, BLUE, RED),
        "FR" to vbands(BLUE, WHITE, RED),
        "IT" to vbands(GREEN, WHITE, RED),
        "RO" to vbands(BLUE, GOLD, RED),
        "IE" to vbands(GREEN, WHITE, ORANGE),
        "AT" to hbands(RED, WHITE, RED),
        "LV" to hbands(MAROON, WHITE, MAROON),
        "ES" to hbands(RED, GOLD, RED),
        "PL" to hbands(WHITE, RED),
        "UA" to hbands(BLUE, GOLD),
        "IN" to hbands(ORANGE, WHITE, GREEN),
        "LT" to hbands(GOLD, GREEN, RED),
        "EE" to hbands(BLUE, BLACK, WHITE),
        "BG" to hbands(WHITE, GREEN, RED),
        "AM" to hbands(RED, BLUE, ORANGE),
        "HU" to hbands(RED, WHITE, GREEN),
        "FI" to nordic(WHITE, BLUE),
        "SE" to nordic(BLUE, GOLD),
        "NO" to nordic(RED, WHITE),
        "CH" to centerCross(RED, WHITE),
        "JP" to disc(WHITE, RED),
        "KR" to disc(WHITE, BLUE),
        "SG" to hbands(RED, WHITE),
        "TR" to disc(RED, RED),
        "HK" to disc(RED, RED),
        "CN" to disc(RED, GOLD),
        "BR" to disc(GREEN, GOLD),
        "AU" to hbands(NAVY, NAVY),
        "CA" to vbands(RED, WHITE, RED),
        "KZ" to disc(LIGHTBLUE, GOLD),
        "CZ" to hbands(WHITE, RED),
        "AE" to hbands(GREEN, WHITE, BLACK),
        "US" to usFlag,
        "GB" to gbFlag
    )
}
