package app.temposhift.service

import android.content.Context
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import app.temposhift.data.Prefs

/**
 * Audio effects (equalizer, bass boost, loudness) attached to the player's audio session.
 * The service attaches it; the equalizer screen reads and changes it. Settings are remembered.
 */
object EqEngine {
    private var eq: Equalizer? = null
    private var bass: BassBoost? = null
    private var loud: LoudnessEnhancer? = null
    private var prefs: Prefs? = null

    var enabled = false
        private set
    var bassStrength = 0
        private set
    var loudnessMb = 0
        private set

    val available: Boolean get() = eq != null

    fun attach(ctx: Context, sessionId: Int) {
        release()
        if (sessionId <= 0) return
        val p = Prefs(ctx)
        prefs = p
        enabled = p.eqEnabled
        bassStrength = p.eqBass
        loudnessMb = p.eqLoud
        try { eq = Equalizer(0, sessionId) } catch (_: Exception) { eq = null }
        try { bass = BassBoost(0, sessionId) } catch (_: Exception) { bass = null }
        try { loud = LoudnessEnhancer(sessionId) } catch (_: Exception) { loud = null }
        eq?.let { e ->
            val saved = p.eqLevels.split(",").mapNotNull { it.trim().toIntOrNull() }
            val range = levelRange()
            for (i in 0 until e.numberOfBands.toInt()) {
                if (i < saved.size) {
                    try { e.setBandLevel(i.toShort(), saved[i].coerceIn(range.first, range.last).toShort()) } catch (_: Exception) { }
                }
            }
        }
        try { bass?.setStrength(bassStrength.toShort()) } catch (_: Exception) { }
        try { loud?.setTargetGain(loudnessMb) } catch (_: Exception) { }
        applyEnabled()
    }

    fun release() {
        try { eq?.release() } catch (_: Exception) { }
        try { bass?.release() } catch (_: Exception) { }
        try { loud?.release() } catch (_: Exception) { }
        eq = null
        bass = null
        loud = null
    }

    private fun applyEnabled() {
        try { eq?.setEnabled(enabled) } catch (_: Exception) { }
        try { bass?.setEnabled(enabled && bassStrength > 0) } catch (_: Exception) { }
        try { loud?.setEnabled(enabled && loudnessMb > 0) } catch (_: Exception) { }
    }

    fun setEnabled(on: Boolean) {
        enabled = on
        prefs?.eqEnabled = on
        applyEnabled()
    }

    fun bandCount(): Int = eq?.numberOfBands?.toInt() ?: 0

    /** Lowest and highest allowed band level in millibels. */
    fun levelRange(): IntRange {
        val r = eq?.bandLevelRange ?: return -1500..1500
        return r[0].toInt()..r[1].toInt()
    }

    fun centerFreqHz(band: Int): Int = (eq?.getCenterFreq(band.toShort()) ?: 0) / 1000

    fun level(band: Int): Int = eq?.getBandLevel(band.toShort())?.toInt() ?: 0

    fun setLevel(band: Int, millibels: Int) {
        val range = levelRange()
        try { eq?.setBandLevel(band.toShort(), millibels.coerceIn(range.first, range.last).toShort()) } catch (_: Exception) { }
        saveLevels()
    }

    fun presetNames(): List<String> {
        val e = eq ?: return emptyList()
        val out = ArrayList<String>()
        for (i in 0 until e.numberOfPresets.toInt()) out.add(e.getPresetName(i.toShort()))
        return out
    }

    fun usePreset(index: Int) {
        try { eq?.usePreset(index.toShort()) } catch (_: Exception) { }
        saveLevels()
    }

    val bassSupported: Boolean get() = bass?.strengthSupported == true
    val loudnessSupported: Boolean get() = loud != null

    fun setBass(strength: Int) {
        bassStrength = strength.coerceIn(0, 1000)
        prefs?.eqBass = bassStrength
        try { bass?.setStrength(bassStrength.toShort()) } catch (_: Exception) { }
        applyEnabled()
    }

    fun setLoudness(millibels: Int) {
        loudnessMb = millibels.coerceIn(0, 1500)
        prefs?.eqLoud = loudnessMb
        try { loud?.setTargetGain(loudnessMb) } catch (_: Exception) { }
        applyEnabled()
    }

    private fun saveLevels() {
        val n = bandCount()
        prefs?.eqLevels = (0 until n).joinToString(",") { level(it).toString() }
    }
}
