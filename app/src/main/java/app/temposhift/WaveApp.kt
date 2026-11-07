package app.temposhift

import android.app.Application
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/** Writes the details of an unexpected close to a small file, so it can be sent from Settings and fixed. */
class WaveApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val trace = StringWriter()
                error.printStackTrace(PrintWriter(trace))
                File(filesDir, "last_crash.txt").writeText(
                    "Wave crash report\n" +
                        "Device: " + Build.MANUFACTURER + " " + Build.MODEL + ", Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")\n" +
                        "Thread: " + thread.name + "\n\n" + trace.toString().take(6000)
                )
            } catch (_: Throwable) { }
            previous?.uncaughtException(thread, error)
        }
    }
}
