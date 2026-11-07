package app.temposhift.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import app.temposhift.R
import app.temposhift.player.PlayerState
import app.temposhift.player.QueueEntry
import app.temposhift.ui.ACCENT
import app.temposhift.ui.Cover
import app.temposhift.ui.DIM
import app.temposhift.ui.PANEL
import app.temposhift.ui.TEXT
import app.temposhift.ui.blockTaps
import app.temposhift.ui.rememberHaptic

/**
 * The "up next" list: rises from the bottom to 70% of the screen while Now Playing blurs behind it.
 * Hold the handle (three lines) at the end of a song and drag it up or down to change its place.
 */
@Composable
fun QueueSheet(state: PlayerState, visible: Boolean, onClose: () -> Unit) {
    BackHandler(enabled = visible) { onClose() }
    val dim by animateFloatAsState(if (visible) 0.45f else 0f, tween(300), label = "queueDim")
    if (dim > 0.001f) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = dim))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClose() }
        )
    }
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        modifier = Modifier.fillMaxSize(),
        enter = slideInVertically(tween(380, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(250)),
        exit = slideOutVertically(tween(300, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(220))
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Column(
                Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .fillMaxHeight(0.7f)
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(PANEL)
                    .blockTaps()
            ) {
                Box(Modifier.fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.width(40.dp).height(4.dp).clip(RoundedCornerShape(50)).background(DIM.copy(alpha = 0.5f)))
                }
                Text(
                    stringResource(R.string.queue_heading),
                    fontSize = 24.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 8.dp)
                )
                QueueList(state)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QueueList(state: PlayerState) {
    val haptic = rememberHaptic()
    val rowHeight = 64.dp
    val rowPx = with(LocalDensity.current) { rowHeight.toPx() }
    val byId = remember(state.songs) { state.songs.associateBy { it.id } }
    val order = remember { mutableStateListOf<QueueEntry>() }
    var draggingKey by remember { mutableStateOf<String?>(null) }
    var dragDelta by remember { mutableFloatStateOf(0f) }
    var startIndex by remember { mutableIntStateOf(-1) }

    // follow the real queue, except while a song is being dragged
    LaunchedEffect(state.queue, draggingKey) {
        if (draggingKey == null) {
            order.clear()
            order.addAll(state.queue)
        }
    }

    val listState = rememberLazyListState()
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        itemsIndexed(order, key = { _, e -> e.key }) { index, entry ->
            val dragging = entry.key == draggingKey
            val lift by animateFloatAsState(if (dragging) 1f else 0f, spring(dampingRatio = 0.7f, stiffness = 500f), label = "lift")
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(rowHeight)
                    .then(if (!dragging) Modifier.animateItemPlacement(tween(220)) else Modifier)
                    .zIndex(if (dragging) 1f else 0f)
                    .graphicsLayer {
                        translationY = if (dragging) dragDelta else 0f
                        scaleX = 1f + 0.03f * lift
                        scaleY = 1f + 0.03f * lift
                        shadowElevation = 18f * lift
                    }
                    .background(if (dragging) PANEL.copy(alpha = 1f) else Color.Transparent)
                    .clickable { state.playQueueItem(index) }
                    .padding(start = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Cover(byId[entry.songId], 44.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        entry.title,
                        color = if (index == 0) ACCENT else TEXT,
                        fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(entry.artist, color = DIM, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (index == 0) {
                    Spacer(Modifier.width(52.dp))
                } else {
                    Icon(
                        Icons.Filled.DragHandle,
                        contentDescription = stringResource(R.string.cd_drag),
                        tint = if (dragging) ACCENT else DIM,
                        modifier = Modifier
                            .size(52.dp)
                            .padding(14.dp)
                            .pointerInput(entry.key) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        haptic()
                                        draggingKey = entry.key
                                        dragDelta = 0f
                                        startIndex = order.indexOfFirst { it.key == entry.key }
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dragDelta += amount.y
                                        val current = order.indexOfFirst { it.key == draggingKey }
                                        if (current >= 1) {
                                            var target = current
                                            if (dragDelta > rowPx / 2f && current < order.lastIndex) target = current + 1
                                            else if (dragDelta < -rowPx / 2f && current > 1) target = current - 1
                                            if (target != current) {
                                                order.add(target, order.removeAt(current))
                                                dragDelta -= (target - current) * rowPx
                                            }
                                        }
                                    },
                                    onDragEnd = {
                                        val k = draggingKey
                                        val end = order.indexOfFirst { it.key == k }
                                        if (k != null && startIndex >= 1 && end >= 1 && end != startIndex) state.moveInQueue(startIndex, end)
                                        draggingKey = null
                                        dragDelta = 0f
                                    },
                                    onDragCancel = {
                                        draggingKey = null
                                        dragDelta = 0f
                                    }
                                )
                            }
                    )
                }
            }
        }
    }
}
