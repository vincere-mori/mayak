package app.mayak.log

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// лёгкий журнал для диагностики: пишет breadcrumbs приложения в runtime.log
// и собирает снимок (app log + sing-box stderr + конфиг) на экспорт.
// перед экспортом секреты вырезаются.
object AppJournal {
    private const val MAX_CHARS = 300_000
    private lateinit var appContext: Context

    fun init(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        logDir().mkdirs()
        info("app", "journal initialized")
    }

    fun info(tag: String, message: String) = append("INFO", tag, message)
    fun warn(tag: String, message: String) = append("WARN", tag, message)
    fun error(tag: String, message: String) = append("ERROR", tag, message)

    fun logThrowable(tag: String, message: String, throwable: Throwable) {
        val stack = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
        append("ERROR", tag, "$message\n$stack")
    }

    @Synchronized
    fun exportToUri(contentResolver: ContentResolver, uri: Uri): Result<Unit> = runCatching {
        contentResolver.openOutputStream(uri, "w")?.bufferedWriter()?.use { it.write(snapshotText()) }
            ?: error("не удалось открыть файл для записи")
    }

    @Synchronized
    private fun snapshotText(): String {
        if (!::appContext.isInitialized) return ""
        val stderr = appContext.cacheDir.resolve("sing-box-stderr.log")
        val lastConfig = appContext.cacheDir.resolve("sing-box.last.json")
        val text = buildString {
            appendLine("# Mayak journal")
            appendLine("updated_at=${timestamp()}")
            appendLine()
            appendLine("## app log")
            appendLine(if (logFile().exists()) logFile().readText() else "<empty>")
            appendLine()
            appendLine("## sing-box stderr")
            appendLine(if (stderr.exists()) stderr.readText() else "<missing>")
            appendLine()
            appendLine("## sing-box config")
            appendLine(if (lastConfig.exists()) lastConfig.readText() else "<missing>")
        }
        return shrink(redactSecrets(text))
    }

    @Synchronized
    private fun append(level: String, tag: String, message: String) {
        if (!::appContext.isInitialized) return
        val file = logFile()
        file.parentFile?.mkdirs()
        file.appendText("${timestamp()} [$level] [$tag] ${message.trim()}\n")
        if (file.length() > MAX_CHARS * 2L) {
            file.writeText(file.readText().takeLast(MAX_CHARS))
        }
    }

    // вырезаем ключи/пароли перед тем как лог уйдёт наружу
    private fun redactSecrets(text: String): String {
        var result = text
        listOf("uuid", "password", "private_key", "pre_shared_key").forEach { key ->
            result = Regex("(\"$key\"\\s*:\\s*\")[^\"]*\"").replace(result) { it.groupValues[1] + "***\"" }
        }
        return result
    }

    private fun logDir(): File = appContext.filesDir.resolve("logs")
    private fun logFile(): File = logDir().resolve("runtime.log")
    private fun shrink(text: String): String = if (text.length <= MAX_CHARS) text else text.takeLast(MAX_CHARS)
    private fun timestamp(): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
}
