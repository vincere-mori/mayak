package app.mayak.desktop

import app.mayak.core.model.DnsMode
import app.mayak.core.model.ProxyProfile
import app.mayak.core.model.RoutingMode
import app.mayak.core.model.RoutingSettings
import app.mayak.core.net.LatencyProbe
import app.mayak.core.parser.ProfileInputParser
import app.mayak.core.singbox.InboundMode
import app.mayak.core.singbox.RoutingPlatform
import app.mayak.core.singbox.SingBoxConfigBuilder
import app.mayak.core.singbox.SingBoxConfigSettings
import com.formdev.flatlaf.FlatDarkLaf
import java.awt.BasicStroke
import java.awt.Toolkit
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Cursor
import java.awt.Desktop
import java.awt.Dimension
import java.awt.Font
import java.awt.FlowLayout
import java.awt.GradientPaint
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GraphicsEnvironment
import java.awt.Image
import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.GridLayout
import java.awt.RenderingHints
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.awt.geom.Ellipse2D
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.ButtonGroup
import javax.swing.ImageIcon
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JMenuItem
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JRadioButton
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.JToggleButton
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.Timer
import javax.swing.ToolTipManager
import javax.swing.UIManager
import javax.swing.border.EmptyBorder
import kotlin.io.path.createFile
import kotlin.io.path.exists
import kotlin.math.sin
import kotlin.system.exitProcess
import app.mayak.desktop.MayakTheme as T

fun main() {
    val store = DesktopProfileStore()
    val state = runCatching { store.load() }.getOrElse {
        JOptionPane.showMessageDialog(null, "Не удалось прочитать настройки. Исходный файл сохранён. Верните резервную копию profiles.json.bak в папке Маяка.", "Маяк", JOptionPane.ERROR_MESSAGE)
        exitProcess(1)
    }
    L.lang = state.language

    if (Platform.isWindows) {
        // Let FlatLaf draw the titlebar instead of Windows DWM so our brand
        // colours apply to the title strip and buttons. On Linux the native WM
        // handles decorations — forcing FlatLaf ones there breaks tiling WMs.
        System.setProperty("flatlaf.useWindowDecorations", "true")
        System.setProperty("flatlaf.menuBarEmbedded", "true")
        JFrame.setDefaultLookAndFeelDecorated(true)
        JDialog.setDefaultLookAndFeelDecorated(true)
    }

    FlatDarkLaf.setup()

    if (Platform.isWindows) {
        // Brand colours for the FlatLaf-drawn titlebar (Windows only)
        UIManager.put("TitlePane.background",          Color(15, 21, 53))
        UIManager.put("TitlePane.inactiveBackground",  Color(10, 15, 38))
        UIManager.put("TitlePane.foreground",          Color(220, 230, 250))
        UIManager.put("TitlePane.inactiveForeground",  Color(120, 140, 190))
        UIManager.put("TitlePane.borderColor",         Color(28, 38, 80))
        UIManager.put("TitlePane.unifiedBackground",   true)
        UIManager.put("TitlePane.buttonHoverBackground",   Color(40, 55, 120))
        UIManager.put("TitlePane.buttonPressedBackground", Color(28, 40, 95))
        UIManager.put("TitlePane.closeHoverBackground",    Color(185, 38, 48))
        UIManager.put("TitlePane.closeHoverForeground",    Color.WHITE)
        UIManager.put("TitlePane.closePressedBackground",  Color(220, 20, 30))
        UIManager.put("TitlePane.closePressedForeground",  Color.WHITE)
        UIManager.put("TitlePane.menuBarEmbedded", true)
    }

    UIManager.put("RootPane.background", Color(15, 21, 53))

    UIManager.put("Component.arc", 14)
    UIManager.put("Button.arc", 18)
    UIManager.put("ScrollBar.width", 8)
    UIManager.put("PopupMenu.arc", 16)
    UIManager.put("MenuItem.selectionBackground", T.BG_INPUT_HOVER)
    UIManager.put("ToolTip.background", Color(28, 38, 80))
    UIManager.put("ToolTip.foreground", Color(220, 230, 255))
    // скруглённые углы подсказок (FlatLaf рисует фон по форме рамки с arc)
    UIManager.put("ToolTip.border", com.formdev.flatlaf.ui.FlatLineBorder(
        java.awt.Insets(6, 10, 6, 10), Color(70, 100, 200), 1f, 10))
    ToolTipManager.sharedInstance().initialDelay = 250
    ToolTipManager.sharedInstance().reshowDelay = 80
    ToolTipManager.sharedInstance().dismissDelay = 8000

    if (!DesktopSingleInstance.claim()) {
        DesktopSingleInstance.requestRestore()
        exitProcess(0)
    }

    if (Platform.isWindows) refreshShellIconCacheOnce()

    SwingUtilities.invokeLater {
        MayakDesktop().show()
    }
}

/**
 * After an install or upgrade, Windows Start-menu search often caches a generic
 * icon for the new shortcut. ie4uinit refreshes the shell icon cache so the
 * real Mayak icon appears next time the user types "Mayak". We tie this to
 * the running .exe's path + last-modified time, so it fires exactly once per
 * installed build — not on every launch.
 */
private fun refreshShellIconCacheOnce() {
    runCatching {
        val exePath = ProcessHandle.current().info().command().orElse(null) ?: return@runCatching
        val exe = java.nio.file.Path.of(exePath)
        if (!java.nio.file.Files.exists(exe)) return@runCatching
        val mtime = java.nio.file.Files.getLastModifiedTime(exe).toMillis()
        val marker = DesktopPaths.appDir.resolve("icon-cache.marker")
        val signature = "$exePath|$mtime"
        if (java.nio.file.Files.exists(marker) &&
            java.nio.file.Files.readString(marker).trim() == signature) return@runCatching

        ProcessBuilder("ie4uinit.exe", "-ClearIconCache").start()
            .waitFor(3, java.util.concurrent.TimeUnit.SECONDS)
        ProcessBuilder("ie4uinit.exe", "-show").start()
            .waitFor(3, java.util.concurrent.TimeUnit.SECONDS)
        java.nio.file.Files.writeString(marker, signature)
    }
}

class MayakDesktop(
    private val store: DesktopProfileStore = DesktopProfileStore(),
    private val parser: ProfileInputParser = ProfileInputParser(),
    private val configBuilder: SingBoxConfigBuilder = SingBoxConfigBuilder(),
    private val singBox: SingBoxProcess = SingBoxProcess(),
    private val systemProxy: SystemProxy = platformProxy()
) {
    private var state = store.load()
    private var connected = false
    private var connecting = false
    private var disconnecting = false
    private var refreshing = false
    private var registeringWarp = false
    // Растёт на каждый connect/disconnect: связывает завершение фонового старта с
    // актуальным намерением, чтобы отменённое подключение не показывало ошибку.
    private var connectToken = 0
    private val connectionLock = Any()

    private lateinit var frame: JFrame
    private val appIcon = ImageIcon(MayakDesktop::class.java.getResource("/icon.png")).image
    private val hero = LighthouseHero()
    private val xray = XrayProcess()
    private var settingsPanel: SettingsPanel? = null
    private val guidance = JLabel()
    private val connectionNotice = JLabel()
    private var lastConnectionError: String? = null
    private lateinit var statsPanel: JPanel
    private val addAccessButton = T.ghostButton("Добавить доступ").apply { addActionListener { openAddAccess() } }
    private val serversButton = T.ghostButton("Серверы").apply { addActionListener { openServers() } }
    private val settingsButton = T.ghostButton("Настройки").apply { addActionListener { openSettings() } }
    private var trayIcon: TrayIcon? = null
    private var trayToggleItem: MenuItem? = null
    private var trayOpenItem: MenuItem? = null
    private var trayExitItem: MenuItem? = null

    private lateinit var pingCard: JPanel
    private lateinit var downCard: JPanel
    private lateinit var upCard: JPanel

    private val statusDot = StatusDot()
    private val statusText = JLabel("Отключено")
    private val keyChip = T.ghostButton("")

    private val mainBtn = primaryConnectButton()

    private val pingValue = JLabel("—")
    private val downValue = JLabel("—")
    private val upValue = JLabel("—")
    private val pingLabel = JLabel("ping")
    private val downLabel = JLabel("↓ down")
    private val upLabel = JLabel("↑ up")
    private val pingTestBtn = T.ghostButton("Тест").apply {
        toolTipText = "Проверить задержку до активного сервера"
        preferredSize = Dimension(60, 21)
        maximumSize = Dimension(60, 21)
        minimumSize = Dimension(60, 21)
        font = font.deriveFont(Font.BOLD, 10f)
        addActionListener { runPing(showProgress = true) }
    }

    private val latencyProbe = LatencyProbe()
    private var trafficMonitor: TrafficMonitor? = null
    private val pingTimer = Timer(5000) { runPing() }
    private var pinging = false
    private var pingFailures = 0
    // перерисовывает дышащие элементы (точка статуса, свечение кнопки)
    private val pulseTimer = Timer(80) { statusDot.repaint() }

    fun show() {
        val windowSize = initialWindowSize()
        frame = JFrame("Маяк").apply {
            defaultCloseOperation = JFrame.DO_NOTHING_ON_CLOSE
            minimumSize = Dimension(
                minOf(560, windowSize.width),
                minOf(560, windowSize.height)
            )
            preferredSize = windowSize
            iconImage = appIcon
            // Theme the titlebar via FlatLaf client properties (3.x)
            rootPane.putClientProperty("JRootPane.titleBarBackground", Color(15, 21, 53))
            rootPane.putClientProperty("JRootPane.titleBarForeground", Color(220, 230, 250))
            rootPane.putClientProperty("JRootPane.titleBarBorderColor", Color(28, 38, 80))
            contentPane = buildRoot()
            addWindowListener(object : WindowAdapter() {
                override fun windowClosing(e: WindowEvent) {
                    if (!hideToTray()) shutdown()
                }
                override fun windowIconified(e: WindowEvent) {
                    hero.pauseAnimation(); pulseTimer.stop()
                }
                override fun windowDeiconified(e: WindowEvent) { hero.resumeAnimation(); refresh() }
            })
            pack()
            // дефолтный размер фиксируем явно (pack ужал бы до preferred контента),
            // пользователь дальше может тянуть как хочет
            size = windowSize
            setLocationRelativeTo(null)
        }
        refresh()
        frame.isVisible = true
        DesktopSingleInstance.onRestore { restoreWindow() }
        syncTrayIcon()
        Timer(700) { e ->
            (e.source as Timer).stop()
            showTrayNoticeOnce()
        }.start()
        pingTimer.initialDelay = 1000
    }

    private fun initialWindowSize(): Dimension {
        val bounds = GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
        val maxW = (bounds.width - 32).coerceAtLeast(640)
        val maxH = (bounds.height - 32).coerceAtLeast(520)
        // вытянутый по вертикали портрет, а не широкий ландшафт
        val width = 640.coerceAtMost(maxW).coerceAtLeast(560.coerceAtMost(maxW))
        val height = 700.coerceAtMost(maxH).coerceAtLeast(560.coerceAtMost(maxH))
        return Dimension(width, height)
    }

    private fun buildRoot(): JPanel {
        return object : JPanel(BorderLayout(0, 0)) {
            override fun paintComponent(g: Graphics) {
                val g2 = g as Graphics2D
                g2.paint = GradientPaint(0f, 0f, T.BG_TOP, 0f, height.toFloat(), T.BG_BOT)
                g2.fillRect(0, 0, width, height)
            }
        }.apply {
            isOpaque = true
            componentPopupMenu = T.popupMenu().apply {
                add(T.menuItem(L.t("Добавить доступ", "Add access"), ::openAddAccess))
                add(T.menuItem(L.t("Серверы", "Servers"), ::openServers))
                add(T.menuItem(L.t("Настройки", "Settings"), ::openSettings))
            }
            keyChip.componentPopupMenu = T.popupMenu().apply {
                add(T.menuItem(L.t("Выбрать сервер", "Choose server"), ::openServers))
                add(T.menuItem(L.t("Скопировать ссылку", "Copy link")) {
                    state.activeProfile?.let { Toolkit.getDefaultToolkit().systemClipboard.setContents(java.awt.datatransfer.StringSelection(it.source), null) }
                })
                add(T.menuItem(L.t("Добавить доступ", "Add access"), ::openAddAccess))
            }
            mainBtn.componentPopupMenu = componentPopupMenu
            addAccessButton.componentPopupMenu = componentPopupMenu
            serversButton.componentPopupMenu = componentPopupMenu
            settingsButton.componentPopupMenu = componentPopupMenu
            add(topBar(), BorderLayout.NORTH)
            add(JScrollPane(centerStack()).apply {
                border = null
                isOpaque = false; viewport.isOpaque = false
                horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
                verticalScrollBar.unitIncrement = 18
            }, BorderLayout.CENTER)
        }
    }

    private fun topBar(): JPanel = JPanel(BorderLayout()).apply {
        isOpaque = false
        border = EmptyBorder(12, 20, 8, 20)
        add(JLabel("Маяк").apply { foreground = T.TEXT; font = font.deriveFont(Font.BOLD, 22f) }, BorderLayout.WEST)
        add(JPanel(FlowLayout(FlowLayout.RIGHT, 6, 0)).apply {
            isOpaque = false
            add(addAccessButton); add(serversButton); add(settingsButton)
        }, BorderLayout.EAST)
    }

    private fun centerStack(): JPanel = object : JPanel(), javax.swing.Scrollable {
        override fun getPreferredScrollableViewportSize() = Dimension(700, 650)
        override fun getScrollableUnitIncrement(rect: java.awt.Rectangle, orientation: Int, direction: Int) = 18
        override fun getScrollableBlockIncrement(rect: java.awt.Rectangle, orientation: Int, direction: Int) = rect.height - 40
        override fun getScrollableTracksViewportWidth() = true
        override fun getScrollableTracksViewportHeight() = (parent?.height ?: 0) >= preferredSize.height
    }.apply {
        isOpaque = false
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = EmptyBorder(0, 20, 18, 20)
        hero.alignmentX = Component.CENTER_ALIGNMENT
        hero.preferredSize = Dimension(600, 290)
        hero.minimumSize = Dimension(0, 200)
        hero.maximumSize = Dimension(Int.MAX_VALUE, 310)
        add(hero)
        add(Box.createVerticalStrut(20))
        add(statusRow())
        add(Box.createVerticalStrut(8))
        guidance.apply {
            alignmentX = Component.CENTER_ALIGNMENT
            foreground = T.MUTED; font = font.deriveFont(13f)
            horizontalAlignment = SwingConstants.CENTER
        }
        add(guidance)
        add(Box.createVerticalStrut(18))
        add(mainBtn.also { it.alignmentX = Component.CENTER_ALIGNMENT })
        add(Box.createVerticalStrut(16))
        keyChip.apply {
            isOpaque = false; isContentAreaFilled = false
            foreground = T.TEXT_DIM
            font = font.deriveFont(Font.BOLD, 13f)
            border = EmptyBorder(14, 20, 14, 20)
            alignmentX = Component.CENTER_ALIGNMENT
            preferredSize = Dimension(440, 52); maximumSize = Dimension(500, 52)
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            addActionListener { showKeyPicker(this) }
        }
        add(keyChip)
        connectionNotice.apply {
            alignmentX = Component.CENTER_ALIGNMENT
            foreground = T.DANGER; font = font.deriveFont(12f)
            border = EmptyBorder(8, 0, 8, 0)
        }
        add(connectionNotice)
        add(Box.createVerticalStrut(20))
        statsPanel = statsRow()
        add(statsPanel)
        add(Box.createVerticalGlue())
        add(T.ghostButton(L.t("Как пользоваться", "How to use")).apply {
            alignmentX = Component.CENTER_ALIGNMENT
            addActionListener { JOptionPane.showMessageDialog(frame,
                L.t("1. Получите ссылку или QR-код у владельца VPN.\n2. Нажмите «Добавить доступ».\n3. Нажмите «Подключить».\n\nНа компьютере по умолчанию подключается браузер.\nДля всех приложений выберите «Весь компьютер» в настройках.",
                    "1. Get a link or QR code from your VPN provider.\n2. Select Add access.\n3. Select Connect.\n\nBrowser mode is selected by default.\nFor all apps choose Whole computer in Settings."), "Маяк", JOptionPane.INFORMATION_MESSAGE) }
        })
    }

    private fun statusRow(): JPanel = JPanel().apply {
        isOpaque = false
        layout = BoxLayout(this, BoxLayout.X_AXIS)
        alignmentX = Component.CENTER_ALIGNMENT
        maximumSize = Dimension(Int.MAX_VALUE, 42)
        add(Box.createHorizontalGlue())
        add(statusDot)
        add(Box.createHorizontalStrut(10))
        add(statusText.apply { foreground = T.TEXT; font = font.deriveFont(Font.BOLD, 24f) })
        add(Box.createHorizontalGlue())
    }

    private fun statsRow(): JPanel = JPanel().apply {
        isOpaque = false
        layout = GridLayout(1, 3, 12, 0)
        alignmentX = Component.CENTER_ALIGNMENT
        maximumSize = Dimension(620, 78)
        preferredSize = Dimension(620, 78)

        pingCard = statCard(pingValue, pingLabel, T.ACCENT_LIGHT,
            L.t("<html>Задержка до сервера в миллисекундах.<br>Чем меньше — тем отзывчивее соединение.</html>",
                "<html>Latency to the server in milliseconds.<br>Lower is more responsive.</html>"),
            pingTestBtn)
        downCard = statCard(downValue, downLabel, T.SUCCESS,
            L.t("<html>Скорость загрузки прямо сейчас (входящий трафик).<br>Обновляется раз в секунду.</html>",
                "<html>Current download speed (incoming traffic).<br>Updates every second.</html>"))
        upCard = statCard(upValue, upLabel, T.WARN,
            L.t("<html>Скорость отдачи прямо сейчас (исходящий трафик).<br>Обновляется раз в секунду.</html>",
                "<html>Current upload speed (outgoing traffic).<br>Updates every second.</html>"))

        add(pingCard)
        add(downCard)
        add(upCard)
    }

    private fun statCard(value: JLabel, label: JLabel, accent: Color, tip: String, action: JButton? = null): JPanel =
        HoverCard(14f).apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = EmptyBorder(8, 12, 8, 12)
        toolTipText = tip
        value.apply {
            foreground = accent
            font = font.deriveFont(Font.BOLD, 18f)
            alignmentX = Component.CENTER_ALIGNMENT
            horizontalAlignment = SwingConstants.CENTER
        }
        label.apply {
            foreground = T.MUTED
            font = font.deriveFont(Font.PLAIN, 10f)
            alignmentX = Component.CENTER_ALIGNMENT
            horizontalAlignment = SwingConstants.CENTER
        }
        add(Box.createVerticalGlue())
        add(value.fullWidth())
        add(Box.createVerticalStrut(1))
        add(label.fullWidth())
        action?.let {
            add(Box.createVerticalStrut(5))
            add(it.also { btn -> btn.alignmentX = Component.CENTER_ALIGNMENT })
            bindHover(it)
        }
        add(Box.createVerticalGlue())
    }

    private fun primaryConnectButton() = T.accentButton(L.t("Подключить", "Connect")).apply {
        preferredSize = Dimension(276, 56)
        minimumSize = Dimension(240, 56)
        maximumSize = Dimension(276, 56)
        font = font.deriveFont(Font.BOLD, 15f)
        addActionListener { toggleConnect() }
    }

    private fun toggleConnect() {
        if (disconnecting) return
        if (state.activeProfile == null) openAddAccess()
        else if (connected && pingFailures >= 3) reconnectAfterModeChange()
        else if (connected || connecting) disconnect() else connect()
    }

    private fun showKeyPicker(anchor: Component) {
        val menu = T.popupMenu()
        fun item(text: String, selected: Boolean = false, muted: Boolean = false, action: () -> Unit): JMenuItem {
            return JMenuItem(text).apply {
                isOpaque = true
                foreground = when {
                    selected -> T.ACCENT_LIGHT
                    muted -> T.MUTED
                    else -> T.TEXT
                }
                background = T.CARD_SOLID
                font = font.deriveFont(if (selected) Font.BOLD else Font.PLAIN, 12f)
                border = EmptyBorder(8, 14, 8, 18)
                addActionListener { action() }
            }
        }
        val all = state.allProfiles
        if (all.isEmpty()) {
            menu.add(item(L.t("Добавить доступ", "Add access")) { openAddAccess() })
        } else {
            all.forEach { p ->
                val active = p.id == state.activeProfileId
                menu.add(item("${if (active) "✓ " else "   "}${p.name}   ${p.host}:${p.port}", active) {
                    if (connecting || disconnecting) return@item
                    val changed = state.activeProfile?.id != p.id
                    state = state.copy(activeProfileId = p.id); persist(); refresh()
                    if (changed && connected) reconnectAfterModeChange()
                })
            }
            menu.addSeparator()
            menu.add(item(L.t("Все серверы", "All servers"), muted = true) { openServers() })
            menu.add(item(L.t("Добавить доступ", "Add access"), muted = true) { openAddAccess() })
        }
        menu.show(anchor, 0, anchor.height + 4)
    }

    private fun openAddAccess() {
        if (connecting || disconnecting) return
        AddAccessDialog(frame, state) { updated ->
            val changed = state.activeProfile?.id != updated.activeProfile?.id
            state = updated
            lastConnectionError = null
            persist(); refresh()
            if (changed && connected) reconnectAfterModeChange()
        }.isVisible = true
    }

    private fun openServers() {
        if (connecting || disconnecting) return
        ConnectionsDialog(frame, state, { updated ->
            val changed = state.activeProfile?.id != updated.activeProfile?.id
            state = updated; persist(); refresh()
            if (changed && connected) {
                if (updated.activeProfile == null) disconnect() else reconnectAfterModeChange()
            }
        }, ::openAddAccess).isVisible = true
    }

    private fun openKeys() {
        KeyManagerDialog(frame, parser, state.profiles, state.activeProfileId) { profiles, activeId ->
            val changed = state.activeProfile?.id != activeId
            state = state.copy(profiles = profiles, activeProfileId = activeId)
            persist(); refresh()
            if (changed && connected) reconnectAfterModeChange()
        }.isVisible = true
    }

    private fun openSubscriptions() {
        SubscriptionDialog(frame, state.subscriptions, state.activeProfileId) { subscriptions, activeId ->
            val changed = state.activeProfile?.id != activeId
            state = state.copy(subscriptions = subscriptions, activeProfileId = activeId)
            persist(); refresh()
            if (changed && connected) reconnectAfterModeChange()
        }.isVisible = true
    }

    private fun handleWarpClick(enabled: Boolean) {
        val wasConnected = connected
        state = state.copy(warpEnabled = enabled)
        persist()
        refresh()

        if (enabled && !hasUsableWarpCredentials()) {
            registerWarp(reconnectOnSuccess = wasConnected)
            return
        }

        if (wasConnected) reconnectAfterModeChange()
    }

    private fun registerWarp(afterSuccess: (() -> Unit)? = null, reconnectOnSuccess: Boolean = true) {
        if (registeringWarp) return
        registeringWarp = true
        refresh()
        Thread {
            val result = runCatching { WarpManager.register() }
            SwingUtilities.invokeLater {
                var successCallback: (() -> Unit)? = null
                result.fold(
                    onSuccess = { creds ->
                        val wasConnected = connected
                        state = state.copy(warpCredentials = creds, warpEnabled = true)
                        persist()
                        registeringWarp = false
                        refresh()
                        if (wasConnected && reconnectOnSuccess) reconnectAfterModeChange()
                        successCallback = afterSuccess
                    },
                    onFailure = { err ->
                        state = state.copy(warpEnabled = false)
                        persist()
                        registeringWarp = false
                        refresh()
                        showError(L.t("WARP не зарегистрировался: ", "WARP registration failed: ") + (err.message ?: L.t("ошибка", "error")))
                    }
                )
                successCallback?.invoke()
            }
        }.apply { isDaemon = true; start() }
    }

    private fun openSettings() {
        val dlg = JDialog(frame, L.t("Настройки", "Settings"), true)
        val panel = SettingsPanel({ state }, { updated ->
            val old = state
            val reconnect = connected && (old.dnsMode != updated.dnsMode || old.ipv6Enabled != updated.ipv6Enabled || old.inboundMode != updated.inboundMode)
            state = updated
            L.lang = updated.language
            persist(); refresh(); syncTrayIcon()
            if (reconnect) reconnectAfterModeChange()
            if (old.language != updated.language) { dlg.dispose(); openSettings() }
        }, { enabled ->
            handleWarpClick(enabled)
        }, { openRoutingSettings(dlg) }, {
            runCatching { systemProxy.restore() }.onFailure { showError(L.t("Не удалось восстановить прокси.", "Could not restore proxy.")) }
        }, ::openLog, { dlg.dispose() }, isTraySupported())
        settingsPanel = panel
        panel.setWarpBusy(registeringWarp)
        dlg.contentPane = panel
        val bounds = GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
        dlg.size = Dimension(560.coerceAtMost(bounds.width - 24), 700.coerceAtMost(bounds.height - 32))
        dlg.minimumSize = Dimension(500, 420)
        dlg.rootPane.registerKeyboardAction({ dlg.dispose() }, javax.swing.KeyStroke.getKeyStroke("ESCAPE"), JComponent.WHEN_IN_FOCUSED_WINDOW)
        dlg.addWindowListener(object : WindowAdapter() {
            override fun windowClosed(e: WindowEvent) { if (settingsPanel === panel) settingsPanel = null }
        })
        dlg.setLocationRelativeTo(frame)
        dlg.isVisible = true
    }

    private fun openRoutingSettings(owner: JDialog) {
        val routing = state.routing.ensureDefaults()
        val dlg = JDialog(owner, L.t("Маршрутизация", "Routing"), true)
        val content = JPanel().apply {
            background = T.BG_BOT
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = EmptyBorder(18, 20, 18, 20)
        }

        fun textArea(values: List<String>, rows: Int = 3) = JTextArea(
            RoutingSettings.toMultiline(values),
            rows,
            36
        ).apply {
            lineWrap = true
            wrapStyleWord = true
            background = T.BG_INPUT
            foreground = T.TEXT
            caretColor = T.TEXT
            border = EmptyBorder(8, 10, 8, 10)
        }

        fun field(
            title: String,
            description: String,
            area: JTextArea
        ) = JPanel(BorderLayout(0, 6)).apply {
            isOpaque = false
            border = EmptyBorder(6, 0, 6, 0)
            add(JPanel().apply {
                isOpaque = false
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                add(JLabel(title).apply {
                    foreground = T.TEXT
                    font = font.deriveFont(Font.BOLD, 12f)
                })
                add(JLabel(description).apply {
                    foreground = T.MUTED
                    font = font.deriveFont(Font.PLAIN, 10f)
                })
            }, BorderLayout.NORTH)
            add(JScrollPane(area).apply {
                border = BorderFactory.createLineBorder(T.BORDER, 1)
                viewport.background = T.BG_INPUT
            }, BorderLayout.CENTER)
        }

        var selectedMode = routing.mode
        val proxyAll = JRadioButton(L.t(
            "VPN для всего, кроме списка",
            "VPN for everything except the list"
        )).apply {
            isOpaque = false
            foreground = T.TEXT
            isSelected = selectedMode == RoutingMode.ProxyAllExcept
            addActionListener { selectedMode = RoutingMode.ProxyAllExcept }
        }
        val directAll = JRadioButton(L.t(
            "VPN только для списка",
            "VPN only for the list"
        )).apply {
            isOpaque = false
            foreground = T.TEXT
            isSelected = selectedMode == RoutingMode.DirectAllExcept
            addActionListener { selectedMode = RoutingMode.DirectAllExcept }
        }
        ButtonGroup().apply {
            add(proxyAll)
            add(directAll)
        }

        val domainsArea = textArea(routing.exceptionDomains)
        val cidrsArea = textArea(routing.exceptionCidrs)
        val processesArea = textArea(routing.desktopProcesses)
        val warpDomainsArea = textArea(routing.warpDomains)
        val warpCidrsArea = textArea(routing.warpCidrs)

        content.add(JLabel(L.t("Маршрутизация", "Routing")).apply {
            foreground = T.TEXT
            font = font.deriveFont(Font.BOLD, 20f)
        })
        content.add(Box.createVerticalStrut(10))
        content.add(T.card().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = EmptyBorder(10, 12, 10, 12)
            add(proxyAll)
            add(Box.createVerticalStrut(4))
            add(directAll)
        })
        content.add(Box.createVerticalStrut(8))
        content.add(field(
            L.t("Домены", "Domains"),
            L.t("по одному домену на строку", "one domain per line"),
            domainsArea
        ))
        content.add(field(
            "IP/CIDR",
            L.t("например 203.0.113.0/24", "for example 203.0.113.0/24"),
            cidrsArea
        ))
        content.add(field(
            L.t("Процессы", "Processes"),
            L.t("например firefox.exe", "for example firefox.exe"),
            processesArea
        ))
        content.add(Box.createVerticalStrut(8))
        content.add(JLabel(L.t("Маршруты WARP", "WARP routes")).apply {
            foreground = T.TEXT
            font = font.deriveFont(Font.BOLD, 14f)
        })
        content.add(field(
            L.t("Домены через WARP", "Domains through WARP"),
            L.t("работают, когда WARP включён", "used when WARP is enabled"),
            warpDomainsArea
        ))
        content.add(field(
            L.t("IP/CIDR через WARP", "IP/CIDR through WARP"),
            L.t("по одному значению на строку", "one value per line"),
            warpCidrsArea
        ))
        content.add(Box.createVerticalStrut(12))
        content.add(JPanel().apply {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(Box.createHorizontalGlue())
            add(T.ghostButton(L.t("Отмена", "Cancel")).apply {
                preferredSize = Dimension(120, 36)
                addActionListener { dlg.dispose() }
            })
            add(Box.createHorizontalStrut(8))
            add(T.accentButton(L.t("Применить", "Apply")).apply {
                preferredSize = Dimension(140, 36)
                addActionListener {
                    val updated = routing.copy(
                        mode = selectedMode,
                        exceptionDomains = RoutingSettings.parseMultiline(
                            domainsArea.text,
                            lowercase = true
                        ),
                        exceptionCidrs = RoutingSettings.parseMultiline(cidrsArea.text),
                        desktopProcesses = RoutingSettings.parseMultiline(processesArea.text),
                        warpDomains = RoutingSettings.parseMultiline(
                            warpDomainsArea.text,
                            lowercase = true
                        ),
                        warpCidrs = RoutingSettings.parseMultiline(warpCidrsArea.text)
                    ).asUserConfigured()
                    val changed = updated != state.routing.ensureDefaults()
                    state = state.copy(routing = updated)
                    persist()
                    if (changed && connected) reconnectAfterModeChange()
                    dlg.dispose()
                }
            })
        })

        dlg.contentPane = JScrollPane(content).apply {
            border = null
            viewport.background = T.BG_BOT
            verticalScrollBar.unitIncrement = 16
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        }
        dlg.size = Dimension(620, 720)
        dlg.setLocationRelativeTo(owner)
        dlg.isVisible = true
    }

    private fun openLog() = runCatching {
        DesktopPaths.logFile.parent?.let { Files.createDirectories(it) }
        if (!DesktopPaths.logFile.exists()) DesktopPaths.logFile.createFile()
        Desktop.getDesktop().open(DesktopPaths.logFile.toFile())
    }.onFailure { showError(L.t("не удалось открыть лог: ", "failed to open log: ") + DesktopPaths.logFile) }

    private fun connect() {
        if (disconnecting) return
        val profile = state.activeProfile ?: run {
            showError(L.t("сначала добавь и выбери ключ", "add and select a key first")); openKeys(); return
        }
        if (state.inboundMode == InboundMode.Tun && !isElevated()) {
            val res = JOptionPane.showConfirmDialog(
                frame,
                L.t("TUN перехватывает весь трафик и требует запуск от администратора.\nПерезапустить Маяк с правами администратора?",
                    "TUN intercepts all traffic and requires administrator privileges.\nRelaunch Mayak as administrator?"),
                "Маяк", JOptionPane.YES_NO_OPTION
            )
            if (res == JOptionPane.YES_OPTION && relaunchElevated()) { shutdown(); return }
            showError(L.t("Запусти Маяк от администратора или выбери режим Proxy", "Run Mayak as administrator or select Proxy mode")); return
        }

        val warp = state.warpCredentials
        if (state.warpEnabled && !hasUsableWarpCredentials()) {
            registerWarp(afterSuccess = { connect() }, reconnectOnSuccess = false)
            return
        }

        lastConnectionError = null
        disconnecting = false
        connecting = true
        refresh()
        val token = ++connectToken

        val snapshot = state
        val warpUsable = hasUsableWarpCredentials()

        Thread {
            // Конфиг собираем здесь, а не в EDT: resolveEndpoint при доменном
            // WARP-эндпоинте уходит в DNS и подвешивает окно на время резолва.
            val result = synchronized(connectionLock) { runCatching {
            if (token != connectToken) error("Подключение отменено")
            val needsXray = profile.vless?.transport == "xhttp" || !profile.vless?.postQuantumVerify.isNullOrBlank()
            if (needsXray) xray.start(profile).getOrThrow()
            val config = configBuilder.build(
                profile = profile,
                settings = SingBoxConfigSettings(
                    dnsMode = snapshot.dnsMode, ipv6Enabled = snapshot.ipv6Enabled,
                    inboundMode = snapshot.inboundMode, mixedListenPort = PROXY_PORT,
                    clashApiPort = CLASH_PORT,
                    localProxyPort = if (needsXray) XrayProcess.PORT else null,
                    cacheFilePath = DesktopPaths.cacheFile.toString(),
                    warpEnabled = snapshot.warpEnabled && warpUsable,
                    warpPrivateKey = warp?.privateKey ?: "",
                    warpLocalAddressV4 = warp?.localAddressV4 ?: "",
                    warpLocalAddressV6 = warp?.localAddressV6 ?: "",
                    warpPeerPublicKey = warp?.peerPublicKey ?: "",
                    warpEndpoint = warp?.endpoint?.let { WarpManager.resolveEndpoint(it) } ?: "",
                    warpReserved = warp?.reserved ?: listOf(0, 0, 0),
                    routing = snapshot.routing.ensureDefaults(),
                    platform = RoutingPlatform.Desktop
                )
            )

            singBox.start(config, tunMode = snapshot.inboundMode == InboundMode.Tun).getOrThrow()
            if (token != connectToken) error("Подключение отменено")
            val delay = latencyProbe.proxyLatencyMs(CLASH_PORT, testUrl = "https://www.gstatic.com/generate_204", timeoutMs = 5000)
                ?: latencyProbe.proxyLatencyMs(CLASH_PORT, testUrl = "https://www.cloudflare.com/cdn-cgi/trace", timeoutMs = 5000)
            check(delay != null) { "Сервер не отвечает. Проверьте интернет или попросите другую ссылку доступа." }
            if (token != connectToken) error("Подключение отменено")
            if (snapshot.inboundMode == InboundMode.Mixed) systemProxy.enable(PROXY_PORT)
            }.onFailure { singBox.stop(); xray.stop(); systemProxy.restore() } }
            val error = result.exceptionOrNull()

            SwingUtilities.invokeLater {
                // Пользователь успел отменить или переключиться — игнорируем результат
                // этого старта, иначе он перезатрёт состояние и покажет ложную ошибку.
                if (token != connectToken) {
                    return@invokeLater
                }
                connecting = false
                connected = error == null
                if (connected) startMonitoring()
                refresh()
                error?.let {
                    lastConnectionError = UserFacingError.describe(it)
                    refresh()
                }
            }
        }.apply { isDaemon = true; start() }
    }

    private fun disconnect() {
        if (disconnecting) return
        connecting = false
        disconnecting = true
        connectToken++
        stopMonitoring()
        refresh()
        Thread {
            synchronized(connectionLock) { runCatching { singBox.stop(); xray.stop(); systemProxy.restore() } }
            SwingUtilities.invokeLater {
                connected = false
                disconnecting = false
                refresh()
            }
        }.apply { isDaemon = true; start() }
    }

    private fun reconnectAfterModeChange() {
        if (connecting || disconnecting) return
        connectToken++
        disconnecting = true
        refresh()
        Thread {
            synchronized(connectionLock) { runCatching { singBox.stop(); xray.stop(); systemProxy.restore() } }
            SwingUtilities.invokeLater {
                connected = false
                stopMonitoring()
                refresh()
            }
            Thread.sleep(220)
            SwingUtilities.invokeLater {
                disconnecting = false
                connect()
            }
        }.apply { isDaemon = true; start() }
    }

    private fun startMonitoring() {
        pingFailures = 0
        pingTimer.start()
        runPing()
        trafficMonitor = TrafficMonitor(CLASH_PORT).also { tm ->
            tm.start { sample ->
                SwingUtilities.invokeLater {
                    downValue.text = formatBytesPerSec(sample.down)
                    upValue.text = formatBytesPerSec(sample.up)
                }
            }
        }
    }

    private fun stopMonitoring() {
        pingTimer.stop()
        trafficMonitor?.stop()
        trafficMonitor = null
        downValue.text = "—"
        upValue.text = "—"
        pingValue.text = "—"
    }

    private fun runPing(showProgress: Boolean = false) {
        if (connected && (!singBox.running || (state.activeProfile?.vless?.transport == "xhttp" && !xray.running))) {
            lastConnectionError = L.t("Подключение прервалось. Нажмите «Подключить», чтобы попробовать снова.", "Connection lost. Select Connect to try again.")
            disconnect()
            return
        }
        if (pinging) return
        val profile = state.activeProfile ?: return
        pinging = true
        if (showProgress) {
            pingValue.text = "..."
            pingValue.foreground = T.ACCENT_LIGHT
            pingTestBtn.isEnabled = false
        }
        Thread {
            // при активном VPN прямой замер уходит в тоннель и врёт (0/мусор),
            // поэтому меряем реальную задержку до узла через clash API
            val ms = if (connected)
                latencyProbe.proxyLatencyMs(CLASH_PORT, testUrl = "https://www.gstatic.com/generate_204")
                    ?: latencyProbe.proxyLatencyMs(CLASH_PORT, testUrl = "https://www.cloudflare.com/cdn-cgi/trace")
            else
                latencyProbe.tcpLatencyMs(profile.host, profile.port)
            SwingUtilities.invokeLater {
                pinging = false
                if (connected) {
                    pingFailures = if (ms == null) pingFailures + 1 else 0
                    if (pingFailures >= 3) lastConnectionError = L.t("VPN включён, но сервер не отвечает. Попробуйте переподключиться или выбрать другой сервер.", "VPN is running but the server is not responding. Reconnect or choose another server.")
                    else if (lastConnectionError?.startsWith("VPN ") == true) lastConnectionError = null
                    refresh()
                }
                pingTestBtn.isEnabled = state.activeProfile != null
                pingValue.text = ms?.let { "$it ms" } ?: "—"
                pingValue.foreground = when {
                    ms == null -> T.MUTED
                    ms < 80 -> T.SUCCESS
                    ms < 200 -> T.WARN
                    else -> T.DANGER
                }
            }
        }.apply { isDaemon = true; start() }
    }

    private fun shutdown() {
        stopMonitoring()
        removeTrayIcon()
        runCatching { singBox.stop(); xray.stop(); systemProxy.restore() }
        DesktopSingleInstance.close()
        frame.dispose(); exitProcess(0)
    }

    private fun persist() = store.save(state)

    private fun refresh() {
        refreshing = true
        try {
            // Dynamic translation updates
            pingLabel.text = L.t("Задержка", "Latency")
            downLabel.text = L.t("Загрузка", "Download")
            upLabel.text = L.t("Отправка", "Upload")

            pingTestBtn.text = L.t("Тест", "Test")
            pingTestBtn.toolTipText = L.t("Проверить задержку до активного сервера", "Check latency to the active server")

            if (::pingCard.isInitialized) {
                pingCard.toolTipText = L.t(
                    "<html>Задержка до сервера в миллисекундах.<br>Чем меньше — тем отзывчивее соединение.</html>",
                    "<html>Latency to the server in milliseconds.<br>Lower is more responsive.</html>"
                )
            }
            if (::downCard.isInitialized) {
                downCard.toolTipText = L.t(
                    "<html>Скорость загрузки прямо сейчас (входящий трафик).<br>Обновляется раз в секунду.</html>",
                    "<html>Current download speed (incoming traffic).<br>Updates every second.</html>"
                )
            }
            if (::upCard.isInitialized) {
                upCard.toolTipText = L.t(
                    "<html>Скорость отдачи прямо сейчас (исходящий трафик).<br>Обновляется раз в секунду.</html>",
                    "<html>Current upload speed (outgoing traffic).<br>Updates every second.</html>"
                )
            }

            when {
                registeringWarp -> {
                    hero.heroState = LighthouseHero.HeroState.CONNECTING
                    statusDot.foreground = T.WARN; statusText.foreground = T.WARN
                    statusText.text = L.t("Регистрация WARP…", "Registering WARP...")
                    mainBtn.text = L.t("Подождите", "Please wait")
                }
                connecting -> {
                    hero.heroState = LighthouseHero.HeroState.CONNECTING
                    statusDot.foreground = T.WARN; statusText.foreground = T.WARN
                    statusText.text = L.t("Подключение…", "Connecting...")
                    mainBtn.text = L.t("Отмена", "Cancel")
                }
                connected -> {
                    if (disconnecting) {
                        hero.heroState = LighthouseHero.HeroState.DISCONNECTING
                        statusDot.foreground = T.WARN
                        statusText.foreground = T.WARN
                        statusText.text = L.t("Отключение…", "Disconnecting...")
                        mainBtn.text = L.t("Отключение", "Disconnecting")
                    } else {
                        hero.heroState = LighthouseHero.HeroState.ON
                        statusDot.foreground = T.SUCCESS; statusText.foreground = T.SUCCESS
                        statusText.text = L.t("Подключено", "Connected")
                        mainBtn.text = L.t("Отключить", "Disconnect")
                    }
                }
                disconnecting -> {
                    hero.heroState = LighthouseHero.HeroState.DISCONNECTING
                    statusDot.foreground = T.WARN; statusText.foreground = T.WARN
                    statusText.text = L.t("Отключение…", "Disconnecting...")
                    mainBtn.text = L.t("Отключение", "Disconnecting")
                }
                else -> {
                    hero.heroState = LighthouseHero.HeroState.OFF
                    statusDot.foreground = T.MUTED; statusText.foreground = T.TEXT_DIM
                    statusText.text = L.t("Отключено", "Disconnected")
                    mainBtn.text = L.t("Подключить", "Connect")
                }
            }
            mainBtn.tone = when {
                connecting || disconnecting || registeringWarp -> ButtonTone.WARNING
                connected -> ButtonTone.SUCCESS
                else -> ButtonTone.PRIMARY
            }
            settingsPanel?.apply { sync(state); setWarpBusy(registeringWarp) }
            mainBtn.isEnabled = !registeringWarp && !disconnecting
            mainBtn.repaint()
            mainBtn.accessibleContext.accessibleName = mainBtn.text

            val pulse = connected || connecting || disconnecting || registeringWarp
            statusDot.pulsing = pulse
            if (pulse && ::frame.isInitialized && frame.isShowing && frame.extendedState and JFrame.ICONIFIED == 0) { if (!pulseTimer.isRunning) pulseTimer.start() } else pulseTimer.stop()

            val active = state.activeProfile
            addAccessButton.text = L.t("Добавить доступ", "Add access")
            serversButton.text = L.t("Серверы", "Servers")
            settingsButton.text = L.t("Настройки", "Settings")
            if (active == null && !connecting && !connected) {
                statusText.text = L.t("Добавьте ваш VPN", "Add your VPN")
                mainBtn.text = L.t("Добавить доступ", "Add access")
            }
            if (connected && !disconnecting && pingFailures >= 3) {
                statusText.text = L.t("Сервер не отвечает", "Server is not responding")
                statusText.foreground = T.WARN
                mainBtn.text = L.t("Переподключить", "Reconnect")
            }
            guidance.text = when {
                connecting -> L.t("Устанавливаем соединение с сервером", "Connecting to your server")
                disconnecting -> L.t("Возвращаем обычное подключение", "Restoring your normal connection")
                active == null -> L.t("Добавьте ссылку или QR-код. Остальное настроит Маяк.", "Add a link or QR code. Mayak handles the rest.")
                state.inboundMode == InboundMode.Mixed -> L.t("VPN для браузера и приложений с поддержкой прокси", "VPN for your browser and proxy-aware apps")
                else -> L.t("VPN для всех приложений компьютера", "VPN for all apps on this computer")
            }
            connectionNotice.text = lastConnectionError?.let { "<html>" + it.replace("&", "&amp;").replace("<", "&lt;") + "</html>" } ?: ""
            connectionNotice.isVisible = lastConnectionError != null
            statsPanel.isVisible = connected
            keyChip.isVisible = active != null
            keyChip.isEnabled = !connecting && !disconnecting
            addAccessButton.isEnabled = !connecting && !disconnecting
            serversButton.isEnabled = !connecting && !disconnecting
            keyChip.text = if (active != null) {
                "  ${active.name}  ▾"
            } else L.t("Выбрать сервер", "Choose server")

            pingTestBtn.isEnabled = active != null && !pinging
        } finally {
            refreshing = false
        }
        frame.contentPane.revalidate()
        frame.contentPane.repaint()
        syncTrayIcon()
    }

    private fun isTraySupported(): Boolean =
        runCatching { SystemTray.isSupported() }.getOrDefault(false)

    private fun hideToTray(): Boolean {
        if (!state.trayEnabled || !isTraySupported()) return false
        syncTrayIcon()
        if (trayIcon == null) return false
        frame.isVisible = false
        hero.pauseAnimation()
        pulseTimer.stop()
        showTrayNoticeOnce()
        return true
    }

    private fun showTrayNoticeOnce() {
        if (!state.trayEnabled || state.trayNoticeShown || trayIcon == null) return
        state = state.copy(trayNoticeShown = true)
        persist()
        trayIcon?.displayMessage(
            L.t("Маяк будет сворачиваться в трей", "Mayak will minimize to tray"),
            L.t("VPN продолжит работать. Это можно поменять в настройках.",
                "VPN will keep running. You can change this in settings."),
            TrayIcon.MessageType.INFO
        )
    }

    private fun restoreWindow() {
        frame.isVisible = true
        frame.extendedState = frame.extendedState and JFrame.ICONIFIED.inv()
        frame.toFront()
        frame.requestFocus()
        hero.resumeAnimation()
        refresh()
    }

    private fun syncTrayIcon() {
        if (!state.trayEnabled || !isTraySupported()) {
            removeTrayIcon()
            return
        }

        if (trayIcon == null) {
            val popup = PopupMenu()
            val openItem = MenuItem(L.t("Открыть", "Open")).apply {
                addActionListener { SwingUtilities.invokeLater { restoreWindow() } }
            }
            trayOpenItem = openItem
            trayToggleItem = MenuItem(trayToggleLabel()).apply {
                addActionListener { SwingUtilities.invokeLater { toggleConnect() } }
            }
            val exitItem = MenuItem(L.t("Выход", "Exit")).apply {
                addActionListener { SwingUtilities.invokeLater { shutdown() } }
            }
            trayExitItem = exitItem
            popup.add(openItem)
            popup.add(trayToggleItem)
            popup.addSeparator()
            popup.add(exitItem)

            val image = appIcon.getScaledInstance(16, 16, Image.SCALE_SMOOTH)
            val icon = TrayIcon(image, trayTooltip(), popup).apply {
                isImageAutoSize = true
                addActionListener { SwingUtilities.invokeLater { restoreWindow() } }
            }
            val added = runCatching { SystemTray.getSystemTray().add(icon) }.isSuccess
            if (added) trayIcon = icon
        }

        trayIcon?.toolTip = trayTooltip()
        trayToggleItem?.label = trayToggleLabel()
        trayToggleItem?.isEnabled = !registeringWarp
        trayOpenItem?.label = L.t("Открыть", "Open")
        trayExitItem?.label = L.t("Выход", "Exit")
    }

    private fun removeTrayIcon() {
        trayIcon?.let { icon ->
            runCatching { SystemTray.getSystemTray().remove(icon) }
        }
        trayIcon = null
        trayToggleItem = null
        trayOpenItem = null
        trayExitItem = null
    }

    private fun trayTooltip(): String {
        val status = when {
            registeringWarp -> L.t("регистрация WARP", "registering WARP")
            connecting -> L.t("подключение", "connecting")
            disconnecting -> L.t("отключение", "disconnecting")
            connected -> L.t("подключено", "connected")
            else -> L.t("отключено", "disconnected")
        }
        return "Маяк: $status"
    }

    private fun trayToggleLabel(): String =
        if (connected || connecting || disconnecting) L.t("Отключить", "Disconnect") else L.t("Подключить", "Connect")

    private fun showError(msg: String) = SwingUtilities.invokeLater {
        JOptionPane.showMessageDialog(frame, msg, "Маяк", JOptionPane.ERROR_MESSAGE)
    }

    private fun hasUsableWarpCredentials(): Boolean {
        val creds = state.warpCredentials ?: return false
        return creds.privateKey.isNotBlank() && creds.localAddressV4.isNotBlank()
    }

    // `net session` стоит ~70мс и дёргается на каждый коннект из EDT, а права
    // процесса без перезапуска не меняются — считаем один раз.
    private val elevated: Boolean by lazy { checkElevated() }

    private fun isElevated() = elevated

    private fun checkElevated() = runCatching {
        if (Platform.isWindows) {
            ProcessBuilder("cmd", "/c", "net session >nul 2>&1").start().waitFor() == 0
        } else if (Platform.isMac) {
            ProcessBuilder("id", "-u").start()
                .also { it.waitFor(2, java.util.concurrent.TimeUnit.SECONDS) }
                .inputStream.bufferedReader().readText().trim() == "0"
        } else {
            ProcessBuilder("id", "-u").start()
                .also { it.waitFor(2, java.util.concurrent.TimeUnit.SECONDS) }
                .inputStream.bufferedReader().readText().trim() == "0"
        }
    }.getOrDefault(false)

    private fun relaunchElevated(): Boolean {
        val command = ProcessHandle.current().info().command().orElse(null) ?: return false
        val args = ProcessHandle.current().info().arguments()
            .map { it.toList() }.orElse(emptyList())
        return if (Platform.isWindows) {
            val path = Path.of(command)
            if (!path.fileName.toString().equals("Mayak.exe", ignoreCase = true)) return false
            val q = "'" + path.toAbsolutePath().toString().replace("'", "''") + "'"
            runCatching {
                ProcessBuilder("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass",
                    "-Command", "Start-Process -FilePath $q -Verb RunAs").start(); true
            }.getOrDefault(false)
        } else if (Platform.isMac) {
            val shellCommand = (listOf(command) + args).joinToString(" ") { shellQuote(it) }
            runCatching {
                ProcessBuilder(
                    "osascript",
                    "-e",
                    "do shell script ${appleScriptQuote(shellCommand)} with administrator privileges"
                ).start()
                true
            }.getOrDefault(false)
        } else {
            runCatching {
                ProcessBuilder(listOf("pkexec") + listOf(command) + args).start(); true
            }.getOrDefault(false)
        }
    }

    private fun shellQuote(value: String): String =
        "'" + value.replace("'", "'\"'\"'") + "'"

    private fun appleScriptQuote(value: String): String =
        "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    private fun JLabel.fullWidth() = apply {
        alignmentX = Component.CENTER_ALIGNMENT
        maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
    }

    private fun mix(a: Color, b: Color, t: Float) = Color(
        a.red + ((b.red - a.red) * t).toInt(),
        a.green + ((b.green - a.green) * t).toInt(),
        a.blue + ((b.blue - a.blue) * t).toInt()
    )

    // ── Inner UI components ──────────────────────────────────────────────────

    /** Status dot with a soft pulsing halo while connecting/connected. */
    private class StatusDot : JComponent() {
        var pulsing = false
        init {
            preferredSize = Dimension(16, 16)
            minimumSize = Dimension(16, 16)
            maximumSize = Dimension(16, 16)
        }
        override fun paintComponent(g: Graphics) {
            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val c = foreground
            val cx = width / 2f
            val cy = height / 2f
            if (pulsing) {
                val phase = ((1 + sin(System.currentTimeMillis() / 280.0)) / 2).toFloat()
                val haloR = 4.5f + 3f * phase
                g2.color = Color(c.red, c.green, c.blue, (70 - 45 * phase).toInt())
                g2.fill(Ellipse2D.Float(cx - haloR, cy - haloR, haloR * 2, haloR * 2))
            }
            g2.color = c
            g2.fill(Ellipse2D.Float(cx - 3.5f, cy - 3.5f, 7f, 7f))
        }
    }

    companion object {
        const val PROXY_PORT = 2080
        const val CLASH_PORT = 9095
    }
}
