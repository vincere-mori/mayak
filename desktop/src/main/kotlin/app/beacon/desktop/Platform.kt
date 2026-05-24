package app.beacon.desktop

object Platform {
    val isWindows: Boolean = System.getProperty("os.name").lowercase().contains("windows")
    val isLinux: Boolean = System.getProperty("os.name").lowercase().contains("linux")
}
