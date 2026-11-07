package app.temposhift.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File

/** Covers the user picked (or removed) in the tag editor. They are kept inside the app and win over the file's own picture. */
object CoverStore {
    private fun dir(ctx: Context): File = File(ctx.filesDir, "covers").also { it.mkdirs() }
    fun file(ctx: Context, id: Long): File = File(dir(ctx), "$id.jpg")
    private fun marker(ctx: Context, id: Long): File = File(dir(ctx), "$id.none")

    fun isRemoved(ctx: Context, id: Long): Boolean = marker(ctx, id).exists()

    fun write(ctx: Context, id: Long, jpeg: ByteArray) {
        file(ctx, id).writeBytes(jpeg)
        marker(ctx, id).delete()
    }

    fun markRemoved(ctx: Context, id: Long) {
        file(ctx, id).delete()
        marker(ctx, id).createNewFile()
    }

    /** The picked cover as JPEG bytes, or null when there is none. */
    fun bytes(ctx: Context, id: Long): ByteArray? {
        val f = file(ctx, id)
        return if (f.exists()) f.readBytes() else null
    }

    fun decode(ctx: Context, id: Long, px: Int): Bitmap? {
        val f = file(ctx, id)
        if (!f.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= px) sample *= 2
        return BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    /** Reads a picture the user chose, shrinks it to at most 1400 px and returns it as JPEG. */
    fun fromUri(ctx: Context, uri: Uri): ByteArray? {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2800) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
            val longest = maxOf(bmp.width, bmp.height)
            val scaled = if (longest > 1400) {
                val r = 1400f / longest
                Bitmap.createScaledBitmap(bmp, (bmp.width * r).toInt(), (bmp.height * r).toInt(), true)
            } else bmp
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 92, out)
            return out.toByteArray()
        } catch (_: Exception) {
            return null
        }
    }
}
