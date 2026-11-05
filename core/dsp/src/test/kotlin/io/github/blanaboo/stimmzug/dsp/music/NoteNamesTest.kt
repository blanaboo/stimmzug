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

    @Test
    fun `parsing is the inverse of the German and scientific names`() {
        for (midi in 12..96) {
            assertEquals(midi, NoteNames.parseMidi(NoteNames.germanOctaveName(midi)), NoteNames.germanOctaveName(midi))
            assertEquals(midi, NoteNames.parseMidi(NoteNames.scientific(midi)), NoteNames.scientific(midi))
        }
    }

    @ParameterizedTest(name = "{0} = {1}")
    @CsvSource(
        "34, 34",
        "' 58 ', 58",
        "Bb1, 34",
        "bb1, 34",
        "B1, 35",
        "F#2, 42",
        "C♯4, 61",
        "C-1, 0",
        "Kontra-B, 34",
        "kontra-h, 35",
        "großes F, 41",
        "grosses Fis, 42",
        "kleines cis, 49",
        "b', 70",
        "fis'', 78",
        "Subkontra-B, 22"
    )
    fun `parses notes`(text: String, midi: Int) {
        assertEquals(midi, NoteNames.parseMidi(text))
    }

    @ParameterizedTest(name = "\"{0}\"")
    @CsvSource("Tuba", "H", "Kontra-X", "x'", "großes", "B♭", "58.27Hz", "''")
    fun `rejects non-notes`(text: String) {
        assertEquals(null, NoteNames.parseMidi(text))
    }
}
