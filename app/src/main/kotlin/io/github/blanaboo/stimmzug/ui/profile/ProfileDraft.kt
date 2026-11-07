package io.github.blanaboo.stimmzug.ui.profile

import io.github.blanaboo.stimmzug.dsp.music.InstrumentProfile
import io.github.blanaboo.stimmzug.dsp.music.TubaKey
import io.github.blanaboo.stimmzug.dsp.music.ValveInterval

/** The questions of the profile setup, in order. */
enum class WizardStep { KEY, MAKER, VALVES, COMPENSATION, TRIGGERS, NAME }

/** Makers offered while typing; any other name can be entered as well. */
val MAKER_SUGGESTIONS = listOf(
    "B&S",
    "Besson",
    "Cerveny",
    "Gronitz",
    "Hirsbrunner",
    "Jupiter",
    "Meinl Weston",
    "Melton",
    "Miraphone",
    "Rudolf Meinl",
    "Thein",
    "Willson",
    "Yamaha"
)

/**
 * The answers of the profile setup so far. Unlike [InstrumentProfile] it may be incomplete: an extra valve without
 * an interval (no common layout for the 6th valve) and no name yet ([name] `null` = use the suggestion).
 */
data class ProfileDraft(
    val id: String,
    val key: TubaKey = TubaKey.B_FLAT,
    val maker: String = "",
    val model: String = "",
    val extraValves: List<ValveInterval?> = listOf(ValveInterval.FOURTH),
    val compensating: Boolean = false,
    val triggerValves: Set<Int> = emptySet(),
    val mainSlideTrigger: Boolean = false,
    val name: String? = null
) {
    val valveCount: Int get() = InstrumentProfile.STANDARD_VALVES.size + extraValves.size

    /** The steps for these answers: compensation is only asked with four or more valves. */
    val steps: List<WizardStep>
        get() = WizardStep.entries.filter {
            it != WizardStep.COMPENSATION || valveCount >= InstrumentProfile.MIN_VALVES_COMPENSATING
        }

    /** Changes the number of valves; kept choices stay, new valves get the proposal, dropped ones lose triggers. */
    fun withValveCount(count: Int): ProfileDraft {
        require(count in InstrumentProfile.STANDARD_VALVES.size..InstrumentProfile.MAX_VALVES) {
            "invalid count $count"
        }
        val first = InstrumentProfile.STANDARD_VALVES.size + 1
        val extra = (first..count).map { n ->
            extraValves.getOrNull(n - first) ?: InstrumentProfile.proposedInterval(n)
        }
        return copy(
            extraValves = extra,
            compensating = compensating && count >= InstrumentProfile.MIN_VALVES_COMPENSATING,
            triggerValves = triggerValves.filterTo(sortedSetOf()) { it <= count }
        )
    }

    /** Sets the interval of valve [number] (4 and up). */
    fun withExtraValve(number: Int, interval: ValveInterval): ProfileDraft {
        val index = number - InstrumentProfile.STANDARD_VALVES.size - 1
        require(index in extraValves.indices) { "no extra valve $number" }
        return copy(extraValves = extraValves.toMutableList().apply { set(index, interval) })
    }

    fun toggleTrigger(valve: Int): ProfileDraft =
        copy(triggerValves = if (valve in triggerValves) triggerValves - valve else triggerValves + valve)

    val hasTriggers: Boolean get() = mainSlideTrigger || triggerValves.isNotEmpty()

    /** The explicit answer "no trigger" (#46). */
    fun withoutTriggers(): ProfileDraft = copy(triggerValves = emptySet(), mainSlideTrigger = false)

    /** Whether the player may leave [step]: every valve needs an interval, and the profile a name. */
    fun isComplete(step: WizardStep, suggestedName: String): Boolean = when (step) {
        WizardStep.VALVES -> extraValves.all { it != null }
        WizardStep.NAME -> nameOr(suggestedName).isNotBlank()
        else -> true
    }

    fun next(step: WizardStep): WizardStep? = steps.getOrNull(steps.indexOf(step) + 1)

    fun previous(step: WizardStep): WizardStep? = steps.getOrNull(steps.indexOf(step) - 1)

    fun nameOr(suggestion: String): String = name ?: suggestion

    fun toProfile(suggestedName: String): InstrumentProfile = InstrumentProfile(
        id = id,
        name = nameOr(suggestedName).trim(),
        key = key,
        maker = maker.trim(),
        model = model.trim(),
        extraValves = extraValves.map { requireNotNull(it) { "valve without interval" } },
        compensating = compensating && valveCount >= InstrumentProfile.MIN_VALVES_COMPENSATING,
        triggerValves = triggerValves,
        mainSlideTrigger = mainSlideTrigger
    )

    companion object {
        fun from(profile: InstrumentProfile) = ProfileDraft(
            id = profile.id,
            key = profile.key,
            maker = profile.maker,
            model = profile.model,
            extraValves = profile.extraValves,
            compensating = profile.compensating,
            triggerValves = profile.triggerValves,
            mainSlideTrigger = profile.mainSlideTrigger,
            name = profile.name
        )

        /** "F-Tuba Melton Tradition 4250" from the key's display name, maker and model. */
        fun suggestName(keyLabel: String, maker: String, model: String): String =
            listOf(keyLabel, maker.trim(), model.trim()).filter { it.isNotEmpty() }.joinToString(" ")

        /** Makers starting with [typed] (case-insensitive), without an exact match. */
        fun makerSuggestions(typed: String): List<String> {
            val t = typed.trim()
            return MAKER_SUGGESTIONS.filter { it.startsWith(t, ignoreCase = true) && !it.equals(t, ignoreCase = true) }
        }
    }
}
