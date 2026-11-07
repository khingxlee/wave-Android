package app.temposhift.player

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import androidx.lifecycle.AndroidViewModel
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.temposhift.R
import app.temposhift.data.AppDb
import app.temposhift.data.LYR_ERROR
import app.temposhift.data.LYR_IDLE
import app.temposhift.data.LYR_LOADING
import app.temposhift.data.LYR_NONE
import app.temposhift.data.LYR_PLAIN
import app.temposhift.data.LYR_SYNCED
import app.temposhift.data.LyricLine
import app.temposhift.data.Lyrics
import app.temposhift.data.LyricsData
import app.temposhift.data.Waveform
import app.temposhift.data.Library
import app.temposhift.data.Playlist
import app.temposhift.data.Prefs
import app.temposhift.data.Song
import app.temposhift.data.TagOverride
import app.temposhift.service.PlayerService
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

data class QueueEntry(val key: String, val songId: Long, val title: String, val artist: String)

private fun Song.toItem(): MediaItem = MediaItem.Builder()
    .setMediaId(id.toString())
    .setUri(uri)
    .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setArtist(artist).setAlbumTitle(album).build())
    .build()

/** Everything the screens show, plus the actions they trigger. Survives screen rotation. */
class PlayerState(private val app: Application) : AndroidViewModel(app) {
    val db = AppDb(app)
    val prefs = Prefs(app)


    var songs by mutableStateOf<List<Song>>(emptyList())
    var playlists by mutableStateOf<List<Playlist>>(emptyList())
    var current by mutableStateOf<Song?>(null)
    var currentId by mutableStateOf<Long?>(null)
    var isPlaying by mutableStateOf(false)
    var positionMs by mutableLongStateOf(0L)
    var durationMs by mutableLongStateOf(0L)
    var tempoPct by mutableIntStateOf(100)
    var tempoMin by mutableIntStateOf(prefs.tempoMin)
    var tempoMax by mutableIntStateOf(prefs.tempoMax)
    var pitchOctaves by mutableIntStateOf(prefs.pitchOctaves)
    var snapPitch by mutableStateOf(prefs.snapPitch)
    var snapSpeed by mutableStateOf(prefs.snapSpeed)
    var sortMode by mutableIntStateOf(prefs.sortMode)
    var pitchSt by mutableFloatStateOf(0f)
    var loopA by mutableLongStateOf(-1L)
    var loopB by mutableLongStateOf(-1L)
    var repeatMode by mutableIntStateOf(Player.REPEAT_MODE_OFF)
    var shuffle by mutableStateOf(false)
    var nextTitle by mutableStateOf("")
    var songsLoaded by mutableStateOf(false)
    var lyricsError by mutableStateOf("")
    var openPlayerRequest by mutableIntStateOf(0)
    var queue by mutableStateOf<List<QueueEntry>>(emptyList())
    var queueOpen by mutableStateOf(false)
    var fastTick by mutableStateOf(false)
    var joinActive by mutableStateOf(prefs.joinActive)
    var lyricsLevel by mutableIntStateOf(prefs.lyricsLevel)

    /** Lyrics size relative to the original: small 26 sp, medium +30%, large +30% again. */
    val lyricsScale: Float get() = when (lyricsLevel) { 0 -> 1.0f; 1 -> 1.3f; else -> 1.69f }
    var lyricsStyle by mutableIntStateOf(prefs.lyricsStyle)
    var minDurationSec by mutableIntStateOf(prefs.minDurationSec)
    var themeMode by mutableIntStateOf(prefs.themeMode)
    var darkGreen by mutableStateOf(prefs.darkGreen)
    var accentDarkIdx by mutableIntStateOf(prefs.accentDarkIdx)
    var accentLightIdx by mutableIntStateOf(prefs.accentLightIdx)
    var seekSeconds by mutableIntStateOf(prefs.seekSeconds)
    var plusMinus by mutableStateOf(prefs.plusMinus)
    var onTrackChange by mutableIntStateOf(prefs.onTrackChange)
    var waveAnimated by mutableStateOf(prefs.waveAnimated)
    var joinPitchTempo by mutableStateOf(prefs.joinButton)
    var persistAdjust by mutableStateOf(prefs.persistAdjust)
    private var songsSeen = 0
    var amoled by mutableStateOf(prefs.amoled)

    var waveform by mutableStateOf<ByteArray?>(null)
    var lyricsStatus by mutableIntStateOf(LYR_IDLE)
    var lyricLines by mutableStateOf<List<LyricLine>>(emptyList())
    var lyricsPlain by mutableStateOf("")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val main = Handler(Looper.getMainLooper())
    private var waveJob: Job? = null
    private var lyricsJob: Job? = null
    private var controller: MediaController? = null
    private var future: ListenableFuture<MediaController>? = null
    private var lastId: Long? = null
    private var dirtyAt = 0L
    private var paramsDirty = false
    private var lastChangeAt = 0L
    private var lastApplyAt = 0L
    private var external: Song? = null
    private var pendingOpen: Uri? = null
    private var waveFor: Long? = null

    init {
        // show the last known library right away; the fresh scan replaces it a moment later
        scope.launch(Dispatchers.IO) {
            val cached = Library.loadCache(app)
            if (cached.isNotEmpty()) main.post { if (songs.isEmpty()) { songs = cached; songsLoaded = true; sync() } }
        }
        if (prefs.persistAdjust || prefs.onTrackChange == 0) {
            tempoPct = prefs.lastTempo.coerceIn(tempoMin, tempoMax)
            pitchSt = prefs.lastPitch.coerceIn(-pitchLimit.toFloat(), pitchLimit.toFloat())
        }
    }

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) { sync() }
    }

    fun connect() {
        if (future != null) return
        val token = SessionToken(app, ComponentName(app, PlayerService::class.java))
        val f = MediaController.Builder(app, token).buildAsync()
        future = f
        f.addListener({
            try {
                val c = f.get()
                controller = c
                c.addListener(listener)
                if (persistActive) {
                    applyParams()
                } else {
                    tempoPct = (c.playbackParameters.speed * 100f).roundToInt()
                    pitchSt = (Math.round(12.0 * ln(c.playbackParameters.pitch.toDouble()) / ln(2.0) * 100.0) / 100.0).toFloat()
                }
                sync()
                pendingOpen?.let { openUri(it) }
                pendingOpen = null
            } catch (_: Exception) { }
        }, ContextCompat.getMainExecutor(app))
    }

    override fun onCleared() {
        scope.cancel()
        future?.let { MediaController.releaseFuture(it) }
        controller = null
        future = null
        super.onCleared()
    }

    fun sync() {
        val c = controller ?: return
        isPlaying = c.isPlaying
        val id = c.currentMediaItem?.mediaId?.toLongOrNull()
        currentId = id
        current = songs.firstOrNull { it.id == id } ?: external?.takeIf { it.id == id }
        if (current?.id != waveFor) {
            waveFor = current?.id
            loadWaveform(current)
        }
        durationMs = if (c.duration == C.TIME_UNSET) 0L else c.duration
        positionMs = c.currentPosition
        repeatMode = c.repeatMode
        shuffle = c.shuffleModeEnabled
        if (queueOpen) refreshQueue()
        nextTitle = if (c.hasNextMediaItem()) c.getMediaItemAt(c.nextMediaItemIndex).mediaMetadata.title?.toString().orEmpty() else ""
        if (id != lastId) {
            lastId = id
            if (id != null) onSongChanged(id)
        }
    }

    private fun onSongChanged(id: Long) {
        clearLoop()
        if (lyricsStatus != LYR_IDLE) loadLyrics(current)
        if (id < 0) return
        db.recordPlay(id)
        val first = songsSeen++ == 0
        when (onTrackChange) {
            1 -> if (!(first && persistAdjust)) {
                tempoPct = 100
                pitchSt = 0f
                applyParams()
            }
            2 -> {
                val saved = db.loadSongSettings(id)
                tempoPct = saved?.first ?: 100
                pitchSt = saved?.second ?: 0f
                applyParams()
            }
            else -> { }
        }
    }

    /** Called a few times a second: moves the seek bar, enforces the A-B loop, saves tempo/pitch changes. */
    fun tick() {
        val c = controller ?: return
        positionMs = c.currentPosition
        val d = c.duration
        if (d != C.TIME_UNSET) durationMs = d
        if (loopA >= 0 && loopB > loopA && positionMs >= loopB) c.seekTo(loopA)
        if (paramsDirty) {
            val now = SystemClock.uptimeMillis()
            if (now - lastChangeAt >= 140L || now - lastApplyAt >= 700L) {
                applyParams()
                paramsDirty = false
                lastApplyAt = now
            }
        }
        if (dirtyAt != 0L && SystemClock.uptimeMillis() - dirtyAt > 600L) {
            dirtyAt = 0L
            val id = currentId
            if (onTrackChange == 2 && id != null && id >= 0) db.saveSongSettings(id, tempoPct, pitchSt)
            if (persistActive) {
                prefs.lastTempo = tempoPct
                prefs.lastPitch = pitchSt
            }
        }
    }

    fun playList(list: List<Song>, index: Int) {
        val c = controller ?: return
        clearLoop()
        c.setMediaItems(list.map { it.toItem() }, index, 0L)
        c.prepare()
        c.play()
        applyParams()
    }

    /** Opens an audio file chosen in another app ("Open with Wave"). */
    fun openUri(uri: Uri) {
        if (controller == null) { pendingOpen = uri; return }
        val last = uri.lastPathSegment?.substringAfterLast('/')?.toLongOrNull()
        val known = songs.firstOrNull { it.id == last }
        val song = known ?: Song(-1L, uri.lastPathSegment ?: "Audio", "", "", 0L, 0L, uri).also { external = it }
        playList(listOf(song), 0)
    }

    fun toggle() { controller?.let { if (it.isPlaying) it.pause() else it.play() } }
    fun next() { controller?.seekToNextMediaItem() }
    fun prev() { controller?.seekToPrevious() }
    fun seek(ms: Long) {
        val target = ms.coerceAtLeast(0L)
        positionMs = target                 // the timeline jumps at once instead of waiting for the next update
        controller?.seekTo(target)
    }
    fun skip(deltaMs: Long) {
        val c = controller ?: return
        val end = if (durationMs > 0) durationMs else Long.MAX_VALUE
        c.seekTo((c.currentPosition + deltaMs).coerceIn(0L, end))
    }

    /** The pitch range setting is the whole span: 1 octave = -6..+6, 2 = -12..+12, 4 = -24..+24 semitones. */
    val pitchLimit: Int get() = pitchOctaves * 6

    fun setTempo(percent: Int) {
        tempoPct = percent.coerceIn(tempoMin, tempoMax)
        if (joinLinked) {
            val limit = pitchLimit.toFloat()
            pitchSt = (Math.round(12f * kotlin.math.log2(tempoPct / 100f) * 100f) / 100f).coerceIn(-limit, limit)
        }
        markParamsChanged()
    }

    fun setPitch(semitones: Float) {
        val limit = pitchLimit.toFloat()
        pitchSt = (Math.round(semitones * 100f) / 100f).coerceIn(-limit, limit)
        if (joinLinked) tempoPct = Math.round(100f * 2f.pow(pitchSt / 12f)).coerceIn(tempoMin, tempoMax)
        markParamsChanged()
    }

    /** Slider position 0..1 for the tempo. 100% is always the exact middle. */
    fun tempoPosition(): Float {
        val t = tempoPct.toFloat()
        return if (t <= 100f) 0.5f * (t - tempoMin) / (100f - tempoMin).coerceAtLeast(1f)
        else 0.5f + 0.5f * (t - 100f) / (tempoMax - 100f).coerceAtLeast(1f)
    }

    fun setTempoFromPosition(position: Float) {
        val p = position.coerceIn(0f, 1f)
        val snapped = if (kotlin.math.abs(p - 0.5f) < 0.004f) 0.5f else p
        var t = if (snapped <= 0.5f) tempoMin + (100f - tempoMin) * (snapped / 0.5f)
        else 100f + (tempoMax - 100f) * ((snapped - 0.5f) / 0.5f)
        if (snapSpeed) t = (Math.round(t / 5f) * 5).toFloat()
        setTempo(Math.round(t))
    }

    /** Slider position 0..1 for the pitch. 0 semitones is always the exact middle. */
    fun pitchPosition(): Float = ((pitchSt + pitchLimit) / (2f * pitchLimit)).coerceIn(0f, 1f)

    fun setPitchFromPosition(position: Float) {
        val p = position.coerceIn(0f, 1f)
        val snapped = if (kotlin.math.abs(p - 0.5f) < 0.004f) 0.5f else p
        var st = (snapped * 2f - 1f) * pitchLimit
        if (snapPitch) st = Math.round(st).toFloat()
        setPitch(st)
    }

    /** The audio engine is only updated when the slider pauses (or every 0.7 s while moving), which keeps the sound clean. */
    private fun markParamsChanged() {
        val now = SystemClock.uptimeMillis()
        lastChangeAt = now
        dirtyAt = now
        scheduleApply()
    }

    fun setSort(mode: Int) { sortMode = mode; prefs.sortMode = mode }

    fun shuffleAll() {
        if (songs.isNotEmpty()) playList(songs.shuffled(), 0)
    }

    fun playNext(song: Song) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) playList(listOf(song), 0) else c.addMediaItem(c.currentMediaItemIndex + 1, song.toItem())
    }

    fun addToQueue(song: Song) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) playList(listOf(song), 0) else c.addMediaItem(song.toItem())
    }

    /** Forget a deleted song everywhere. */
    fun removeSong(song: Song) {
        songs = songs.filter { it.id != song.id }
        val c = controller ?: return
        for (i in c.mediaItemCount - 1 downTo 0) {
            if (c.getMediaItemAt(i).mediaId == song.id.toString()) c.removeMediaItem(i)
        }
    }

    /** Sends the current tempo and pitch to the audio engine, which glides to them smoothly. */
    private fun applyParams() {
        val ratio = 2.0.pow(pitchSt / 12.0).toFloat().coerceIn(0.25f, 4f)
        val speed = (tempoPct / 100f).coerceIn(0.25f, 5f)
        controller?.setPlaybackParameters(PlaybackParameters(speed, ratio))
    }

    // While a slider is dragged, values arrive faster than needed; send at most one update every 30 ms (the last one wins).
    private var applyScheduled = false
    private val applyRunnable = Runnable {
        applyScheduled = false
        applyParams()
    }

    private fun scheduleApply() {
        if (!applyScheduled) {
            applyScheduled = true
            main.postDelayed(applyRunnable, 30L)
        }
    }

    fun markA() { loopA = positionMs; if (loopB <= loopA) loopB = -1L }
    fun markB() { if (loopA >= 0 && positionMs > loopA) loopB = positionMs }
    fun clearLoop() { loopA = -1L; loopB = -1L }

    fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun toggleShuffle() { controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } }


    fun refreshPlaylists() { playlists = db.playlists() }
    fun createPlaylist(name: String) { if (name.isNotBlank()) { db.createPlaylist(name.trim()); refreshPlaylists() } }
    fun deletePlaylist(p: Playlist) { db.deletePlaylist(p.id); refreshPlaylists() }
    fun addToPlaylist(p: Playlist, s: Song) { db.addToPlaylist(p.id, s.id); refreshPlaylists() }
    fun playPlaylist(p: Playlist) {
        val byId = songs.associateBy { it.id }
        val list = db.playlistSongIds(p.id).mapNotNull { byId[it] }
        if (list.isNotEmpty()) playList(list, 0)
    }

    fun setMinDuration(seconds: Int) { minDurationSec = seconds; prefs.minDurationSec = seconds }
    fun updateThemeMode(mode: Int) { themeMode = mode; prefs.themeMode = mode }
    fun updateDarkGreen(on: Boolean) { darkGreen = on; prefs.darkGreen = on }
    fun updateAccentDark(i: Int) { accentDarkIdx = i; prefs.accentDarkIdx = i }
    fun updateAccentLight(i: Int) { accentLightIdx = i; prefs.accentLightIdx = i }
    fun updateSeek(seconds: Int) { seekSeconds = seconds; prefs.seekSeconds = seconds }
    fun updatePlusMinus(on: Boolean) { plusMinus = on; prefs.plusMinus = on }
    fun updateOnTrack(mode: Int) {
        onTrackChange = mode
        prefs.onTrackChange = mode
        if (mode == 0) { prefs.lastTempo = tempoPct; prefs.lastPitch = pitchSt }
    }
    fun updateWaveAnimated(on: Boolean) { waveAnimated = on; prefs.waveAnimated = on }
    fun updateJoin(on: Boolean) { joinPitchTempo = on; prefs.joinButton = on }
    fun updateJoinActive(on: Boolean) {
        joinActive = on
        prefs.joinActive = on
        if (on && joinPitchTempo) setTempo(tempoPct)     // line pitch up with the tempo straight away
    }
    fun updateLyricsStyle(style: Int) { lyricsStyle = style; prefs.lyricsStyle = style }

    /** Pitch and tempo move together (like a record) when the join button is shown and switched on. */
    private val joinLinked: Boolean get() = joinPitchTempo && joinActive

    /** Pitch and tempo are remembered across restarts when "Persistence" is on, or when the track-change mode is "keep". */
    private val persistActive: Boolean get() = persistAdjust || onTrackChange == 0
    fun updateLyricsLevel(level: Int) {
        lyricsLevel = level.coerceIn(0, 2)
        prefs.lyricsLevel = lyricsLevel
    }

    // ---- play queue (up next) ----
    fun refreshQueue() {
        val c = controller
        if (c == null) { queue = emptyList(); return }
        val count = c.mediaItemCount
        val seen = HashMap<String, Int>()
        val out = ArrayList<QueueEntry>()
        var i = c.currentMediaItemIndex
        while (i < count && out.size < 500) {
            val item = c.getMediaItemAt(i)
            val n = seen[item.mediaId] ?: 0
            seen[item.mediaId] = n + 1
            out.add(
                QueueEntry(
                    key = item.mediaId + "#" + n,
                    songId = item.mediaId.toLongOrNull() ?: -1L,
                    title = item.mediaMetadata.title?.toString().orEmpty(),
                    artist = item.mediaMetadata.artist?.toString().orEmpty()
                )
            )
            i++
        }
        queue = out
    }

    /** [from] and [to] are positions in the visible list; 0 is the song that is playing now. */
    fun moveInQueue(from: Int, to: Int) {
        val c = controller ?: return
        if (from == to || from < 1 || to < 1) return
        val base = c.currentMediaItemIndex
        if (base + from < c.mediaItemCount && base + to < c.mediaItemCount) c.moveMediaItem(base + from, base + to)
        refreshQueue()
    }

    fun playQueueItem(position: Int) {
        val c = controller ?: return
        val target = c.currentMediaItemIndex + position
        if (target < c.mediaItemCount) { c.seekToDefaultPosition(target); c.play() }
    }
    fun updatePersist(on: Boolean) {
        persistAdjust = on
        prefs.persistAdjust = on
        if (on) { prefs.lastTempo = tempoPct; prefs.lastPitch = pitchSt }
    }
    fun updatePitchRange(octaves: Int) { pitchOctaves = octaves; prefs.pitchOctaves = octaves; setPitch(pitchSt) }
    fun updateMinSpeed(percent: Int) { tempoMin = percent; prefs.tempoMin = percent; setTempo(tempoPct) }
    fun updateMaxSpeed(percent: Int) { tempoMax = percent; prefs.tempoMax = percent; setTempo(tempoPct) }
    fun updateSnapPitch(on: Boolean) { snapPitch = on; prefs.snapPitch = on }
    fun updateSnapSpeed(on: Boolean) { snapSpeed = on; prefs.snapSpeed = on }
    fun updateAmoled(on: Boolean) { amoled = on; prefs.amoled = on }

    /** Playlists and per-song tempo/pitch as a JSON backup. */
    fun exportJson(): String {
        val root = JSONObject()
        val pls = JSONArray()
        for (p in db.playlists()) {
            val o = JSONObject()
            o.put("name", p.name)
            val ids = JSONArray()
            db.playlistSongIds(p.id).forEach { ids.put(it) }
            o.put("songs", ids)
            pls.put(o)
        }
        root.put("playlists", pls)
        val ss = JSONArray()
        for (t in db.allSongSettings()) {
            val o = JSONObject()
            o.put("id", t.first)
            o.put("tempo", t.second)
            o.put("pitch", t.third.toDouble())
            ss.put(o)
        }
        root.put("songSettings", ss)
        return root.toString(2)
    }

    /** Restores a backup made by exportJson. Throws if the text is not a valid backup. */
    fun importJson(text: String) {
        val root = JSONObject(text)
        val pls = root.optJSONArray("playlists")
        if (pls != null) {
            for (i in 0 until pls.length()) {
                val o = pls.getJSONObject(i)
                val name = o.getString("name")
                val pid = db.playlistIdByName(name) ?: db.createPlaylist(name)
                val ids = o.getJSONArray("songs")
                for (j in 0 until ids.length()) db.addToPlaylist(pid, ids.getLong(j))
            }
        }
        val ss = root.optJSONArray("songSettings")
        if (ss != null) {
            for (i in 0 until ss.length()) {
                val o = ss.getJSONObject(i)
                db.saveSongSettings(o.getLong("id"), o.getInt("tempo"), o.getDouble("pitch").toFloat())
            }
        }
        refreshPlaylists()
    }

    /** Loudness bars for the strip above the seek bar: cached after the first decode of each song. */
    private fun loadWaveform(song: Song?) {
        waveJob?.cancel()
        waveform = null
        if (song == null) return
        waveJob = scope.launch {
            val cached = if (song.id >= 0) db.loadWaveform(song.id) else null
            val bars = cached ?: Waveform.compute(app, song.uri, song.durationMs) { !isActive }?.also {
                if (song.id >= 0) db.saveWaveform(song.id, it)
            }
            main.post { if (currentId == song.id) waveform = bars }
        }
    }

    /** Loads lyrics from the local cache, or from the internet the first time. */
    fun loadLyrics(song: Song?, force: Boolean = false) {
        lyricsJob?.cancel()
        lyricLines = emptyList()
        lyricsPlain = ""
        if (song == null) { lyricsStatus = LYR_NONE; return }
        lyricsStatus = LYR_LOADING
        lyricsJob = scope.launch(Dispatchers.IO) {
            if (force && song.id >= 0) db.deleteLyrics(song.id)
            var data: LyricsData? = if (song.id >= 0) db.loadLyrics(song.id) else null
            var failed = false
            var errorText = ""
            if (data == null) {
                val unknown = app.getString(R.string.unknown_artist)
                val artist = if (song.artist == unknown) "" else song.artist
                data = Lyrics.fetch(song.title, artist, song.album, (song.durationMs / 1000L).toInt())
                if (data == null) { failed = true; errorText = Lyrics.lastError }
                else if (song.id >= 0) db.saveLyrics(song.id, data)
            }
            val d = data
            main.post {
                if (currentId == song.id) {
                    if (failed || d == null) {
                        lyricsError = errorText
                        lyricsStatus = LYR_ERROR
                    } else if (d.synced.isNotEmpty()) {
                        lyricLines = d.synced
                        lyricsStatus = LYR_SYNCED
                    } else if (d.plain.isNotBlank()) {
                        lyricsPlain = d.plain
                        lyricsStatus = LYR_PLAIN
                    } else {
                        lyricsStatus = LYR_NONE
                    }
                }
            }
        }
    }

    /** Shows the edited title / artist / album everywhere in the app right away and remembers them. */
    fun applyTags(song: Song, tags: TagOverride) {
        if (song.id < 0) return
        db.saveTagOverride(song.id, tags)
        val updated = song.copy(
            title = tags.title.ifBlank { song.title },
            artist = tags.artist.ifBlank { song.artist },
            album = tags.album.ifBlank { song.album },
            genre = tags.genre,
            year = tags.year,
            track = tags.track
        )
        songs = songs.map { if (it.id == song.id) updated else it }
        if (currentId == song.id) current = updated
    }
}
