package app.temposhift.ui.screens

import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import app.temposhift.ui.ON_ACCENT
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.temposhift.R
import app.temposhift.data.LYR_ERROR
import app.temposhift.data.LYR_LOADING
import app.temposhift.data.LYR_PLAIN
import app.temposhift.data.LYR_SYNCED
import app.temposhift.data.LyricsTranslator
import app.temposhift.player.PlayerState
import app.temposhift.ui.ACCENT
import app.temposhift.ui.BlurredBackdrop
import app.temposhift.ui.Cover
import app.temposhift.ui.PlayPauseIcon
import app.temposhift.ui.blockTaps
import app.temposhift.ui.bounceClick
import app.temposhift.ui.fmt
import app.temposhift.ui.rememberCover
import app.temposhift.ui.rememberHaptic
import kotlin.math.abs

private val White = Color.White
private val WhiteDim = Color(0xB3FFFFFF)

/**
 * Full-screen lyrics, Apple-Music style: the cover blurred behind, small cover + title at the top,
 * the current line big and bright, the others softer and blurred, and a floating player card.
 */
@Composable
fun LyricsPage(state: PlayerState, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val s = state.current
    val bmp by rememberCover(s, 400)
    var center by remember { mutableStateOf(state.prefs.lyricsCenter) }
    var translateOn by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    var translated by remember { mutableStateOf<List<String>?>(null) }
    var menu by remember { mutableStateOf(false) }

    LaunchedEffect(state.currentId) { state.loadLyrics(state.current) }

    val synced = state.lyricsStatus == LYR_SYNCED
    val hasText = synced || state.lyricsStatus == LYR_PLAIN
    val texts: List<String> = if (synced) state.lyricLines.map { it.text } else state.lyricsPlain.lines()

    LaunchedEffect(translateOn, state.currentId, state.lyricsStatus) {
        translated = null
        if (!translateOn || !hasText || texts.isEmpty()) return@LaunchedEffect
        working = true
        val result = LyricsTranslator.toEnglish(texts)
        working = false
        when (result.kind) {
            LyricsTranslator.OK -> translated = result.lines
            LyricsTranslator.ALREADY_ENGLISH -> {
                translateOn = false
                Toast.makeText(ctx, R.string.lyr_already_english, Toast.LENGTH_SHORT).show()
            }
            else -> {
                translateOn = false
                Toast.makeText(ctx, R.string.lyr_translate_failed, Toast.LENGTH_LONG).show()
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF0E1620)).blockTaps()) {
        BlurredBackdrop(bmp, Modifier.fillMaxSize(), 1f)
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color(0x80000000), Color(0x59000000), Color(0xB3000000)))
            )
        )

        Column(Modifier.fillMaxSize()) {
            // top bar: small cover, title and "artist - album", close
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Cover(s, 52.dp, px = 160)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(s?.title ?: "", color = White, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        (s?.artist ?: "") + " \u2022 " + (s?.album ?: ""),
                        color = WhiteDim, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                Box(
                    Modifier.size(44.dp).bounceClick(onClick = onClose).clip(CircleShape).background(Color(0x40FFFFFF)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cd_back), tint = White)
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    when (state.lyricsStatus) {
                        LYR_LOADING -> CircularProgressIndicator(color = White)
                        LYR_SYNCED, LYR_PLAIN -> LyricsList(state, synced, texts, translated, center, state.lyricsStyle)
                        else -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                            Text(
                                stringResource(if (state.lyricsStatus == LYR_ERROR) R.string.lyrics_error else R.string.lyrics_none),
                                color = WhiteDim, textAlign = TextAlign.Center
                            )
                            if (state.lyricsStatus == LYR_ERROR && state.lyricsError.isNotBlank()) {
                                Spacer(Modifier.height(6.dp))
                                Text(state.lyricsError, color = WhiteDim.copy(alpha = 0.7f), fontSize = 11.sp, textAlign = TextAlign.Center)
                            }
                            Spacer(Modifier.height(12.dp))
                            TextButton(onClick = { state.loadLyrics(state.current, true) }) {
                                Text(stringResource(R.string.lyrics_retry), color = ACCENT)
                            }
                        }
                    }
                }

                // buttons on the right: translate to English, and alignment
                if (hasText) {
                    // Translate and alignment stay in the middle, where they always were; the A circle sits just above them.
                    // When the pill opens it pushes those two down, and they glide back when it closes.
                    var fontOpen by remember { mutableStateOf(false) }
                    val fontT by animateFloatAsState(if (fontOpen) 1f else 0f, tween(340, easing = FastOutSlowInEasing), label = "fontPill")
                    val extraPx = with(LocalDensity.current) { (176.dp * fontT).toPx() }
                    Column(
                        Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 10.dp)
                            // up 28dp so translate and alignment keep their original spot in the middle (the A sits just above them);
                            // down by half the pill growth so the A itself stays put while the pill stretches downward
                            .offset { IntOffset(0, (extraPx / 2f - 28.dp.toPx()).roundToInt()) },
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        FontSizeControl(
                            level = state.lyricsLevel,
                            onLevel = { state.updateLyricsLevel(it) },
                            open = fontT,
                            onOpen = { fontOpen = it }
                        )
                        SideButton(Icons.Filled.Translate, stringResource(R.string.cd_translate), active = translateOn, busy = working) {
                            translateOn = !translateOn
                        }
                        Box {
                            SideButton(Icons.Filled.Tune, stringResource(R.string.cd_adjust), active = false, busy = false) { menu = true }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.lyr_align_left)) },
                                    leadingIcon = { Icon(Icons.Filled.FormatAlignLeft, contentDescription = null) },
                                    trailingIcon = { if (!center) Icon(Icons.Filled.Check, contentDescription = null, tint = ACCENT) },
                                    onClick = { center = false; state.prefs.lyricsCenter = false; menu = false }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.lyr_align_center)) },
                                    leadingIcon = { Icon(Icons.Filled.FormatAlignCenter, contentDescription = null) },
                                    trailingIcon = { if (center) Icon(Icons.Filled.Check, contentDescription = null, tint = ACCENT) },
                                    onClick = { center = true; state.prefs.lyricsCenter = true; menu = false }
                                )
                            }
                        }
                    }
                }
            }

            PlayerCard(state)
        }
    }
}

@Composable
private fun SideButton(icon: ImageVector, description: String, active: Boolean, busy: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (active) White else Color(0x59000000), tween(200), label = "sideBg")
    Box(
        Modifier.size(44.dp).bounceClick(onClick = onClick).clip(CircleShape).background(bg),
        contentAlignment = Alignment.Center
    ) {
        if (busy) {
            CircularProgressIndicator(color = if (active) Color.Black else White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        } else {
            Icon(icon, contentDescription = description, tint = if (active) Color.Black else White, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun LyricsList(state: PlayerState, synced: Boolean, texts: List<String>, translated: List<String>?, center: Boolean, style: Int) {
    val lines = state.lyricLines
    val listState = rememberLazyListState()
    val pos = state.positionMs
    val idx = if (synced) remember(pos, lines) { lines.indexOfLast { it.timeMs <= pos + 300L } } else -1
    LaunchedEffect(idx) {
        if (idx >= 0) listState.animateScrollToItem((idx - 2).coerceAtLeast(0))
    }
    val align = if (center) TextAlign.Center else TextAlign.Start
    val textScale = state.lyricsScale
    // Material's default text style has a fixed 24 sp line height, which made big lyrics overlap. Set the spacing from the size.
    val lyricSize = 26f * textScale
    val lyricLineHeight = (lyricSize * 1.25f).sp
    val transLineHeight = (16f * textScale * 1.3f).sp
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 24.dp, end = 72.dp, top = 24.dp, bottom = 120.dp)
    ) {
        itemsIndexed(texts) { i, text ->
            val current = i == idx
            val focused = !synced || current
            val distance = if (synced && idx >= 0) abs(i - idx) else 0
            val blurTarget = if (!synced) 0f else when (distance) { 0 -> 0f; 1 -> 3f; 2 -> 6f; else -> 9f }
            val scale by animateFloatAsState(if (focused) 1f else 0.88f, spring(dampingRatio = 0.8f, stiffness = 260f), label = "lineScale")
            val alpha by animateFloatAsState(if (focused) 1f else 0.55f, tween(250), label = "lineAlpha")
            val blurPx by animateFloatAsState(blurTarget, tween(300), label = "lineBlur")
            val color by animateColorAsState(if (synced && current) White else WhiteDim, tween(250), label = "lineColor")
            val tapToSeek = if (synced) Modifier.clickable { lines.getOrNull(i)?.let { state.seek(it.timeMs) } } else Modifier
            Column(
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                        transformOrigin = TransformOrigin(if (center) 0.5f else 0f, 0.5f)
                    }
                    .then(if (blurPx > 0.5f) Modifier.blur(blurPx.dp) else Modifier)
                    .then(tapToSeek)
                    .padding(vertical = (9f * textScale).dp),
                horizontalAlignment = if (center) Alignment.CenterHorizontally else Alignment.Start
            ) {
                if (text.isBlank() && !synced) {
                    Spacer(Modifier.height(10.dp))
                } else {
                    if (style == 1 && synced && current && text.isNotBlank()) {
                        // colour sweep: white text, and the accent colour flows through it word by word as the line is sung
                        val startMs = lines.getOrNull(i)?.timeMs ?: 0L
                        val endMs = lines.getOrNull(i + 1)?.timeMs ?: (startMs + 4000L)
                        val raw = ((pos - startMs).toFloat() / (endMs - startMs).coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
                        val progress by animateFloatAsState(raw, tween(240, easing = LinearEasing), label = "sweep")
                        val words = text.split(" ")
                        val spot = progress * words.size
                        val sweepText = buildAnnotatedString {
                            words.forEachIndexed { w, word ->
                                val f = (spot - w).coerceIn(0f, 1f)
                                withStyle(SpanStyle(color = lerp(White, ACCENT, f))) { append(word) }
                                if (w < words.size - 1) append(" ")
                            }
                        }
                        Text(
                            sweepText, fontSize = lyricSize.sp, lineHeight = lyricLineHeight, fontWeight = FontWeight.Bold, textAlign = align,
                            style = TextStyle(shadow = Shadow(color = ACCENT.copy(alpha = 0.35f * progress), blurRadius = 24f))
                        )
                    } else {
                        Text(
                            text.ifBlank { "\u266A" }, fontSize = lyricSize.sp, lineHeight = lyricLineHeight, letterSpacing = 0.sp,
                            fontWeight = FontWeight.Bold, color = color, textAlign = align
                        )
                    }
                }
                val t = translated?.getOrNull(i)
                if (!t.isNullOrBlank()) {
                    Text(t, fontSize = (16f * textScale).sp, lineHeight = transLineHeight, letterSpacing = 0.sp, color = WhiteDim, textAlign = align, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
    }
}

/** Floating card at the bottom: seek bar with times and the transport buttons. */
@Composable
private fun PlayerCard(state: PlayerState) {
    val haptic = rememberHaptic()
    var drag by remember { mutableStateOf<Float?>(null) }
    val dur = state.durationMs
    val frac = if (dur > 0) (state.positionMs.toFloat() / dur.toFloat()).coerceIn(0f, 1f) else 0f
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .clip(RoundedCornerShape(30.dp))
            .background(Color(0x73000000))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(fmt(state.positionMs), color = WhiteDim, fontSize = 12.sp)
            Slider(
                value = drag ?: frac,
                onValueChange = { drag = it },
                onValueChangeFinished = {
                    drag?.let { state.seek((it * dur).toLong()) }
                    drag = null
                },
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                colors = SliderDefaults.colors(thumbColor = ACCENT, activeTrackColor = ACCENT, inactiveTrackColor = Color(0x40FFFFFF))
            )
            Text(fmt(dur), color = WhiteDim, fontSize = 12.sp)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            CardButton(Icons.Filled.SkipPrevious) { state.prev() }
            CardButton(Icons.Filled.FastRewind) { state.skip(-state.seekSeconds * 1000L) }
            Box(
                Modifier.size(64.dp).bounceClick { haptic(); state.toggle() }.clip(CircleShape).background(ACCENT),
                contentAlignment = Alignment.Center
            ) {
                PlayPauseIcon(state.isPlaying, ON_ACCENT, 34.dp, stringResource(R.string.cd_play_pause))
            }
            CardButton(Icons.Filled.FastForward) { state.skip(state.seekSeconds * 1000L) }
            CardButton(Icons.Filled.SkipNext) { state.next() }
        }
    }
}

@Composable
private fun CardButton(icon: ImageVector, onClick: () -> Unit) {
    Box(Modifier.size(48.dp).bounceClick(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = ACCENT, modifier = Modifier.size(30.dp))
    }
}

/** Finger position on the vertical track to a size level: top = Large (2), middle = Medium (1), bottom = Small (0). */
private fun levelFromY(y: Float, height: Float, pad: Float): Int {
    val top = pad
    val bottom = (height - pad).coerceAtLeast(top + 1f)
    val fraction = ((y - top) / (bottom - top)).coerceIn(0f, 1f)
    return 2 - Math.round(fraction * 2f)
}

/**
 * A round "A" button. Tap it and it stretches down into a rounded vertical pill: big A on top, small a at the bottom,
 * a line with three notches between them (Large, Medium, Small). Drag the knob and it snaps from notch to notch, resizing
 * the lyrics at once and giving a small tick at each stop. It stays open until A or a is tapped. [open] is the animation
 * progress (0 = circle, 1 = pill), driven by the caller so the buttons below can make room.
 */
@Composable
private fun FontSizeControl(level: Int, onLevel: (Int) -> Unit, open: Float, onOpen: (Boolean) -> Unit) {
    val textSizeLabel = stringResource(R.string.cd_text_size)
    val haptic = rememberHaptic()
    val levelNow by rememberUpdatedState(level)
    val knob by animateFloatAsState((2 - level) / 2f, spring(dampingRatio = 0.6f, stiffness = 700f), label = "fontKnob")
    val t = open

    fun choose(newLevel: Int) {
        if (newLevel != levelNow) {
            haptic()
            onLevel(newLevel)
        }
    }

    Box(
        Modifier
            .width(44.dp)
            .height(44.dp + 176.dp * t)
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0x66000000))
    ) {
        if (t < 0.2f) {
            Box(
                Modifier.fillMaxSize().graphicsLayer { alpha = 1f - t * 5f }.bounceClick { onOpen(true) },
                contentAlignment = Alignment.Center
            ) {
                Text("A", color = White, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { contentDescription = textSizeLabel })
            }
        }
        if (t > 0.05f) {
            Column(
                Modifier.fillMaxSize().graphicsLayer { alpha = ((t - 0.25f) / 0.6f).coerceIn(0f, 1f) },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier.size(44.dp).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onOpen(false) },
                    contentAlignment = Alignment.Center
                ) {
                    Text("A", color = White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            detectVerticalDragGestures { change, _ ->
                                change.consume()
                                choose(levelFromY(change.position.y, size.height.toFloat(), 10.dp.toPx()))
                            }
                        }
                        .pointerInput(Unit) {
                            detectTapGestures { off -> choose(levelFromY(off.y, size.height.toFloat(), 10.dp.toPx())) }
                        }
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        val pad = 10.dp.toPx()
                        val top = pad
                        val bottom = (size.height - pad).coerceAtLeast(top + 1f)
                        val cx = size.width / 2f
                        drawLine(Color(0x66FFFFFF), Offset(cx, top), Offset(cx, bottom), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                        for (i in 0..2) drawCircle(Color(0xB3FFFFFF), 2.5.dp.toPx(), Offset(cx, top + (bottom - top) * i / 2f))
                        drawCircle(ACCENT, 9.dp.toPx(), Offset(cx, top + (bottom - top) * knob))
                    }
                }
                Box(
                    Modifier.size(44.dp).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onOpen(false) },
                    contentAlignment = Alignment.Center
                ) {
                    Text("a", color = White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
