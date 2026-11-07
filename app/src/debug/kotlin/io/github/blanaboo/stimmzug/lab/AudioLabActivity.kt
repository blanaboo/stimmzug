package io.github.blanaboo.stimmzug.lab

import android.Manifest
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import io.github.blanaboo.stimmzug.ui.theme.StimmzugTheme

/**
 * Audio lab for the audio spike (#32), debug builds only, with its own launcher icon "Stimmzug Labor".
 *
 * Start a protocol from adb (after `pm grant <package> android.permission.RECORD_AUDIO`):
 * `am start -n <package>/io.github.blanaboo.stimmzug.lab.AudioLabActivity --es protocol selbsttest`
 */
class AudioLabActivity : ComponentActivity() {

    private val viewModel: LabViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // The phone stands on the music stand during a session; the screen must not go dark.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val autostart = if (savedInstanceState == null) intent.getStringExtra(EXTRA_PROTOCOL) else null
        setContent {
            StimmzugTheme {
                var permitted by remember { mutableStateOf(viewModel.hasMicPermission()) }
                val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
                    permitted = it
                    // A protocol requested via adb starts as soon as the player allows the microphone.
                    if (it) autostart?.let(viewModel::protocol)?.let(viewModel::start)
                }
                LaunchedEffect(Unit) {
                    if (!permitted) request.launch(Manifest.permission.RECORD_AUDIO)
                    autostart?.let(viewModel::protocol)?.let { if (permitted) viewModel.start(it) }
                }
                AudioLabScreen(
                    state = viewModel.state,
                    micPermitted = permitted,
                    actions = LabActions(
                        start = { key -> viewModel.protocol(key)?.let(viewModel::start) },
                        requestPermission = { request.launch(Manifest.permission.RECORD_AUDIO) },
                        proceed = viewModel::proceed,
                        repeat = viewModel::repeatTake,
                        cancel = viewModel::cancel
                    ),
                    modifier = Modifier.safeDrawingPadding().semantics { testTagsAsResourceId = true }
                )
            }
        }
    }

    companion object {
        /** Protocol to start right away: "selbsttest", "tuba" or "drone". */
        const val EXTRA_PROTOCOL = "protocol"
    }
}
