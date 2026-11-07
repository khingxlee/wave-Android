package app.temposhift.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * The side menu. It is built by hand on purpose: its width comes from the real window size, it starts fully off-screen,
 * and when it is closed it is not on screen at all - so it can never hang open, on a small phone, a big phone, a tablet,
 * in split screen or in a floating window. It opens only when the menu button is tapped (no accidental edge swipes).
 */
@Composable
fun SideDrawer(open: Boolean, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val progress by animateFloatAsState(
        targetValue = if (open) 1f else 0f,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "drawer"
    )
    if (progress <= 0.001f && !open) return
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val limit = maxWidth
        val drawerWidth = (limit * 0.83f).coerceIn(minOf(280.dp, limit), minOf(360.dp, limit))
        // dark veil: tap it to close
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f * progress))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose)
        )
        Column(
            modifier = Modifier
                .width(drawerWidth)
                .fillMaxHeight()
                .offset { IntOffset((-(1f - progress) * drawerWidth.toPx()).roundToInt(), 0) }
                .clip(RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp))
                .background(PANEL)
                .pointerInput(Unit) { detectHorizontalDragGestures { _, drag -> if (drag < -24f) onClose() } }
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
                .verticalScroll(rememberScrollState()),
            content = content
        )
    }
}
