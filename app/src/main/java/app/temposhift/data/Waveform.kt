package app.temposhift.data

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteOrder
import kotlin.math.abs

/** Builds a small loudness profile (one byte per bar) by decoding the song. Run it off the main thread. */
object Waveform {
    const val BARS = 240

    /** Returns BARS bytes (0..255 loudness) or null if the file could not be decoded or [cancelled] returned true. */
    fun compute(ctx: Context, uri: Uri, durationMs: Long, cancelled: () -> Boolean): ByteArray? {
        return try {
            decode(ctx, uri, durationMs, cancelled)
        } catch (_: Exception) {
            null
        }
    }

    private fun decode(ctx: Context, uri: Uri, fallbackDurationMs: Long, cancelled: () -> Boolean): ByteArray? {
        val extractor = MediaExtractor()
        extractor.setDataSource(ctx, uri, null)
        var track = -1
        var format: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val f = extractor.getTrackFormat(i)
            if ((f.getString(MediaFormat.KEY_MIME) ?: "").startsWith("audio/")) {
                track = i
                format = f
                break
            }
        }
        if (track < 0 || format == null) { extractor.release(); return null }
        var durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L
        if (durationUs <= 0L) durationUs = fallbackDurationMs * 1000L
        if (durationUs <= 0L) { extractor.release(); return null }
        extractor.selectTrack(track)

        val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
        val peaks = FloatArray(BARS)
        try {
            codec.configure(format, null, null, 0)
            codec.start()
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var floatPcm = false
            var idle = 0
            while (!outputDone) {
                if (cancelled()) return null
                if (!inputDone) {
                    val ii = codec.dequeueInputBuffer(10000)
                    if (ii >= 0) {
                        val buf = codec.getInputBuffer(ii)!!
                        val n = extractor.readSampleData(buf, 0)
                        if (n < 0) {
                            codec.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(ii, 0, n, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val oi = codec.dequeueOutputBuffer(info, 10000)
                if (oi >= 0) {
                    idle = 0
                    if (info.size > 0) {
                        val out = codec.getOutputBuffer(oi)!!
                        out.position(info.offset)
                        out.limit(info.offset + info.size)
                        val bar = ((info.presentationTimeUs.toDouble() / durationUs.toDouble()) * BARS).toInt().coerceIn(0, BARS - 1)
                        var m = 0f
                        if (floatPcm) {
                            val fb = out.order(ByteOrder.nativeOrder()).asFloatBuffer()
                            while (fb.hasRemaining()) { val v = abs(fb.get()); if (v > m) m = v }
                        } else {
                            val sb = out.order(ByteOrder.nativeOrder()).asShortBuffer()
                            while (sb.hasRemaining()) { val v = abs(sb.get().toInt()) / 32768f; if (v > m) m = v }
                        }
                        if (m > peaks[bar]) peaks[bar] = m
                    }
                    codec.releaseOutputBuffer(oi, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputDone = true
                } else if (oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val of = codec.outputFormat
                    floatPcm = of.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                        of.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                } else if (oi == MediaCodec.INFO_TRY_AGAIN_LATER && inputDone) {
                    idle++
                    if (idle > 300) outputDone = true
                }
            }
        } finally {
            try { codec.stop() } catch (_: Exception) { }
            codec.release()
            extractor.release()
        }
        var max = 0f
        for (p in peaks) if (p > max) max = p
        if (max <= 0f) return null
        return ByteArray(BARS) { i -> ((peaks[i] / max).coerceIn(0f, 1f) * 255f).toInt().toByte() }
    }
}
