package app.temposhift.ui.screens

import android.widget.Toast
import androidx.compose.ui.draw.blur
import androidx.compose.runtime.DisposableEffect
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Share
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import app.temposhift.ui.DockShape
import app.temposhift.ui.ON_ACCENT
import app.temposhift.ui.shareSong
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import app.temposhift.R
import app.temposhift.data.CoverSaver
import app.temposhift.player.PlayerState
import app.temposhift.ui.ACCENT
import app.temposhift.ui.BG
import app.temposhift.ui.CenterSlider
import app.temposhift.ui.Cover
import app.temposhift.ui.DIM
import app.temposhift.ui.Line
import app.temposhift.ui.PANEL
import app.temposhift.ui.PlayPauseIcon
import app.temposhift.ui.SongMenuHost
import app.temposhift.ui.TEXT
import app.temposhift.ui.Waveform
import app.temposhift.ui.blockTaps
import app.temposhift.ui.bounceClick
import app.temposhift.ui.fmt
import app.temposhift.ui.rememberHaptic
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@Composable
fun NowPlayingScreen(
    state: PlayerState,
    host: SongMenuHost,
    onClose: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    progress: Animatable<Float, AnimationVector1D>
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = rememberHaptic()
    val s = state.current
    var showLyrics by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var showEq by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var showFx by remember { mutableStateOf(false) }
    val dragRange = with(LocalDensity.current) { LocalConfiguration.current.screenHeightDp.dp.toPx() * 0.7f }

    fun toast(res: Int) { Toast.makeText(ctx, res, Toast.LENGTH_SHORT).show() }

    LaunchedEffect(showQueue) {
        state.queueOpen = showQueue
        if (showQueue) state.refreshQueue()
    }
    DisposableEffect(Unit) {
        state.fastTick = true
        onDispose { state.fastTick = false; state.queueOpen = false }
    }
    val blurRadius by animateDpAsState(if (showQueue) 14.dp else 0.dp, tween(300), label = "queueBlur")

    val coverScale by animateFloatAsState(
        targetValue = if (state.isPlaying) 1f else 0.9f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 200f),
        label = "coverScale"
    )

    // fully solid: the screen behind can never be tapped through this one
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                shape = DockShape(progress.value)
                clip = true
            }
            .drawBehind { drawRect(lerp(PANEL, BG, progress.value.coerceIn(0f, 1f))) }
            .blockTaps()
    ) {
      // the content fades and settles in while the window grows out of the mini player
      Box(
        Modifier.fillMaxSize().graphicsLayer {
            val p = progress.value
            alpha = ((p - 0.2f) / 0.5f).coerceIn(0f, 1f)
            val k = 0.94f + 0.06f * p.coerceIn(0f, 1f)
            scaleX = k
            scaleY = k
        }
      ) {
        BoxWithConstraints(
            Modifier.fillMaxSize().then(if (blurRadius > 0.5.dp) Modifier.blur(blurRadius) else Modifier),
            contentAlignment = Alignment.TopCenter
        ) {
        val compact = maxHeight < 620.dp
        Column(
            if (compact) Modifier.widthIn(max = 640.dp).fillMaxSize().verticalScroll(rememberScrollState())
            else Modifier.widthIn(max = 640.dp).fillMaxSize()
        ) {
            // header: drag it down to close, like a sheet
            Row(
                Modifier
                    .fillMaxWidth()
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = rememberDraggableState { d -> scope.launch { progress.snapTo((progress.value - d / dragRange).coerceIn(0f, 1f)) } },
                        onDragStopped = { velocity ->
                            if (progress.value < 0.72f || velocity > 1800f) onClose()
                            else scope.launch { progress.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium)) }
                        }
                    )
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.cd_collapse))
                }
                // tapping the small cover opens this song's album
                Box(
                    Modifier
                        .graphicsLayer { scaleX = coverScale; scaleY = coverScale }
                        .clickable(enabled = s != null) { s?.let { onOpenAlbum(it.album) } }
                ) { Cover(s, 44.dp) }
                Spacer(Modifier.width(12.dp))
                Crossfade(targetState = s, modifier = Modifier.weight(1f), animationSpec = tween(250), label = "title") { song ->
                    Column {
                        Text(song?.title ?: stringResource(R.string.nothing_playing), fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(song?.artist ?: "", color = DIM, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                IconButton(onClick = { showLyrics = true }, modifier = Modifier.size(40.dp)) {
                    Icon(painterResource(R.drawable.ic_lyrics), contentDescription = stringResource(R.string.cd_lyrics))
                }
                Box {
                    IconButton(onClick = { menu = true }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.cd_more))
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_tag_editor)) },
                            leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                            enabled = s != null && s.id >= 0,
                            onClick = { menu = false; s?.let { host.openTagEditor(it) } }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_equalizer)) },
                            leadingIcon = { Icon(Icons.Filled.Equalizer, contentDescription = null) },
                            onClick = { menu = false; showEq = true }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_effects)) },
                            leadingIcon = { Icon(Icons.Filled.AutoAwesome, contentDescription = null) },
                            onClick = { menu = false; showFx = true }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_share)) },
                            leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
                            enabled = s != null,
                            onClick = { menu = false; s?.let { shareSong(ctx, it) } }
                        )
                    }
                }
            }
            Line()
            ControlBlock(
                label = stringResource(R.string.pitch),
                valueText = String.format(java.util.Locale.US, "%+.2f", state.pitchSt),
                position = state.pitchPosition(),
                onPosition = { state.setPitchFromPosition(it) },
                onMinus = { state.setPitch(state.pitchSt - 0.1f) },
                onPlus = { state.setPitch(state.pitchSt + 0.1f) },
                onReset = { state.setPitch(0f) },
                showButtons = state.plusMinus,
                extra = if (state.joinPitchTempo) ({ JoinButton(state.joinActive) { state.updateJoinActive(!state.joinActive) } }) else null
            )
            Line()
            ControlBlock(
                label = stringResource(R.string.tempo),
                valueText = state.tempoPct.toString() + "%",
                position = state.tempoPosition(),
                onPosition = { state.setTempoFromPosition(it) },
                onMinus = { state.setTempo(state.tempoPct - 1) },
                onPlus = { state.setTempo(state.tempoPct + 1) },
                onReset = { state.setTempo(100) },
                showButtons = state.plusMinus
            )
            Line()
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.loop), fontWeight = FontWeight.Bold, modifier = Modifier.width(72.dp))
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center) {
                    LoopButton("A", state.loopA >= 0) { haptic(); state.markA() }
                    Spacer(Modifier.width(24.dp))
                    LoopButton("B", state.loopB >= 0) { haptic(); state.markB() }
                }
                IconButton(onClick = { state.clearLoop() }) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cd_clear_loop)) }
            }
            Line()
            var drag by remember { mutableStateOf<Float?>(null) }
            val dur = state.durationMs
            val frac = if (dur > 0) (state.positionMs.toFloat() / dur.toFloat()).coerceIn(0f, 1f) else 0f
            // the waveform takes the free space in the middle: big and obvious, edge to edge
            Box(if (compact) Modifier.fillMaxWidth().height(110.dp) else Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Waveform(
                    bars = state.waveform,
                    progress = drag ?: frac,
                    onSeek = { f -> if (dur > 0) state.seek((f * dur).toLong()) },
                    modifier = Modifier.fillMaxWidth().heightIn(max = 130.dp).fillMaxHeight(0.9f),
                    animated = state.waveAnimated,
                    playing = state.isPlaying,
                    loopA = if (dur > 0 && state.loopA >= 0) state.loopA.toFloat() / dur.toFloat() else -1f,
                    loopB = if (dur > 0 && state.loopB >= 0) state.loopB.toFloat() / dur.toFloat() else -1f
                )
            }
            Line()
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(fmt(state.positionMs), color = DIM, fontSize = 13.sp)
                Slider(
                    value = drag ?: frac,
                    onValueChange = { drag = it },
                    onValueChangeFinished = {
                        drag?.let { state.seek((it * dur).toLong()) }
                        drag = null
                    },
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    colors = SliderDefaults.colors(thumbColor = ACCENT, activeTrackColor = ACCENT, inactiveTrackColor = PANEL)
                )
                Text(fmt(dur), color = DIM, fontSize = 13.sp)
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TransportButton(
                    if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                    stringResource(R.string.cd_repeat),
                    if (state.repeatMode == Player.REPEAT_MODE_OFF) TEXT else ACCENT
                ) { state.cycleRepeat() }
                TransportButton(Icons.Filled.SkipPrevious, stringResource(R.string.cd_previous), ACCENT) { state.prev() }
                TransportButton(Icons.Filled.FastRewind, stringResource(R.string.cd_seek_back, state.seekSeconds), ACCENT) { state.skip(-state.seekSeconds * 1000L) }
                Box(
                    Modifier
                        .size(68.dp)
                        .bounceClick { haptic(); state.toggle() }
                        .clip(RoundedCornerShape(22.dp))
                        .background(ACCENT),
                    contentAlignment = Alignment.Center
                ) {
                    PlayPauseIcon(state.isPlaying, ON_ACCENT, 36.dp, stringResource(R.string.cd_play_pause))
                }
                TransportButton(Icons.Filled.FastForward, stringResource(R.string.cd_seek_forward, state.seekSeconds), ACCENT) { state.skip(state.seekSeconds * 1000L) }
                TransportButton(Icons.Filled.SkipNext, stringResource(R.string.cd_next), ACCENT) { state.next() }
                TransportButton(Icons.Filled.Shuffle, stringResource(R.string.cd_shuffle), if (state.shuffle) ACCENT else TEXT) { state.toggleShuffle() }
            }
            Row(
                Modifier.fillMaxWidth().clickable { showQueue = true }.padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.QueueMusic, contentDescription = null, tint = ACCENT, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Crossfade(targetState = state.nextTitle, modifier = Modifier.weight(1f, fill = false), animationSpec = tween(250), label = "next") { next ->
                    Text(
                        if (next.isNotBlank()) stringResource(R.string.playing_next, next) else stringResource(R.string.queue_up_next),
                        color = ACCENT, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        }

        // the lyrics are a full page of their own, above everything here
        AnimatedVisibility(
            visible = showLyrics,
            enter = fadeIn(tween(340, easing = FastOutSlowInEasing)) + slideInVertically(tween(380, easing = FastOutSlowInEasing)) { it / 6 },
            exit = fadeOut(tween(260, easing = FastOutSlowInEasing)) + slideOutVertically(tween(300, easing = FastOutSlowInEasing)) { it / 8 }
        ) {
            LyricsPage(state) { showLyrics = false }
        }

        QueueSheet(state, visible = showQueue, onClose = { showQueue = false })
      }
    }

    if (showEq) EqualizerSheet { showEq = false }
    if (showFx) EffectsSheet { showFx = false }
}

@Composable
private fun TransportButton(icon: ImageVector, description: String, tint: Color, onClick: () -> Unit) {
    Box(Modifier.size(42.dp).bounceClick(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = description, tint = tint)
    }
}

@Composable
private fun ControlBlock(
    label: String,
    valueText: String,
    position: Float,
    onPosition: (Float) -> Unit,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    onReset: () -> Unit,
    showButtons: Boolean,
    extra: (@Composable () -> Unit)? = null
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontWeight = FontWeight.Bold, modifier = Modifier.width(72.dp))
            Text(valueText, fontSize = 22.sp, color = ACCENT, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            if (extra != null) extra()
            IconButton(onClick = onReset) { Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.cd_reset), tint = DIM) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showButtons) IconButton(onClick = onMinus) { Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.cd_decrease)) }
            CenterSlider(position = position, onPosition = onPosition, modifier = Modifier.weight(1f))
            if (showButtons) IconButton(onClick = onPlus) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.cd_increase)) }
        }
    }
}

@Composable
private fun LoopButton(label: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(38.dp).bounceClick(onClick = onClick).clip(RoundedCornerShape(8.dp)).background(if (on) ACCENT else DIM),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = BG, fontWeight = FontWeight.Bold)
    }
}

/** The link button beside the Pitch value: when it is on, pitch and tempo move together like a record. */
@Composable
private fun JoinButton(active: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (active) ACCENT.copy(alpha = 0.22f) else Color.Transparent, tween(200), label = "joinBg")
    Box(
        Modifier.size(44.dp).bounceClick(onClick = onClick).clip(CircleShape).background(bg),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            if (active) Icons.Filled.Link else Icons.Filled.LinkOff,
            contentDescription = stringResource(R.string.cd_join),
            tint = if (active) ACCENT else DIM
        )
    }
}
