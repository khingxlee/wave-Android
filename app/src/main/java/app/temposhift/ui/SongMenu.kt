package app.temposhift.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.temposhift.R
import app.temposhift.data.Song
import app.temposhift.player.PlayerState
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Opens the phone's share sheet for the song file. */
fun shareSong(ctx: Context, song: Song) {
    val send = Intent(Intent.ACTION_SEND)
        .setType(song.mime.ifBlank { "audio/*" })
        .putExtra(Intent.EXTRA_STREAM, song.uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try { ctx.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) { }
}

/** Shared by every screen: opens the same song menu and its dialogs (playlist, tags, details, delete). */
class SongMenuHost(
    val state: PlayerState,
    val openAlbum: (String) -> Unit,
    val openArtist: (String) -> Unit,
    val openTagEditor: (Song) -> Unit
) {
    var playlistFor by mutableStateOf<Song?>(null)
    var detailsFor by mutableStateOf<Song?>(null)
    var deleteFor by mutableStateOf<Song?>(null)
}

@Composable
fun SongMenu(song: Song, host: SongMenuHost, expanded: Boolean, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(text = { Text(stringResource(R.string.menu_play_next)) }, onClick = { onDismiss(); host.state.playNext(song) })
        DropdownMenuItem(text = { Text(stringResource(R.string.menu_add_queue)) }, onClick = { onDismiss(); host.state.addToQueue(song) })
        DropdownMenuItem(text = { Text(stringResource(R.string.menu_add_playlist)) }, onClick = { onDismiss(); host.playlistFor = song })
        DropdownMenuItem(text = { Text(stringResource(R.string.menu_go_album)) }, onClick = { onDismiss(); host.openAlbum(song.album) })
        DropdownMenuItem(text = { Text(stringResource(R.string.menu_go_artist)) }, onClick = { onDismiss(); host.openArtist(song.artist) })
        DropdownMenuItem(
            text = { Text(stringResource(R.string.menu_share)) },
            onClick = {
                onDismiss()
                val send = Intent(Intent.ACTION_SEND)
                    .setType(song.mime.ifBlank { "audio/*" })
                    .putExtra(Intent.EXTRA_STREAM, song.uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                try { ctx.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) { }
            }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.menu_tag_editor)) },
            enabled = song.id >= 0,
            onClick = { onDismiss(); host.openTagEditor(song) }
        )
        DropdownMenuItem(text = { Text(stringResource(R.string.menu_details)) }, onClick = { onDismiss(); host.detailsFor = song })
        DropdownMenuItem(
            text = { Text(stringResource(R.string.menu_delete)) },
            enabled = song.id >= 0,
            onClick = { onDismiss(); host.deleteFor = song }
        )
    }
}

/** Renders whichever dialog the song menu asked for. Place it once, high in the app. */
@Composable
fun SongDialogs(host: SongMenuHost) {
    val ctx = LocalContext.current
    val state = host.state
    val scope = rememberCoroutineScope()
    var pendingDelete by remember { mutableStateOf<Song?>(null) }

    fun toast(res: Int) { Toast.makeText(ctx, res, Toast.LENGTH_SHORT).show() }

    // ---- delete from device ----
    val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val s = pendingDelete
        pendingDelete = null
        if (s != null && result.resultCode == Activity.RESULT_OK) {
            state.removeSong(s)
            toast(R.string.deleted_toast)
        }
    }

    fun deleteSong(song: Song) {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                pendingDelete = song
                val pi = MediaStore.createDeleteRequest(ctx.contentResolver, listOf(song.uri))
                deleteLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
            } catch (_: Exception) {
                pendingDelete = null
                toast(R.string.delete_failed)
            }
        } else {
            try {
                if (ctx.contentResolver.delete(song.uri, null, null) > 0) {
                    state.removeSong(song)
                    toast(R.string.deleted_toast)
                } else {
                    toast(R.string.delete_failed)
                }
            } catch (_: Exception) {
                toast(R.string.delete_failed)
            }
        }
    }

    // ---- the dialogs ----
    host.deleteFor?.let { song ->
        AlertDialog(
            onDismissRequest = { host.deleteFor = null },
            containerColor = PANEL,
            title = { Text(stringResource(R.string.delete_title)) },
            text = { Text(stringResource(R.string.delete_message, song.title)) },
            confirmButton = { TextButton(onClick = { host.deleteFor = null; deleteSong(song) }) { Text(stringResource(R.string.delete), color = ACCENT) } },
            dismissButton = { TextButton(onClick = { host.deleteFor = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    host.detailsFor?.let { song ->
        val sizeText = if (song.sizeBytes > 0) String.format(Locale.US, "%.1f MB", song.sizeBytes / 1048576.0) else "-"
        val dateText = if (song.modified > 0) DateFormat.getDateTimeInstance().format(Date(song.modified * 1000L)) else "-"
        AlertDialog(
            onDismissRequest = { host.detailsFor = null },
            containerColor = PANEL,
            title = { Text(song.title) },
            text = {
                Column {
                    DetailLine(R.string.tag_artist, song.artist)
                    DetailLine(R.string.tag_album, song.album)
                    DetailLine(R.string.detail_duration, fmt(song.durationMs))
                    DetailLine(R.string.detail_format, song.mime.ifBlank { "-" })
                    DetailLine(R.string.detail_size, sizeText)
                    DetailLine(R.string.detail_modified, dateText)
                    DetailLine(R.string.detail_path, song.path.ifBlank { "-" })
                }
            },
            confirmButton = { TextButton(onClick = { host.detailsFor = null }) { Text(stringResource(R.string.close), color = ACCENT) } }
        )
    }

    host.playlistFor?.let { song ->
        var creating by remember { mutableStateOf(false) }
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { host.playlistFor = null },
            containerColor = PANEL,
            title = { Text(stringResource(R.string.add_to_playlist)) },
            text = {
                Column {
                    if (creating) {
                        TextField(value = name, onValueChange = { name = it }, singleLine = true, placeholder = { Text(stringResource(R.string.playlist_name)) })
                    } else if (state.playlists.isEmpty()) {
                        Text(stringResource(R.string.no_playlists), color = DIM)
                    } else {
                        state.playlists.forEach { p ->
                            Text(
                                p.name,
                                fontSize = 16.sp,
                                modifier = Modifier.fillMaxWidth().clickable { state.addToPlaylist(p, song); host.playlistFor = null }.padding(vertical = 12.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                if (creating) {
                    TextButton(onClick = {
                        state.createPlaylist(name)
                        state.playlists.firstOrNull { it.name == name.trim() }?.let { state.addToPlaylist(it, song) }
                        host.playlistFor = null
                    }) { Text(stringResource(R.string.create), color = ACCENT) }
                } else {
                    TextButton(onClick = { creating = true }) { Text(stringResource(R.string.new_playlist), color = ACCENT) }
                }
            },
            dismissButton = { TextButton(onClick = { host.playlistFor = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

@Composable
private fun DetailLine(label: Int, value: String) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(stringResource(label), color = DIM, fontSize = 12.sp)
        Text(value, fontSize = 15.sp)
    }
}
