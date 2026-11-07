package app.temposhift.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Springy press feedback (shrinks slightly while pressed, bounces back) - the feel of iOS buttons. */
@Composable
fun Modifier.bounceClick(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
        label = "press"
    )
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)
}

/** A light tap vibration. */
@Composable
fun rememberHaptic(): () -> Unit {
    val haptic = LocalHapticFeedback.current
    return remember(haptic) { { haptic.performHapticFeedback(HapticFeedbackType.LongPress) } }
}

/** Play / pause glyph that morphs with a quick fade and pop. */
@Composable
fun PlayPauseIcon(playing: Boolean, tint: Color, size: Dp, contentDescription: String?) {
    AnimatedContent(
        targetState = playing,
        transitionSpec = {
            (fadeIn(tween(180, delayMillis = 40)) + scaleIn(spring(dampingRatio = 0.6f), initialScale = 0.55f)) togetherWith
                (fadeOut(tween(90)) + scaleOut(tween(90), targetScale = 0.55f))
        },
        label = "playpause"
    ) { isPlaying ->
        Icon(
            if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(size)
        )
    }
}

/** Stops touches from falling through to whatever is underneath a full-screen layer. */
@Composable
fun Modifier.blockTaps(): Modifier = this.clickable(
    interactionSource = remember { MutableInteractionSource() },
    indication = null,
    onClick = {}
)

/** Staggered rise-in used on album pages: rows slide up and fade in one after another. */
@Composable
fun Rise(index: Int, entered: Boolean, content: @Composable () -> Unit) {
    val p by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(450, delayMillis = index.coerceAtMost(10) * 45, easing = androidx.compose.animation.core.CubicBezierEasing(0.22f, 1f, 0.36f, 1f)),
        label = "rise"
    )
    androidx.compose.foundation.layout.Box(Modifier.graphicsLayer { alpha = p; translationY = (1f - p) * 36.dp.toPx() }) { content() }
}
