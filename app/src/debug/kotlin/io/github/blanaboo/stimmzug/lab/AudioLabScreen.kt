package io.github.blanaboo.stimmzug.lab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.blanaboo.stimmzug.ui.theme.StimmzugTheme
import java.util.Locale

const val TAG_LAB_INSTRUCTION = "lab_instruction"
const val TAG_LAB_STATUS = "lab_status"

/** What the buttons of the lab screen do. */
class LabActions(
    val start: (protocolKey: String) -> Unit = {},
    val requestPermission: () -> Unit = {},
    val proceed: () -> Unit = {},
    val repeat: () -> Unit = {},
    val cancel: () -> Unit = {}
)

@Composable
fun AudioLabScreen(state: LabUiState, micPermitted: Boolean, actions: LabActions, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Audio-Labor", style = MaterialTheme.typography.headlineMedium)
            Text(
                text = statusText(state),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.testTag(TAG_LAB_STATUS)
            )
            when (state.status) {
                LabStatus.IDLE, LabStatus.DONE, LabStatus.CANCELLED, LabStatus.FAILED ->
                    StartPanel(state, micPermitted, actions)

                LabStatus.WAITING, LabStatus.PREPARING, LabStatus.RECORDING ->
                    RunPanel(state, actions)
            }
            state.log.asReversed().forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun StartPanel(state: LabUiState, micPermitted: Boolean, actions: LabActions) {
    state.message?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
    state.sessionDir?.let { Text("Ordner: $it", style = MaterialTheme.typography.bodySmall) }
    Text(
        "Nimmt nacheinander mit jeder Audioquelle und jedem Mikrofon auf (Spike #32). " +
            "Das Handy steht dabei wie beim Üben auf dem Notenständer.",
        style = MaterialTheme.typography.bodyMedium
    )
    if (!micPermitted) {
        Button(onClick = actions.requestPermission, modifier = Modifier.fillMaxWidth()) {
            Text("Mikrofon erlauben")
        }
        return
    }
    Button(onClick = { actions.start("tuba") }, modifier = Modifier.fillMaxWidth()) { Text("Tuba-Messung starten") }
    OutlinedButton(onClick = { actions.start("selbsttest") }, modifier = Modifier.fillMaxWidth()) {
        Text("Selbsttest ohne Spieler")
    }
}

@Composable
private fun RunPanel(state: LabUiState, actions: LabActions) {
    LinearProgressIndicator(
        progress = { if (state.takeCount > 0) (state.takeNumber - 1f) / state.takeCount else 0f },
        modifier = Modifier.fillMaxWidth()
    )
    Text(state.takeTitle, style = MaterialTheme.typography.titleMedium)
    Text(state.noteText, style = MaterialTheme.typography.titleLarge)
    val color = if (state.playerPlays) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
    Text(
        text = state.instruction,
        style = MaterialTheme.typography.displaySmall,
        fontWeight = FontWeight.Bold,
        color = color,
        modifier = Modifier.testTag(TAG_LAB_INSTRUCTION)
    )
    if (state.status != LabStatus.WAITING) {
        Text("noch ${state.remainingSec} s", style = MaterialTheme.typography.headlineSmall)
    }
    state.live?.let { Text(liveText(it), style = MaterialTheme.typography.titleMedium) }
    if (state.status == LabStatus.WAITING) {
        state.message?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
        Button(onClick = actions.proceed, modifier = Modifier.fillMaxWidth()) { Text("Weiter") }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = actions.repeat) { Text("Nochmal") }
        OutlinedButton(onClick = actions.cancel) { Text("Abbrechen") }
    }
    Text(
        "„Nochmal“ wiederholt die laufende Aufnahme, im Countdown die vorige.",
        style = MaterialTheme.typography.bodySmall
    )
}

private fun statusText(state: LabUiState): String = when (state.status) {
    LabStatus.IDLE -> "Bereit"

    LabStatus.WAITING, LabStatus.PREPARING, LabStatus.RECORDING ->
        "${state.protocolTitle}: Aufnahme ${state.takeNumber} von ${state.takeCount}"

    LabStatus.DONE -> "${state.protocolTitle}: fertig"

    LabStatus.CANCELLED -> "${state.protocolTitle}: abgebrochen"

    LabStatus.FAILED -> "Fehler"
}

private fun liveText(r: LiveReading): String {
    val level = String.format(Locale.GERMANY, "%.0f dBFS", r.levelDb)
    val note = r.note ?: return "Pegel $level · kein Ton erkannt"
    val cents = r.cents?.let { String.format(Locale.GERMANY, " %+.0f ct", it) }.orEmpty()
    val clarity = String.format(Locale.GERMANY, "%.2f", r.clarity)
    return "Pegel $level · $note$cents · Klarheit $clarity"
}

@Preview(showBackground = true)
@Composable
private fun AudioLabScreenRecordingPreview() {
    StimmzugTheme(darkTheme = true) {
        AudioLabScreen(
            state = LabUiState(
                status = LabStatus.RECORDING,
                protocolTitle = "Tuba-Messung",
                takeNumber = 3,
                takeCount = 23,
                takeTitle = "T03 · VOICE_RECOGNITION · Mikro unten",
                instruction = "Kontra-F spielen und halten",
                noteText = "Kontra-F (43,65 Hz)",
                playerPlays = true,
                remainingSec = 4,
                live = LiveReading(-24.0, "Kontra-F", -3.0, 0.93)
            ),
            micPermitted = true,
            actions = LabActions()
        )
    }
}
