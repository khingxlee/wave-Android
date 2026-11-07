package app.temposhift.service

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Sits in front of the real audio output and does the tempo / pitch change itself with [Stretcher], so a slider can
 * be moved at any moment without the player restarting its audio (which is what made the old engine crackle).
 *
 * The real output is fed audio on its own steady clock; this class translates that clock back to the position in the
 * song, so the progress bar, the lock screen and A-B loops stay exact. If anything ever goes wrong in here, it steps
 * aside and lets the built-in engine take over, so music never stops.
 */
@UnstableApi
class StretchAudioSink(inner: AudioSink) : ForwardingAudioSink(inner) {
    private var stretcher: Stretcher? = null
    private var engineOk = true
    private var rate = 0
    private var channels = 0
    private var params = PlaybackParameters.DEFAULT
    private var pendingOut: ByteBuffer? = null
    private var pendingPts = 0L
    private var outFramesCum = 0L
    private var baseSet = false
    private var inBaseUs = 0L
    private var outBaseUs = 0L
    private var tailQueued = false
    private var ioBuf: ByteBuffer? = null
    private var inF = FloatArray(0)

    private fun active(): Boolean = stretcher != null && engineOk

    override fun configure(inputFormat: Format, specifiedBufferSize: Int, outputChannels: IntArray?) {
        super.configure(inputFormat, specifiedBufferSize, outputChannels)
        rate = inputFormat.sampleRate
        channels = inputFormat.channelCount
        val ok = engineOk && inputFormat.pcmEncoding == C.ENCODING_PCM_16BIT && channels in 1..2 && rate > 0
        stretcher = if (ok) Stretcher(rate, channels) else null
        // the built-in engine must not also change the speed
        super.setPlaybackParameters(if (stretcher != null) PlaybackParameters.DEFAULT else params)
        resetStream()
    }

    private fun resetStream() {
        pendingOut = null
        stretcher?.reset()
        baseSet = false
        outFramesCum = 0L
        tailQueued = false
    }

    override fun setPlaybackParameters(playbackParameters: PlaybackParameters) {
        params = playbackParameters
        if (!active()) super.setPlaybackParameters(playbackParameters)
    }

    override fun getPlaybackParameters(): PlaybackParameters {
        return if (active()) params else super.getPlaybackParameters()
    }

    override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean {
        val st = stretcher
        if (st == null || !engineOk) return super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
        if (pendingOut == null) {
            try {
                if (!baseSet) {
                    baseSet = true
                    inBaseUs = presentationTimeUs
                    outBaseUs = presentationTimeUs
                }
                st.setTargets(params.speed.toDouble(), params.pitch.toDouble())
                val frames = buffer.remaining() / (2 * channels)
                if (frames <= 0) return true
                if (inF.size < frames * channels) inF = FloatArray(frames * channels)
                val src = buffer.duplicate().order(ByteOrder.nativeOrder())
                for (i in 0 until frames * channels) inF[i] = src.getShort() / 32768f
                st.process(inF, frames)
                buffer.position(buffer.position() + frames * 2 * channels)
                val produced = toBuffer(st)
                if (produced == null) return true
                pendingOut = produced
                pendingPts = outBaseUs + framesToUs(outFramesCum)
                outFramesCum += st.outLen.toLong()
            } catch (e: Exception) {
                giveUp()
                return super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
            }
        }
        val out = pendingOut ?: return true
        val done = super.handleBuffer(out, pendingPts, 1)
        if (done) pendingOut = null
        return done
    }

    override fun playToEndOfStream() {
        val st = stretcher
        if (st != null && engineOk && baseSet && !tailQueued) {
            tailQueued = true
            try {
                st.finish()
                val tail = toBuffer(st)
                if (tail != null) {
                    pendingOut = tail
                    pendingPts = outBaseUs + framesToUs(outFramesCum)
                    outFramesCum += st.outLen.toLong()
                }
            } catch (e: Exception) {
                giveUp()
            }
        }
        val out = pendingOut
        if (out != null) {
            if (!super.handleBuffer(out, pendingPts, 1)) return
            pendingOut = null
        }
        super.playToEndOfStream()
    }

    override fun hasPendingData(): Boolean = pendingOut != null || super.hasPendingData()

    override fun isEnded(): Boolean = super.isEnded() && pendingOut == null

    override fun flush() {
        super.flush()
        resetStream()
    }

    override fun reset() {
        super.reset()
        pendingOut = null
        stretcher = null
        baseSet = false
    }

    /** Translates the output's own clock into the position in the song. */
    override fun getCurrentPositionUs(sourceEnded: Boolean): Long {
        val p = super.getCurrentPositionUs(sourceEnded)
        val st = stretcher
        if (p == AudioSink.CURRENT_POSITION_NOT_SET || st == null || !engineOk || !baseSet || rate <= 0) return p
        val outFrames = (p - outBaseUs) * rate / 1_000_000L
        if (outFrames <= 0L) return p
        val inFrames = st.mediaPosFrames(outFrames)
        return inBaseUs + (inFrames * 1_000_000.0 / rate).toLong()
    }

    private fun framesToUs(frames: Long): Long = frames * 1_000_000L / rate

    /** Packs what the stretcher produced into 16-bit audio, or null when it produced nothing. */
    private fun toBuffer(st: Stretcher): ByteBuffer? {
        val frames = st.outLen
        if (frames <= 0) return null
        val bytes = frames * channels * 2
        var buf = ioBuf
        if (buf == null || buf.capacity() < bytes) {
            buf = ByteBuffer.allocateDirect(maxOf(bytes, 16384)).order(ByteOrder.nativeOrder())
            ioBuf = buf
        }
        buf!!.clear()
        for (i in 0 until frames) {
            for (c in 0 until channels) {
                val v = Math.round(st.out[c][i] * 32768f)
                buf.putShort((if (v > 32767) 32767 else if (v < -32768) -32768 else v).toShort())
            }
        }
        buf.flip()
        return buf
    }

    /** Last resort: hand tempo and pitch back to the built-in engine. */
    private fun giveUp() {
        engineOk = false
        stretcher = null
        pendingOut = null
        super.setPlaybackParameters(params)
    }
}
