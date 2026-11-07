package io.github.blanaboo.stimmzug.dsp.music

/** The key a tuba is built in. [pedalMidi] is the fundamental of the open tube, the pedal note. */
enum class TubaKey(val pedalMidi: Int) {
    /** BB♭ tuba, pedal Subkontra-B (29 Hz). */
    B_FLAT(22),

    /** F tuba, pedal Kontra-F (44 Hz). */
    F(29),

    /** E♭ tuba, pedal Kontra-Es (39 Hz). */
    E_FLAT(27),

    /** CC tuba, pedal Kontra-C (33 Hz). */
    C(24)
}

/**
 * How far a valve lowers the pitch. [semitones] is the nominal interval used to work out fingerings; the actual
 * slide length is whatever the player set, which the tuning assistant (#39) measures.
 */
enum class ValveInterval(val semitones: Int) {
    SEMITONE(1),
    WHOLE_TONE(2),

    /** A slightly long whole tone, typical for the 5th valve of German F tubas. */
    LARGE_WHOLE_TONE(2),
    MINOR_THIRD(3),
    MAJOR_THIRD(4),
    FOURTH(5)
}

/**
 * One of the player's tubas (#40). Valves 1–3 are always whole tone, semitone and minor third; [extraValves] are
 * valves 4 and up in order. Triggers are slides the player can move while playing: the main tuning slide and any
 * valve slide.
 */
data class InstrumentProfile(
    val id: String,
    val name: String,
    val key: TubaKey,
    val maker: String = "",
    val model: String = "",
    val extraValves: List<ValveInterval> = listOf(ValveInterval.FOURTH),
    /** Compensating system (extra tubing that corrects valve combinations with the 4th valve). */
    val compensating: Boolean = false,
    /** 1-based numbers of the valves whose slide has a trigger. */
    val triggerValves: Set<Int> = emptySet(),
    val mainSlideTrigger: Boolean = false
) {
    init {
        require(name.isNotBlank()) { "a profile needs a name" }
        require(extraValves.size <= MAX_EXTRA_VALVES) { "at most ${MAX_VALVES} valves, got $valveCount" }
        require(triggerValves.all { it in 1..valveCount }) { "trigger on a missing valve: $triggerValves" }
        require(!compensating || valveCount >= MIN_VALVES_COMPENSATING) { "compensation needs at least 4 valves" }
    }

    val valveCount: Int get() = STANDARD_VALVES.size + extraValves.size

    /** Nominal interval of every valve, valve 1 first. */
    val valves: List<ValveInterval> get() = STANDARD_VALVES + extraValves

    companion object {
        val STANDARD_VALVES = listOf(ValveInterval.WHOLE_TONE, ValveInterval.SEMITONE, ValveInterval.MINOR_THIRD)
        const val MAX_EXTRA_VALVES = 3
        val MAX_VALVES = STANDARD_VALVES.size + MAX_EXTRA_VALVES
        const val MIN_VALVES_COMPENSATING = 4

        /** Proposed interval for valve [number] (4 or 5); `null` where there is no common layout (6). */
        fun proposedInterval(number: Int): ValveInterval? = when (number) {
            4 -> ValveInterval.FOURTH
            5 -> ValveInterval.LARGE_WHOLE_TONE
            else -> null
        }

        /** Used when the player skips the setup: BB♭ tuba with four valves. */
        fun default(id: String, name: String) = InstrumentProfile(id = id, name = name, key = TubaKey.B_FLAT)
    }
}
