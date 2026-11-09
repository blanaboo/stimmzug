package io.github.blanaboo.stimmzug.ui.practice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.blanaboo.stimmzug.R
import io.github.blanaboo.stimmzug.data.settings.DisplayMode
import io.github.blanaboo.stimmzug.data.settings.DroneOutput
import io.github.blanaboo.stimmzug.data.settings.PracticeSettings
import io.github.blanaboo.stimmzug.dsp.exercise.Direction
import io.github.blanaboo.stimmzug.dsp.music.InstrumentProfile
import io.github.blanaboo.stimmzug.dsp.music.NoteNames
import io.github.blanaboo.stimmzug.practice.ExerciseChoice
import io.github.blanaboo.stimmzug.practice.ExerciseKind
import io.github.blanaboo.stimmzug.ui.profile.profileSummary

const val TAG_PRACTICE_START = "practice_start"
const val TAG_PRACTICE_KIND = "practice_kind_"
const val TAG_PRACTICE_LOW = "practice_low"
const val TAG_PRACTICE_HIGH = "practice_high"
const val TAG_PRACTICE_NOTE = "practice_note_"
const val TAG_PRACTICE_TUBA = "practice_tuba"
const val TAG_PRACTICE_TUBA_ITEM = "practice_tuba_"

/** What "Übung wählen" can do. */
class SetupActions(
    val back: () -> Unit = {},
    val choose: ((ExerciseChoice) -> ExerciseChoice) -> Unit = {},
    val setDisplay: (DisplayMode) -> Unit = {},
    val openProfiles: () -> Unit = {},
    val openSettings: () -> Unit = {},
    val start: () -> Unit = {},
    /** Makes the profile with this id the active tuba (#44). */
    val chooseProfile: (String) -> Unit = {}
)

/** Everything "Übung wählen" shows. */
class SetupState(
    val choice: ExerciseChoice,
    val noteCount: Int,
    val settings: PracticeSettings,
    val profile: InstrumentProfile?,
    /** All tubas of the player; with more than one, „ändern“ picks among them right here (#44). */
    val profiles: List<InstrumentProfile> = emptyList(),
    /** The notes the range pickers offer (the active tuba's compass). */
    val range: IntRange,
    val micDenied: Boolean = false
)

/** "Übung wählen" (#67): the tuba, the exercise, the display, then start. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeSetupScreen(state: SetupState, actions: SetupActions, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.practice_title)) },
                navigationIcon = {
                    IconButton(onClick = actions.back) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TubaCard(state.profile, state.profiles, actions)
            ExerciseSection(state, actions)
            DisplaySection(state.settings.display, actions.setDisplay)
            SettingsLine(state.settings, actions.openSettings)
            if (state.micDenied) {
                Text(
                    stringResource(R.string.practice_mic_denied),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Button(
                onClick = actions.start,
                enabled = state.noteCount > 0,
                modifier = Modifier.fillMaxWidth().height(56.dp).testTag(TAG_PRACTICE_START)
            ) {
                Text(stringResource(R.string.practice_start), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun TubaCard(profile: InstrumentProfile?, profiles: List<InstrumentProfile>, actions: SetupActions) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (profile != null) {
                    Text(stringResource(R.string.practice_today_with), style = MaterialTheme.typography.labelMedium)
                    Text(profile.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        profileSummary(profile),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(stringResource(R.string.practice_no_tuba), style = MaterialTheme.typography.titleMedium)
                }
            }
            TubaChoice(profile, profiles, actions)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExerciseSection(state: SetupState, actions: SetupActions) {
    val choice = state.choice
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.practice_exercise), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (kind in ExerciseKind.entries) {
                FilterChip(
                    selected = choice.kind == kind,
                    onClick = { actions.choose { it.copy(kind = kind) } },
                    label = { Text(stringResource(kind.label)) },
                    modifier = Modifier.testTag(TAG_PRACTICE_KIND + kind.name)
                )
            }
        }
        if (choice.usesKey) {
            Picker(
                label = stringResource(R.string.practice_key),
                value = choice.keyName,
                options = (0..11).map { it to ExerciseChoice.keyName(it) },
                onPick = { pc -> actions.choose { it.copy(tonicPitchClass = pc) } }
            )
        }
        RangePickers(state, actions)
        if (choice.usesDirection) DirectionChips(choice.direction, actions)
        NoteCount(choice.kind, state.noteCount)
    }
}

@Composable
private fun RangePickers(state: SetupState, actions: SetupActions) {
    val notes = state.range.map { it to NoteNames.germanOctaveName(it) }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.weight(1f)) {
            Picker(
                label = stringResource(R.string.practice_from),
                value = NoteNames.germanOctaveName(state.choice.lowMidi),
                options = notes,
                onPick = { low -> actions.choose { it.copy(lowMidi = low) } },
                tag = TAG_PRACTICE_LOW
            )
        }
        Box(Modifier.weight(1f)) {
            Picker(
                label = stringResource(R.string.practice_to),
                value = NoteNames.germanOctaveName(state.choice.highMidi),
                options = notes,
                onPick = { high -> actions.choose { it.copy(highMidi = high) } },
                tag = TAG_PRACTICE_HIGH
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DirectionChips(selected: Direction, actions: SetupActions) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (direction in Direction.entries) {
            FilterChip(
                selected = selected == direction,
                onClick = { actions.choose { it.copy(direction = direction) } },
                label = { Text(stringResource(direction.label)) }
            )
        }
    }
}

@Composable
private fun NoteCount(kind: ExerciseKind, count: Int) {
    val text = when {
        count == 0 -> stringResource(R.string.practice_does_not_fit)
        kind == ExerciseKind.KEY_NOTES -> stringResource(R.string.practice_key_notes_hint, count)
        else -> stringResource(R.string.practice_note_count, count)
    }
    val color = if (count == 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    Text(text, style = MaterialTheme.typography.bodyMedium, color = color)
}

/** A labelled button that opens a menu of [options] (value to text). */
@Composable
private fun <T> Picker(
    label: String,
    value: String,
    options: List<Pair<T, String>>,
    onPick: (T) -> Unit,
    tag: String? = null
) {
    var open by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            OutlinedButton(
                onClick = { open = true },
                modifier = Modifier.fillMaxWidth().let { if (tag != null) it.testTag(tag) else it }
            ) {
                Text(value, modifier = Modifier.weight(1f))
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                for ((option, text) in options) {
                    DropdownMenuItem(
                        text = { Text(text) },
                        onClick = {
                            open = false
                            onPick(option)
                        },
                        modifier = Modifier.testTag(TAG_PRACTICE_NOTE + option)
                    )
                }
            }
        }
    }
}

@Composable
private fun DisplaySection(display: DisplayMode, setDisplay: (DisplayMode) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.practice_display), style = MaterialTheme.typography.titleSmall)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            DisplayMode.entries.forEachIndexed { i, mode ->
                SegmentedButton(
                    selected = mode == display,
                    onClick = { setDisplay(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index = i, count = DisplayMode.entries.size)
                ) {
                    Text(stringResource(if (mode == DisplayMode.EAR) R.string.display_ear else R.string.display_tuner))
                }
            }
        }
    }
}

@Composable
private fun SettingsLine(settings: PracticeSettings, openSettings: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(vertical = 12.dp)) {
                val output = if (settings.droneOutput ==
                    DroneOutput.SPEAKER
                ) {
                    R.string.drone_speaker
                } else {
                    R.string.drone_auto
                }
                Text(
                    stringResource(R.string.practice_drone_setting, stringResource(output)),
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    stringResource(
                        R.string.practice_settings_line,
                        settings.a4Hz,
                        settings.toleranceCents,
                        settings.holdSec
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = openSettings) {
                Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings_title))
            }
        }
    }
}

private val ExerciseKind.label: Int
    get() = when (this) {
        ExerciseKind.KEY_NOTES -> R.string.kind_key_notes
        ExerciseKind.SCALE -> R.string.kind_scale
        ExerciseKind.CHROMATIC -> R.string.kind_chromatic
    }

private val Direction.label: Int
    get() = when (this) {
        Direction.UP -> R.string.direction_up
        Direction.DOWN -> R.string.direction_down
        Direction.UP_DOWN -> R.string.direction_up_down
    }

/** „ändern“: with several tubas a short list, the active one checked; otherwise straight to the instruments. */
@Composable
private fun TubaChoice(active: InstrumentProfile?, profiles: List<InstrumentProfile>, actions: SetupActions) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(
            onClick = { if (profiles.size > 1) open = true else actions.openProfiles() },
            modifier = Modifier.testTag(TAG_PRACTICE_TUBA)
        ) {
            Text(stringResource(R.string.practice_change))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (p in profiles) {
                DropdownMenuItem(
                    text = { Text(p.name) },
                    leadingIcon = {
                        if (p.id == active?.id) Icon(Icons.Filled.Check, contentDescription = null)
                    },
                    onClick = {
                        open = false
                        actions.chooseProfile(p.id)
                    },
                    modifier = Modifier.testTag(TAG_PRACTICE_TUBA_ITEM + p.id)
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.practice_manage_tubas)) },
                onClick = {
                    open = false
                    actions.openProfiles()
                }
            )
        }
    }
}
