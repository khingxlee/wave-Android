package app.temposhift.service

import android.content.ContentUris
import android.content.Intent
import android.net.Uri
import android.app.PendingIntent
import app.temposhift.MainActivity
import android.content.Context
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.DefaultAudioTrackBufferSizeProvider
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionResult
import app.temposhift.data.AppDb
import app.temposhift.data.Library
import app.temposhift.data.Prefs
import app.temposhift.data.Song
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/**
 * Native playback service. ExoPlayer decodes and changes tempo / pitch independently.
 * As a media library service it also gives Android Auto (and other media browsers such as Wear OS)
 * a browsable tree: Songs, Albums, Artists, Playlists. The media session provides the notification,
 * lock-screen controls, Bluetooth buttons and audio focus.
 */
class PlayerService : MediaLibraryService() {
    private var mediaSession: MediaLibrarySession? = null
    private val io = Executors.newSingleThreadExecutor()
    private val db by lazy { AppDb(applicationContext) }
    private val prefs by lazy { Prefs(applicationContext) }

    @Volatile private var cache: List<Song> = emptyList()
    @Volatile private var cacheAt = 0L

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        val attrs = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        val extractors = DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true)
        Fx.load(this)
        val player = ExoPlayer.Builder(this, WaveRenderersFactory(this))
            .setMediaSourceFactory(DefaultMediaSourceFactory(this, extractors))
            .setAudioAttributes(attrs, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        EqEngine.attach(this, player.audioSessionId)
        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                EqEngine.attach(this@PlayerService, audioSessionId)
            }
        })
        // tapping the notification or the lock-screen player opens Now Playing
        val openPlayer = Intent(this, MainActivity::class.java)
            .setAction(MainActivity.ACTION_OPEN_PLAYER)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val sessionActivity = PendingIntent.getActivity(this, 0, openPlayer, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        mediaSession = MediaLibrarySession.Builder(this, player, BrowseCallback()).setSessionActivity(sessionActivity).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = mediaSession?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        EqEngine.release()
        mediaSession?.let { it.player.release(); it.release() }
        mediaSession = null
        io.shutdown()
        super.onDestroy()
    }

    // ---------- browse tree for Android Auto / Wear OS ----------

    private fun songs(): List<Song> {
        val now = System.currentTimeMillis()
        if (cache.isEmpty() || now - cacheAt > 30_000L) {
            cache = Library.load(applicationContext, prefs.minDurationSec, db.tagOverrides())
            cacheAt = now
        }
        return cache
    }

    private fun folder(id: String, title: String, subtitle: String? = null): MediaItem = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                .build()
        )
        .build()

    private fun songItem(s: Song): MediaItem = MediaItem.Builder()
        .setMediaId(s.id.toString())
        .setUri(s.uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(s.title)
                .setArtist(s.artist)
                .setAlbumTitle(s.album)
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .build()
        )
        .build()

    private fun children(parentId: String): List<MediaItem> {
        val all = songs()
        return when {
            parentId == ROOT -> listOf(
                folder(SONGS, "Songs"), folder(ALBUMS, "Albums"), folder(ARTISTS, "Artists"), folder(PLAYLISTS, "Playlists")
            )
            parentId == SONGS -> all.take(500).map { songItem(it) }
            parentId == ALBUMS -> all.groupBy { it.album }.map { folder("album:" + it.key, it.key, it.value.first().artist) }
            parentId == ARTISTS -> all.groupBy { it.artist }.map { folder("artist:" + it.key, it.key, it.value.size.toString()) }
            parentId == PLAYLISTS -> db.playlists().map { folder("pl:" + it.id, it.name, it.count.toString()) }
            parentId.startsWith("album:") -> all.filter { it.album == parentId.removePrefix("album:") }.map { songItem(it) }
            parentId.startsWith("artist:") -> all.filter { it.artist == parentId.removePrefix("artist:") }.map { songItem(it) }
            parentId.startsWith("pl:") -> {
                val byId = all.associateBy { it.id }
                val id = parentId.removePrefix("pl:").toLongOrNull()
                if (id == null) emptyList() else db.playlistSongIds(id).mapNotNull { byId[it] }.map { songItem(it) }
            }
            else -> emptyList()
        }
    }

    private fun resolve(item: MediaItem): MediaItem {
        if (item.localConfiguration != null) return item
        val id = item.mediaId.toLongOrNull() ?: return item
        val song = songs().firstOrNull { it.id == id } ?: return item
        return songItem(song)
    }

    private inner class BrowseCallback : MediaLibrarySession.Callback {
        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> =
            Futures.immediateFuture(LibraryResult.ofItem(folder(ROOT, "Wave"), params))

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> =
            Futures.submit(Callable<LibraryResult<MediaItem>> {
                val id = mediaId.toLongOrNull()
                val song = if (id == null) null else songs().firstOrNull { it.id == id }
                if (song != null) LibraryResult.ofItem(songItem(song), null)
                else LibraryResult.ofError(SessionResult.RESULT_ERROR_BAD_VALUE)
            }, io)

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
            Futures.submit(Callable<LibraryResult<ImmutableList<MediaItem>>> {
                LibraryResult.ofItemList(ImmutableList.copyOf(children(parentId)), params)
            }, io)

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> =
            Futures.submit(Callable<MutableList<MediaItem>> {
                mediaItems.map { resolve(it) }.toMutableList()
            }, io)
    }

    private companion object {
        const val ROOT = "root"
        const val SONGS = "songs"
        const val ALBUMS = "albums"
        const val ARTISTS = "artists"
        const val PLAYLISTS = "playlists"
    }
}

/**
 * Builds the audio output:
 * - tempo and pitch are changed by [StretchAudioSink], live and without gaps;
 * - the listening effects (underwater, muffle, reverb, echo, 8D) run just in front of the speaker;
 * - the speaker buffer is kept short, so a switch or slider is heard within a fraction of a second.
 */
@OptIn(UnstableApi::class)
private class WaveRenderersFactory(ctx: Context) : DefaultRenderersFactory(ctx) {
    override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink? {
        val buffers = DefaultAudioTrackBufferSizeProvider.Builder()
            .setMinPcmBufferDurationUs(120_000)
            .setMaxPcmBufferDurationUs(200_000)
            .setPcmBufferMultiplicationFactor(1)
            .build()
        val real = DefaultAudioSink.Builder(context)
            .setEnableFloatOutput(enableFloatOutput)
            .setEnableAudioTrackPlaybackParams(false)
            .setAudioTrackBufferSizeProvider(buffers)
            .setAudioProcessors(arrayOf<AudioProcessor>(EffectsProcessor()))
            .build()
        return StretchAudioSink(real)
    }
}
