package io.github.blanaboo.stimmzug.ui.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.blanaboo.stimmzug.R
import io.github.blanaboo.stimmzug.dsp.music.InstrumentProfile
import io.github.blanaboo.stimmzug.dsp.music.NoteNames
import io.github.blanaboo.stimmzug.dsp.music.TubaKey
import io.github.blanaboo.stimmzug.dsp.music.ValveInterval
import io.github.blanaboo.stimmzug.ui.theme.StimmzugTheme

const val TAG_WIZARD_NEXT = "wizard_next"
const val TAG_WIZARD_MAKER = "wizard_maker"
const val TAG_WIZARD_MODEL = "wizard_model"
const val TAG_WIZARD_NAME = "wizard_name"

/** What the setup can do; [next] saves on the last question. */
class WizardActions(
    val change: ((ProfileDraft) -> ProfileDraft) -> Unit = {},
    val next: () -> Unit = {},
    val back: () -> Unit = {}
)

/** The profile setup (#40), one question per page. Leaves through [onDone] after saving or backing out. */
@Composable
fun ProfileWizardScreen(viewModel: ProfileWizardViewModel, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val draft = viewModel.draft
    val suggestion = ProfileDraft.suggestName(stringResource(draft.key.labelRes()), draft.maker, draft.model)
    val back = { if (!viewModel.back()) onDone() }
    BackHandler(onBack = back)
    if (!viewModel.ready) return
    ProfileWizardContent(
        draft = draft,
        step = viewModel.step,
        suggestedName = suggestion,
        isEdit = viewModel.isEdit,
        actions = WizardActions(
            change = viewModel::update,
            next = { if (!viewModel.next()) viewModel.save(suggestion, onDone) },
            back = back
        ),
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileWizardContent(
    draft: ProfileDraft,
    step: WizardStep,
    suggestedName: String,
    isEdit: Boolean,
    actions: WizardActions,
    modifier: Modifier = Modifier
) {
    val steps = draft.steps
    val last = step == steps.last()
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(if (isEdit) R.string.wizard_title_edit else R.string.wizard_title_new))
                        Text(
                            stringResource(R.string.wizard_step, steps.indexOf(step) + 1, steps.size),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = actions.back) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(onClick = actions.back, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.back))
                }
                Button(
                    onClick = actions.next,
                    enabled = draft.isComplete(step, suggestedName),
                    modifier = Modifier.weight(1f).testTag(TAG_WIZARD_NEXT)
                ) {
                    Text(stringResource(if (last) R.string.wizard_save else R.string.wizard_next))
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(stringResource(step.titleRes()), style = MaterialTheme.typography.headlineSmall)
            when (step) {
                WizardStep.KEY -> KeyStep(draft, actions.change)
                WizardStep.MAKER -> MakerStep(draft, actions.change)
                WizardStep.VALVES -> ValvesStep(draft, actions.change)
                WizardStep.COMPENSATION -> CompensationStep(draft, actions.change)
                WizardStep.TRIGGERS -> TriggersStep(draft, actions.change)
                WizardStep.NAME -> NameStep(draft, suggestedName, actions.change)
            }
        }
    }
}

private fun WizardStep.titleRes(): Int = when (this) {
    WizardStep.KEY -> R.string.wizard_key_title
    WizardStep.MAKER -> R.string.wizard_maker_title
    WizardStep.VALVES -> R.string.wizard_valves_title
    WizardStep.COMPENSATION -> R.string.wizard_compensation_title
    WizardStep.TRIGGERS -> R.string.wizard_triggers_title
    WizardStep.NAME -> R.string.wizard_name_title
}

private typealias Change = ((ProfileDraft) -> ProfileDraft) -> Unit

@Composable
private fun KeyStep(draft: ProfileDraft, change: Change) {
    TubaKey.entries.forEach { key ->
        ChoiceRow(
            selected = draft.key == key,
            title = stringResource(key.labelRes()),
            subtitle = stringResource(R.string.key_pedal, NoteNames.germanOctaveName(key.pedalMidi)),
            onClick = { change { it.copy(key = key) } }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MakerStep(draft: ProfileDraft, change: Change) {
    Hint(stringResource(R.string.wizard_maker_hint))
    OutlinedTextField(
        value = draft.maker,
        onValueChange = { value -> change { it.copy(maker = value) } },
        label = { Text(stringResource(R.string.wizard_maker_label)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag(TAG_WIZARD_MAKER)
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ProfileDraft.makerSuggestions(draft.maker).forEach { maker ->
            SuggestionChip(onClick = { change { it.copy(maker = maker) } }, label = { Text(maker) })
        }
    }
    OutlinedTextField(
        value = draft.model,
        onValueChange = { value -> change { it.copy(model = value) } },
        label = { Text(stringResource(R.string.wizard_model_label)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag(TAG_WIZARD_MODEL)
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ValvesStep(draft: ProfileDraft, change: Change) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (count in InstrumentProfile.STANDARD_VALVES.size..InstrumentProfile.MAX_VALVES) {
            FilterChip(
                selected = draft.valveCount == count,
                onClick = { change { it.withValveCount(count) } },
                label = { Text(count.toString()) }
            )
        }
    }
    Hint(stringResource(R.string.wizard_valves_standard))
    draft.extraValves.forEachIndexed { index, interval ->
        val number = InstrumentProfile.STANDARD_VALVES.size + 1 + index
        Text(stringResource(R.string.wizard_valve_number, number), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ValveInterval.entries.forEach { option ->
                FilterChip(
                    selected = interval == option,
                    onClick = { change { it.withExtraValve(number, option) } },
                    label = { Text(stringResource(option.labelRes())) }
                )
            }
        }
        if (interval == null) {
            Text(
                stringResource(R.string.wizard_valve_choose),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun CompensationStep(draft: ProfileDraft, change: Change) {
    Hint(stringResource(R.string.wizard_compensation_hint))
    ChoiceRow(
        selected = !draft.compensating,
        title = stringResource(R.string.wizard_compensation_no),
        onClick = { change { it.copy(compensating = false) } }
    )
    ChoiceRow(
        selected = draft.compensating,
        title = stringResource(R.string.wizard_compensation_yes),
        onClick = { change { it.copy(compensating = true) } }
    )
}

@Composable
private fun TriggersStep(draft: ProfileDraft, change: Change) {
    Hint(stringResource(R.string.wizard_triggers_hint))
    // Checked while nothing else is: makes "no trigger" an answer instead of a question left open.
    CheckRow(
        checked = !draft.hasTriggers,
        title = stringResource(R.string.trigger_none),
        onToggle = { change { it.withoutTriggers() } }
    )
    HorizontalDivider()
    CheckRow(
        checked = draft.mainSlideTrigger,
        title = stringResource(R.string.trigger_main_slide),
        onToggle = { change { it.copy(mainSlideTrigger = !it.mainSlideTrigger) } }
    )
    for (valve in 1..draft.valveCount) {
        CheckRow(
            checked = valve in draft.triggerValves,
            title = stringResource(R.string.trigger_valve, valve),
            onToggle = { change { it.toggleTrigger(valve) } }
        )
    }
}

@Composable
private fun NameStep(draft: ProfileDraft, suggestedName: String, change: Change) {
    OutlinedTextField(
        value = draft.nameOr(suggestedName),
        onValueChange = { value -> change { it.copy(name = value) } },
        label = { Text(stringResource(R.string.wizard_name_label)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag(TAG_WIZARD_NAME)
    )
    if (draft.isComplete(WizardStep.NAME, suggestedName)) Hint(profileSummary(draft.toProfile(suggestedName)))
}

@Composable
private fun ChoiceRow(selected: Boolean, title: String, onClick: () -> Unit, subtitle: String? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 4.dp)
    ) {
        RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            subtitle?.let { Hint(it) }
        }
    }
}

@Composable
private fun CheckRow(checked: Boolean, title: String, onToggle: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(vertical = 4.dp)
    ) {
        Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Preview(showBackground = true)
@Composable
private fun ValvesStepPreview() {
    val draft = ProfileDraft(id = "f", key = TubaKey.F, maker = "Melton", model = "Tradition 4250").withValveCount(6)
    StimmzugTheme(darkTheme = true) {
        ProfileWizardContent(draft, WizardStep.VALVES, "F-Tuba Melton Tradition 4250", isEdit = false, WizardActions())
    }
}
