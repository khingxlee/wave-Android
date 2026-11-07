package app.temposhift.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.floor

private const val PLACEHOLDER_BARS = 240

/**
 * The loudness strip. The playhead stays fixed in the middle of the screen and the wave scrolls past it:
 * at the start the wave begins at the middle and extends to the right, while playing it moves left, and at the end
 * it finishes at the middle with the whole song on the left. Tap to jump, or drag the wave to scrub.
 * [loopA] / [loopB] are positions from 0..1 (or -1 when not set).
 */
@Composable
fun Waveform(
    bars: ByteArray?,
    progress: Float,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    animated: Boolean = false,
    playing: Boolean = false,
    loopA: Float = -1f,
    loopB: Float = -1f
) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val seekNow by rememberUpdatedState(onSeek)
    val progressNow by rememberUpdatedState(progress)
    val count = if (bars != null && bars.isNotEmpty()) bars.size else PLACEHOLDER_BARS
    val countNow by rememberUpdatedState(count)

    // the position only arrives a few times a second; gliding between updates keeps the scrolling smooth
    val glide = animateFloatAsState(progress, tween(220, easing = LinearEasing), label = "waveScroll")
    // "animated" look: while playing, the bars around the playhead dance like a voice note
    val phaseState: State<Float>? = if (animated && playing) {
        val transition = rememberInfiniteTransition(label = "waveDance")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = (2 * Math.PI).toFloat(),
            animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing)),
            label = "wavePhase"
        )
    } else null
    val grow = animateFloatAsState(
        targetValue = if (bars != null) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow),
        label = "waveGrow"
    )

    Canvas(
        modifier
            .pointerInput(Unit) {
                detectTapGestures { off ->
                    val total = countNow * 8.dp.toPx()
                    seekNow((progressNow + (off.x - size.width / 2f) / total).coerceIn(0f, 1f))
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { dragging = progressNow },
                    onDragEnd = { dragging?.let { seekNow(it) }; dragging = null },
                    onDragCancel = { dragging = null },
                    onHorizontalDrag = { change, amount ->
                        change.consume()
                        val total = countNow * 8.dp.toPx()
                        dragging = ((dragging ?: progressNow) - amount / total).coerceIn(0f, 1f)
                    }
                )
            }
    ) {
        if (size.width < 16f || size.height < 2f) return@Canvas
        val shown = dragging ?: glide.value
        val phase = phaseState?.value ?: 0f
        val step = 8.dp.toPx()                       // one bar every 8 dp
        val barW = step * 0.62f
        val mid = size.height / 2f
        val maxH = (size.height * 0.96f).coerceAtLeast(2f)
        val minH = minOf(10.dp.toPx(), maxH)
        val cx = size.width / 2f
        val n = count
        val pos = shown * n                          // playhead position, counted in bars

        // A-B loop area
        if (loopA >= 0f) {
            val x1 = cx + (loopA * n - pos) * step
            val x2 = if (loopB > loopA) cx + (loopB * n - pos) * step else x1 + 3.dp.toPx()
            val l = x1.coerceIn(0f, size.width)
            val r = x2.coerceIn(0f, size.width)
            if (r > l) drawRect(ACCENT.copy(alpha = 0.16f), Offset(l, 0f), Size(r - l, size.height))
            if (x1 in 0f..size.width) drawRect(ACCENT, Offset(x1, 0f), Size(2.dp.toPx(), size.height))
            if (loopB > loopA && x2 in 0f..size.width) drawRect(ACCENT, Offset(x2 - 2.dp.toPx(), 0f), Size(2.dp.toPx(), size.height))
        }

        val first = floor(pos - cx / step - 1f).toInt().coerceAtLeast(0)
        val last = ceil(pos + (size.width - cx) / step + 1f).toInt().coerceAtMost(n - 1)
        for (i in first..last) {
            val real = if (bars != null && bars.isNotEmpty()) (bars[i].toInt() and 0xFF) / 255f else 0.15f
            val shaped = Math.pow(real.toDouble(), 0.8).toFloat()
            val v = 0.15f + (shaped - 0.15f) * grow.value
            var h = (v * maxH).coerceIn(minH, maxH)
            if (phaseState != null) {
                val d = kotlin.math.abs(i + 0.5f - pos)
                if (d < 10f) h = (h * (1f + 0.4f * (1f - d / 10f) * kotlin.math.sin(phase + i * 0.9f))).coerceIn(minH, maxH)
            }
            val played = i + 0.5f <= pos
            val color = if (played) ACCENT else TEXT.copy(alpha = 0.28f)
            val x = cx + (i + 0.5f - pos) * step - barW / 2f
            drawRoundRect(color, Offset(x, mid - h / 2f), Size(barW, h), CornerRadius(barW / 2f))
        }

        // playhead, fixed in the middle
        drawRect(TEXT, Offset(cx - 1.dp.toPx(), 0f), Size(2.dp.toPx(), size.height))
    }
}
