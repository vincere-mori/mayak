package app.mayak.desktop

import app.mayak.core.model.Subscription
import app.mayak.core.net.SubscriptionFetcher
import app.mayak.core.parser.AccessInput
import app.mayak.core.parser.ProfileInputParser
import app.mayak.core.parser.SubscriptionParser
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.client.j2se.BufferedImageLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.awt.*
import java.awt.datatransfer.DataFlavor
import java.util.UUID
import javax.imageio.ImageIO
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.filechooser.FileNameExtensionFilter
import app.mayak.desktop.MayakTheme as T

class AddAccessDialog(owner: JFrame, private val state: DesktopProfileState, private val onAdded: (DesktopProfileState) -> Unit) :
    JDialog(owner, L.t("Добавить доступ", "Add VPN access"), true) {
    private val input = JTextArea(4, 30)
    private val feedback = JLabel(" ")
    private val add = T.accentButton(L.t("Добавить доступ", "Add access"))
    private val paste = T.ghostButton(L.t("Вставить ссылку", "Paste link"))
    private val image = T.ghostButton(L.t("Открыть QR-код", "Open QR image"))
    private var busy = false

    init {
        val content = JPanel().apply {
            background = T.BG_BOT
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = EmptyBorder(28, 28, 24, 28)
        }
        fun label(text: String, size: Float, color: Color) = JLabel(text).apply {
            font = font.deriveFont(size); foreground = color; alignmentX = 0f
        }
        content.add(label(L.t("Начнём с подключения", "Let's add your connection"), 24f, T.TEXT))
        content.add(Box.createVerticalStrut(10))
        content.add(label(L.t("Получите ссылку или QR-код у владельца VPN.", "Get a link or QR code from your VPN provider."), 13f, T.TEXT_DIM))
        content.add(label(L.t("Маяк сам прочитает настройки. Вводить их вручную не нужно.", "Mayak reads the settings for you. No manual setup needed."), 13f, T.MUTED))
        content.add(Box.createVerticalStrut(24))
        input.apply {
            lineWrap = true; wrapStyleWord = true
            font = font.deriveFont(14f); background = T.BG_INPUT
            foreground = T.TEXT; caretColor = T.ACCENT_LIGHT
            border = EmptyBorder(12, 14, 12, 14)
            accessibleContext.accessibleName = L.t("Ссылка доступа к VPN", "VPN access link")
        }
        content.add(JScrollPane(input).apply {
            alignmentX = 0f; preferredSize = Dimension(490, 112)
            maximumSize = Dimension(Int.MAX_VALUE, 112)
            border = BorderFactory.createLineBorder(T.BORDER)
        })
        content.add(Box.createVerticalStrut(12))
        content.add(JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            isOpaque = false; alignmentX = 0f; maximumSize = Dimension(Int.MAX_VALUE, 42)
            add(paste); add(Box.createHorizontalStrut(12)); add(image)
        })
        feedback.foreground = T.DANGER; feedback.alignmentX = 0f
        content.add(Box.createVerticalStrut(14)); content.add(feedback)
        content.add(Box.createVerticalStrut(16))
        add.apply { alignmentX = 0f; maximumSize = Dimension(Int.MAX_VALUE, 46); preferredSize = Dimension(490, 46) }
        content.add(add)
        content.add(Box.createVerticalStrut(18))
        content.add(label(L.t("Нет ссылки? Попросите её у того, кто предоставил VPN.", "No link yet? Ask the person who provided your VPN."), 12f, T.MUTED))
        contentPane = content
        paste.addActionListener {
            runCatching { Toolkit.getDefaultToolkit().systemClipboard.getData(DataFlavor.stringFlavor) as String }
                .onSuccess { input.text = it; feedback.text = " " }
                .onFailure { feedback.text = L.t("Сначала скопируйте ссылку.", "Copy a link first.") }
        }
        image.addActionListener { readQr() }
        add.addActionListener { importAccess() }
        rootPane.defaultButton = add
        rootPane.registerKeyboardAction({ if (!busy) dispose() }, KeyStroke.getKeyStroke("ESCAPE"), JComponent.WHEN_IN_FOCUSED_WINDOW)
        defaultCloseOperation = DISPOSE_ON_CLOSE
        pack(); minimumSize = Dimension(560, 440); setLocationRelativeTo(owner)
    }

    private fun readQr() {
        val chooser = JFileChooser().apply { fileFilter = FileNameExtensionFilter("QR image (PNG, JPG)", "png", "jpg", "jpeg") }
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return
        runCatching {
            require(chooser.selectedFile.length() <= 15_000_000) { "Изображение слишком большое." }
            val data = ImageIO.createImageInputStream(chooser.selectedFile).use { stream ->
                val readers = ImageIO.getImageReaders(stream)
                require(readers.hasNext())
                val reader = readers.next()
                try {
                    reader.input = stream
                    require(reader.getWidth(0) <= 4096 && reader.getHeight(0) <= 4096)
                    reader.read(0)
                } finally { reader.dispose() }
            }
            MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(BufferedImageLuminanceSource(data)))).text
        }.onSuccess { input.text = it; feedback.text = L.t("QR-код прочитан. Нажмите «Добавить доступ».", "QR code read. Select Add access."); feedback.foreground = T.SUCCESS }
            .onFailure { feedback.foreground = T.DANGER; feedback.text = L.t("QR-код не найден. Выберите чёткое изображение целиком.", "QR code not found. Choose a clear, complete image.") }
    }

    private fun importAccess() {
        if (busy) return
        val raw = input.text
        if (raw.isBlank()) { feedback.text = L.t("Вставьте ссылку или откройте QR-код.", "Paste a link or open a QR image."); return }
        busy = true; add.isEnabled = false; paste.isEnabled = false; image.isEnabled = false; input.isEditable = false
        add.text = L.t("Проверяем ссылку...", "Checking the link...")
        feedback.text = " "
        Thread {
            val result = runCatching {
                val text = AccessInput.normalize(raw)
                if (AccessInput.isSubscription(text)) {
                    val profiles = SubscriptionParser().parse(SubscriptionFetcher().fetch(text))
                    require(profiles.isNotEmpty()) { "В ссылке нет поддерживаемых подключений. Проверьте её у владельца VPN." }
                    val previous = state.subscriptions.firstOrNull { it.url == text }
                    val sub = Subscription(previous?.id ?: UUID.randomUUID().toString(), previous?.name ?: "Мой VPN", text, profiles, System.currentTimeMillis())
                    state.copy(subscriptions = state.subscriptions.filterNot { it.url == text } + sub, activeProfileId = profiles.first().id)
                } else {
                    val profile = ProfileInputParser().parse(text)
                    state.copy(profiles = state.profiles.filterNot { it.id == profile.id } + profile, activeProfileId = profile.id)
                }
            }
            SwingUtilities.invokeLater {
                busy = false
                if (!isDisplayable) return@invokeLater
                result.onSuccess { onAdded(it); dispose() }.onFailure {
                    feedback.foreground = T.DANGER
                    feedback.text = "<html><div style='width:460px'>" + UserFacingError.describe(it).replace("&", "&amp;").replace("<", "&lt;") + "</div></html>"
                    add.text = L.t("Попробовать ещё раз", "Try again")
                    add.isEnabled = true; paste.isEnabled = true; image.isEnabled = true; input.isEditable = true
                    pack()
                }
            }
        }.apply { isDaemon = true; start() }
    }
}
