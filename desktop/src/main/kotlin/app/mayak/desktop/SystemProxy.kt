package app.mayak.desktop

interface SystemProxy {
    fun enable(port: Int)
    fun restore()
}

fun platformProxy(): SystemProxy = when {
    Platform.isWindows -> WindowsProxy()
    Platform.isMac -> MacProxy()
    else -> LinuxProxy()
}
