package io.github.blanaboo.stimmzug.practice

import io.github.blanaboo.stimmzug.dsp.exercise.Direction
import io.github.blanaboo.stimmzug.dsp.exercise.Exercise
import io.github.blanaboo.stimmzug.dsp.exercise.ExerciseSequencer
import io.github.blanaboo.stimmzug.dsp.exercise.Instrument
import io.github.blanaboo.stimmzug.dsp.exercise.Key
import io.github.blanaboo.stimmzug.dsp.exercise.ScaleType
import io.github.blanaboo.stimmzug.dsp.music.NoteNames
import io.github.blanaboo.stimmzug.dsp.music.NoteNaming
import io.github.blanaboo.stimmzug.dsp.music.TubaKey

/** The kinds of exercise the practice screen offers (#67). */
enum class ExerciseKind {
    /** Every note of a major key in a range once, in random order: the owner's first exercise. */
    KEY_NOTES,

    /** The major scale on the key's tonic, from the range's lowest tonic up as many octaves as fit. */
    SCALE,

    /** All semitones of the range. */
    CHROMATIC
}

/**
 * What the player chose on "Übung wählen". The default is the owner's wish (08.11.): the notes of B♭ major from
 * Kontra-F to kleines f, each once in random order.
 */
data class ExerciseChoice(
    val kind: ExerciseKind = ExerciseKind.KEY_NOTES,
    /** Tonic pitch class of the major key, 0 = C … 10 = B♭ … 11 = B. */
    val tonicPitchClass: Int = B_FLAT,
    val lowMidi: Int = KONTRA_F,
    val highMidi: Int = KLEINES_F,
    val direction: Direction = Direction.UP
) {
    init {
        require(tonicPitchClass in 0..11) { "pitch class must be in 0..11, was $tonicPitchClass" }
        require(lowMidi <= highMidi) { "low note $lowMidi is above high note $highMidi" }
    }

    val usesKey: Boolean get() = kind != ExerciseKind.CHROMATIC
    val usesDirection: Boolean get() = kind != ExerciseKind.KEY_NOTES

    /** The core's exercise; [seed] makes the random order. `null` if the range holds no note of it. */
    fun toExercise(seed: Long): Exercise? = when (kind) {
        ExerciseKind.KEY_NOTES -> Exercise.Random.eachOnce(lowMidi, highMidi, majorKey, seed)
            .takeIf { it.pool.size >= 2 }

        ExerciseKind.SCALE -> {
            val tonic = (lowMidi..highMidi).firstOrNull { Math.floorMod(it - tonicPitchClass, 12) == 0 }
            val octaves = tonic?.let { (highMidi - it) / 12 } ?: 0
            if (tonic == null || octaves < 1) null else Exercise.Scale(tonic, ScaleType.MAJOR, octaves, direction)
        }

        ExerciseKind.CHROMATIC -> if (highMidi > lowMidi) Exercise.Chromatic(lowMidi, highMidi, direction) else null
    }

    /** The notes to play, empty if the choice does not fit the [instrument]. */
    fun notes(instrument: Instrument, seed: Long): List<Int> {
        val exercise = toExercise(seed) ?: return emptyList()
        return runCatching { ExerciseSequencer.notesOf(exercise, instrument) }.getOrDefault(emptyList())
    }

    /** The choice in one line for the session log (#37), e.g. "KEY_NOTES B-Dur Kontra-F–kleines f UP". */
    val logText: String
        get() {
            val range = "${NoteNames.germanOctaveName(lowMidi)}–${NoteNames.germanOctaveName(highMidi)}"
            return "$kind $keyName $range $direction"
        }

    /** "B-Dur", the German name of the key. */
    val keyName: String get() = keyName(tonicPitchClass)

    private val majorKey: Key get() = Key(tonicPitchClass, ScaleType.MAJOR)

    companion object {
        const val B_FLAT = 10

        /** Kontra-F (F1) and kleines f (F3). */
        const val KONTRA_F = 29
        const val KLEINES_F = 53

        fun keyName(pitchClass: Int): String = NoteNames.pitchClass(MIDDLE_C + pitchClass, NoteNaming.GERMAN) + "-Dur"

        /** The range check of the core per tuba: the B♭ and C tubas share the deep range, F and E♭ the higher. */
        fun instrumentFor(key: TubaKey?): Instrument = when (key) {
            TubaKey.F, TubaKey.E_FLAT -> Instrument.F_TUBA
            TubaKey.B_FLAT, TubaKey.C, null -> Instrument.B_FLAT_TUBA
        }

        private const val MIDDLE_C = 60
    }
}
