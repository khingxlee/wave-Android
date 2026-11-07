package app.temposhift.data

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Looks through the whole phone for music files and asks Android to add them to the media library. */
object DeviceScanner {
    private val EXT = setOf("mp3", "m4a", "aac", "flac", "wav", "ogg", "opus", "wma", "amr", "mka", "3gp")

    /** Returns how many audio files were found. */
    suspend fun scan(ctx: Context): Int = withContext(Dispatchers.IO) {
        val found = ArrayList<String>()
        fun walk(dir: File, depth: Int) {
            if (depth > 12) return
            val kids = dir.listFiles() ?: return
            for (f in kids) {
                if (f.isDirectory) {
                    val n = f.name
                    if (n.startsWith(".") || n == "Android") continue
                    walk(f, depth + 1)
                } else if (f.extension.lowercase() in EXT) {
                    found.add(f.absolutePath)
                }
            }
        }
        walk(Environment.getExternalStorageDirectory(), 0)
        if (found.isNotEmpty()) {
            withTimeoutOrNull(45_000L) {
                suspendCancellableCoroutine<Unit> { cont ->
                    val left = AtomicInteger(found.size)
                    MediaScannerConnection.scanFile(ctx, found.toTypedArray(), null) { _, _ ->
                        if (left.decrementAndGet() <= 0 && cont.isActive) cont.resume(Unit)
                    }
                }
            }
        }
        found.size
    }
}
