package io.github.blanaboo.stimmzug.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.blanaboo.stimmzug.R
import io.github.blanaboo.stimmzug.data.settings.DisplayMode
import io.github.blanaboo.stimmzug.data.settings.DroneOutput
import io.github.blanaboo.stimmzug.data.settings.PracticeSettings
import io.github.blanaboo.stimmzug.ui.theme.StimmzugTheme
import kotlin.math.roundToInt

const val TAG_SETTINGS_A4 = "settings_a4"
const val TAG_SETTINGS_TOLERANCE = "settings_tolerance"
const val TAG_SETTINGS_HOLD = "settings_hold"
const val TAG_SETTINGS_HOLD_VALUE = "settings_hold_value"
const val TAG_SETTINGS_RESET = "settings_reset"

/** What the settings screen can do. */
class SettingsActions(
    val back: () -> Unit = {},
    val update: ((PracticeSettings) -> PracticeSettings) -> Unit = {},
    val reset: () -> Unit = {}
)

/** A whole-number setting shown as a snapping slider. */
private class SliderSpec(
    @StringRes val title: Int,
    @StringRes val hint: Int,
    @StringRes val valueFormat: Int,
    val range: IntRange,
    val tag: String
)

/** One choice of a segmented setting, with the hint shown while it is selected. */
private class ChoiceOption<T>(val value: T, @StringRes val label: Int, @StringRes val hint: Int)

private val A4 = SliderSpec(
    R.string.settings_a4,
    R.string.settings_a4_hint,
    R.string.settings_a4_value,
    PracticeSettings.A4_RANGE,
    TAG_SETTINGS_A4
)
private val TOLERANCE = SliderSpec(
    R.string.settings_tolerance,
    R.string.settings_tolerance_hint,
    R.string.settings_tolerance_value,
    PracticeSettings.TOLERANCE_RANGE,
    TAG_SETTINGS_TOLERANCE
)
private val HOLD = SliderSpec(
    R.string.settings_hold,
    R.string.settings_hold_hint,
    R.string.settings_hold_value,
    PracticeSettings.HOLD_RANGE,
    TAG_SETTINGS_HOLD
)
private val DISPLAY_OPTIONS = listOf(
    ChoiceOption(DisplayMode.EAR, R.string.display_ear, R.string.display_ear_hint),
    ChoiceOption(DisplayMode.TUNER, R.string.display_tuner, R.string.display_tuner_hint)
)
private val DRONE_OPTIONS = listOf(
    ChoiceOption(DroneOutput.AUTO, R.string.drone_auto, R.string.drone_auto_hint),
    ChoiceOption(DroneOutput.SPEAKER, R.string.drone_speaker, R.string.drone_speaker_hint)
)

/** Practice settings (#68). [settings] is `null` while loading. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(settings: PracticeSettings?, actions: SettingsActions, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = actions.back) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        if (settings != null) SettingsContent(settings, actions, padding)
    }
}

@Composable
private fun SettingsContent(settings: PracticeSettings, actions: SettingsActions, padding: PaddingValues) {
    Column(
        modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        SliderSetting(A4, settings.a4Hz) { v -> actions.update { it.copy(a4Hz = v) } }
        SliderSetting(TOLERANCE, settings.toleranceCents) { v -> actions.update { it.copy(toleranceCents = v) } }
        SliderSetting(HOLD, settings.holdSec) { v -> actions.update { it.copy(holdSec = v) } }
        HorizontalDivider()
        ChoiceSetting(R.string.settings_display, DISPLAY_OPTIONS, settings.display) { mode ->
            actions.update { it.copy(display = mode) }
        }
        ChoiceSetting(R.string.settings_drone, DRONE_OPTIONS, settings.droneOutput) { output ->
            actions.update { it.copy(droneOutput = output) }
        }
        HorizontalDivider()
        OutlinedButton(onClick = actions.reset, modifier = Modifier.fillMaxWidth().testTag(TAG_SETTINGS_RESET)) {
            Text(stringResource(R.string.settings_reset))
        }
    }
}

/** The stored value changes when the finger lifts, so dragging does not write on every step. */
@Composable
private fun SliderSetting(spec: SliderSpec, value: Int, onChange: (Int) -> Unit) {
    var position by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(spec.title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            Text(
                stringResource(spec.valueFormat, position.roundToInt()),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("${spec.tag}_value")
            )
        }
        Slider(
            value = position,
            onValueChange = { position = it },
            onValueChangeFinished = { onChange(position.roundToInt()) },
            valueRange = spec.range.first.toFloat()..spec.range.last.toFloat(),
            steps = spec.range.last - spec.range.first - 1,
            modifier = Modifier.testTag(spec.tag)
        )
        Text(
            stringResource(spec.hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun <T> ChoiceSetting(
    @StringRes title: Int,
    options: List<ChoiceOption<T>>,
    selected: T,
    onSelect: (T) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, option ->
                SegmentedButton(
                    selected = option.value == selected,
                    onClick = { onSelect(option.value) },
                    shape = SegmentedButtonDefaults.itemShape(index = i, count = options.size)
                ) {
                    Text(stringResource(option.label))
                }
            }
        }
        options.firstOrNull { it.value == selected }?.let {
            Text(
                stringResource(it.hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    StimmzugTheme(darkTheme = true) {
        SettingsScreen(PracticeSettings(), SettingsActions())
    }
}
