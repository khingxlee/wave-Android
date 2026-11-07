package app.temposhift.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.temposhift.R
import app.temposhift.service.EqEngine
import app.temposhift.ui.ACCENT
import app.temposhift.ui.BG
import app.temposhift.ui.DIM
import app.temposhift.ui.PANEL
import app.temposhift.ui.TEXT
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerSheet(onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val available = remember { EqEngine.available }
    val bands = remember { EqEngine.bandCount() }
    val range = remember { EqEngine.levelRange() }
    val presets = remember { EqEngine.presetNames() }
    val levels = remember { mutableStateListOf<Int>().also { l -> for (i in 0 until bands) l.add(EqEngine.level(i)) } }
    var enabled by remember { mutableStateOf(EqEngine.enabled) }
    var bass by remember { mutableFloatStateOf(EqEngine.bassStrength.toFloat()) }
    var loud by remember { mutableFloatStateOf(EqEngine.loudnessMb.toFloat()) }
    var preset by remember { mutableIntStateOf(-1) }

    val sliderColors = SliderDefaults.colors(thumbColor = ACCENT, activeTrackColor = ACCENT, inactiveTrackColor = BG)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = PANEL, contentColor = TEXT) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.eq_title), fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Switch(
                    checked = enabled,
                    onCheckedChange = { enabled = it; EqEngine.setEnabled(it) },
                    enabled = available,
                    colors = SwitchDefaults.colors(checkedThumbColor = BG, checkedTrackColor = ACCENT, uncheckedThumbColor = DIM, uncheckedTrackColor = BG)
                )
            }
            if (!available) {
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.eq_unavailable), color = DIM)
                return@Column
            }

            Spacer(Modifier.height(14.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = preset == -2,
                    onClick = {
                        for (b in 0 until bands) { EqEngine.setLevel(b, 0); levels[b] = 0 }
                        preset = -2
                    },
                    label = { Text(stringResource(R.string.eq_flat)) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = ACCENT, selectedLabelColor = BG)
                )
                presets.forEachIndexed { i, name ->
                    FilterChip(
                        selected = preset == i,
                        onClick = {
                            EqEngine.usePreset(i)
                            for (b in 0 until bands) levels[b] = EqEngine.level(b)
                            preset = i
                        },
                        label = { Text(name) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = ACCENT, selectedLabelColor = BG)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            for (b in 0 until bands) {
                val hz = EqEngine.centerFreqHz(b)
                val label = if (hz >= 1000) String.format(Locale.US, "%.1f kHz", hz / 1000.0) else "$hz Hz"
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, color = DIM, fontSize = 13.sp, modifier = Modifier.width(66.dp))
                    Slider(
                        value = levels[b].toFloat(),
                        onValueChange = { v ->
                            levels[b] = v.toInt()
                            EqEngine.setLevel(b, v.toInt())
                            preset = -1
                        },
                        valueRange = range.first.toFloat()..range.last.toFloat(),
                        modifier = Modifier.weight(1f),
                        colors = sliderColors
                    )
                    Text(
                        String.format(Locale.US, "%+.1f dB", levels[b] / 100f),
                        color = ACCENT, fontSize = 13.sp, textAlign = TextAlign.End, modifier = Modifier.width(66.dp)
                    )
                }
            }

            if (EqEngine.bassSupported) {
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.eq_bass), fontWeight = FontWeight.SemiBold)
                Slider(
                    value = bass,
                    onValueChange = { bass = it; EqEngine.setBass(it.toInt()) },
                    valueRange = 0f..1000f,
                    colors = sliderColors
                )
            }
            if (EqEngine.loudnessSupported) {
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.eq_loudness), fontWeight = FontWeight.SemiBold)
                Slider(
                    value = loud,
                    onValueChange = { loud = it; EqEngine.setLoudness(it.toInt()) },
                    valueRange = 0f..1500f,
                    colors = sliderColors
                )
            }
        }
    }
}
