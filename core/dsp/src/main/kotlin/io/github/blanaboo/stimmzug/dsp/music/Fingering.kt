package io.github.blanaboo.stimmzug.dsp.music

/**
 * A fingering: the natural tone ([partial], 1 = pedal) and the valves pressed (numbers from 1, ascending).
 * The app only hears the pitch; the fingering comes from [Fingering.standard], alternatives the player marks later.
 */
data class Fingering(val partial: Int, val valves: List<Int>) {
    /** "0" for open, else e.g. "1+3". */
    val valveText: String get() = if (valves.isEmpty()) "0" else valves.joinToString("+")

    companion object {
        /** Nominal semitones of natural tones 1..12 above the pedal (equal temperament, rounded). */
        private val PARTIAL_SEMITONES = intArrayOf(0, 12, 19, 24, 28, 31, 34, 36, 38, 40, 42, 43)

        /** Too far off (7th: about −31 ct, 11th: about −49 ct) for a standard fingering. */
        private val AVOIDED_PARTIALS = setOf(7, 11)

        /**
         * How much a valve "costs" in a combination: 1+2 is the usual minor third rather than 3 alone, 4 rather than
         * 1+3 and 2+4 rather than 1+2+3 (valves 5 and 6 are extras for corrections).
         */
        private fun weight(valve: Int): Double = when (valve) {
            1, 2, 4 -> 1.0
            3 -> 2.1
            else -> 1.5
        }

        /**
         * The standard fingering of [midi] on [profile] (#37), as in the usual fingering charts: the nearest natural
         * tone at or above the note (avoiding the 7th and 11th), lowered by the cheapest valve combination.
         * Below the reach of the 2nd natural tone only the pedal is left. `null` if the note cannot be played.
         */
        fun standard(profile: InstrumentProfile, midi: Int): Fingering? {
            val pedal = profile.key.pedalMidi
            val combinations = combinations(profile.valves)
            val fromPartials = (2..PARTIAL_SEMITONES.size)
                .filter { it !in AVOIDED_PARTIALS }
                .mapNotNull { partial ->
                    val lowering = pedal + PARTIAL_SEMITONES[partial - 1] - midi
                    combinations[lowering]?.let { Fingering(partial, it) }
                }
                .minByOrNull { PARTIAL_SEMITONES[it.partial - 1] }
            return fromPartials ?: combinations[pedal - midi]?.let { Fingering(1, it) }
        }

        /** For each lowering in semitones the cheapest valve combination (valve numbers from 1). */
        private fun combinations(valves: List<ValveInterval>): Map<Int, List<Int>> {
            val best = HashMap<Int, Pair<Double, List<Int>>>()
            for (mask in 0 until (1 shl valves.size)) {
                val used = valves.indices.filter { mask and (1 shl it) != 0 }.map { it + 1 }
                val lowering = used.sumOf { valves[it - 1].semitones }
                val cost = used.sumOf { weight(it) }
                val current = best[lowering]
                if (current == null || cost < current.first) best[lowering] = cost to used
            }
            return best.mapValues { it.value.second }
        }
    }
}
