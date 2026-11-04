package io.github.blanaboo.stimmzug.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.blanaboo.stimmzug.R
import io.github.blanaboo.stimmzug.dsp.music.NoteMath
import io.github.blanaboo.stimmzug.dsp.music.NoteNames
import io.github.blanaboo.stimmzug.dsp.music.NoteNaming
import io.github.blanaboo.stimmzug.ui.theme.StimmzugTheme

/** MIDI number of Kontra-B (B♭1), the home note of the BBb tuba. */
private const val KONTRA_B = 34

/** Placeholder start screen for M0: proves the app/core wiring and the theme. */
@Composable
fun HomeScreen(versionName: String, modifier: Modifier = Modifier) {
    val frequency = String.format(LocalLocale.current.platformLocale, "%.2f", NoteMath.frequency(KONTRA_B))
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(text = stringResource(R.string.tagline), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(32.dp))
            Text(
                text = NoteNames.pitchClass(KONTRA_B, NoteNaming.GERMAN),
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = stringResource(R.string.reference_note, NoteNames.germanOctaveName(KONTRA_B), frequency),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(48.dp))
            Text(
                text = stringResource(R.string.status_in_development, versionName),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    StimmzugTheme(darkTheme = true) {
        HomeScreen(versionName = "0.0.1-dev")
    }
}
