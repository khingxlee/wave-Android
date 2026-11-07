package app.temposhift.service

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import app.temposhift.data.Prefs
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tanh

/**
 * The five listening effects: switches and strengths shared between the screen and the audio thread.
 * The audio thread reads [target] (an array that is replaced as a whole, so it is always consistent).
 */
object Fx {
    const val UNDERWATER = 0
    const val MUFFLE = 1
    const val REVERB = 2
    const val ECHO = 3
    const val EIGHT_D = 4
    const val COUNT = 5

    @Volatile
    var target: FloatArray = FloatArray(COUNT)
        private set

    private val on = BooleanArray(COUNT)
    private val amount = FloatArray(COUNT) { 0.6f }
    private var prefs: Prefs? = null

    fun load(ctx: Context) {
        val p = Prefs(ctx)
        prefs = p
        val parts = p.fxState.split(";")
        for (i in 0 until COUNT) {
            val pair = parts.getOrNull(i)?.split(",") ?: continue
            on[i] = pair.getOrNull(0) == "1"
            amount[i] = ((pair.getOrNull(1)?.toIntOrNull() ?: 60) / 100f).coerceIn(0f, 1f)
        }
        publish(false)
    }

    fun isOn(index: Int): Boolean = on[index]
    fun amountOf(index: Int): Float = amount[index]

    fun setOn(index: Int, value: Boolean) {
        on[index] = value
        publish(true)
    }

    fun setAmount(index: Int, value: Float) {
        amount[index] = value.coerceIn(0f, 1f)
        publish(true)
    }

    private fun publish(save: Boolean) {
        target = FloatArray(COUNT) { if (on[it]) amount[it] else 0f }
        if (save) prefs?.fxState = (0 until COUNT).joinToString(";") { (if (on[it]) "1" else "0") + "," + Math.round(amount[it] * 100f) }
    }
}

@UnstableApi
class EffectsProcessor : BaseAudioProcessor() {
    private var dsp: Dsp? = null
    private var sampleRate = 0

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        // only 16-bit mono or stereo is handled; anything else plays untouched
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount !in 1..2 || inputAudioFormat.sampleRate <= 0) {
            return AudioFormat.NOT_SET
        }
        sampleRate = inputAudioFormat.sampleRate
        dsp = Dsp(sampleRate, Fx.target)
        return AudioFormat(inputAudioFormat.sampleRate, 2, C.ENCODING_PCM_16BIT)
    }

    override fun onFlush() {
        // after a seek or a new song, old echo / reverb tails must not leak in
        if (sampleRate > 0) dsp = Dsp(sampleRate, Fx.target)
    }

    override fun onReset() {
        dsp = null
        sampleRate = 0
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val d = dsp ?: return
        val channels = inputAudioFormat.channelCount
        val frames = inputBuffer.remaining() / (2 * channels)
        if (frames <= 0) return
        val out = replaceOutputBuffer(frames * 4)
        val t = Fx.target
        for (n in 0 until frames) {
            val l = inputBuffer.getShort() / 32768.0
            val r = if (channels == 2) inputBuffer.getShort() / 32768.0 else l
            d.frame(l, r, t)
            out.putShort(toPcm(d.outL))
            out.putShort(toPcm(d.outR))
        }
        out.flip()
    }

    private fun toPcm(x: Double): Short {
        val v = Math.round(x * 32768.0)
        return (if (v > 32767L) 32767L else if (v < -32768L) -32768L else v).toShort()
    }
}

/** One-channel low-pass filter. Doubles are used on purpose: low cut-offs are not stable in 32-bit floats. */
private class Biquad(fs: Int, fc: Double, q: Double) {
    private val b0: Double
    private val b1: Double
    private val b2: Double
    private val a1: Double
    private val a2: Double
    private var z1 = 0.0
    private var z2 = 0.0

    init {
        val w0 = 2.0 * PI * fc / fs
        val c = cos(w0)
        val alpha = sin(w0) / (2.0 * q)
        val a0 = 1.0 + alpha
        b0 = (1.0 - c) / 2.0 / a0
        b1 = (1.0 - c) / a0
        b2 = b0
        a1 = -2.0 * c / a0
        a2 = (1.0 - alpha) / a0
    }

    fun run(x: Double): Double {
        val y = b0 * x + z1
        z1 = b1 * x - a1 * y + z2
        z2 = b2 * x - a2 * y
        return y
    }

    fun clear() {
        z1 = 0.0
        z2 = 0.0
    }
}

/**
 * The effects chain, one frame at a time: underwater, muffle, 8D, echo, reverb. A strength that changes glides over
 * about a tenth of a second, and an effect that is off costs nothing and leaves the sound bit-for-bit untouched.
 */
private class Dsp(private val fs: Int, initial: FloatArray) {
    var outL = 0.0
    var outR = 0.0

    private val twoPi = 2.0 * PI
    private val k = 1.0 - exp(-1.0 / (0.03 * fs))
    private val s = DoubleArray(5) { initial[it].toDouble() }
    private val was = BooleanArray(5)
    private val act = BooleanArray(5)

    // underwater
    private var wdL = DoubleArray(256)
    private var wdR = DoubleArray(256)
    private var wi = 0
    private var wph = 0.0
    private val wLpL = Biquad(fs, 1100.0, 0.9)
    private val wLpR = Biquad(fs, 1100.0, 0.9)

    // muffle
    private val mLpL = Biquad(fs, 700.0, 0.7071)
    private val mLpR = Biquad(fs, 700.0, 0.7071)

    // 8D
    private var ring = DoubleArray(128)
    private var ri = 0
    private var theta = 0.0
    private val a4k = 1.0 - exp(-twoPi * 4000.0 / fs)
    private val a25k = 1.0 - exp(-twoPi * 2500.0 / fs)
    private var farL = 0.0
    private var farR = 0.0
    private var backL = 0.0
    private var backR = 0.0

    // echo
    private val dly = Math.round(0.32 * fs).toInt()
    private var ebL = DoubleArray(dly)
    private var ebR = DoubleArray(dly)
    private var ei = 0
    private var edL = 0.0
    private var edR = 0.0
    private val aD = 1.0 - exp(-twoPi * 3500.0 / fs)

    // reverb (Freeverb style)
    private val sc = fs / 44100.0
    private val combTune = intArrayOf(1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617)
    private val allpTune = intArrayOf(556, 441, 341, 225)
    private val cbuf = Array(2) { ch -> Array(8) { c -> DoubleArray(Math.round(combTune[c] * sc).toInt() + 23 * ch) } }
    private val cidx = Array(2) { IntArray(8) }
    private val cfil = Array(2) { DoubleArray(8) }
    private val abuf = Array(2) { ch -> Array(4) { a -> DoubleArray(Math.round(allpTune[a] * sc).toInt() + 23 * ch) } }
    private val aidx = Array(2) { IntArray(4) }

    private fun clearEffect(i: Int) {
        when (i) {
            0 -> { wdL = DoubleArray(256); wdR = DoubleArray(256); wi = 0; wLpL.clear(); wLpR.clear() }
            1 -> { mLpL.clear(); mLpR.clear() }
            2 -> {
                for (ch in 0..1) {
                    for (c in 0 until 8) { cbuf[ch][c].fill(0.0); cfil[ch][c] = 0.0 }
                    for (a in 0 until 4) abuf[ch][a].fill(0.0)
                }
            }
            3 -> { ebL.fill(0.0); ebR.fill(0.0); edL = 0.0; edR = 0.0 }
            4 -> { ring = DoubleArray(128); farL = 0.0; farR = 0.0; backL = 0.0; backR = 0.0 }
        }
    }

    private fun softClip(x: Double): Double {
        val a = abs(x)
        if (a <= 0.9) return x
        val y = 0.9 + 0.1 * tanh((a - 0.9) / 0.1)
        return if (x > 0.0) y else -y
    }

    private fun ringTap(delay: Double): Double {
        val pos = ri - delay
        val f = floor(pos)
        val fr = pos - f
        var i0 = f.toInt() % 128
        if (i0 < 0) i0 += 128
        val i1 = (i0 + 1) and 127
        return ring[i0] * (1.0 - fr) + ring[i1] * fr
    }

    fun frame(l0: Double, r0: Double, t: FloatArray) {
        var l = l0
        var r = r0
        var any = false
        for (i in 0 until 5) {
            val tv = t[i].toDouble()
            s[i] += (tv - s[i]) * k
            val a = s[i] > 0.0005 || tv > 0.0
            if (was[i] && !a) clearEffect(i)
            was[i] = a
            act[i] = a
            if (a) any = true
        }
        if (!any) {
            outL = l
            outR = r
            return
        }

        // underwater: a gently moving delay, then a low-pass, blended in by the strength
        if (act[0]) {
            val su = s[0]
            wdL[wi] = l
            wdR[wi] = r
            wph += twoPi * 0.4 / fs
            if (wph > twoPi) wph -= twoPi
            val d = su * 0.0014 * fs * (0.5 + 0.5 * sin(wph))
            val pos = wi - d
            val f = floor(pos)
            val fr = pos - f
            var i0 = f.toInt() % 256
            if (i0 < 0) i0 += 256
            val i1 = (i0 + 1) and 255
            val dl = wdL[i0] * (1.0 - fr) + wdL[i1] * fr
            val dr = wdR[i0] * (1.0 - fr) + wdR[i1] * fr
            wi = (wi + 1) and 255
            val fl = wLpL.run(dl)
            val fr2 = wLpR.run(dr)
            l = dl * (1.0 - su) + fl * su
            r = dr * (1.0 - su) + fr2 * su
        }

        // muffle: a low-pass blended in
        if (act[1]) {
            val sm = s[1]
            val fl = mLpL.run(l)
            val fr2 = mLpR.run(r)
            l = l * (1.0 - sm) + fl * sm
            r = r * (1.0 - sm) + fr2 * sm
        }

        // 8D: the sound circles slowly around the head
        if (act[4]) {
            val s8 = s[4]
            val m = (l + r) * 0.5
            ring[ri] = m
            theta += twoPi * 0.11 / fs
            if (theta > twoPi) theta -= twoPi
            val depth = 0.3 + 0.7 * s8
            val p = sin(theta) * depth
            val front = cos(theta)
            val itd = abs(p) * 0.00055 * fs
            var left = ringTap(if (p > 0.0) itd else 0.0)
            var right = ringTap(if (p < 0.0) itd else 0.0)
            ri = (ri + 1) and 127
            val ang = (p + 1.0) * PI / 4.0
            left *= cos(ang) * 1.41421356
            right *= sin(ang) * 1.41421356
            farL += a4k * (left - farL)
            farR += a4k * (right - farR)
            if (p > 0.0) left += (farL - left) * 0.6 * abs(p)
            if (p < 0.0) right += (farR - right) * 0.6 * abs(p)
            backL += a25k * (left - backL)
            backR += a25k * (right - backR)
            if (front < 0.0) {
                val bk = -front * 0.5 * depth
                left += (backL - left) * bk
                right += (backR - right) * bk
            }
            val mix = min(1.0, s8 * 5.0)
            l = l * (1.0 - mix) + left * mix
            r = r * (1.0 - mix) + right * mix
        }

        // echo: ping-pong repeats
        if (act[3]) {
            val se = s[3]
            val i = ei
            val eL = ebL[i]
            val eR = ebR[i]
            edL += aD * (eL - edL)
            edR += aD * (eR - edR)
            ebL[i] = l + edR * 0.42
            ebR[i] = r + edL * 0.42
            ei = if (i + 1 == dly) 0 else i + 1
            l += se * 0.6 * eL
            r += se * 0.6 * eR
        }

        // reverb
        if (act[2]) {
            val sr = s[2]
            val inp = (l + r) * 0.015
            var oL = 0.0
            var oR = 0.0
            for (ch in 0..1) {
                var acc = 0.0
                for (c in 0 until 8) {
                    val buf = cbuf[ch][c]
                    val ix = cidx[ch][c]
                    val o = buf[ix]
                    cfil[ch][c] = o * 0.92 + cfil[ch][c] * 0.08
                    buf[ix] = inp + cfil[ch][c] * 0.88
                    cidx[ch][c] = if (ix + 1 == buf.size) 0 else ix + 1
                    acc += o
                }
                for (a in 0 until 4) {
                    val buf = abuf[ch][a]
                    val ix = aidx[ch][a]
                    val bo = buf[ix]
                    val out = -acc + bo
                    buf[ix] = acc + bo * 0.5
                    aidx[ch][a] = if (ix + 1 == buf.size) 0 else ix + 1
                    acc = out
                }
                if (ch == 0) oL = acc else oR = acc
            }
            l += sr * 0.6 * oL
            r += sr * 0.6 * oR
        }

        outL = softClip(l)
        outR = softClip(r)
    }
}
