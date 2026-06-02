package app.beacon.desktop

object Platform {
    private val osName: String = System.getProperty("os.name").lowercase()

    val isWindows: Boolean = osName.contains("windows")
    val isMac: Boolean = osName.contains("mac") || osName.contains("darwin")
    val isLinux: Boolean = osName.contains("linux")
}
