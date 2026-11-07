package app.temposhift.data

import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray

/**
 * Translates lyrics to English.
 * 1) Google ML Kit on the phone (works offline after the language pack is downloaded once) for the languages it knows.
 * 2) Otherwise, or if that fails, an online translation service as a fallback - this covers many more languages.
 */
object LyricsTranslator {
    const val OK = 0
    const val ALREADY_ENGLISH = 1
    const val FAILED = 2

    class Result(val kind: Int, val lines: List<String>)

    suspend fun toEnglish(lines: List<String>): Result {
        val sample = lines.filter { it.isNotBlank() }.take(24).joinToString("\n")
        if (sample.isBlank()) return Result(FAILED, emptyList())

        // find the language (best guess)
        var tag = "und"
        try {
            val identifier = LanguageIdentification.getClient(
                LanguageIdentificationOptions.Builder().setConfidenceThreshold(0.2f).build()
            )
            try {
                val found = identifier.identifyPossibleLanguages(sample).await()
                tag = found.firstOrNull { it.languageTag != "und" }?.languageTag ?: "und"
            } finally {
                identifier.close()
            }
        } catch (_: Exception) { }
        if (tag == "en") return Result(ALREADY_ENGLISH, emptyList())

        // on-device translation when the language is supported
        val source = if (tag == "und") null else TranslateLanguage.fromLanguageTag(tag)
        if (source != null) {
            try {
                val translator = Translation.getClient(
                    TranslatorOptions.Builder().setSourceLanguage(source).setTargetLanguage(TranslateLanguage.ENGLISH).build()
                )
                try {
                    translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
                    val out = ArrayList<String>(lines.size)
                    for (line in lines) out.add(if (line.isBlank()) "" else translator.translate(line).await())
                    return Result(OK, out)
                } finally {
                    translator.close()
                }
            } catch (_: Exception) { }
        }

        // online fallback
        return try {
            withContext(Dispatchers.IO) { online(lines) }
        } catch (_: Exception) {
            Result(FAILED, emptyList())
        }
    }

    /** Translates in small groups of lines; the group's own language is detected by the service. */
    private fun online(lines: List<String>): Result {
        val out = ArrayList<String>(lines.size)
        var i = 0
        var detectedEnglish = false
        while (i < lines.size) {
            val group = ArrayList<String>()
            var j = i
            while (j < lines.size && group.size < 12) { group.add(lines[j]); j++ }
            if (group.all { it.isBlank() }) {
                out.addAll(group)
            } else {
                val (text, src) = request(group.joinToString("\n"))
                if (src == "en" && i == 0) detectedEnglish = true
                val parts = text.split("\n")
                if (parts.size == group.size) {
                    out.addAll(parts)
                } else {
                    for (line in group) out.add(if (line.isBlank()) "" else request(line).first)
                }
            }
            if (detectedEnglish) return Result(ALREADY_ENGLISH, emptyList())
            i = j
        }
        return Result(OK, out)
    }

    /** Returns the translated text and the language the service detected. */
    private fun request(text: String): Pair<String, String> {
        val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=en&dt=t&q=" + URLEncoder.encode(text, "UTF-8")
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 8000
            c.readTimeout = 10000
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) Wave")
            val body = c.inputStream.bufferedReader().use { it.readText() }
            val root = JSONArray(body)
            val segments = root.getJSONArray(0)
            val sb = StringBuilder()
            for (k in 0 until segments.length()) sb.append(segments.getJSONArray(k).optString(0, ""))
            val src = if (root.length() > 2 && !root.isNull(2)) root.optString(2, "") else ""
            return Pair(sb.toString().trimEnd('\n'), src)
        } finally {
            c.disconnect()
        }
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
}
