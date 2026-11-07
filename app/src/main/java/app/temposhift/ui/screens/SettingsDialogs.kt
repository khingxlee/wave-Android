package app.temposhift.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.temposhift.R
import app.temposhift.ui.ACCENT
import app.temposhift.ui.DIM
import app.temposhift.ui.PANEL
import app.temposhift.ui.TEXT
import app.temposhift.ui.bounceClick

/** A compact, rounded option picker: title, radio rows, Cancel. */
@Composable
fun ChoiceDialog(
    title: String,
    options: List<Pair<Int, String>>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = PANEL,
            contentColor = TEXT,
            modifier = Modifier.fillMaxWidth(0.82f).widthIn(max = 330.dp)
        ) {
            Column(Modifier.padding(start = 22.dp, end = 16.dp, top = 22.dp, bottom = 8.dp)) {
                Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                options.forEach { option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onSelect(option.first); onDismiss() }
                            .heightIn(min = 46.dp)
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = option.first == selected,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(selectedColor = ACCENT, unselectedColor = DIM)
                        )
                        Spacer(Modifier.width(14.dp))
                        Text(option.second, fontSize = 16.sp)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = ACCENT, fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}

/** Eight colour dots in two rows; the chosen one has a ring. */
@Composable
fun ColorDialog(
    title: String,
    palette: List<Color>,
    selected: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = PANEL,
            contentColor = TEXT,
            modifier = Modifier.fillMaxWidth(0.82f).widthIn(max = 330.dp)
        ) {
            Column(Modifier.padding(start = 22.dp, end = 16.dp, top = 22.dp, bottom = 8.dp)) {
                Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                for (row in 0 until 2) {
                    Row(Modifier.fillMaxWidth().padding(end = 6.dp, bottom = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        for (col in 0 until 4) {
                            val i = row * 4 + col
                            if (i < palette.size) {
                                Box(
                                    Modifier
                                        .size(40.dp)
                                        .bounceClick { onPick(i); onDismiss() }
                                        .clip(CircleShape)
                                        .background(palette[i])
                                        .border(if (i == selected) 3.dp else 0.dp, TEXT, CircleShape)
                                )
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = ACCENT, fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}
