package io.github.blanaboo.stimmzug.practice

import io.github.blanaboo.stimmzug.audio.AudioInput
import io.github.blanaboo.stimmzug.audio.OutputKind
import io.github.blanaboo.stimmzug.audio.OutputRoute
import io.github.blanaboo.stimmzug.dsp.session.DroneControl
import io.github.blanaboo.stimmzug.dsp.session.PracticeSession
import io.github.blanaboo.stimmzug.dsp.session.SessionConfig
import java.io.File
import java.time.LocalDateTime
import javax.sound.sampled.AudioSystem
import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PracticeRecorderTest {

    @get:Rule
    val temp = TemporaryFolder()

    /** Pushes a tone in 10 ms chunks on the test thread. */
    private class ToneInput(private val hz: Double) : AudioInput {
        override val sampleRate = 48_000
        override val sourceName = "TEST_TONE"
        private var onChunk: ((FloatArray, Int) -> Unit)? = null
        private var n = 0L
        var forwarded = 0L

        override fun start(onChunk: (FloatArray, Int) -> Unit, onError: (Exception) -> Unit) {
            this.onChunk = onChunk
        }

        fun push(seconds: Double) {
            val chunk = FloatArray(sampleRate / 100)
            repeat((seconds * 100).toInt()) {
                for (i in chunk.indices) chunk[i] = (0.3 * sin(2 * PI * hz * n++ / sampleRate)).toFloat()
                checkNotNull(onChunk)(chunk, chunk.size)
            }
        }

        override fun close() = Unit
    }

    private object NoDrone : DroneControl {
        override fun play(frequencyHz: Double) = Unit
        override fun stop() = Unit
        override fun playConfirmation() = Unit
    }

    private var clock = LocalDateTime.of(2026, 11, 8, 20, 15, 3)

    private fun recorder(root: File = temp.root, keep: Int = PracticeRecorder.KEEP) =
        PracticeRecorder(root, listOf("Stimmzug Übungs-Mitschnitt (#75), App test"), keep) { clock }

    /** Runs a session on großes C through the recorder like the view model does. */
    private fun record(recorder: PracticeRecorder, seconds: Double): Pair<RecordingInput, PracticeEngine> {
        val tone = ToneInput(65.70)
        val notes = listOf(36)
        val config = SessionConfig()
        lateinit var engine: PracticeEngine
        val input = recorder.wrap(tone, TapInfo(notes, config, "Test")) { engine.state.value } as RecordingInput
        engine = PracticeEngine(
            PracticeSession(notes, input.sampleRate.toDouble(), NoDrone, config),
            input,
            route = { OutputRoute(OutputKind.HEADPHONES, "Bose QC Earbuds II") }
        )
        engine.start()
        tone.push(seconds)
        engine.close()
        return input to engine
    }

    @Test
    fun recordsAudioTimelineAndSession() {
        val (input, _) = record(recorder(), seconds = 5.0)
        assertNull(input.failure)
        assertEquals("20261108-201503", input.folder.name)

        AudioSystem.getAudioInputStream(File(input.folder, RecordingInput.AUDIO_FILE)).use { wav ->
            assertEquals(48_000f, wav.format.sampleRate)
            assertEquals(16, wav.format.sampleSizeInBits)
            assertEquals(1, wav.format.channels)
            assertEquals(5L * 48_000, wav.frameLength)
        }

        val rows = File(input.folder, RecordingInput.TIMELINE_FILE).readLines()
        assertEquals(RecordingInput.TIMELINE_HEADER.trim(), rows.first())
        val times = rows.drop(1).map { it.split(',')[0].toDouble() }
        assertEquals(0.01, times.first(), 1e-9)
        assertTrue("a row at least every 50 ms", times.zipWithNext().all { (a, b) -> b - a <= 0.05 + 1e-9 })
        assertTrue(rows.any { it.contains(",CALIBRATING,") })
        val playing = rows.last().split(',')
        assertEquals(listOf("großes C", "36", "65.704", "PLAYING"), playing.subList(2, 6))
        assertEquals("HEADPHONES", playing.last())

        val session = File(input.folder, RecordingInput.SESSION_FILE).readText()
        listOf(
            "Stimmzug Übungs-Mitschnitt (#75), App test",
            "Beginn: 2026-11-08 20:15:03",
            "a′: 442 Hz, Toleranz ±5 ct, Haltedauer 5 s, Gnadenzeit 0.3 s",
            "Töne: großes C (36)",
            "Eingang: TEST_TONE",
            "Ausgabe: HEADPHONES Bose QC Earbuds II",
            "Audio 5.0 s"
        ).forEach { assertTrue("session.txt has \"$it\":\n$session", session.contains(it)) }
    }

    @Test
    fun audioIsReadableBeforeTheSessionIsClosed() {
        val recorder = recorder()
        val tone = ToneInput(65.70)
        val config = SessionConfig()
        lateinit var engine: PracticeEngine
        val input = recorder.wrap(tone, TapInfo(listOf(36), config, "Test")) { engine.state.value } as RecordingInput
        engine = PracticeEngine(PracticeSession(listOf(36), 48_000.0, NoDrone, config), input)
        engine.start()
        tone.push(2.5)
        AudioSystem.getAudioInputStream(File(input.folder, RecordingInput.AUDIO_FILE)).use { wav ->
            assertTrue("header covers at least 2 s, was ${wav.frameLength}", wav.frameLength >= 2L * 48_000)
        }
        engine.close()
    }

    @Test
    fun keepsOnlyTheNewestRecordings() {
        val recorder = recorder(keep = 3)
        repeat(5) { i ->
            clock = LocalDateTime.of(2026, 11, 8, 20, 0, i)
            record(recorder, seconds = 0.1)
        }
        assertEquals(
            listOf("20261108-200002", "20261108-200003", "20261108-200004"),
            recorder.recordings().map { it.name }
        )
    }

    @Test
    fun recordingsInTheSameSecondGetTheirOwnFolder() {
        val recorder = recorder()
        val (first, _) = record(recorder, seconds = 0.1)
        val (second, _) = record(recorder, seconds = 0.1)
        assertEquals("20261108-201503", first.folder.name)
        assertEquals("20261108-201503-2", second.folder.name)
    }

    @Test
    fun aFailingDiskDoesNotStopThePractice() {
        val blocked = temp.newFile("not-a-folder")
        val (input, engine) = record(recorder(root = blocked), seconds = 4.0)
        assertNotNull(input.failure)
        assertTrue("the session went on", engine.state.value.snapshot.timeSec >= 3.99)
    }
}
