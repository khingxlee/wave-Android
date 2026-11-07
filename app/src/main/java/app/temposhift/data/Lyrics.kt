package app.temposhift.data

import java.net.URLEncoder
import okhttp3.Request
import okhttp3.Protocol
import okhttp3.OkHttpClient
import okhttp3.Dns
import java.util.concurrent.TimeUnit
import java.net.InetAddress
import java.net.Inet4Address
import java.io.IOException
import org.json.JSONArray
import org.json.JSONObject

const val LYR_IDLE = 0
const val LYR_LOADING = 1
const val LYR_SYNCED = 2
const val LYR_PLAIN = 3
const val LYR_NONE = 4
const val LYR_ERROR = 5

data class LyricLine(val timeMs: Long, val text: String)

/** Lyrics as received: timed text in LRC format ([syncedRaw]) and/or plain text. */
class LyricsData(val syncedRaw: String, val plain: String) {
    val synced: List<LyricLine> by lazy { parseLrc(syncedRaw) }
}

private val TIMESTAMP = Regex("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]")

fun parseLrc(text: String): List<LyricLine> {
    val out = ArrayList<LyricLine>()
    for (raw in text.lines()) {
        val stamps = TIMESTAMP.findAll(raw).toList()
        if (stamps.isEmpty()) continue
        val body = raw.substring(stamps.last().range.last + 1).trim()
        for (m in stamps) {
            val minutes = m.groupValues[1].toLong()
            val seconds = m.groupValues[2].toLong()
            val frac = m.groupValues[3]
            val millis = if (frac.isEmpty()) 0L else frac.padEnd(3, '0').take(3).toLong()
            out.add(LyricLine(minutes * 60000L + seconds * 1000L + millis, body))
        }
    }
    out.sortBy { it.timeMs }
    return out
}

/** Looks lyrics up on LRCLIB (free, no account needed). */
object Lyrics {
    private const val BASE = "https://lrclib.net/api/"

    /** What went wrong the last time the service could not be reached; shown in small text so it can be reported. */
    @Volatile
    var lastError: String = ""

    /** Some phone networks cannot use IPv6 properly. Trying the IPv4 address first avoids long waits and failed connections. */
    private object Ipv4First : Dns {
        override fun lookup(hostname: String): List<InetAddress> =
            Dns.SYSTEM.lookup(hostname).sortedBy { if (it is Inet4Address) 0 else 1 }
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(Ipv4First)
            .protocols(listOf(Protocol.HTTP_1_1))
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(35, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * Returns the lyrics found (possibly empty when the song has none) or null when the service could not be reached.
     * Pass an empty [artist] when the artist is unknown.
     */
    fun fetch(title: String, artist: String, album: String, durationSec: Int): LyricsData? {
        var networkFailed = false
        lastError = ""
        if (artist.isNotBlank() && album.isNotBlank() && durationSec > 0) {
            try {
                val q = "track_name=" + enc(title) + "&artist_name=" + enc(artist) + "&album_name=" + enc(album) + "&duration=" + durationSec
                val body = get(BASE + "get?" + q)
                if (body != null) {
                    val d = fromJson(JSONObject(body))
                    if (!isEmpty(d)) return d
                }
            } catch (_: IOException) {
                networkFailed = true
            } catch (_: Exception) { }
        }
        try {
            val query = if (artist.isNotBlank()) "$artist $title" else title
            val arr = JSONArray(get(BASE + "search?q=" + enc(query)) ?: "[]")
            var best: LyricsData? = null
            for (i in 0 until arr.length()) {
                val d = fromJson(arr.getJSONObject(i))
                if (isEmpty(d)) continue
                if (d.syncedRaw.isNotBlank()) return d
                if (best == null) best = d
            }
            return best ?: LyricsData("", "")
        } catch (_: IOException) {
            networkFailed = true
        } catch (_: Exception) { }
        return if (networkFailed) null else LyricsData("", "")
    }

    private fun isEmpty(d: LyricsData) = d.syncedRaw.isBlank() && d.plain.isBlank()

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    private fun str(o: JSONObject, key: String): String = if (o.isNull(key)) "" else o.optString(key, "")

    private fun fromJson(o: JSONObject) = LyricsData(str(o, "syncedLyrics"), str(o, "plainLyrics"))

    /** Tries up to three times with short pauses. A "not found" style answer is not an error; a network failure is. */
    private fun get(url: String): String? {
        var last: IOException? = null
        for (attempt in 0 until 3) {
            try {
                val request = Request.Builder().url(url)
                    .header("User-Agent", "Wave/6.0 (Android)")
                    .header("Accept", "application/json")
                    .build()
                client.newCall(request).execute().use { response ->
                    val code = response.code
                    if (code in 200..299) return response.body?.string()
                    if (code in 400..499 && code != 408 && code != 429) return null
                    throw IOException("HTTP $code")
                }
            } catch (e: IOException) {
                last = e
                lastError = e.javaClass.simpleName + (e.message?.let { ": " + it.take(70) } ?: "")
            }
            if (attempt < 2) {
                try { Thread.sleep(500L * (attempt + 1)) } catch (_: InterruptedException) { }
            }
        }
        throw last ?: IOException("network")
    }
}
