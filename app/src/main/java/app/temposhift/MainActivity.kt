package app.temposhift

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import app.temposhift.player.PlayerState
import app.temposhift.ui.AppTheme
import app.temposhift.ui.BG
import app.temposhift.ui.screens.TempoShiftApp

class MainActivity : ComponentActivity() {
    companion object {
        const val ACTION_OPEN_PLAYER = "app.temposhift.OPEN_PLAYER"
    }

    private val state: PlayerState by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        state.connect()
        setContent {
            AppTheme(state) {
                Surface(modifier = Modifier.fillMaxSize(), color = BG) {
                    TempoShiftApp(state)
                }
            }
        }
        if (savedInstanceState == null) handleIntent(intent)
        useHighestRefreshRate()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** "Open with Wave" from a file manager or another app. */
    private fun handleIntent(intent: Intent?) {
        if (intent?.action == ACTION_OPEN_PLAYER) {
            state.openPlayerRequest++
        } else if (intent?.action == Intent.ACTION_VIEW) {
            intent.data?.let { state.openUri(it) }
        }
    }

    /** Asks Android for the phone's highest refresh rate (90 / 120 Hz) so scrolling and animations feel fluid. */
    @Suppress("DEPRECATION")
    private fun useHighestRefreshRate() {
        try {
            val display = windowManager.defaultDisplay
            val current = display.mode
            val best = display.supportedModes
                .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
                .maxByOrNull { it.refreshRate }
            if (best != null) {
                val lp = window.attributes
                lp.preferredDisplayModeId = best.modeId
                window.attributes = lp
            }
        } catch (_: Exception) { }
    }
}
