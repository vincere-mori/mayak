package app.mayak.desktop

import java.util.concurrent.TimeUnit

class LinuxProxy : SystemProxy {
    private var enabled = false

    override fun enable(port: Int) {
        runCatching {
            exec("gsettings", "set", "org.gnome.system.proxy", "mode", "manual")
            exec("gsettings", "set", "org.gnome.system.proxy.http", "host", "127.0.0.1")
            exec("gsettings", "set", "org.gnome.system.proxy.http", "port", port.toString())
            exec("gsettings", "set", "org.gnome.system.proxy.https", "host", "127.0.0.1")
            exec("gsettings", "set", "org.gnome.system.proxy.https", "port", port.toString())
            exec("gsettings", "set", "org.gnome.system.proxy.socks", "host", "127.0.0.1")
            exec("gsettings", "set", "org.gnome.system.proxy.socks", "port", port.toString())
        }
        enabled = true
    }

    override fun restore() {
        if (!enabled) return
        runCatching {
            exec("gsettings", "set", "org.gnome.system.proxy", "mode", "none")
        }
        enabled = false
    }

    private fun exec(vararg cmd: String) {
        ProcessBuilder(*cmd).start().waitFor(3, TimeUnit.SECONDS)
    }
}
