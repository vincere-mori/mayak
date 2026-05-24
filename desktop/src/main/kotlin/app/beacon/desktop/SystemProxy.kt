package app.beacon.desktop

interface SystemProxy {
    fun enable(port: Int)
    fun restore()
}

fun platformProxy(): SystemProxy = if (Platform.isWindows) WindowsProxy() else LinuxProxy()
