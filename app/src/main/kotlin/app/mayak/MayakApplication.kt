package app.mayak

import android.app.Application
import app.mayak.log.AppJournal

class MayakApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppJournal.init(this)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            AppJournal.logThrowable("crash", "uncaught on ${thread.name}", throwable)
            previous?.uncaughtException(thread, throwable)
        }
    }
}
