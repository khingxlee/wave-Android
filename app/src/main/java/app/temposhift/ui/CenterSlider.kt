package app.temposhift.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * A slider whose resting place (0 semitones / 100%) is the exact middle. The coloured part is drawn
 * from the middle mark out to the thumb, so you can see at a glance whether anything is changed.
 * [position] and the value passed to [onPosition] run from 0 (far left) to 1 (far right).
 */
@Composable
fun CenterSlider(position: Float, onPosition: (Float) -> Unit, modifier: Modifier = Modifier) {
    val latest by rememberUpdatedState(onPosition)
    Box(
        modifier
            .height(40.dp)
            .pointerInput(Unit) {
                val pad = 14.dp.toPx()
                detectTapGestures { o -> latest(((o.x - pad) / (size.width - 2 * pad).coerceAtLeast(1f)).coerceIn(0f, 1f)) }
            }
            .pointerInput(Unit) {
                val pad = 14.dp.toPx()
                detectHorizontalDragGestures { change, _ ->
                    latest(((change.position.x - pad) / (size.width - 2 * pad).coerceAtLeast(1f)).coerceIn(0f, 1f))
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val pad = 14.dp.toPx()
            val w = (size.width - 2 * pad).coerceAtLeast(1f)
            val cy = size.height / 2f
            val x = pad + w * position.coerceIn(0f, 1f)
            val cx = pad + w * 0.5f
            val stroke = 4.dp.toPx()
            drawLine(DIM.copy(alpha = 0.35f), Offset(pad, cy), Offset(pad + w, cy), strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(ACCENT, Offset(cx, cy), Offset(x, cy), strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(DIM, Offset(cx, cy - 10.dp.toPx()), Offset(cx, cy + 10.dp.toPx()), strokeWidth = 2.dp.toPx())
            drawCircle(ACCENT, radius = 10.dp.toPx(), center = Offset(x, cy))
        }
    }
}
