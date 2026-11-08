package io.github.blanaboo.stimmzug.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.blanaboo.stimmzug.R
import io.github.blanaboo.stimmzug.data.profile.ProfileState
import io.github.blanaboo.stimmzug.data.settings.PracticeSettings
import io.github.blanaboo.stimmzug.dsp.music.InstrumentProfile
import io.github.blanaboo.stimmzug.dsp.music.NoteMath
import io.github.blanaboo.stimmzug.dsp.music.NoteNames
import io.github.blanaboo.stimmzug.dsp.music.NoteNaming
import io.github.blanaboo.stimmzug.ui.profile.profileSummary
import io.github.blanaboo.stimmzug.ui.theme.StimmzugTheme

/** MIDI number of Kontra-B (B♭1), the home note of the BBb tuba. */
private const val KONTRA_B = 34

const val TAG_REFERENCE_NOTE = "home_reference_note"
const val TAG_HOME_PROFILES = "home_profiles"
const val TAG_HOME_SETTINGS = "home_settings"

/** What the start screen can do. */
class HomeActions(
    val openProfiles: () -> Unit = {},
    val openSettings: () -> Unit = {},
    val createProfile: () -> Unit = {},
    val useDefault: () -> Unit = {}
)

/** Start screen: the reference note at [a4Hz] and the active instrument (#40). [profiles] is `null` while loading. */
@Composable
fun HomeScreen(
    versionName: String,
    modifier: Modifier = Modifier,
    profiles: ProfileState? = null,
    a4Hz: Int = PracticeSettings.DEFAULT_A4_HZ,
    actions: HomeActions = HomeActions()
) {
    val frequency = String.format(
        LocalLocale.current.platformLocale,
        "%.2f",
        NoteMath.frequency(KONTRA_B, a4Hz.toDouble())
    )
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag(TAG_REFERENCE_NOTE)
            )
            Spacer(Modifier.height(32.dp))
            if (profiles != null) InstrumentCard(profiles.active, actions)
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = actions.openSettings,
                modifier = Modifier.fillMaxWidth().testTag(TAG_HOME_SETTINGS)
            ) {
                Text(stringResource(R.string.home_settings))
            }
            Spacer(Modifier.height(32.dp))
            Text(
                text = stringResource(R.string.status_in_development, versionName),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** The active tuba, or the way to create one. */
@Composable
private fun InstrumentCard(active: InstrumentProfile?, actions: HomeActions) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (active != null) {
                Text(
                    stringResource(R.string.home_profile_active, active.name),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    profileSummary(active),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(stringResource(R.string.home_no_profile), style = MaterialTheme.typography.titleMedium)
                Button(onClick = actions.createProfile, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.home_create_profile))
                }
                OutlinedButton(onClick = actions.useDefault, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.home_use_default))
                }
            }
            OutlinedButton(
                onClick = actions.openProfiles,
                modifier = Modifier.fillMaxWidth().testTag(TAG_HOME_PROFILES)
            ) {
                Text(stringResource(R.string.home_profiles))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    StimmzugTheme(darkTheme = true) {
        HomeScreen(versionName = "0.0.1-dev", profiles = ProfileState())
    }
}
