package app.temposhift.data

import android.content.Context

/** Small settings store. */
class Prefs(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("tempo_prefs", Context.MODE_PRIVATE)

    var rememberPerSong: Boolean
        get() = sp.getBoolean("remember_per_song", true)
        set(v) { sp.edit().putBoolean("remember_per_song", v).apply() }

    var batteryAsked: Boolean
        get() = sp.getBoolean("battery_asked", false)
        set(v) { sp.edit().putBoolean("battery_asked", v).apply() }

    var minDurationSec: Int
        get() = sp.getInt("min_duration_sec", 15)
        set(v) { sp.edit().putInt("min_duration_sec", v).apply() }

    var accentIndex: Int
        get() = sp.getInt("accent_index", 0)
        set(v) { sp.edit().putInt("accent_index", v).apply() }

    var amoled: Boolean
        get() = sp.getBoolean("amoled", false)
        set(v) { sp.edit().putBoolean("amoled", v).apply() }

    var eqEnabled: Boolean
        get() = sp.getBoolean("eq_enabled", false)
        set(v) { sp.edit().putBoolean("eq_enabled", v).apply() }

    var eqLevels: String
        get() = sp.getString("eq_levels", "") ?: ""
        set(v) { sp.edit().putString("eq_levels", v).apply() }

    var eqBass: Int
        get() = sp.getInt("eq_bass", 0)
        set(v) { sp.edit().putInt("eq_bass", v).apply() }

    var eqLoud: Int
        get() = sp.getInt("eq_loud", 0)
        set(v) { sp.edit().putInt("eq_loud", v).apply() }

    var lyricsCenter: Boolean
        get() = sp.getBoolean("lyrics_center", false)
        set(v) { sp.edit().putBoolean("lyrics_center", v).apply() }

    var sortMode: Int
        get() = sp.getInt("sort_mode", 0)
        set(v) { sp.edit().putInt("sort_mode", v).apply() }

    var tempoMin: Int
        get() = sp.getInt("tempo_min", 25)
        set(v) { sp.edit().putInt("tempo_min", v).apply() }

    var tempoMax: Int
        get() = sp.getInt("tempo_max", 250)
        set(v) { sp.edit().putInt("tempo_max", v).apply() }

    var pitchOctaves: Int
        get() = sp.getInt("pitch_octaves_v2", 2)
        set(v) { sp.edit().putInt("pitch_octaves_v2", v).apply() }

    var snapPitch: Boolean
        get() = sp.getBoolean("snap_pitch", false)
        set(v) { sp.edit().putBoolean("snap_pitch", v).apply() }

    var snapSpeed: Boolean
        get() = sp.getBoolean("snap_speed", false)
        set(v) { sp.edit().putBoolean("snap_speed", v).apply() }

    var themeMode: Int            // 0 dark, 1 light, 2 follow the system
        get() = sp.getInt("theme_mode", 0)
        set(v) { sp.edit().putInt("theme_mode", v).apply() }

    var darkGreen: Boolean
        get() = sp.getBoolean("dark_green", false)
        set(v) { sp.edit().putBoolean("dark_green", v).apply() }

    var accentDarkIdx: Int
        get() = sp.getInt("accent_dark_idx", 2)
        set(v) { sp.edit().putInt("accent_dark_idx", v).apply() }

    var accentLightIdx: Int
        get() = sp.getInt("accent_light_idx", 3)
        set(v) { sp.edit().putInt("accent_light_idx", v).apply() }

    var seekSeconds: Int
        get() = sp.getInt("seek_seconds", 10)
        set(v) { sp.edit().putInt("seek_seconds", v).apply() }

    var plusMinus: Boolean
        get() = sp.getBoolean("plus_minus", true)
        set(v) { sp.edit().putBoolean("plus_minus", v).apply() }

    var onTrackChange: Int        // 0 keep, 1 reset, 2 save and restore by track
        get() = sp.getInt("on_track_change", 1)
        set(v) { sp.edit().putInt("on_track_change", v).apply() }

    var waveAnimated: Boolean
        get() = sp.getBoolean("wave_animated", false)
        set(v) { sp.edit().putBoolean("wave_animated", v).apply() }

    var joinButton: Boolean
        get() = sp.getBoolean("join_button", false)
        set(v) { sp.edit().putBoolean("join_button", v).apply() }

    var persistAdjust: Boolean
        get() = sp.getBoolean("persist_adjust", false)
        set(v) { sp.edit().putBoolean("persist_adjust", v).apply() }

    var lastTempo: Int
        get() = sp.getInt("last_tempo", 100)
        set(v) { sp.edit().putInt("last_tempo", v).apply() }

    var lastPitch: Float
        get() = sp.getFloat("last_pitch", 0f)
        set(v) { sp.edit().putFloat("last_pitch", v).apply() }

    var joinActive: Boolean
        get() = sp.getBoolean("join_active", false)
        set(v) { sp.edit().putBoolean("join_active", v).apply() }

    var lyricsStyle: Int          // 0 default (blurred), 1 colour sweep
        get() = sp.getInt("lyrics_style", 0)
        set(v) { sp.edit().putInt("lyrics_style", v).apply() }

    var lyricsLevel: Int          // lyrics size: 0 small, 1 medium, 2 large (default)
        get() = sp.getInt("lyrics_level", 2)
        set(v) { sp.edit().putInt("lyrics_level", v).apply() }

    /** "on,strength%" for each effect: underwater, muffle, reverb, echo, 8D. */
    var fxState: String
        get() = sp.getString("fx_state", "0,60;0,60;0,60;0,60;0,60") ?: "0,60;0,60;0,60;0,60;0,60"
        set(v) { sp.edit().putString("fx_state", v).apply() }
}
