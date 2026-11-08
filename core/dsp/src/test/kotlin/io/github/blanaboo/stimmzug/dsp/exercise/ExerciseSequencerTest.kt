package io.github.blanaboo.stimmzug.dsp.exercise

import io.github.blanaboo.stimmzug.dsp.exercise.Direction.DOWN
import io.github.blanaboo.stimmzug.dsp.exercise.Direction.UP
import io.github.blanaboo.stimmzug.dsp.exercise.Direction.UP_DOWN
import io.github.blanaboo.stimmzug.dsp.exercise.Instrument.B_FLAT_TUBA
import io.github.blanaboo.stimmzug.dsp.exercise.Instrument.F_TUBA
import io.github.blanaboo.stimmzug.dsp.music.NoteNames
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ExerciseSequencerTest {

    private fun notes(exercise: Exercise, instrument: Instrument = B_FLAT_TUBA) =
        ExerciseSequencer.notesOf(exercise, instrument)

    private fun names(notes: List<Int>) = notes.joinToString(" ") { NoteNames.germanOctaveName(it) }

    @Test
    fun `instrument profiles`() {
        assertEquals("Subkontra-B", NoteNames.germanOctaveName(B_FLAT_TUBA.lowestMidi))
        assertEquals("Kontra-B", NoteNames.germanOctaveName(B_FLAT_TUBA.homeMidi))
        assertEquals("großes F", NoteNames.germanOctaveName(F_TUBA.homeMidi))
        for (instrument in Instrument.entries) {
            assertTrue(instrument.homeMidi in instrument.range)
            assertTrue(instrument.range.count() >= 48, "$instrument spans at least four octaves")
        }
    }

    @Test
    fun `chromatic up, down and up-down without repeating the turning note`() {
        assertEquals(listOf(34, 35, 36, 37), notes(Exercise.Chromatic(34, 37, UP)))
        assertEquals(listOf(37, 36, 35, 34), notes(Exercise.Chromatic(34, 37, DOWN)))
        assertEquals(listOf(34, 35, 36, 37, 36, 35, 34), notes(Exercise.Chromatic(34, 37, UP_DOWN)))
        assertEquals(listOf(40), notes(Exercise.Chromatic(40, 40, UP_DOWN)))
    }

    @Test
    fun `B flat major over two octaves from Kontra-B`() {
        val up = notes(Exercise.Scale(34, ScaleType.MAJOR, octaves = 2))
        assertEquals(listOf(34, 36, 38, 39, 41, 43, 45, 46, 48, 50, 51, 53, 55, 57, 58), up)
        assertEquals(up.reversed(), notes(Exercise.Scale(34, ScaleType.MAJOR, octaves = 2, direction = DOWN)))
        val upDown = notes(Exercise.Scale(34, ScaleType.MAJOR, octaves = 2, direction = UP_DOWN))
        assertEquals(up + up.reversed().drop(1), upDown)
    }

    @Test
    fun `minor scales`() {
        // c minor from kleines c (MIDI 48), one octave.
        assertEquals(listOf(48, 50, 51, 53, 55, 56, 58, 60), notes(Exercise.Scale(48, ScaleType.NATURAL_MINOR)))
        assertEquals(listOf(48, 50, 51, 53, 55, 56, 59, 60), notes(Exercise.Scale(48, ScaleType.HARMONIC_MINOR)))
        // Melodic: raised 6th and 7th up (a, h), natural down (b, as).
        val melodic = notes(Exercise.Scale(48, ScaleType.MELODIC_MINOR, direction = UP_DOWN))
        assertEquals(listOf(48, 50, 51, 53, 55, 57, 59, 60, 58, 56, 55, 53, 51, 50, 48), melodic)
        assertEquals(
            listOf(60, 58, 56, 55, 53, 51, 50, 48),
            notes(Exercise.Scale(48, ScaleType.MELODIC_MINOR, direction = DOWN))
        )
        assertEquals("c d es f g a h c' b as g f es d c", names(melodic).replace("kleines ", ""))
    }

    @Test
    fun `exercises outside the instrument range are rejected, not clipped`() {
        val tooHigh = assertThrows<IllegalArgumentException> {
            notes(Exercise.Scale(58, ScaleType.MAJOR, octaves = 2), B_FLAT_TUBA)
        }
        assertTrue(tooHigh.message!!.contains("outside the range"))
        // One octave from kleines b ends on b', the top of the B♭ tuba.
        assertEquals(70, notes(Exercise.Scale(58, ScaleType.MAJOR, octaves = 1), B_FLAT_TUBA).last())
        // D major up to d'' fits the F tuba but not the B♭ tuba.
        assertEquals(74, notes(Exercise.Scale(62, ScaleType.MAJOR), F_TUBA).last())
        assertThrows<IllegalArgumentException> { notes(Exercise.Scale(62, ScaleType.MAJOR), B_FLAT_TUBA) }
        // Subkontra-B is in the B♭ tuba's range, but below the F tuba's.
        assertEquals(22, notes(Exercise.Chromatic(22, 30), B_FLAT_TUBA).first())
        assertThrows<IllegalArgumentException> { notes(Exercise.Chromatic(22, 30), F_TUBA) }
        assertThrows<IllegalArgumentException> { notes(Exercise.Random(30, 80, 10, seed = 1)) }
    }

    @Test
    fun `random notes are reproducible, never repeat directly and cover the range`() {
        val exercise = Exercise.Random(34, 46, count = 2000, seed = 42)
        val first = notes(exercise)
        assertEquals(first, notes(exercise), "same seed, same notes")
        assertNotEquals(first, notes(exercise.copy(seed = 43)))
        assertEquals(2000, first.size)
        assertTrue(first.zipWithNext().none { (a, b) -> a == b }, "direct repetition")
        assertEquals((34..46).toSet(), first.toSet(), "every note of the range occurs")
        // Roughly uniform: each of the 13 notes about 2000/13 ≈ 154 times.
        assertTrue(first.groupingBy { it }.eachCount().values.all { it in 110..200 })
    }

    @Test
    fun `random notes restricted to a key`() {
        val bFlatMajor = Key(tonicPitchClass = 10, type = ScaleType.MAJOR)
        val inKey = notes(Exercise.Random(34, 58, count = 500, seed = 7, key = bFlatMajor))
        assertTrue(inKey.all { it in bFlatMajor })
        assertEquals(15, inKey.toSet().size, "all 15 notes of B♭ major over two octaves")
        // Melodic minor admits both the raised and the natural 6th and 7th.
        val cMelodic = Key(0, ScaleType.MELODIC_MINOR)
        assertTrue(56 in cMelodic && 57 in cMelodic && 58 in cMelodic && 59 in cMelodic)
        assertFalse(52 in cMelodic, "e is not in c minor")
        assertThrows<IllegalArgumentException> { Key(12, ScaleType.MAJOR) }
    }

    @Test
    fun `each note of B flat major from Kontra-F to f once, in random order`() {
        val bFlatMajor = Key(tonicPitchClass = 10, type = ScaleType.MAJOR)
        val exercise = Exercise.Random.eachOnce(lowMidi = 29, highMidi = 53, key = bFlatMajor, seed = 7)
        val played = notes(exercise)
        assertEquals(
            "Kontra-F Kontra-G Kontra-A Kontra-B großes C großes D großes Es großes F großes G großes A großes B " +
                "kleines c kleines d kleines es kleines f",
            names(played.sorted())
        )
        assertEquals(15, played.size)
        assertNotEquals(played.sorted(), played, "shuffled")
        assertEquals(played, notes(exercise), "reproducible from the seed")
        assertNotEquals(played, notes(exercise.copy(seed = 8)))

        val part = notes(exercise.copy(count = 5))
        assertEquals(5, part.toSet().size, "no repetition: $part")
        assertThrows<IllegalArgumentException> { notes(exercise.copy(count = 16)) }
    }

    @Test
    fun `random edge cases`() {
        assertEquals(listOf(40), notes(Exercise.Random(40, 40, count = 1, seed = 1)))
        assertThrows<IllegalArgumentException> { notes(Exercise.Random(40, 40, count = 2, seed = 1)) }
        assertThrows<IllegalArgumentException> { notes(Exercise.Random(40, 41, count = 0, seed = 1)) }
        assertThrows<IllegalArgumentException> { notes(Exercise.Random(41, 40, count = 3, seed = 1)) }
        // No note of F♯ major between E and F.
        assertThrows<IllegalArgumentException> {
            notes(Exercise.Random(40, 41, count = 3, seed = 1, key = Key(6, ScaleType.MAJOR)))
        }
        assertThrows<IllegalArgumentException> { notes(Exercise.Scale(34, ScaleType.MAJOR, octaves = 0)) }
        assertThrows<IllegalArgumentException> { notes(Exercise.Chromatic(40, 39)) }
    }

    @Test
    fun `sequencer walks forward, back and finishes`() {
        val sequencer = ExerciseSequencer(Exercise.Chromatic(34, 36), B_FLAT_TUBA)
        assertEquals(listOf(34, 35, 36), sequencer.notes)
        assertEquals(34, sequencer.current)
        assertEquals(34, sequencer.back(), "stays on the first note")
        assertEquals(35, sequencer.advance())
        assertEquals(36, sequencer.advance())
        assertEquals(35, sequencer.back())
        assertEquals(36, sequencer.advance())
        assertNull(sequencer.advance())
        assertTrue(sequencer.isFinished)
        assertEquals(3, sequencer.position)
        assertNull(sequencer.advance(), "stays finished")
        assertEquals(36, sequencer.back(), "back from the end returns to the last note")
        assertFalse(sequencer.isFinished)
    }
}
