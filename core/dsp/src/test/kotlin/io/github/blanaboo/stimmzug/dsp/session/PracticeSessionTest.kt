package io.github.blanaboo.stimmzug.dsp.session

import io.github.blanaboo.stimmzug.dsp.match.MatchConfig
import io.github.blanaboo.stimmzug.dsp.match.MatchPhase
import io.github.blanaboo.stimmzug.dsp.music.NoteMath
import io.github.blanaboo.stimmzug.dsp.synth.DroneSynth
import io.github.blanaboo.stimmzug.dsp.synth.TubaSpectrum
import io.github.blanaboo.stimmzug.dsp.synth.TubaToneGenerator
import io.github.blanaboo.stimmzug.dsp.synth.Wavetable
import java.util.Locale
import java.util.Random
import kotlin.math.max
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Whole sessions in a closed loop: the session drives a real [DroneSynth], whose sound bleeds into the "microphone"
 * like a phone speaker would, and a virtual tubist reacts to the session like a player: waits for the pre-listen,
 * enters a bit flat, glides to the target, stops after the hit to breathe.
 */
class PracticeSessionTest {

    /** Records the drone commands and renders them, so the drone reaches the input. */
    private class RecordingDrone : DroneControl {
        val synth = DroneSynth(FS)
        val calls = ArrayList<String>()

        override fun play(frequencyHz: Double) {
            calls += String.format(Locale.ROOT, "play %.2f", frequencyHz)
            synth.play(frequencyHz)
        }

        override fun stop() {
            calls += "stop"
            synth.stop()
        }

        override fun playConfirmation() {
            calls += "chime"
            synth.playConfirmation()
        }
    }

    /** Plays the session's target while it is listening, unless the current note is in [silentOn]. */
    private class VirtualTubist(val entryCents: Double = -15.0, val glideSec: Double = 1.0) {
        val silentOn = HashSet<Int>()
        private var table: Wavetable? = null
        private var tableHz = 0.0
        private var phase = 0.0
        private var gain = 0.0
        private var startedAt: Double? = null

        /** Plays only after the pre-listen and before the hit, like a player waiting for the drone. */
        private fun wantsToPlay(snapshot: SessionSnapshot): Boolean {
            val notePhase = snapshot.note?.phase
            val listening = notePhase == MatchPhase.LISTENING || notePhase == MatchPhase.IN_RANGE
            return snapshot.phase == SessionPhase.PLAYING && listening && snapshot.index !in silentOn
        }

        private fun tuneTo(targetHz: Double) {
            if (targetHz == tableHz) return
            tableHz = targetHz
            table = TubaToneGenerator.tubaWavetable(targetHz, FS, TubaSpectrum.Dynamic.MEZZO, Random(targetHz.toLong()))
        }

        fun render(out: DoubleArray, snapshot: SessionSnapshot) {
            val playing = wantsToPlay(snapshot) && snapshot.targetHz != null
            snapshot.targetHz?.let(::tuneTo)
            startedAt = if (playing) startedAt ?: snapshot.timeSec else null
            val since = startedAt?.let { snapshot.timeSec - it } ?: 0.0
            val cents = entryCents * max(0.0, 1.0 - since / glideSec)
            val increment = tableHz * Math.pow(2.0, cents / 1200.0) / FS
            val goal = if (playing) 1.0 else 0.0
            for (i in out.indices) {
                // 50 ms attack and release.
                gain += (goal - gain).coerceIn(-1.0 / (0.05 * FS), 1.0 / (0.05 * FS))
                out[i] = TUBA_PEAK * gain * (table?.valueAt(phase) ?: 0.0)
                phase += increment
                if (phase >= 1.0) phase -= 1.0
            }
        }
    }

    private class Run(val session: PracticeSession, val drone: RecordingDrone, val tubist: VirtualTubist) {
        private val noise = Random(1)
        private val tuba = DoubleArray(CHUNK)
        private val droneOut = FloatArray(CHUNK)
        private val mic = FloatArray(CHUNK)
        var snapshot: SessionSnapshot = session.snapshot

        /** Runs until the session finishes or [maxSec] more seconds have passed; [onTick] may operate the session. */
        fun until(maxSec: Double, onTick: (SessionSnapshot) -> Unit = {}): SessionSnapshot {
            val end = session.timeSec + maxSec
            while (session.timeSec < end && snapshot.phase != SessionPhase.FINISHED) {
                tubist.render(tuba, snapshot)
                drone.synth.render(droneOut, 0, CHUNK)
                for (i in 0 until CHUNK) {
                    mic[i] = (tuba[i] + BLEED * droneOut[i] + NOISE * noise.nextGaussian()).toFloat()
                }
                snapshot = session.process(mic)
                onTick(snapshot)
                snapshot = session.snapshot
            }
            return snapshot
        }
    }

    private fun run(notes: List<Int>, tubist: VirtualTubist = VirtualTubist()): Run {
        val drone = RecordingDrone()
        val session = PracticeSession(notes, FS, drone, CONFIG)
        session.start()
        return Run(session, drone, tubist)
    }

    private fun hz(midi: Int) = String.format(Locale.ROOT, "%.2f", NoteMath.frequency(midi, CONFIG.a4Hz))

    @Test
    fun `a whole exercise with calibration, every note hit and the summary`() {
        val notes = listOf(34, 41, 46, 53)
        val run = run(notes)
        val calibrating = run.until(1.0)
        assertEquals(SessionPhase.CALIBRATING, calibrating.phase)
        assertEquals(1.0 / 3.0, calibrating.calibrationProgress, 0.02)

        val shownHits = HashSet<Int>()
        val end = run.until(80.0) { s ->
            val hit = s.hit
            if (hit != null) {
                assertEquals(s.index, hit.index)
                assertTrue(s.note!!.phase == MatchPhase.HIT || s.note.phase == MatchPhase.PAUSE, "hit shown: $s")
                shownHits += hit.index
            }
        }
        assertEquals(setOf(0, 1, 2, 3), shownHits, "every hit is shown during confirmation and breathing pause")
        assertEquals(SessionPhase.FINISHED, end.phase)
        assertEquals(4, end.index)
        val summary = run.session.summary()
        assertEquals(List(4) { NoteStatus.HIT }, summary.outcomes.map { it.status })
        assertEquals(4, summary.hits)
        // Reaction (pre-listen) + glide from −15 ct + 3 s hold.
        val mean = summary.meanTimeToHitSec!!
        assertTrue(mean in 3.2..5.0, "mean time to hit $mean")
        for (o in summary.outcomes) {
            assertTrue(o.result!!.entryCents!! < -5.0, "entered flat: $o")
            assertTrue(kotlin.math.abs(o.result.holdMeanCents!!) < 3.0, "held in tune: $o")
        }
        val slowest = summary.slowest!!
        assertEquals(summary.outcomes.maxOf { it.result!!.timeToHitSec!! }, slowest.result!!.timeToHitSec!!)
        assertEquals(
            notes.flatMap { listOf("play ${hz(it)}", "chime", "stop") },
            run.drone.calls,
            "drone on each note, chime and fade-out after each hit"
        )
        assertTrue(summary.durationSec in 30.0..60.0, "duration ${summary.durationSec}")
    }

    @Test
    fun `the drone alone never makes a hit, skip moves on`() {
        val tubist = VirtualTubist().apply { silentOn += 0 }
        val run = run(listOf(41, 46), tubist)
        val waiting = run.until(15.0)
        assertEquals(SessionPhase.PLAYING, waiting.phase)
        assertEquals(0, waiting.index)
        assertEquals(MatchPhase.LISTENING, waiting.note!!.phase)
        assertNull(waiting.hit)

        run.session.skip()
        assertEquals(1, run.session.snapshot.index)
        val end = run.until(30.0)
        assertEquals(SessionPhase.FINISHED, end.phase)
        val outcomes = run.session.summary().outcomes
        assertEquals(listOf(NoteStatus.SKIPPED, NoteStatus.HIT), outcomes.map { it.status })
        assertFalse(outcomes[0].result!!.hit)
    }

    @Test
    fun `pause in the middle of the hold starts the note again`() {
        val run = run(listOf(41, 46))
        var paused = false
        run.until(20.0) { s ->
            if (!paused && s.note?.phase == MatchPhase.IN_RANGE && s.note.holdProgress > 0.5) {
                run.session.pause()
                paused = true
            }
        }.also { assertEquals(SessionPhase.PAUSED, it.phase) }
        assertEquals("stop", run.drone.calls.last(), "drone off while paused")
        val pausedAt = run.session.timeSec
        run.until(2.0).also { assertEquals(SessionPhase.PAUSED, it.phase, "input ignored while paused") }

        run.session.resume()
        val resumedAt = run.session.timeSec
        assertEquals("play ${hz(41)}", run.drone.calls.last())
        assertEquals(MatchPhase.PRE_LISTEN, run.until(0.5).note!!.phase)
        val end = run.until(40.0)
        assertEquals(SessionPhase.FINISHED, end.phase)
        val first = run.session.summary().outcomes[0]
        assertEquals(NoteStatus.HIT, first.status)
        assertTrue(resumedAt - pausedAt >= 2.0)
        assertTrue(first.result!!.timeToHitSec!! >= 3.0, "hold counted from the restart: ${first.result}")
    }

    @Test
    fun `stop ends the session, the current note counts as stopped`() {
        val run = run(listOf(34, 41, 46, 53))
        val end = run.until(40.0) { s -> if (s.index == 1 && s.note?.phase == MatchPhase.LISTENING) run.session.stop() }
        assertEquals(SessionPhase.FINISHED, end.phase)
        val summary = run.session.summary()
        assertEquals(
            listOf(NoteStatus.HIT, NoteStatus.STOPPED, NoteStatus.NOT_REACHED, NoteStatus.NOT_REACHED),
            summary.outcomes.map { it.status }
        )
        assertFalse(summary.outcomes[1].result!!.hit)
        assertNull(summary.outcomes[2].result)
        assertEquals("stop", run.drone.calls.last())
    }

    @Test
    fun `back after a hit replays the previous note and records it again`() {
        val run = run(listOf(41, 46))
        var backDone = false
        var firstResult: Double? = null
        val end = run.until(60.0) { s ->
            if (!backDone && s.index == 1 && s.note?.phase == MatchPhase.LISTENING) {
                firstResult = run.session.summary().outcomes[0].result!!.timeToHitSec
                run.session.back()
                backDone = true
            }
        }
        assertTrue(backDone)
        assertEquals(SessionPhase.FINISHED, end.phase)
        val outcomes = run.session.summary().outcomes
        assertEquals(listOf(NoteStatus.HIT, NoteStatus.HIT), outcomes.map { it.status })
        assertEquals(
            listOf("play ${hz(41)}", "chime", "stop", "play ${hz(46)}", "play ${hz(41)}"),
            run.drone.calls.take(5)
        )
        assertTrue(firstResult != null)
    }

    @Test
    fun `controls do nothing where they make no sense`() {
        val run = run(listOf(41))
        run.session.pause()
        run.session.skip()
        run.session.back()
        run.session.resume()
        assertEquals(SessionPhase.CALIBRATING, run.session.snapshot.phase)
        run.session.stop()
        assertEquals(SessionPhase.FINISHED, run.session.snapshot.phase)
        assertEquals(listOf(NoteStatus.NOT_REACHED), run.session.summary().outcomes.map { it.status })
        run.session.stop()
        assertEquals(listOf("play ${hz(41)}", "stop"), run.drone.calls)
    }

    @Test
    fun `invalid use is rejected`() {
        assertThrows<IllegalArgumentException> { PracticeSession(emptyList(), FS, RecordingDrone()) }
        assertThrows<IllegalStateException> {
            PracticeSession(listOf(41), FS, RecordingDrone()).process(FloatArray(10))
        }
        val session = PracticeSession(listOf(41), FS, RecordingDrone()).apply { start() }
        assertThrows<IllegalStateException> { session.start() }
        assertThrows<IllegalArgumentException> { SessionConfig(calibrationSec = 0.8) }
        assertThrows<IllegalArgumentException> { SessionConfig(a4Hz = 0.0) }
        assertEquals(442.0, SessionConfig().a4Hz)
        assertNull(SessionSummary(emptyList(), 0.0).meanTimeToHitSec)
        assertNull(SessionSummary(emptyList(), 0.0).slowest)
        assertEquals(0, SessionSummary(emptyList(), 0.0).hits)
    }

    private companion object {
        const val FS = 48_000.0
        const val CHUNK = 480
        const val TUBA_PEAK = 0.25
        const val BLEED = 0.05
        const val NOISE = 0.001
        val CONFIG = SessionConfig(match = MatchConfig(holdSec = 3.0))
    }
}
