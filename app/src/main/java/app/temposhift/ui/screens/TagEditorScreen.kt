package app.temposhift.ui.screens

import android.app.Activity
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.temposhift.R
import app.temposhift.data.CoverSaver
import app.temposhift.data.CoverStore
import app.temposhift.data.Library
import app.temposhift.data.Song
import app.temposhift.data.TagEditor
import app.temposhift.data.TagOverride
import app.temposhift.player.PlayerState
import app.temposhift.ui.ACCENT
import app.temposhift.ui.BG
import app.temposhift.ui.DIM
import app.temposhift.ui.IS_DARK
import app.temposhift.ui.Line
import app.temposhift.ui.LocalMiniInset
import app.temposhift.ui.TEXT
import app.temposhift.ui.blockTaps
import app.temposhift.ui.bounceClick
import app.temposhift.ui.rememberCover
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private class PendingWrite(val tags: TagOverride, val cover: ByteArray?, val removeCover: Boolean)

/** Full-page tag editor: cover (change / remove / save), then title, album, artist, genre, year and track. */
@Composable
fun TagEditorScreen(song: Song?, state: PlayerState, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val unknownTitle = stringResource(R.string.unknown_title)
    val unknownArtist = stringResource(R.string.unknown_artist)
    val unknownAlbum = stringResource(R.string.unknown_album)
    fun clean(value: String, unknown: String) = if (value == unknown) "" else value

    var title by remember(song?.id) { mutableStateOf(clean(song?.title ?: "", unknownTitle)) }
    var album by remember(song?.id) { mutableStateOf(clean(song?.album ?: "", unknownAlbum)) }
    var artist by remember(song?.id) { mutableStateOf(clean(song?.artist ?: "", unknownArtist)) }
    var genre by remember(song?.id) { mutableStateOf(song?.genre ?: "") }
    var year by remember(song?.id) { mutableStateOf(if ((song?.year ?: 0) > 0) song!!.year.toString() else "") }
    var track by remember(song?.id) { mutableStateOf(if ((song?.track ?: 0) > 0) song!!.track.toString() else "") }
    var newCover by remember(song?.id) { mutableStateOf<ByteArray?>(null) }
    var removed by remember(song?.id) { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<PendingWrite?>(null) }

    val currentCover by rememberCover(song, 800)
    val pickedBitmap = remember(newCover) { newCover?.let { BitmapFactory.decodeByteArray(it, 0, it.size) } }
    val shownBitmap = if (removed) null else (pickedBitmap ?: currentCover)

    fun toast(res: Int) { Toast.makeText(ctx, res, Toast.LENGTH_SHORT).show() }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val bytes = withContext(Dispatchers.IO) { CoverStore.fromUri(ctx, uri) }
                if (bytes != null) { newCover = bytes; removed = false } else toast(R.string.cover_failed)
            }
        }
    }

    // Android 11+: the user approves editing the file once; then the tags are written into it
    val writeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val p = pending
        pending = null
        val s = song
        if (p != null && s != null) {
            scope.launch {
                val changed = if (result.resultCode == Activity.RESULT_OK) {
                    withContext(Dispatchers.IO) { TagEditor.writeFile(ctx, s.uri, p.tags, p.cover, p.removeCover) }
                } else false
                toast(if (changed) R.string.tags_saved else R.string.tags_saved_app)
                saving = false
                onBack()
            }
        }
    }

    fun save() {
        val s = song ?: return
        if (saving) return
        saving = true
        val tags = TagOverride(
            title = title.trim(), artist = artist.trim(), album = album.trim(), genre = genre.trim(),
            year = year.trim().toIntOrNull() ?: 0, track = track.trim().toIntOrNull() ?: 0
        )
        val cover = newCover
        val removeCover = removed && cover == null
        if (cover != null) { CoverStore.write(ctx, s.id, cover); Library.bumpCovers() }
        else if (removeCover) { CoverStore.markRemoved(ctx, s.id); Library.bumpCovers() }
        state.applyTags(s, tags)
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                pending = PendingWrite(tags, cover, removeCover)
                val pi = MediaStore.createWriteRequest(ctx.contentResolver, listOf(s.uri))
                writeLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
            } catch (_: Exception) {
                pending = null
                toast(R.string.tags_saved_app)
                saving = false
                onBack()
            }
        } else {
            scope.launch {
                val changed = withContext(Dispatchers.IO) { TagEditor.writeFile(ctx, s.uri, tags, cover, removeCover) }
                toast(if (changed) R.string.tags_saved else R.string.tags_saved_app)
                saving = false
                onBack()
            }
        }
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = ACCENT, unfocusedBorderColor = DIM.copy(alpha = 0.7f),
        focusedLabelColor = ACCENT, unfocusedLabelColor = DIM, cursorColor = ACCENT,
        focusedTextColor = TEXT, unfocusedTextColor = TEXT
    )
    val fieldShape = RoundedCornerShape(14.dp)

    Column(Modifier.fillMaxSize().background(BG).blockTaps()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp).height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back)) }
            Text(stringResource(R.string.menu_tag_editor), fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
        }
        Line()
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.tag_art), fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                if (shownBitmap != null) {
                    IconButton(onClick = { removed = true; newCover = null }) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cd_remove_cover))
                    }
                }
            }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(240.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF24362D))
                        .clickable { pickImage.launch(arrayOf("image/*")) },
                    contentAlignment = Alignment.Center
                ) {
                    val b = shownBitmap
                    if (b != null) {
                        Image(bitmap = b.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else {
                        Icon(Icons.Filled.MusicNote, contentDescription = null, tint = DIM, modifier = Modifier.size(64.dp))
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)) {
                SmallPill(stringResource(R.string.tag_change_cover)) { pickImage.launch(arrayOf("image/*")) }
                SmallPill(stringResource(R.string.menu_save_cover)) {
                    val s = song
                    if (s != null) {
                        scope.launch {
                            when (CoverSaver.save(ctx, s)) {
                                CoverSaver.SAVED -> toast(R.string.cover_saved)
                                CoverSaver.NO_COVER -> toast(R.string.cover_none)
                                else -> toast(R.string.cover_failed)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(18.dp))

            OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth().padding(vertical = 6.dp), label = { Text(stringResource(R.string.tag_title)) },
                singleLine = true, shape = fieldShape, colors = fieldColors)
            OutlinedTextField(album, { album = it }, Modifier.fillMaxWidth().padding(vertical = 6.dp), label = { Text(stringResource(R.string.tag_album)) },
                singleLine = true, shape = fieldShape, colors = fieldColors)
            OutlinedTextField(artist, { artist = it }, Modifier.fillMaxWidth().padding(vertical = 6.dp), label = { Text(stringResource(R.string.tag_artist)) },
                singleLine = true, shape = fieldShape, colors = fieldColors)
            OutlinedTextField(genre, { genre = it }, Modifier.fillMaxWidth().padding(vertical = 6.dp), label = { Text(stringResource(R.string.tag_genre)) },
                singleLine = true, shape = fieldShape, colors = fieldColors)
            OutlinedTextField(year, { year = it.filter { c -> c.isDigit() }.take(4) }, Modifier.fillMaxWidth().padding(vertical = 6.dp), label = { Text(stringResource(R.string.tag_year)) },
                singleLine = true, shape = fieldShape, colors = fieldColors, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(track, { track = it.filter { c -> c.isDigit() }.take(5) }, Modifier.fillMaxWidth().padding(vertical = 6.dp), label = { Text(stringResource(R.string.tag_track)) },
                supportingText = { Text(stringResource(R.string.tag_track_hint)) },
                singleLine = true, shape = fieldShape, colors = fieldColors, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))

            if (song != null && song.mime != "audio/mpeg") {
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.tag_note_app), color = DIM, fontSize = 13.sp)
            }
            Spacer(Modifier.height(18.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .bounceClick(enabled = !saving) { save() }
                    .clip(RoundedCornerShape(50))
                    .background(if (saving) DIM else ACCENT)
                    .padding(vertical = 15.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.save), color = if (IS_DARK) BG else Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Spacer(Modifier.height(28.dp + LocalMiniInset.current))
        }
    }
}

@Composable
private fun SmallPill(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .bounceClick(onClick = onClick)
            .clip(RoundedCornerShape(50))
            .background(DIM.copy(alpha = 0.18f))
            .padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}
