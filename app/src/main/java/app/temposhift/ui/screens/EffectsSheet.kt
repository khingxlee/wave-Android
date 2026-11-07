package app.temposhift.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.temposhift.R
import app.temposhift.service.Fx
import app.temposhift.ui.ACCENT
import app.temposhift.ui.BG
import app.temposhift.ui.DIM
import app.temposhift.ui.PANEL
import app.temposhift.ui.TEXT

/** The listening effects: each has a switch and a strength. Changes fade in and out smoothly, so nothing clicks. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EffectsSheet(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val on = remember { Fx.load(ctx); mutableStateListOf<Boolean>().also { l -> for (i in 0 until Fx.COUNT) l.add(Fx.isOn(i)) } }
    val amount = remember { mutableStateListOf<Float>().also { l -> for (i in 0 until Fx.COUNT) l.add(Fx.amountOf(i)) } }

    val names = listOf(R.string.fx_underwater, R.string.fx_muffle, R.string.fx_reverb, R.string.fx_echo, R.string.fx_8d)
    val notes = listOf(R.string.fx_underwater_sub, R.string.fx_muffle_sub, R.string.fx_reverb_sub, R.string.fx_echo_sub, R.string.fx_8d_sub)
    val sliderColors = SliderDefaults.colors(thumbColor = ACCENT, activeTrackColor = ACCENT, inactiveTrackColor = BG)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = PANEL, contentColor = TEXT) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text(stringResource(R.string.fx_title), fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            // the order of the effects in Fx: underwater, muffle, reverb, echo, 8D
            val order = listOf(Fx.UNDERWATER, Fx.MUFFLE, Fx.REVERB, Fx.ECHO, Fx.EIGHT_D)
            for (row in order.indices) {
                val i = order[row]
                Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(names[row]), fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                        Text(stringResource(notes[row]), color = DIM, fontSize = 13.sp)
                    }
                    Switch(
                        checked = on[i],
                        onCheckedChange = { on[i] = it; Fx.setOn(i, it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = BG, checkedTrackColor = ACCENT, uncheckedThumbColor = DIM, uncheckedTrackColor = BG)
                    )
                }
                AnimatedVisibility(visible = on[i], enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Slider(
                            value = amount[i],
                            onValueChange = { amount[i] = it; Fx.setAmount(i, it) },
                            valueRange = 0.05f..1f,
                            modifier = Modifier.weight(1f),
                            colors = sliderColors
                        )
                        Text(
                            stringResource(R.string.fx_strength, Math.round(amount[i] * 100f)),
                            color = ACCENT, fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        }
    }
}
