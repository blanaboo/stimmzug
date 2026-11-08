package io.github.blanaboo.stimmzug.practice

import io.github.blanaboo.stimmzug.audio.AudioInput
import io.github.blanaboo.stimmzug.audio.OutputKind
import io.github.blanaboo.stimmzug.audio.OutputRoute
import io.github.blanaboo.stimmzug.dsp.exercise.Direction
import io.github.blanaboo.stimmzug.dsp.exercise.Instrument
import io.github.blanaboo.stimmzug.dsp.music.NoteNames
import io.github.blanaboo.stimmzug.dsp.music.TubaKey
import io.github.blanaboo.stimmzug.dsp.session.DroneControl
import io.github.blanaboo.stimmzug.dsp.session.NoteStatus
import io.github.blanaboo.stimmzug.dsp.session.PracticeSession
import io.github.blanaboo.stimmzug.dsp.session.SessionPhase
import io.github.blanaboo.stimmzug.ui.practice.octavesOff
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticeLogicTest {

    private fun names(notes: List<Int>) = notes.joinToString(" ") { NoteNames.germanOctaveName(it) }

    @Test
    fun defaultIsTheOwnersFirstExercise() {
        val choice = ExerciseChoice()
        assertEquals("B-Dur", choice.keyName)
        val notes = choice.notes(Instrument.B_FLAT_TUBA, seed = 3)
        assertEquals(15, notes.size)
        assertEquals("Kontra-F", NoteNames.germanOctaveName(notes.min()))
        assertEquals("kleines f", NoteNames.germanOctaveName(notes.max()))
        assertEquals(15, notes.toSet().size)
        assertEquals(notes.sorted().toSet(), choice.notes(Instrument.F_TUBA, seed = 4).toSet())
    }

    @Test
    fun scaleStartsOnTheLowestTonicInTheRange() {
        val scale = ExerciseChoice(kind = ExerciseKind.SCALE, direction = Direction.UP)
        assertEquals(
            "Kontra-B großes C großes D großes Es großes F großes G großes A großes B",
            names(scale.notes(Instrument.B_FLAT_TUBA, seed = 0))
        )
        val tooShort = scale.copy(lowMidi = 41, highMidi = 50)
        assertEquals(emptyList<Int>(), tooShort.notes(Instrument.B_FLAT_TUBA, seed = 0))
    }

    @Test
    fun chromaticAndRangeChecks() {
        val chromatic =
            ExerciseChoice(kind = ExerciseKind.CHROMATIC, lowMidi = 41, highMidi = 43, direction = Direction.DOWN)
        assertEquals(listOf(43, 42, 41), chromatic.notes(Instrument.F_TUBA, seed = 0))
        assertEquals(emptyList<Int>(), chromatic.copy(highMidi = 41).notes(Instrument.F_TUBA, seed = 0))
        assertEquals(emptyList<Int>(), chromatic.copy(lowMidi = 20).notes(Instrument.F_TUBA, seed = 0))
        assertThrows(IllegalArgumentException::class.java) { ExerciseChoice(lowMidi = 50, highMidi = 40) }
        assertEquals("Es-Dur", ExerciseChoice.keyName(3))
        assertEquals(Instrument.F_TUBA, ExerciseChoice.instrumentFor(TubaKey.E_FLAT))
        assertEquals(Instrument.B_FLAT_TUBA, ExerciseChoice.instrumentFor(null))
    }

    /** Delivers chunks only when the test pushes them, on the test thread. */
    private class ManualInput : AudioInput {
        override val sampleRate = 48_000
        override val sourceName = "MANUAL"
        var onChunk: ((FloatArray, Int) -> Unit)? = null
        var onError: ((Exception) -> Unit)? = null
        var closed = false

        override fun start(onChunk: (FloatArray, Int) -> Unit, onError: (Exception) -> Unit) {
            this.onChunk = onChunk
            this.onError = onError
        }

        fun push(seconds: Double) {
            val chunk = FloatArray(480)
            repeat((seconds * 100).toInt()) { onChunk!!(chunk, chunk.size) }
        }

        override fun close() {
            closed = true
        }
    }

    private class SilentDrone : DroneControl {
        val calls = ArrayList<String>()
        override fun play(frequencyHz: Double) {
            calls += "play"
        }

        override fun stop() {
            calls += "stop"
        }

        override fun playConfirmation() {
            calls += "chime"
        }
    }

    @Test
    fun engineRunsTheSessionOnTheInputAndAppliesControlsThere() {
        val input = ManualInput()
        val drone = SilentDrone()
        var closed = false
        val engine = PracticeEngine(
            PracticeSession(listOf(41, 43), 48_000.0, drone),
            input,
            route = { OutputRoute(OutputKind.HEADPHONES, "Bose") },
            onClose = { closed = true }
        )
        engine.start()
        assertEquals(listOf("play"), drone.calls)
        input.push(1.0)
        assertEquals(SessionPhase.CALIBRATING, engine.state.value.snapshot.phase)
        assertEquals("Bose", engine.state.value.route.name)
        input.push(3.0)
        assertEquals(SessionPhase.PLAYING, engine.state.value.snapshot.phase)

        engine.skip()
        assertEquals("queued until the next chunk", 0, engine.state.value.snapshot.index)
        input.push(0.01)
        assertEquals(1, engine.state.value.snapshot.index)

        engine.stop()
        input.push(0.01)
        val summary = checkNotNull(engine.state.value.summary)
        assertEquals(listOf(NoteStatus.SKIPPED, NoteStatus.STOPPED), summary.outcomes.map { it.status })
        assertTrue("load measured", engine.state.value.load!! > 0.0)

        engine.close()
        assertTrue(input.closed && closed)
    }

    @Test
    fun wrongOctaveTellsTheDirection() {
        assertEquals(1, octavesOff(1203.0))
        assertEquals(-1, octavesOff(-1188.0))
        assertEquals(2, octavesOff(2390.0))
        assertEquals(-2, octavesOff(-2410.0))
        assertEquals(0, octavesOff(-40.0))
    }

    @Test
    fun inputErrorsReachTheState() {
        val input = ManualInput()
        val engine = PracticeEngine(PracticeSession(listOf(41), 48_000.0, SilentDrone()), input)
        engine.start()
        assertNull(engine.state.value.error)
        input.onError!!(IllegalStateException("Kein Mikrofon verfügbar"))
        assertEquals("Kein Mikrofon verfügbar", engine.state.value.error)
    }
}
