package app.mayak.desktop

import app.mayak.core.net.SubscriptionFetcher
import app.mayak.core.parser.SubscriptionParser
import java.awt.*
import javax.swing.*
import javax.swing.border.EmptyBorder
import app.mayak.desktop.MayakTheme as T

class ConnectionsDialog(owner: JFrame, private var state: DesktopProfileState,
    private val onChange: (DesktopProfileState) -> Unit, private val onAdd: () -> Unit) :
    JDialog(owner, L.t("Серверы", "Servers"), true) {
    private val rows = JPanel().apply { isOpaque = false; layout = BoxLayout(this, BoxLayout.Y_AXIS) }
    private val feedback = JLabel(" ")
    private val refresh = T.ghostButton(L.t("Обновить доступ", "Refresh access"))
    private var busy = false

    init {
        val root = JPanel(BorderLayout(0, 20)).apply { background = T.BG_BOT; border = EmptyBorder(24, 24, 24, 24) }
        root.add(JPanel(BorderLayout()).apply {
            isOpaque = false
            add(JLabel(L.t("Ваши серверы", "Your servers")).apply { foreground = T.TEXT; font = font.deriveFont(Font.BOLD, 24f) }, BorderLayout.WEST)
            add(T.accentButton(L.t("Добавить доступ", "Add access")).apply { addActionListener { dispose(); onAdd() } }, BorderLayout.EAST)
        }, BorderLayout.NORTH)
        root.add(JScrollPane(rows).apply { border = null; viewport.background = T.BG_BOT; verticalScrollBar.unitIncrement = 18 }, BorderLayout.CENTER)
        root.add(JPanel(BorderLayout(0, 10)).apply {
            isOpaque = false
            feedback.foreground = T.MUTED
            add(feedback, BorderLayout.NORTH)
            add(refresh, BorderLayout.WEST)
            add(T.ghostButton(L.t("Закрыть", "Close")).apply { addActionListener { dispose() } }, BorderLayout.EAST)
        }, BorderLayout.SOUTH)
        refresh.addActionListener { refreshAccess() }
        contentPane = root; size = Dimension(620, 530); minimumSize = Dimension(540, 400)
        rootPane.registerKeyboardAction({ dispose() }, KeyStroke.getKeyStroke("ESCAPE"), JComponent.WHEN_IN_FOCUSED_WINDOW)
        setLocationRelativeTo(owner); rebuild()
    }

    private fun rebuild() {
        rows.removeAll()
        if (state.allProfiles.isEmpty()) rows.add(JLabel(L.t("Добавьте ссылку или QR-код от владельца VPN.", "Add a link or QR code from your VPN provider.")).apply { foreground = T.TEXT_DIM })
        for (profile in state.allProfiles) {
            val selected = state.activeProfile?.id == profile.id
            val card = T.card().apply {
                layout = BorderLayout(10, 0); maximumSize = Dimension(Int.MAX_VALUE, 84)
                border = EmptyBorder(12, 14, 12, 14)
            }
            val choose = T.ghostButton(profile.name).apply {
                horizontalAlignment = SwingConstants.LEFT
                foreground = if (selected) T.SUCCESS else T.TEXT
                accessibleContext.accessibleName = profile.name + if (selected) " выбран" else " выбрать"
                addActionListener {
                    if (busy) return@addActionListener
                    state = state.copy(activeProfileId = profile.id); onChange(state); rebuild()
                }
            }
            card.add(JPanel(BorderLayout(0, 3)).apply {
                isOpaque = false; add(choose, BorderLayout.CENTER)
                add(JLabel(if (selected) L.t("Выбран для подключения", "Selected for connection") else L.t("Нажмите, чтобы выбрать", "Select to use this server")).apply { foreground = if (selected) T.SUCCESS else T.MUTED; font = font.deriveFont(12f) }, BorderLayout.SOUTH)
            }, BorderLayout.CENTER)
            card.add(JPanel(FlowLayout(FlowLayout.RIGHT, 6, 4)).apply {
                isOpaque = false
                add(T.ghostButton("QR").apply { addActionListener {
                    runCatching {
                        val matrix = com.google.zxing.qrcode.QRCodeWriter().encode(profile.source, com.google.zxing.BarcodeFormat.QR_CODE, 320, 320,
                            mapOf(com.google.zxing.EncodeHintType.CHARACTER_SET to "UTF-8"))
                        val image = com.google.zxing.client.j2se.MatrixToImageWriter.toBufferedImage(matrix)
                        JOptionPane.showMessageDialog(this@ConnectionsDialog, JLabel(ImageIcon(image)), profile.name, JOptionPane.PLAIN_MESSAGE)
                    }.onFailure { feedback.text = L.t("Ссылка слишком большая для QR-кода.", "This link is too large for a QR code.") }
                } })
                if (state.profiles.any { it.id == profile.id }) add(T.ghostButton(L.t("Удалить", "Delete")).apply { addActionListener {
                    if (busy) return@addActionListener
                    if (JOptionPane.showConfirmDialog(this@ConnectionsDialog, L.t("Удалить подключение «${profile.name}»?", "Delete connection ${profile.name}?"), "Маяк", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return@addActionListener
                    state = state.copy(profiles = state.profiles.filterNot { it.id == profile.id })
                    if (state.activeProfileId == profile.id) state = state.copy(activeProfileId = state.allProfiles.firstOrNull()?.id)
                    onChange(state); rebuild()
                } })
            }, BorderLayout.EAST)
            rows.add(card); rows.add(Box.createVerticalStrut(10))
        }
        refresh.isEnabled = state.subscriptions.isNotEmpty() && !busy
        rows.revalidate(); rows.repaint()
    }

    private fun refreshAccess() {
        if (busy) return
        busy = true; refresh.isEnabled = false; feedback.text = L.t("Обновляем список серверов...", "Refreshing servers...")
        val snapshot = state
        Thread {
            val result = runCatching {
                snapshot.subscriptions.map { sub ->
                    val profiles = SubscriptionParser().parse(SubscriptionFetcher().fetch(sub.url))
                    require(profiles.isNotEmpty())
                    sub.copy(profiles = profiles, updatedAtMillis = System.currentTimeMillis())
                }
            }
            SwingUtilities.invokeLater {
                busy = false
                if (!isDisplayable) return@invokeLater
                result.onSuccess {
                    state = snapshot.copy(subscriptions = it)
                    onChange(state); feedback.text = L.t("Список серверов обновлён", "Servers refreshed")
                }.onFailure { feedback.text = UserFacingError.describe(it) }
                rebuild()
            }
        }.apply { isDaemon = true; start() }
    }
}
