package app.temposhift.data

import android.content.Context
import java.io.File

object CrashReport {
    private fun file(ctx: Context) = File(ctx.filesDir, "last_crash.txt")
    fun text(ctx: Context): String? = try { if (file(ctx).exists()) file(ctx).readText() else null } catch (_: Exception) { null }
    fun clear(ctx: Context) { try { file(ctx).delete() } catch (_: Exception) { } }
}
