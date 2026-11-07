package app.temposhift.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * A rounded window that starts exactly where the floating mini player sits (progress 0) and grows to fill
 * the screen (progress 1). Running it backwards makes the player shrink back into the bar.
 */
class DockShape(private val progress: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val p = progress.coerceIn(0f, 1f)
        val left0 = with(density) { 14.dp.toPx() }
        val bottom0 = size.height - with(density) { 12.dp.toPx() }
        val top0 = bottom0 - with(density) { 72.dp.toPx() }
        val right0 = size.width - left0
        val radius0 = with(density) { 72.dp.toPx() } * 0.4f
        fun mix(a: Float, b: Float) = a + (b - a) * p
        val radius = mix(radius0, with(density) { 28.dp.toPx() } * (1f - p))
        return Outline.Rounded(
            RoundRect(
                left = mix(left0, 0f), top = mix(top0, 0f), right = mix(right0, size.width), bottom = mix(bottom0, size.height),
                cornerRadius = CornerRadius(radius.coerceAtLeast(0f))
            )
        )
    }
}
