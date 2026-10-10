package app.mayak.desktop

import app.mayak.core.model.DnsMode
import app.mayak.core.singbox.InboundMode
import java.awt.*
import javax.swing.*
import javax.swing.border.EmptyBorder
import app.mayak.desktop.MayakTheme as T

class SettingsPanel(
    private val current: () -> DesktopProfileState,
    private val onChange: (DesktopProfileState) -> Unit,
    private val onWarp: (Boolean) -> Unit,
    onRouting: () -> Unit,
    onRestore: () -> Unit,
    onLogs: () -> Unit,
    onClose: () -> Unit,
    traySupported: Boolean = true
) : JPanel(BorderLayout(0, 14)) {
    private var syncing = false
    private val browser = MotionToggle(L.t("Браузер", "Browser"))
    private val computer = MotionToggle(L.t("Весь компьютер", "Whole computer"))
    private val warp = JCheckBox()
    private val dns = JComboBox(DnsMode.entries.toTypedArray())
    private val ipv6 = JCheckBox()
    private val tray = JCheckBox()
    private val language = JComboBox(AppLanguage.entries.toTypedArray())

    init {
        background = T.BG_BOT
        border = EmptyBorder(20, 20, 18, 20)
        add(JLabel(L.t("Настройки", "Settings")).apply { foreground = T.TEXT; font = font.deriveFont(Font.BOLD, 22f) }, BorderLayout.NORTH)
        val column = object : JPanel(), Scrollable {
            override fun getPreferredScrollableViewportSize() = Dimension(500, 520)
            override fun getScrollableTracksViewportWidth() = true
            override fun getScrollableTracksViewportHeight() = false
            override fun getScrollableUnitIncrement(r: Rectangle, o: Int, d: Int) = 18
            override fun getScrollableBlockIncrement(r: Rectangle, o: Int, d: Int) = r.height - 40
        }.apply { isOpaque = false; layout = BoxLayout(this, BoxLayout.Y_AXIS) }
        fun section(card: JPanel) {
            card.alignmentX = 0f
            card.maximumSize = Dimension(Int.MAX_VALUE, card.preferredSize.height)
            column.add(card); column.add(Box.createVerticalStrut(12))
        }
        val modes = T.card().apply {
            layout = BorderLayout(0, 10)
            add(JLabel(L.t("Где использовать VPN", "Where to use VPN")).apply { foreground = T.TEXT; font = font.deriveFont(Font.BOLD, 14f) }, BorderLayout.NORTH)
            add(JPanel(GridLayout(1, 2, 8, 0)).apply {
                isOpaque = false
                ButtonGroup().apply { add(browser); add(computer) }
                add(browser); add(computer)
            }, BorderLayout.CENTER)
            add(wrapped(L.t("Для всех приложений нужен запуск от администратора.", "All apps mode requires administrator access.")), BorderLayout.SOUTH)
        }
        section(modes)
        val general = T.card().apply {
            layout = GridLayout(0, 1, 0, 5)
            add(row(L.t("DNS-сервер", "DNS server"), L.t("Служба, которая находит адреса сайтов.", "Resolves website addresses."), dns))
            add(row("IPv6", L.t("Поддержка сетей с IPv6-адресами.", "Support networks with IPv6 addresses."), ipv6))
            add(row(L.t("Сворачивать в трей", "Minimize to tray"), L.t("При закрытии окна VPN продолжит работать.", "VPN keeps running when the window is closed."), tray))
            add(row(L.t("Язык интерфейса", "Language"), L.t("Язык кнопок и сообщений приложения.", "Language of app controls and messages."), language))
        }
        section(general)
        section(T.card().apply {
            layout = BorderLayout()
            add(row(L.t("Google и Gemini", "Google and Gemini"), L.t("Дополнительный маршрут WARP. Включайте, если эти сервисы не открываются.", "Extra WARP route. Enable if these services do not open."), warp))
        })
        section(action(L.t("Маршрутизация", "Routing"), L.t("Выбор сайтов и приложений для VPN.", "Choose websites and apps for VPN."), L.t("Настроить", "Configure"), onRouting))
        section(action(L.t("Восстановить прокси", "Restore proxy"), L.t("Если интернет пропал после отключения VPN.", "If internet stopped after disconnecting VPN."), L.t("Восстановить", "Restore"), onRestore))
        section(action(L.t("Диагностика", "Diagnostics"), L.t("Журнал подключения для поиска ошибок.", "Connection logs for troubleshooting."), L.t("Открыть", "Open"), onLogs))
        add(JScrollPane(column).apply {
            border = null; isOpaque = false; viewport.isOpaque = false
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
            verticalScrollBar.unitIncrement = 18
        }, BorderLayout.CENTER)
        add(JPanel(BorderLayout()).apply {
            isOpaque = false
            add(T.accentButton(L.t("Закрыть", "Close")).apply { addActionListener { onClose() }; preferredSize = Dimension(138, 42) }, BorderLayout.EAST)
        }, BorderLayout.SOUTH)
        for (box in listOf(warp, ipv6, tray)) {
            box.isOpaque = false; box.putClientProperty("JButton.buttonType", "roundRect")
            box.preferredSize = Dimension(34, 34)
        }
        for (box in listOf(dns, language)) {
            box.background = T.BG_INPUT; box.foreground = T.TEXT
            box.preferredSize = Dimension(144, 36)
            box.putClientProperty("JComponent.roundRect", true)
        }
        tray.isEnabled = traySupported
        sync(current())
        browser.addActionListener { if (!syncing) onChange(current().copy(inboundMode = InboundMode.Mixed)) }
        computer.addActionListener { if (!syncing) onChange(current().copy(inboundMode = InboundMode.Tun)) }
        dns.addActionListener { if (!syncing) onChange(current().copy(dnsMode = dns.selectedItem as DnsMode)) }
        ipv6.addActionListener { if (!syncing) onChange(current().copy(ipv6Enabled = ipv6.isSelected)) }
        tray.addActionListener { if (!syncing) onChange(current().copy(trayEnabled = tray.isSelected)) }
        language.addActionListener { if (!syncing) onChange(current().copy(language = language.selectedItem as AppLanguage)) }
        warp.addActionListener { if (!syncing) onWarp(warp.isSelected) }
    }

    fun sync(state: DesktopProfileState) {
        syncing = true
        try {
            browser.isSelected = state.inboundMode == InboundMode.Mixed
            computer.isSelected = state.inboundMode == InboundMode.Tun
            dns.selectedItem = state.dnsMode
            ipv6.isSelected = state.ipv6Enabled
            tray.isSelected = state.trayEnabled
            language.selectedItem = state.language
            warp.isSelected = state.warpEnabled
        } finally { syncing = false }
    }

    fun setWarpBusy(busy: Boolean) {
        warp.isEnabled = !busy
        warp.toolTipText = if (busy) L.t("Подключаем дополнительный маршрут...", "Setting up the extra route...") else null
    }

    private fun wrapped(text: String) = JTextArea(text, 2, 0).apply {
        isEditable = false; isFocusable = false; isOpaque = false
        lineWrap = true; wrapStyleWord = true; border = null
        foreground = T.MUTED; font = UIManager.getFont("Label.font").deriveFont(11f)
    }

    private fun row(title: String, detail: String, control: JComponent) = JPanel(BorderLayout(14, 0)).apply {
        isOpaque = false; border = EmptyBorder(6, 0, 6, 0)
        add(JPanel(BorderLayout(0, 3)).apply {
            isOpaque = false
            add(JLabel(title).apply { foreground = T.TEXT; font = font.deriveFont(Font.BOLD, 13f) }, BorderLayout.NORTH)
            add(wrapped(detail), BorderLayout.CENTER)
        }, BorderLayout.CENTER)
        add(JPanel(GridBagLayout()).apply { isOpaque = false; add(control) }, BorderLayout.EAST)
    }

    private fun action(title: String, detail: String, text: String, callback: () -> Unit) = T.card().apply {
        layout = BorderLayout()
        add(row(title, detail, T.ghostButton(text).apply {
            preferredSize = Dimension(144, 40)
            addActionListener { callback() }
        }))
    }
}
