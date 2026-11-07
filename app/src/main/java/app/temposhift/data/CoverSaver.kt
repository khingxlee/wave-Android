package app.temposhift.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Size
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Saves a song's album cover to the phone's Pictures folder in its original (full) quality. */
object CoverSaver {
    const val SAVED = 0
    const val NO_COVER = 1
    const val FAILED = 2

    suspend fun save(ctx: Context, song: Song): Int = withContext(Dispatchers.IO) {
        try {
            // best quality: the picture exactly as embedded in the audio file (no re-compression)
            if (CoverStore.isRemoved(ctx, song.id)) return@withContext NO_COVER
            var bytes: ByteArray? = CoverStore.bytes(ctx, song.id)
            val retriever = MediaMetadataRetriever()
            try {
                if (bytes == null) {
                    retriever.setDataSource(ctx, song.uri)
                    bytes = retriever.embeddedPicture
                }
            } catch (_: Exception) {
            } finally {
                try { retriever.release() } catch (_: Exception) { }
            }
            var png = bytes != null && isPng(bytes)
            if (bytes == null && Build.VERSION.SDK_INT >= 29) {
                // no embedded picture: fall back to the largest picture Android can give us, saved losslessly
                val bmp = ctx.contentResolver.loadThumbnail(song.uri, Size(2048, 2048), null)
                val bos = ByteArrayOutputStream()
                bmp.compress(Bitmap.CompressFormat.PNG, 100, bos)
                bytes = bos.toByteArray()
                png = true
            }
            val data = bytes ?: return@withContext NO_COVER
            val ext = if (png) "png" else "jpg"
            val mime = if (png) "image/png" else "image/jpeg"
            val name = safeName(song.album + " - " + song.artist) + "." + ext
            if (store(ctx, name, mime, data)) SAVED else FAILED
        } catch (_: Exception) {
            FAILED
        }
    }

    private fun store(ctx: Context, name: String, mime: String, data: ByteArray): Boolean {
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues()
            values.put(MediaStore.Images.Media.DISPLAY_NAME, name)
            values.put(MediaStore.Images.Media.MIME_TYPE, mime)
            values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Wave")
            values.put(MediaStore.Images.Media.IS_PENDING, 1)
            val cr = ctx.contentResolver
            val uri = cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
            return try {
                cr.openOutputStream(uri)?.use { it.write(data) } ?: return false
                val done = ContentValues()
                done.put(MediaStore.Images.Media.IS_PENDING, 0)
                cr.update(uri, done, null, null)
                true
            } catch (_: Exception) {
                try { cr.delete(uri, null, null) } catch (_: Exception) { }
                false
            }
        }
        val dir = ctx.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: return false
        dir.mkdirs()
        File(dir, name).writeBytes(data)
        return true
    }

    private fun isPng(b: ByteArray): Boolean =
        b.size > 8 && b[0] == 0x89.toByte() && b[1] == 0x50.toByte() && b[2] == 0x4E.toByte() && b[3] == 0x47.toByte()

    private fun safeName(s: String): String = s.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().take(80).ifEmpty { "cover" }
}
