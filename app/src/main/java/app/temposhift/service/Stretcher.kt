package app.temposhift.service

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** A growing queue of audio per channel, addressed by absolute frame numbers. */
private class Fifo(private val channels: Int, initial: Int) {
    var data = Array(channels) { FloatArray(initial) }
    var base = 0L
    var len = 0

    fun end(): Long = base + len

    fun ensure(extra: Int) {
        if (len + extra > data[0].size) {
            val size = max(data[0].size * 2, len + extra)
            for (c in 0 until channels) data[c] = data[c].copyOf(size)
        }
    }

    fun drop(count: Int) {
        if (count <= 0) return
        val n = min(count, len)
        for (c in 0 until channels) System.arraycopy(data[c], n, data[c], 0, len - n)
        base += n
        len -= n
    }

    fun clear() {
        base = 0L
        len = 0
    }
}

/**
 * Changes tempo and pitch of music while it plays, and can be adjusted at any moment without gaps or clicks.
 *
 * Stage 1 changes the speed without changing the pitch (overlapping windows cut from the song, each lined up with the
 * previous one by looking for the best match). Stage 2 then reads the result faster or slower to move the pitch.
 * Both targets glide, so a slider that is dragged never makes a jump. It also keeps track of exactly which part of the
 * song is coming out at every moment, so the progress bar stays right.
 */
internal class Stretcher(private val fs: Int, private val nc: Int) {
    private val n: Int
    private val hs: Int
    private val d: Int
    private val win: FloatArray
    private val win0: FloatArray
    private val aF: Double
    private val aP: Double

    var speedT = 1.0
    var pitchT = 1.0

    private val x = Fifo(nc, 16384)
    private var a = 0.0
    private var prev = -1L
    private var k = 0L
    private val acc: Array<FloatArray>
    private val s1 = Fifo(nc, 16384)
    private val frameIn = ArrayList<Long>()
    private var frameBase = 0L
    private var rp = 0.0
    private var speedC = 1.0
    private var pitchC = 1.0
    private var p = 1.0
    private var started = false
    var outTotal = 0L
        private set

    /** The output of the latest [process] call: [outLen] frames in each channel of [out]. */
    var out = Array(nc) { FloatArray(8192) }
        private set
    var outLen = 0

    private val cpOut = LongArray(256)
    private val cpIn = DoubleArray(256)
    private var cpCount = 0
    private var lastCp = 0L

    private val mon: FloatArray
    private val ref: FloatArray

    init {
        var size = 1024
        while (size < 0.04 * fs) size *= 2
        n = size
        hs = size / 2
        d = hs / 2
        win = FloatArray(n) { (0.5 - 0.5 * Math.cos(2.0 * Math.PI * it / n)).toFloat() }
        win0 = win.copyOf()
        for (i in 0 until hs) win0[i] = 1f
        val tau = 0.07
        aF = 1.0 - exp(-(hs.toDouble() / fs) / tau)
        aP = 1.0 - exp(-1.0 / (tau * fs))
        acc = Array(nc) { FloatArray(n) }
        mon = FloatArray(2 * d + hs + 8)
        ref = FloatArray(hs)
        reset()
    }

    fun reset() {
        x.clear()
        s1.clear()
        for (c in 0 until nc) java.util.Arrays.fill(acc[c], 0f)
        a = 0.0
        prev = -1L
        k = 0L
        frameIn.clear()
        frameBase = 0L
        rp = 0.0
        speedC = speedT
        pitchC = pitchT
        p = pitchT
        started = false
        outTotal = 0L
        outLen = 0
        cpCount = 0
        lastCp = 0L
    }

    fun setTargets(speed: Double, pitch: Double) {
        speedT = speed
        pitchT = pitch
        if (!started) {
            speedC = speed
            pitchC = pitch
            p = pitch
        }
    }

    /** Feeds [frames] frames of interleaved audio (-1..1). The result is in [out] / [outLen]. */
    fun process(input: FloatArray, frames: Int) {
        started = true
        outLen = 0
        x.ensure(frames)
        for (i in 0 until frames) {
            for (c in 0 until nc) x.data[c][x.len + i] = input[i * nc + c]
        }
        x.len += frames
        processFrames()
        resample()
    }

    /** Pushes silence through so that everything still inside comes out. The result is in [out] / [outLen]. */
    fun finish() {
        val pad = FloatArray(3 * n * nc)
        process(pad, 3 * n)
    }

    private fun processFrames() {
        while (true) {
            val inEnd = x.end()
            val center = Math.round(a)
            if (inEnd < center + d + n) break
            val first = k == 0L
            if (!first) {
                speedC += (speedT - speedC) * aF
                pitchC += (pitchT - pitchC) * aF
            }
            val t = speedC / max(p, 1e-3)
            var s: Long
            if (first) {
                s = max(0L, center)
            } else {
                val lo = max(0L, center - d)
                val hi = center + d
                if (abs(t - 1.0) < 1e-4) {
                    s = max(0L, min(center, hi))
                } else {
                    s = bestStart(lo, hi)
                }
            }
            val off = (s - x.base).toInt()
            val w = if (first) win0 else win
            s1.ensure(hs)
            for (c in 0 until nc) {
                val src = x.data[c]
                val ac = acc[c]
                for (i in 0 until n) ac[i] += src[off + i] * w[i]
                System.arraycopy(ac, 0, s1.data[c], s1.len, hs)
                System.arraycopy(ac, hs, ac, 0, hs)
                java.util.Arrays.fill(ac, hs, n, 0f)
            }
            s1.len += hs
            frameIn.add(s)
            prev = s
            a += hs * t
            k++
        }
        // let go of input that nobody needs any more
        val keepFrom = max(0L, min(if (prev >= 0) prev else 0L, Math.round(a) - d) - n)
        if (keepFrom - x.base > 4L * n) x.drop((keepFrom - x.base).toInt())
    }

    /** Finds where, between [lo] and [hi], the song continues most smoothly from the previous window. */
    private fun bestStart(lo: Long, hi: Long): Long {
        val cont = prev + hs
        val total = (hi - lo).toInt() + hs
        for (i in 0 until total) mon[i] = mono(lo + i)
        for (i in 0 until hs) ref[i] = mono(cont + i)
        var best = -1e30
        var bs = lo
        var cnd = 0
        val span = (hi - lo).toInt()
        while (cnd <= span) {
            var dot = 0.0
            var en = 0.0
            var j = 0
            while (j < hs) {
                val v = mon[cnd + j].toDouble()
                dot += v * ref[j]
                en += v * v
                j += 4
            }
            val sc = dot / sqrt(en + 1e-9)
            if (sc > best) { best = sc; bs = lo + cnd }
            cnd += 4
        }
        best = -1e30
        var chosen = bs
        val from = max(lo, bs - 3)
        val to = min(hi, bs + 3)
        var c = from
        while (c <= to) {
            val o = (c - lo).toInt()
            var dot = 0.0
            var en = 0.0
            for (j in 0 until hs) {
                val v = mon[o + j].toDouble()
                dot += v * ref[j]
                en += v * v
            }
            val sc = dot / sqrt(en + 1e-9)
            if (sc > best) { best = sc; chosen = c }
            c++
        }
        return chosen
    }

    private fun mono(absIndex: Long): Float {
        val i = (absIndex - x.base).toInt()
        return if (nc == 2) (x.data[0][i] + x.data[1][i]) * 0.5f else x.data[0][i]
    }

    private fun inPos(s1Index: Double): Double {
        val f = floor(s1Index / hs).toLong()
        var j = (f - frameBase).toInt()
        if (j < 0) j = 0
        if (j >= frameIn.size) j = frameIn.size - 1
        if (j < 0) return 0.0
        return frameIn[j] + (s1Index - f * hs)
    }

    private fun addCheckpoint(outFrame: Long, position: Double) {
        if (cpCount == cpOut.size) {
            System.arraycopy(cpOut, cpOut.size / 2, cpOut, 0, cpOut.size / 2)
            System.arraycopy(cpIn, cpIn.size / 2, cpIn, 0, cpIn.size / 2)
            cpCount = cpOut.size / 2
        }
        cpOut[cpCount] = outFrame
        cpIn[cpCount] = position
        cpCount++
    }

    private fun ensureOut(extra: Int) {
        if (outLen + extra > out[0].size) {
            val size = max(out[0].size * 2, outLen + extra)
            out = Array(nc) { out[it].copyOf(size) }
        }
    }

    /** Stage 2: reads stage 1 at the (gliding) pitch ratio with cubic interpolation. */
    private fun resample() {
        val s1End = s1.end()
        var rpl = rp
        var pc = p
        val pt = pitchT
        val ap = aP
        val need = (((s1End - rpl) / max(pc, 0.05)).toInt() + 16)
        if (need > 0) ensureOut(need)
        while (true) {
            val i = floor(rpl).toLong()
            if (i + 2 >= s1End) break
            val o = (i - s1.base).toInt()
            if (o < 0) { rpl = s1.base.toDouble() + 1.0; continue }
            if (outLen >= out[0].size) ensureOut(4096)
            val tt = (rpl - i).toFloat()
            for (c in 0 until nc) {
                val dd = s1.data[c]
                val y1 = dd[o]
                val y0 = if (o >= 1) dd[o - 1] else y1
                val y2 = dd[o + 1]
                val y3 = dd[o + 2]
                out[c][outLen] = y1 + 0.5f * tt * (y2 - y0 + tt * (2f * y0 - 5f * y1 + 4f * y2 - y3 + tt * (3f * (y1 - y2) + y3 - y0)))
            }
            outLen++
            outTotal++
            if (outTotal - lastCp >= 256) {
                addCheckpoint(outTotal, inPos(rpl))
                lastCp = outTotal
            }
            rpl += pc
            pc += (pt - pc) * ap
        }
        rp = rpl
        p = pc
        // let go of stage-1 audio behind the read position
        val drop = (floor(rp).toLong() - 2 - s1.base).toInt()
        if (drop > 4 * n) {
            s1.drop(drop)
            val fdrop = (s1.base / hs - frameBase).toInt()
            if (fdrop > 2) {
                frameIn.subList(0, fdrop - 2).clear()
                frameBase += fdrop - 2
            }
        }
    }

    /** The position in the original song (in frames) of a frame of output counted since the stream began. */
    fun mediaPosFrames(outFrame: Long): Double {
        if (cpCount == 0) return outFrame.toDouble()
        if (outFrame <= cpOut[0]) return cpIn[0] - (cpOut[0] - outFrame) * speedC
        for (i in 1 until cpCount) {
            if (outFrame <= cpOut[i]) {
                val f0 = cpOut[i - 1]
                val f1 = cpOut[i]
                return cpIn[i - 1] + (cpIn[i] - cpIn[i - 1]) * (outFrame - f0) / max(1L, f1 - f0)
            }
        }
        return cpIn[cpCount - 1] + (outFrame - cpOut[cpCount - 1]) * speedC
    }
}
