package io.github.blanaboo.stimmzug.dsp.music

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class NoteNamesTest {

    @ParameterizedTest(name = "MIDI {0} = {1}")
    @CsvSource(
        "21, Subkontra-A",
        "22, Subkontra-B",
        "29, Kontra-F",
        "34, Kontra-B",
        "35, Kontra-H",
        "41, großes F",
        "46, großes B",
        "53, kleines f",
        "58, kleines b",
        "65, f'",
        "77, f''",
        "11, B-1" // below the subcontra octave: scientific fallback
    )
    fun `german octave names`(midi: Int, expected: String) {
        assertEquals(expected, NoteNames.germanOctaveName(midi))
    }

    @Test
    fun `german and international pitch classes`() {
        assertEquals("B", NoteNames.pitchClass(34, NoteNaming.GERMAN))
        assertEquals("H", NoteNames.pitchClass(35, NoteNaming.GERMAN))
        assertEquals("Es", NoteNames.pitchClass(39, NoteNaming.GERMAN))
        assertEquals("B♭", NoteNames.pitchClass(34, NoteNaming.INTERNATIONAL))
        assertEquals("B", NoteNames.pitchClass(35, NoteNaming.INTERNATIONAL))
        assertEquals("C", NoteNames.pitchClass(-12, NoteNaming.GERMAN))
    }

    @Test
    fun `scientific notation`() {
        assertEquals("B♭1", NoteNames.scientific(34))
        assertEquals("C4", NoteNames.scientific(60))
        assertEquals("A4", NoteNames.scientific(69))
        assertEquals(-1, NoteNames.octave(0))
    }
}
