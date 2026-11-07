package app.temposhift.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class Playlist(val id: Long, val name: String, val count: Int)

/** Title / artist / album the user typed in; shown instead of what the file says. */
data class TagOverride(
    val title: String,
    val artist: String,
    val album: String,
    val genre: String = "",
    val year: Int = 0,
    val track: Int = 0
)

/** Local database: per-song tempo/pitch, playlists and play history. */
class AppDb(ctx: Context) : SQLiteOpenHelper(ctx.applicationContext, "tempo.db", null, 4) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE song_settings (song_id INTEGER PRIMARY KEY, tempo INTEGER NOT NULL, pitch REAL NOT NULL)")
        db.execSQL("CREATE TABLE playlists (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL)")
        db.execSQL("CREATE TABLE playlist_songs (playlist_id INTEGER NOT NULL, song_id INTEGER NOT NULL, position INTEGER NOT NULL, PRIMARY KEY (playlist_id, song_id))")
        db.execSQL("CREATE TABLE history (id INTEGER PRIMARY KEY AUTOINCREMENT, song_id INTEGER NOT NULL, played_at INTEGER NOT NULL)")
        createCacheTables(db)
        createTagTable(db)
    }

    private fun createTagTable(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS tag_overrides (song_id INTEGER PRIMARY KEY, title TEXT NOT NULL, artist TEXT NOT NULL, album TEXT NOT NULL, genre TEXT NOT NULL DEFAULT '', year INTEGER NOT NULL DEFAULT 0, track INTEGER NOT NULL DEFAULT 0)")
    }

    private fun createCacheTables(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS waveforms (song_id INTEGER PRIMARY KEY, bars BLOB NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS lyrics (song_id INTEGER PRIMARY KEY, synced TEXT NOT NULL, plain TEXT NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) createCacheTables(db)
        if (oldVersion < 3) {
            createTagTable(db)
        } else if (oldVersion < 4) {
            db.execSQL("ALTER TABLE tag_overrides ADD COLUMN genre TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE tag_overrides ADD COLUMN year INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE tag_overrides ADD COLUMN track INTEGER NOT NULL DEFAULT 0")
        }
    }

    fun saveSongSettings(songId: Long, tempo: Int, pitch: Float) {
        val v = ContentValues()
        v.put("song_id", songId)
        v.put("tempo", tempo)
        v.put("pitch", pitch)
        writableDatabase.insertWithOnConflict("song_settings", null, v, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun loadSongSettings(songId: Long): Pair<Int, Float>? {
        readableDatabase.rawQuery("SELECT tempo, pitch FROM song_settings WHERE song_id = ?", arrayOf(songId.toString())).use { c ->
            return if (c.moveToFirst()) Pair(c.getInt(0), c.getFloat(1)) else null
        }
    }

    fun playlists(): List<Playlist> {
        val out = ArrayList<Playlist>()
        val sql = "SELECT p.id, p.name, COUNT(s.song_id) FROM playlists p " +
            "LEFT JOIN playlist_songs s ON s.playlist_id = p.id GROUP BY p.id ORDER BY p.name COLLATE NOCASE"
        readableDatabase.rawQuery(sql, null).use { c ->
            while (c.moveToNext()) out.add(Playlist(c.getLong(0), c.getString(1), c.getInt(2)))
        }
        return out
    }

    fun createPlaylist(name: String): Long {
        val v = ContentValues()
        v.put("name", name)
        return writableDatabase.insert("playlists", null, v)
    }

    fun deletePlaylist(id: Long) {
        writableDatabase.delete("playlist_songs", "playlist_id = ?", arrayOf(id.toString()))
        writableDatabase.delete("playlists", "id = ?", arrayOf(id.toString()))
    }

    fun addToPlaylist(playlistId: Long, songId: Long) {
        var next = 0
        readableDatabase.rawQuery("SELECT COALESCE(MAX(position), -1) + 1 FROM playlist_songs WHERE playlist_id = ?", arrayOf(playlistId.toString())).use { c ->
            if (c.moveToFirst()) next = c.getInt(0)
        }
        val v = ContentValues()
        v.put("playlist_id", playlistId)
        v.put("song_id", songId)
        v.put("position", next)
        writableDatabase.insertWithOnConflict("playlist_songs", null, v, SQLiteDatabase.CONFLICT_IGNORE)
    }

    fun playlistSongIds(playlistId: Long): List<Long> {
        val out = ArrayList<Long>()
        readableDatabase.rawQuery("SELECT song_id FROM playlist_songs WHERE playlist_id = ? ORDER BY position", arrayOf(playlistId.toString())).use { c ->
            while (c.moveToNext()) out.add(c.getLong(0))
        }
        return out
    }

    fun recordPlay(songId: Long) {
        val v = ContentValues()
        v.put("song_id", songId)
        v.put("played_at", System.currentTimeMillis())
        writableDatabase.insert("history", null, v)
    }

    fun allSongSettings(): List<Triple<Long, Int, Float>> {
        val out = ArrayList<Triple<Long, Int, Float>>()
        readableDatabase.rawQuery("SELECT song_id, tempo, pitch FROM song_settings", null).use { c ->
            while (c.moveToNext()) out.add(Triple(c.getLong(0), c.getInt(1), c.getFloat(2)))
        }
        return out
    }

    fun playlistIdByName(name: String): Long? {
        readableDatabase.rawQuery("SELECT id FROM playlists WHERE name = ? LIMIT 1", arrayOf(name)).use { c ->
            return if (c.moveToFirst()) c.getLong(0) else null
        }
    }

    fun loadWaveform(songId: Long): ByteArray? {
        readableDatabase.rawQuery("SELECT bars FROM waveforms WHERE song_id = ?", arrayOf(songId.toString())).use { c ->
            return if (c.moveToFirst()) c.getBlob(0) else null
        }
    }

    fun saveWaveform(songId: Long, bars: ByteArray) {
        val v = ContentValues()
        v.put("song_id", songId)
        v.put("bars", bars)
        writableDatabase.insertWithOnConflict("waveforms", null, v, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun loadLyrics(songId: Long): LyricsData? {
        readableDatabase.rawQuery("SELECT synced, plain FROM lyrics WHERE song_id = ?", arrayOf(songId.toString())).use { c ->
            return if (c.moveToFirst()) LyricsData(c.getString(0), c.getString(1)) else null
        }
    }

    fun saveLyrics(songId: Long, data: LyricsData) {
        val v = ContentValues()
        v.put("song_id", songId)
        v.put("synced", data.syncedRaw)
        v.put("plain", data.plain)
        writableDatabase.insertWithOnConflict("lyrics", null, v, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun deleteLyrics(songId: Long) {
        writableDatabase.delete("lyrics", "song_id = ?", arrayOf(songId.toString()))
    }

    fun saveTagOverride(songId: Long, t: TagOverride) {
        val v = ContentValues()
        v.put("song_id", songId)
        v.put("title", t.title)
        v.put("artist", t.artist)
        v.put("album", t.album)
        v.put("genre", t.genre)
        v.put("year", t.year)
        v.put("track", t.track)
        writableDatabase.insertWithOnConflict("tag_overrides", null, v, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun tagOverrides(): Map<Long, TagOverride> {
        val out = HashMap<Long, TagOverride>()
        readableDatabase.rawQuery("SELECT song_id, title, artist, album, genre, year, track FROM tag_overrides", null).use { c ->
            while (c.moveToNext()) out[c.getLong(0)] = TagOverride(c.getString(1), c.getString(2), c.getString(3), c.getString(4), c.getInt(5), c.getInt(6))
        }
        return out
    }
}
