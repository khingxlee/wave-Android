package app.temposhift.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.temposhift.R
import app.temposhift.data.Song
import app.temposhift.player.PlayerState
import app.temposhift.ui.ACCENT
import app.temposhift.ui.BG
import app.temposhift.ui.BlurredBackdrop
import app.temposhift.ui.Cover
import app.temposhift.ui.DIM
import app.temposhift.ui.LocalMiniInset
import app.temposhift.ui.Rise
import app.temposhift.ui.SongMenuHost
import app.temposhift.ui.SongRow
import app.temposhift.ui.TEXT
import app.temposhift.ui.blockTaps
import app.temposhift.ui.bounceClick
import app.temposhift.ui.rememberCover

/**
 * Album / artist / folder page: the cover blurred behind everything and fading into the dark page,
 * a big cover with a soft shadow, the title block, Play + Shuffle, and the songs rising in one by one.
 */
@Composable
fun DetailScreen(
    title: String,
    line1: String,
    line2: String,
    songs: List<Song>,
    state: PlayerState,
    host: SongMenuHost,
    onBack: () -> Unit
) {
    val cover = songs.firstOrNull()
    val bmp by rememberCover(cover, 600)
    val listState = rememberLazyListState()
    val backdropHeight = (LocalConfiguration.current.screenHeightDp * 0.78f).dp
    val scrollPx by remember {
        derivedStateOf { if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset else 100000 }
    }
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val coverScale by animateFloatAsState(
        targetValue = if (entered) 1f else 0.72f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 260f),
        label = "heroCover"
    )
    val coverAlpha by animateFloatAsState(if (entered) 1f else 0f, label = "heroAlpha")

    Box(Modifier.fillMaxSize().background(BG).blockTaps()) {
        // blurred cover that melts into the dark page; it scrolls away with the content
        Box(Modifier.fillMaxWidth().height(backdropHeight).offset { IntOffset(0, -scrollPx) }) {
            BlurredBackdrop(bmp, Modifier.fillMaxSize(), 0.95f)
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color(0x66000000), Color.Transparent, BG))
                )
            )
        }

        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp + LocalMiniInset.current)) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(top = 64.dp, start = 24.dp, end = 24.dp, bottom = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        Modifier
                            .graphicsLayer { scaleX = coverScale; scaleY = coverScale; alpha = coverAlpha }
                            .shadow(28.dp, RoundedCornerShape(22.dp))
                    ) {
                        Cover(cover, Modifier.size(220.dp), px = 600, shape = RoundedCornerShape(22.dp))
                    }
                    Spacer(Modifier.height(20.dp))
                    Rise(0, entered) {
                        Text(title, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Rise(1, entered) {
                        Text(line1, color = Color(0xCCFFFFFF), fontSize = 16.sp, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Rise(2, entered) {
                        Text(line2, color = Color(0xB3FFFFFF), fontSize = 13.sp, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.height(16.dp))
                    Rise(3, entered) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            PillButton(stringResource(R.string.play), Icons.Filled.PlayArrow, filled = true) { state.playList(songs, 0) }
                            PillButton(stringResource(R.string.shuffle), Icons.Filled.Shuffle, filled = false) { state.playList(songs.shuffled(), 0) }
                        }
                    }
                }
            }
            item {
                Text(
                    stringResource(R.string.tab_songs),
                    color = DIM, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth().background(BG.copy(alpha = 0.55f)).padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
            itemsIndexed(songs, key = { _, s -> s.id }) { i, s ->
                Rise(i + 4, entered) {
                    SongRow(song = s, active = s.id == state.currentId, host = host, onClick = { state.playList(songs, i) })
                }
            }
        }

        Box(
            Modifier.padding(12.dp).size(42.dp).bounceClick(onClick = onBack).clip(CircleShape).background(Color(0x55000000)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back), tint = Color.White)
        }
    }
}

@Composable
private fun PillButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, filled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .bounceClick(onClick = onClick)
            .clip(RoundedCornerShape(50))
            .background(if (filled) Color.White else Color(0x40FFFFFF))
            .padding(horizontal = 22.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = if (filled) Color.Black else Color.White, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, color = if (filled) Color.Black else Color.White, fontWeight = FontWeight.SemiBold)
        }
    }
}
