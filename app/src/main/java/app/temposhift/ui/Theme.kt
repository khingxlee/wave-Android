package app.temposhift.ui

import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.temposhift.player.PlayerState

/** The 8 accent colours offered for the dark and light themes. */
val PAL_DARK = listOf(
    Color(0xFF6FDC9C), Color(0xFF22C55E), Color(0xFF7FBFFF), Color(0xFF3B82F6),
    Color(0xFFA78BFA), Color(0xFFF472B6), Color(0xFFFB923C), Color(0xFFFACC15)
)
val PAL_LIGHT = listOf(
    Color(0xFF2E7D52), Color(0xFF16A34A), Color(0xFF2F80ED), Color(0xFF1D4ED8),
    Color(0xFF7C3AED), Color(0xFFDB2777), Color(0xFFEA580C), Color(0xFFCA8A04)
)

var BG by mutableStateOf(Color(0xFF0F1A15))
var PANEL by mutableStateOf(Color(0xFF16251E))
var ACCENT by mutableStateOf(PAL_DARK[0])
var TEXT by mutableStateOf(Color(0xFFE6F0EA))
var DIM by mutableStateOf(Color(0xFF86A394))
var IS_DARK by mutableStateOf(true)

/** Colour for icons drawn on top of the accent colour. */
val ON_ACCENT: Color get() = if (IS_DARK) BG else Color.White

/** Works out the colours for the chosen theme and applies them app-wide. Only changes what is different. */
fun applyTheme(mode: Int, systemDark: Boolean, darkGreen: Boolean, amoled: Boolean, accentDark: Int, accentLight: Int) {
    val dark = mode == 0 || (mode == 2 && systemDark)
    val bg: Color
    val panel: Color
    val text: Color
    val dim: Color
    val accent: Color
    if (!dark) {
        bg = Color(0xFFF3F7F4); panel = Color(0xFFFFFFFF); text = Color(0xFF16231B); dim = Color(0xFF5B6B62)
        accent = PAL_LIGHT[accentLight.coerceIn(0, PAL_LIGHT.lastIndex)]
    } else {
        accent = PAL_DARK[accentDark.coerceIn(0, PAL_DARK.lastIndex)]
        text = if (darkGreen) Color(0xFFE6F0EA) else Color(0xFFEDEEFA)
        dim = if (darkGreen) Color(0xFF86A394) else Color(0xFF9EA0C8)
        if (amoled) { bg = Color(0xFF000000); panel = Color(0xFF0C0C0C) }
        else if (darkGreen) { bg = Color(0xFF0F1A15); panel = Color(0xFF16251E) }
        else { bg = Color(0xFF0E0F26); panel = Color(0xFF181B44) }
    }
    if (BG != bg) BG = bg
    if (PANEL != panel) PANEL = panel
    if (TEXT != text) TEXT = text
    if (DIM != dim) DIM = dim
    if (ACCENT != accent) ACCENT = accent
    if (IS_DARK != dark) IS_DARK = dark
}

@Composable
fun AppTheme(state: PlayerState, content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    applyTheme(state.themeMode, systemDark, state.darkGreen, state.amoled, state.accentDarkIdx, state.accentLightIdx)

    // status / navigation bar icons follow the theme
    val activity = LocalContext.current as? ComponentActivity
    val dark = IS_DARK
    LaunchedEffect(dark) {
        val transparent = android.graphics.Color.TRANSPARENT
        activity?.enableEdgeToEdge(
            statusBarStyle = if (dark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent),
            navigationBarStyle = if (dark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent)
        )
    }

    val scheme = if (dark) {
        darkColorScheme(
            primary = ACCENT, onPrimary = Color(0xFF062014),
            background = BG, onBackground = TEXT,
            surface = BG, onSurface = TEXT, onSurfaceVariant = DIM,
            surfaceVariant = PANEL, secondaryContainer = PANEL
        )
    } else {
        lightColorScheme(
            primary = ACCENT, onPrimary = Color.White,
            background = BG, onBackground = TEXT,
            surface = BG, onSurface = TEXT, onSurfaceVariant = DIM,
            surfaceVariant = PANEL, secondaryContainer = PANEL
        )
    }
    MaterialTheme(
        colorScheme = scheme,
        shapes = Shapes(extraSmall = RoundedCornerShape(18.dp)),
        content = content
    )
}
