package app.temposhift.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.temposhift.R
import app.temposhift.data.CrashReport
import app.temposhift.player.PlayerState
import app.temposhift.ui.ACCENT
import app.temposhift.ui.BG
import app.temposhift.ui.DIM
import app.temposhift.ui.IS_DARK
import app.temposhift.ui.Line
import app.temposhift.ui.LocalMiniInset
import app.temposhift.ui.PAL_DARK
import app.temposhift.ui.PAL_LIGHT
import app.temposhift.ui.PANEL
import app.temposhift.ui.TEXT
import app.temposhift.ui.bounceClick

private fun labelOf(options: List<Pair<Int, String>>, value: Int): String =
    options.firstOrNull { it.first == value }?.second ?: ""

@Composable
fun SettingsScreen(state: PlayerState, modifier: Modifier, onScan: () -> Unit, onRefresh: () -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var page by remember { mutableIntStateOf(0) }
    var dialog by remember { mutableStateOf("") }
    BackHandler(enabled = page == 1) { page = 0 }

    fun toast(res: Int) { Toast.makeText(ctx, res, Toast.LENGTH_SHORT).show() }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri != null) {
            try {
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(state.exportJson().toByteArray()) }
                toast(R.string.st_export_done)
            } catch (_: Exception) { toast(R.string.st_import_failed) }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            try {
                val text = ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                state.importJson(text)
                toast(R.string.st_import_done)
            } catch (_: Exception) { toast(R.string.st_import_failed) }
        }
    }

    // ---- the lists behind each dialog ----
    val octaves = listOf(1 to R.string.oct_1, 2 to R.string.oct_2, 4 to R.string.oct_4).map { it.first to stringResource(it.second) }
    val minSpeeds = listOf(15, 20, 25, 30, 40, 50).map { it to "$it%" }
    val maxSpeeds = listOf(200, 250, 300, 400, 500).map { it to "$it%" }
    val seeks = listOf(1 to R.string.sec_1, 2 to R.string.sec_2, 5 to R.string.sec_5, 10 to R.string.sec_10, 15 to R.string.sec_15, 30 to R.string.sec_30)
        .map { it.first to stringResource(it.second) }
    val trackModes = listOf(0 to R.string.ot_keep, 1 to R.string.ot_reset, 2 to R.string.ot_save).map { it.first to stringResource(it.second) }
    val lyricStyles = listOf(0 to R.string.ls_default, 1 to R.string.ls_sweep).map { it.first to stringResource(it.second) }
    val crash = remember { CrashReport.text(ctx) }
    fun mail(subject: String, body: String) {
        try {
            val i = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
                .putExtra(Intent.EXTRA_EMAIL, arrayOf(ctx.getString(R.string.contact_email)))
                .putExtra(Intent.EXTRA_SUBJECT, subject)
                .putExtra(Intent.EXTRA_TEXT, body)
            ctx.startActivity(i)
        } catch (_: Exception) { toast(R.string.no_mail_app) }
    }
    val waves = listOf(0 to R.string.wf_normal, 1 to R.string.wf_animated).map { it.first to stringResource(it.second) }
    val themes = listOf(0 to R.string.th_dark, 1 to R.string.th_light, 2 to R.string.th_system).map { it.first to stringResource(it.second) }
    val minLens = listOf(0 to R.string.opt_none, 15 to R.string.opt_15s, 30 to R.string.opt_30s, 60 to R.string.opt_60s)
        .map { it.first to stringResource(it.second) }

    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp).height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (page == 1) page = 0 else onBack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
            }
            Text(
                stringResource(if (page == 0) R.string.settings else R.string.st_pitch_tempo),
                fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp)
            )
        }
        Line()
        AnimatedContent(
            targetState = page,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            transitionSpec = {
                if (targetState > initialState) {
                    (slideInHorizontally(spring(dampingRatio = 0.9f, stiffness = 400f)) { it / 3 } + fadeIn(tween(200))) togetherWith
                        (slideOutHorizontally(tween(220)) { -it / 4 } + fadeOut(tween(150)))
                } else {
                    (slideInHorizontally(spring(dampingRatio = 0.9f, stiffness = 400f)) { -it / 4 } + fadeIn(tween(200))) togetherWith
                        (slideOutHorizontally(tween(220)) { it / 3 } + fadeOut(tween(150)))
                }
            },
            label = "settingsPages"
        ) { p ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = LocalMiniInset.current)) {
                if (p == 0) {
                    SectionTitle(R.string.st_editor_controls)
                    NavRow(stringResource(R.string.st_pitch_tempo), stringResource(R.string.st_pitch_tempo_sub)) { page = 1 }
                    CheckRow(stringResource(R.string.st_plusminus), "", state.plusMinus) { state.updatePlusMinus(it) }
                    NavRow(stringResource(R.string.st_seek), labelOf(seeks, state.seekSeconds)) { dialog = "seek" }
                    NavRow(stringResource(R.string.st_on_track), labelOf(trackModes, state.onTrackChange)) { dialog = "track" }
                    NavRow(stringResource(R.string.st_waveform), labelOf(waves, if (state.waveAnimated) 1 else 0)) { dialog = "wave" }
                    NavRow(stringResource(R.string.st_lyrics_style), labelOf(lyricStyles, state.lyricsStyle)) { dialog = "lyricStyle" }
                    ThinLine()

                    SectionTitle(R.string.st_colors)
                    NavRow(stringResource(R.string.st_theme), labelOf(themes, state.themeMode)) { dialog = "theme" }
                    CheckRow(stringResource(R.string.st_dark_green), stringResource(R.string.st_dark_green_sub), state.darkGreen) { state.updateDarkGreen(it) }
                    CheckRow(stringResource(R.string.st_amoled_black), stringResource(R.string.st_amoled_black_sub), state.amoled) { state.updateAmoled(it) }
                    SwatchRow(stringResource(R.string.st_accent_light), PAL_LIGHT[state.accentLightIdx.coerceIn(0, PAL_LIGHT.lastIndex)]) { dialog = "accentLight" }
                    SwatchRow(stringResource(R.string.st_accent_dark), PAL_DARK[state.accentDarkIdx.coerceIn(0, PAL_DARK.lastIndex)]) { dialog = "accentDark" }
                    ThinLine()

                    SectionTitle(R.string.st_library)
                    NavRow(stringResource(R.string.st_min_length), labelOf(minLens, state.minDurationSec)) { dialog = "minLen" }
                    CardRow(stringResource(R.string.st_scan), stringResource(R.string.st_scan_sub), onScan)
                    CardRow(stringResource(R.string.st_refresh), stringResource(R.string.st_refresh_sub), onRefresh)
                    ThinLine()

                    SectionTitle(R.string.st_background)
                    NavRow(stringResource(R.string.st_battery), stringResource(R.string.st_battery_sub), chevron = false) {
                        try { ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) } catch (_: Exception) { }
                    }
                    if (Build.VERSION.SDK_INT >= 33) {
                        NavRow(stringResource(R.string.st_language), stringResource(R.string.st_language_sub), chevron = false) {
                            try { ctx.startActivity(Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.parse("package:" + ctx.packageName))) } catch (_: Exception) { }
                        }
                    }
                    ThinLine()

                    SectionTitle(R.string.st_backup)
                    NavRow(stringResource(R.string.st_export), stringResource(R.string.st_export_sub), chevron = false) { exportLauncher.launch("wave-backup.json") }
                    NavRow(stringResource(R.string.st_import), stringResource(R.string.st_import_sub), chevron = false) { importLauncher.launch(arrayOf("application/json", "*/*")) }
                    ThinLine()

                    SectionTitle(R.string.st_support)
                    if (crash != null) {
                        NavRow(stringResource(R.string.st_crash_send), stringResource(R.string.st_crash_send_sub), chevron = false) {
                            mail("Wave crash report", crash)
                        }
                    }
                    NavRow(stringResource(R.string.st_contact), stringResource(R.string.contact_email), chevron = false) { mail("Wave", "") }
                } else {
                    Spacer(Modifier.height(8.dp))
                    NavRow(stringResource(R.string.st_pitch_range), labelOf(octaves, state.pitchOctaves)) { dialog = "pitchRange" }
                    NavRow(stringResource(R.string.st_min_speed), state.tempoMin.toString() + "%") { dialog = "minSpeed" }
                    NavRow(stringResource(R.string.st_max_speed), state.tempoMax.toString() + "%") { dialog = "maxSpeed" }
                    CheckRow(stringResource(R.string.st_snap_pitch), stringResource(R.string.st_snap_pitch_sub), state.snapPitch) { state.updateSnapPitch(it) }
                    CheckRow(stringResource(R.string.st_snap_speed), stringResource(R.string.st_snap_speed_sub), state.snapSpeed) { state.updateSnapSpeed(it) }
                    CheckRow(stringResource(R.string.st_join), stringResource(R.string.st_join_sub), state.joinPitchTempo) { state.updateJoin(it) }
                    CheckRow(stringResource(R.string.st_persist), stringResource(R.string.st_persist_sub), state.persistAdjust) { state.updatePersist(it) }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }

    // ---- dialogs ----
    when (dialog) {
        "pitchRange" -> ChoiceDialog(stringResource(R.string.st_pitch_range), octaves, state.pitchOctaves, { state.updatePitchRange(it) }) { dialog = "" }
        "minSpeed" -> ChoiceDialog(stringResource(R.string.st_min_speed), minSpeeds, state.tempoMin, { state.updateMinSpeed(it) }) { dialog = "" }
        "maxSpeed" -> ChoiceDialog(stringResource(R.string.st_max_speed), maxSpeeds, state.tempoMax, { state.updateMaxSpeed(it) }) { dialog = "" }
        "seek" -> ChoiceDialog(stringResource(R.string.st_seek), seeks, state.seekSeconds, { state.updateSeek(it) }) { dialog = "" }
        "track" -> ChoiceDialog(stringResource(R.string.st_on_track), trackModes, state.onTrackChange, { state.updateOnTrack(it) }) { dialog = "" }
        "lyricStyle" -> ChoiceDialog(stringResource(R.string.st_lyrics_style), lyricStyles, state.lyricsStyle, { state.updateLyricsStyle(it) }) { dialog = "" }
        "wave" -> ChoiceDialog(stringResource(R.string.st_waveform), waves, if (state.waveAnimated) 1 else 0, { state.updateWaveAnimated(it == 1) }) { dialog = "" }
        "theme" -> ChoiceDialog(stringResource(R.string.st_theme), themes, state.themeMode, { state.updateThemeMode(it) }) { dialog = "" }
        "minLen" -> ChoiceDialog(stringResource(R.string.st_min_length), minLens, state.minDurationSec, { state.setMinDuration(it) }) { dialog = "" }
        "accentLight" -> ColorDialog(stringResource(R.string.st_accent_light), PAL_LIGHT, state.accentLightIdx, { state.updateAccentLight(it) }) { dialog = "" }
        "accentDark" -> ColorDialog(stringResource(R.string.st_accent_dark), PAL_DARK, state.accentDarkIdx, { state.updateAccentDark(it) }) { dialog = "" }
        else -> { }
    }
}

@Composable
private fun SectionTitle(res: Int) {
    Text(
        stringResource(res).uppercase(),
        color = ACCENT, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 22.dp, bottom = 6.dp)
    )
}

@Composable
private fun ThinLine() {
    Box(Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) { Line() }
}

@Composable
private fun NavRow(title: String, subtitle: String, chevron: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.Medium)
            if (subtitle.isNotBlank()) Text(subtitle, color = DIM, fontSize = 14.sp)
        }
        if (chevron) Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = DIM)
    }
}

@Composable
private fun CheckRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.Medium)
            if (subtitle.isNotBlank()) Text(subtitle, color = DIM, fontSize = 14.sp)
        }
        Spacer(Modifier.width(12.dp))
        Checkbox(
            checked = checked,
            onCheckedChange = onChange,
            colors = CheckboxDefaults.colors(checkedColor = ACCENT, uncheckedColor = DIM, checkmarkColor = if (IS_DARK) BG else Color.White)
        )
    }
}

@Composable
private fun SwatchRow(title: String, color: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(color).border(2.dp, DIM.copy(alpha = 0.6f), RoundedCornerShape(12.dp)))
        Spacer(Modifier.width(12.dp))
        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = DIM)
    }
}

/** Rounded outlined card used for Scan and Refresh library. */
@Composable
private fun CardRow(title: String, subtitle: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 6.dp)
            .bounceClick(onClick = onClick)
            .clip(shape)
            .background(PANEL)
            .border(1.dp, DIM.copy(alpha = 0.3f), shape)
            .padding(horizontal = 24.dp, vertical = 18.dp)
    ) {
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = DIM, fontSize = 15.sp)
    }
}
