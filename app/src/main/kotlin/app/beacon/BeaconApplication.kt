package app.beacon

import android.app.Application
import app.beacon.log.AppJournal

class BeaconApplication : Application() {
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
