package io.github.blanaboo.stimmzug.ui.profile

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.blanaboo.stimmzug.R
import io.github.blanaboo.stimmzug.dsp.music.InstrumentProfile
import io.github.blanaboo.stimmzug.dsp.music.TubaKey
import io.github.blanaboo.stimmzug.dsp.music.ValveInterval

@StringRes
fun TubaKey.labelRes(): Int = when (this) {
    TubaKey.B_FLAT -> R.string.key_b_flat
    TubaKey.F -> R.string.key_f
    TubaKey.E_FLAT -> R.string.key_e_flat
    TubaKey.C -> R.string.key_c
}

@StringRes
fun ValveInterval.labelRes(): Int = when (this) {
    ValveInterval.SEMITONE -> R.string.interval_semitone
    ValveInterval.WHOLE_TONE -> R.string.interval_whole_tone
    ValveInterval.LARGE_WHOLE_TONE -> R.string.interval_large_whole_tone
    ValveInterval.MINOR_THIRD -> R.string.interval_minor_third
    ValveInterval.MAJOR_THIRD -> R.string.interval_major_third
    ValveInterval.FOURTH -> R.string.interval_fourth
}

/** e.g. "F-Tuba · 5 Ventile · Trigger: 2. Ventilzug". */
@Composable
fun profileSummary(profile: InstrumentProfile): String {
    val parts = mutableListOf(
        stringResource(profile.key.labelRes()),
        pluralStringResource(R.plurals.summary_valves, profile.valveCount, profile.valveCount)
    )
    if (profile.compensating) parts += stringResource(R.string.summary_compensating)
    val triggers = buildList {
        if (profile.mainSlideTrigger) add(stringResource(R.string.trigger_main_slide))
        profile.triggerValves.sorted().forEach { add(stringResource(R.string.trigger_valve, it)) }
    }
    if (triggers.isNotEmpty()) parts += stringResource(R.string.summary_triggers, triggers.joinToString(", "))
    return parts.joinToString(" · ")
}
