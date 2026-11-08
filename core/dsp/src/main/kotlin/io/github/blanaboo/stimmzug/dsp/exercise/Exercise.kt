package io.github.blanaboo.stimmzug.dsp.exercise

/**
 * Instrument profile: the playable range (MIDI, concert pitch) and the home note used as default tonic.
 * The ranges are the usual compass of the instruments, to be adjusted after the field test.
 */
enum class Instrument(val lowestMidi: Int, val highestMidi: Int, val homeMidi: Int) {
    /** Bass tuba in F: Kontra-C (C1) to d'' (D5); home note großes F (F2), the open 2nd partial. */
    F_TUBA(lowestMidi = 24, highestMidi = 74, homeMidi = 41),

    /** Contrabass tuba in B♭: Subkontra-B (B♭0, pedal) to b' (B♭4); home note Kontra-B (B♭1), the open 2nd partial. */
    B_FLAT_TUBA(lowestMidi = 22, highestMidi = 70, homeMidi = 34);

    val range: IntRange get() = lowestMidi..highestMidi
}

enum class Direction { UP, DOWN, UP_DOWN }

/** Scale steps in semitones above the tonic; [descending] differs only for the melodic minor. */
enum class ScaleType(val ascending: List<Int>, val descending: List<Int> = ascending) {
    MAJOR(listOf(0, 2, 4, 5, 7, 9, 11)),
    NATURAL_MINOR(listOf(0, 2, 3, 5, 7, 8, 10)),
    HARMONIC_MINOR(listOf(0, 2, 3, 5, 7, 8, 11)),

    /** Classical form: raised 6th and 7th ascending, natural minor descending. */
    MELODIC_MINOR(ascending = listOf(0, 2, 3, 5, 7, 9, 11), descending = listOf(0, 2, 3, 5, 7, 8, 10))
}

/** Key for restricting random notes: tonic pitch class (0 = C … 11 = B) and scale type. */
data class Key(val tonicPitchClass: Int, val type: ScaleType) {
    init {
        require(tonicPitchClass in 0..11) { "pitch class must be in 0..11, was $tonicPitchClass" }
    }

    /** True if [midi] belongs to the key (both forms of the melodic minor count). */
    operator fun contains(midi: Int): Boolean {
        val step = Math.floorMod(midi - tonicPitchClass, 12)
        return step in type.ascending || step in type.descending
    }
}

/** What the player practises: a sequence of target notes. */
sealed interface Exercise {
    /** All semitones from [lowMidi] to [highMidi]. */
    data class Chromatic(val lowMidi: Int, val highMidi: Int, val direction: Direction = Direction.UP) : Exercise

    /** Scale on [tonicMidi] over [octaves] octaves (up from the tonic, down from the top tonic). */
    data class Scale(
        val tonicMidi: Int,
        val type: ScaleType,
        val octaves: Int = 1,
        val direction: Direction = Direction.UP
    ) : Exercise

    /**
     * [count] random notes from [lowMidi]..[highMidi] (only those in [key], if given), never twice in a row.
     * With [repeat] = `false` every note comes at most once; [count] = the pool size plays each note once in random
     * order, see [eachOnce].
     */
    data class Random(
        val lowMidi: Int,
        val highMidi: Int,
        val count: Int,
        val seed: Long,
        val key: Key? = null,
        val repeat: Boolean = true
    ) : Exercise {
        /** The notes to draw from. */
        val pool: List<Int> get() = (lowMidi..highMidi).filter { key == null || it in key }

        companion object {
            /** Every note of [key] in [lowMidi]..[highMidi] once, in random order (e.g. B♭ major, Kontra-F to f). */
            fun eachOnce(lowMidi: Int, highMidi: Int, key: Key?, seed: Long): Random {
                val count = Random(lowMidi, highMidi, 1, seed, key).pool.size
                return Random(lowMidi, highMidi, count, seed, key, repeat = false)
            }
        }
    }
}
