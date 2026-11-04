package io.github.blanaboo.stimmzug.dsp.music

/** How note names are displayed. */
enum class NoteNaming {
    /** German names with B = B-flat and H = B natural, flats for black keys (Des, Es, Ges, As, B). */
    GERMAN,

    /** International names with flats (D♭, E♭, G♭, A♭, B♭). */
    INTERNATIONAL
}

/** Note names for MIDI numbers. Brass players think in flats, so black keys are spelled as flats. */
object NoteNames {
    private val german = listOf("C", "Des", "D", "Es", "E", "F", "Ges", "G", "As", "A", "B", "H")
    private val international = listOf("C", "D♭", "D", "E♭", "E", "F", "G♭", "G", "A♭", "A", "B♭", "B")

    /** Pitch class name without octave, e.g. "B" (German) or "B♭" (international) for MIDI 34. */
    fun pitchClass(midi: Int, naming: NoteNaming): String {
        val index = Math.floorMod(midi, 12)
        return when (naming) {
            NoteNaming.GERMAN -> german[index]
            NoteNaming.INTERNATIONAL -> international[index]
        }
    }

    /** Scientific octave number (C4 = middle C = MIDI 60). */
    fun octave(midi: Int): Int = Math.floorDiv(midi, 12) - 1

    /** Scientific pitch notation, e.g. "B♭1" for MIDI 34. */
    fun scientific(midi: Int): String = pitchClass(midi, NoteNaming.INTERNATIONAL) + octave(midi)

    /**
     * German octave name as used by tuba players, e.g. "Subkontra-B" (MIDI 22), "Kontra-B" (34), "großes F" (41),
     * "kleines f" (53), "f'" (65), "f''" (77). Falls back to scientific notation below the subcontra octave.
     */
    fun germanOctaveName(midi: Int): String {
        val name = pitchClass(midi, NoteNaming.GERMAN)
        return when (val octave = octave(midi)) {
            0 -> "Subkontra-$name"
            1 -> "Kontra-$name"
            2 -> "großes $name"
            3 -> "kleines ${name.lowercase()}"
            in 4..Int.MAX_VALUE -> name.lowercase() + "'".repeat(octave - 3)
            else -> scientific(midi)
        }
    }
}
