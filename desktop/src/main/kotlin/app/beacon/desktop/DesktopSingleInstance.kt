package app.beacon.desktop

import java.io.IOException
import java.io.RandomAccessFile
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.util.UUID
import javax.swing.SwingUtilities

object DesktopSingleInstance {
    private var lockChannel: FileChannel? = null
    private var lockHandle: RandomAccessFile? = null
    private var lock: FileLock? = null
    private var server: ServerSocket? = null
    private var token: String = ""

    @Volatile
    private var restoreHandler: (() -> Unit)? = null

    @Volatile
    private var pendingRestore = false

    fun claim(): Boolean {
        var handle: RandomAccessFile? = null
        var channel: FileChannel? = null

        return try {
            val lockFile = DesktopPaths.appDir.resolve("beacon.lock").toFile()
            handle = RandomAccessFile(lockFile, "rw")
            channel = handle.channel
            val fileLock = channel.tryLock()

            if (fileLock == null) {
                channel.close()
                handle.close()
                false
            } else {
                lockHandle = handle
                lockChannel = channel
                lock = fileLock
                lockFile.deleteOnExit()
                runCatching { startServer() }
                true
            }
        } catch (_: Exception) {
            runCatching { channel?.close() }
            runCatching { handle?.close() }
            true
        }
    }

    fun requestRestore(): Boolean {
        val lines = runCatching {
            Files.readAllLines(DesktopPaths.appDir.resolve("beacon.ipc"), StandardCharsets.UTF_8)
        }.getOrNull() ?: return false

        val port = lines.getOrNull(0)?.toIntOrNull() ?: return false
        val remoteToken = lines.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return false

        return runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(InetAddress.getLoopbackAddress(), port), 700)
                socket.getOutputStream().write("$remoteToken restore\n".toByteArray(StandardCharsets.UTF_8))
                socket.getOutputStream().flush()
            }
            true
        }.getOrDefault(false)
    }

    fun onRestore(handler: () -> Unit) {
        restoreHandler = handler
        if (pendingRestore) {
            pendingRestore = false
            SwingUtilities.invokeLater(handler)
        }
    }

    fun close() {
        runCatching { server?.close() }
        runCatching { lock?.release() }
        runCatching { lockChannel?.close() }
        runCatching { lockHandle?.close() }
        runCatching { Files.deleteIfExists(DesktopPaths.appDir.resolve("beacon.ipc")) }
        server = null
        lock = null
        lockChannel = null
        lockHandle = null
    }

    private fun startServer() {
        val socket = ServerSocket(0, 16, InetAddress.getLoopbackAddress())
        val ipcFile = DesktopPaths.appDir.resolve("beacon.ipc")
        token = UUID.randomUUID().toString()
        server = socket

        Files.writeString(
            ipcFile,
            "${socket.localPort}\n$token\n",
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE
        )
        ipcFile.toFile().deleteOnExit()

        Thread({ listen(socket) }, "BeaconSingleInstance").apply {
            isDaemon = true
            start()
        }
    }

    private fun listen(socket: ServerSocket) {
        while (!socket.isClosed) {
            val client = try {
                socket.accept()
            } catch (_: IOException) {
                break
            }

            client.use {
                val line = it.getInputStream().bufferedReader(StandardCharsets.UTF_8).readLine()
                if (line == "$token restore") restore()
            }
        }
    }

    private fun restore() {
        val handler = restoreHandler
        if (handler == null) {
            pendingRestore = true
        } else {
            SwingUtilities.invokeLater(handler)
        }
    }
}
