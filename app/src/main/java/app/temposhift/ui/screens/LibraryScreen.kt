package app.temposhift.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabPosition
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.temposhift.R
import app.temposhift.data.Playlist
import app.temposhift.data.Song
import app.temposhift.player.PlayerState
import app.temposhift.ui.ACCENT
import app.temposhift.ui.AlbumCard
import app.temposhift.ui.BG
import app.temposhift.ui.DIM
import app.temposhift.ui.GroupRow
import app.temposhift.ui.LocalMiniInset
import app.temposhift.ui.PANEL
import app.temposhift.ui.SongMenuHost
import app.temposhift.ui.SongRow
import app.temposhift.ui.TEXT
import kotlinx.coroutines.launch

private fun sorted(list: List<Song>, mode: Int): List<Song> = when (mode) {
    1 -> list.sortedByDescending { it.title.lowercase() }
    2 -> list.sortedBy { it.modified }
    3 -> list.sortedByDescending { it.modified }
    else -> list.sortedBy { it.title.lowercase() }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    state: PlayerState,
    host: SongMenuHost,
    modifier: Modifier,
    onMenu: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit
) {
    val pagerState = rememberPagerState { 4 }
    val scope = rememberCoroutineScope()
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var more by remember { mutableStateOf(false) }
    var sortPage by remember { mutableStateOf(false) }
    var showCreate by remember { mutableStateOf(false) }

    val filtered = remember(state.songs, query) {
        if (query.isBlank()) state.songs
        else state.songs.filter { it.title.contains(query, true) || it.artist.contains(query, true) || it.album.contains(query, true) }
    }
    val list = remember(filtered, state.sortMode) { sorted(filtered, state.sortMode) }

    Column(modifier) {
        AnimatedContent(
            targetState = searching,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            transitionSpec = {
                if (targetState) {
                    (fadeIn(tween(240)) + scaleIn(tween(300, easing = FastOutSlowInEasing), initialScale = 0.88f, transformOrigin = TransformOrigin(1f, 0.5f))) togetherWith fadeOut(tween(120))
                } else {
                    fadeIn(tween(220)) togetherWith fadeOut(tween(140))
                }
            },
            label = "topbar"
        ) { isSearching ->
            if (isSearching) {
                SearchPill(query, { query = it }) { searching = false; query = "" }
            } else {
                Row(Modifier.fillMaxSize().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onMenu) { Icon(Icons.Filled.Menu, contentDescription = stringResource(R.string.cd_menu)) }
                    Text(
                        stringResource(R.string.library),
                        fontSize = 22.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f).padding(start = 8.dp)
                    )
                    IconButton(onClick = { searching = true }) {
                        Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.cd_search))
                    }
                    Box {
                        IconButton(onClick = { more = true; sortPage = false }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.cd_more))
                        }
                        DropdownMenu(expanded = more, onDismissRequest = { more = false; sortPage = false }) {
                            if (!sortPage) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.sort_order)) },
                                    trailingIcon = { Icon(Icons.Filled.ChevronRight, contentDescription = null) },
                                    onClick = { sortPage = true }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.shuffle_all)) },
                                    onClick = { more = false; state.shuffleAll() }
                                )
                            } else {
                                Text(
                                    stringResource(R.string.sort_order),
                                    color = DIM, fontSize = 13.sp,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                                val options = listOf(
                                    0 to R.string.sort_name_asc, 1 to R.string.sort_name_desc,
                                    2 to R.string.sort_mod_asc, 3 to R.string.sort_mod_desc
                                )
                                options.forEach { opt ->
                                    DropdownMenuItem(
                                        text = { Text(stringResource(opt.second)) },
                                        trailingIcon = {
                                            RadioButton(
                                                selected = state.sortMode == opt.first,
                                                onClick = null,
                                                colors = RadioButtonDefaults.colors(selectedColor = ACCENT, unselectedColor = DIM)
                                            )
                                        },
                                        onClick = { state.setSort(opt.first); more = false; sortPage = false }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        val tabs = listOf(R.string.tab_songs, R.string.tab_albums, R.string.tab_artists, R.string.tab_playlists)
        ScrollableTabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = BG,
            contentColor = ACCENT,
            edgePadding = 8.dp,
            indicator = { positions -> ElasticIndicator(positions, pagerState.currentPage) }
        ) {
            tabs.forEachIndexed { i, res ->
                Tab(
                    selected = pagerState.currentPage == i,
                    onClick = { scope.launch { pagerState.animateScrollToPage(i) } },
                    text = { Text(stringResource(res), fontWeight = FontWeight.SemiBold) },
                    selectedContentColor = ACCENT,
                    unselectedContentColor = DIM
                )
            }
        }

        // swipe left and right to move between the categories
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            LibraryPage(page, state, host, list, query.isNotBlank(), onOpenAlbum, onOpenArtist) { showCreate = true }
        }
    }

    if (showCreate) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text(stringResource(R.string.new_playlist)) },
            text = {
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.playlist_name)) }
                )
            },
            confirmButton = {
                TextButton(onClick = { state.createPlaylist(name); showCreate = false }) { Text(stringResource(R.string.create)) }
            },
            dismissButton = {
                TextButton(onClick = { showCreate = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

private class Move(var prev: Int, var dir: Int)

/** The coloured line under the selected category: the leading edge shoots ahead and the trailing edge follows, like a stretching band. */
@Composable
private fun ElasticIndicator(tabPositions: List<TabPosition>, selected: Int) {
    if (selected !in tabPositions.indices) return
    val move = remember { Move(selected, 1) }
    if (selected != move.prev) {
        move.dir = if (selected > move.prev) 1 else -1
        move.prev = selected
    }
    val fast = spring<Dp>(dampingRatio = 0.7f, stiffness = 900f)
    val slow = spring<Dp>(dampingRatio = 0.85f, stiffness = 220f)
    val pos = tabPositions[selected]
    val left by animateDpAsState(pos.left, if (move.dir > 0) slow else fast, label = "tabLeft")
    val right by animateDpAsState(pos.right, if (move.dir > 0) fast else slow, label = "tabRight")
    Box(
        Modifier
            .fillMaxWidth()
            .wrapContentSize(Alignment.BottomStart)
            .offset(x = left)
            .width((right - left).coerceAtLeast(8.dp))
            .height(3.dp)
            .clip(RoundedCornerShape(50))
            .background(ACCENT)
    )
}

/** A perfectly rounded, pill-shaped search field. */
@Composable
private fun SearchPill(query: String, onChange: (String) -> Unit, onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { try { focus.requestFocus() } catch (_: Exception) { } }
    Row(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(50))
            .background(PANEL)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = DIM, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        BasicTextField(
            value = query,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(color = TEXT, fontSize = 16.sp),
            cursorBrush = SolidColor(ACCENT),
            modifier = Modifier.weight(1f).focusRequester(focus),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) Text(stringResource(R.string.search_hint), color = DIM, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    inner()
                }
            }
        )
        IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cd_close_search)) }
    }
}

@Composable
private fun LibraryPage(
    page: Int,
    state: PlayerState,
    host: SongMenuHost,
    list: List<Song>,
    searching: Boolean,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    onNewPlaylist: () -> Unit
) {
    val inset = LocalMiniInset.current
    when {
        page == 3 -> PlaylistsTab(state, inset, onNewPlaylist)
        !state.songsLoaded && list.isEmpty() -> SkeletonList()
        list.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(if (searching) R.string.no_results else R.string.no_music), color = DIM)
        }
        page == 0 -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = inset)) {
            itemsIndexed(list, key = { _, s -> s.id }) { i, s ->
                SongRow(song = s, active = s.id == state.currentId, host = host, onClick = { state.playList(list, i) })
            }
        }
        page == 1 -> {
            val groups = remember(list) { list.groupBy { it.album }.toList().sortedBy { it.first.lowercase() } }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp + inset),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                gridItems(groups, key = { it.first }) { g ->
                    AlbumCard(g.first, stringResource(R.string.n_songs, g.second.size), g.second.first()) { onOpenAlbum(g.first) }
                }
            }
        }
        else -> {
            val groups = remember(list) { list.groupBy { it.artist }.toList().sortedBy { it.first.lowercase() } }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = inset)) {
                items(groups, key = { it.first }) { g ->
                    GroupRow(g.first, stringResource(R.string.n_songs, g.second.size), g.second.first()) { onOpenArtist(g.first) }
                }
            }
        }
    }
}

/** Soft pulsing placeholder rows shown while the library loads, so there is no "no music" flash. */
@Composable
private fun SkeletonList() {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val pulse by transition.animateFloat(
        initialValue = 0.35f, targetValue = 0.75f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "skeletonPulse"
    )
    val tone = DIM.copy(alpha = pulse * 0.45f)
    Column(Modifier.fillMaxSize().padding(top = 8.dp)) {
        repeat(9) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(tone))
                Spacer(Modifier.width(14.dp))
                Column {
                    Box(Modifier.fillMaxWidth(0.55f).height(14.dp).clip(RoundedCornerShape(7.dp)).background(tone))
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth(0.35f).height(11.dp).clip(RoundedCornerShape(6.dp)).background(tone))
                }
            }
        }
    }
}

@Composable
private fun PlaylistsTab(state: PlayerState, inset: Dp, onNew: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        TextButton(onClick = onNew, modifier = Modifier.padding(horizontal = 8.dp)) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.new_playlist))
        }
        if (state.playlists.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.no_playlists), color = DIM)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = inset)) {
                items(state.playlists, key = { it.id }) { p ->
                    PlaylistRow(p, onClick = { state.playPlaylist(p) }, onDelete = { state.deletePlaylist(p) })
                }
            }
        }
    }
}

@Composable
private fun PlaylistRow(p: Playlist, onClick: () -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.MusicNote, contentDescription = null, tint = ACCENT)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(p.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(stringResource(R.string.n_songs, p.count), color = DIM, fontSize = 13.sp)
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.cd_delete_playlist), tint = DIM)
        }
    }
}
