package io.github.blanaboo.stimmzug.dsp.match

import io.github.blanaboo.stimmzug.dsp.pitch.PitchEstimate
import io.github.blanaboo.stimmzug.dsp.pitch.PitchFrame
import kotlin.math.pow
import kotlin.math.roundToInt
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class MatchStateMachineTest {

    /** What the player does at time t (seconds since note start): cents off the target, or null = nothing heard. */
    private fun interface Player {
        fun cents(t: Double): Double?
    }

    private class Run(val statuses: List<MatchStatus>, val result: NoteResult) {
        fun phaseAt(t: Double) = statuses.last { it.timeSec <= t + 1e-9 }.phase
        val hitTime: Double? get() = statuses.firstOrNull { it.phase == MatchPhase.HIT }?.timeSec
    }

    /** Feeds frames every [HOP] s for [seconds]; the note starts at t = 0, so listening starts at 1 s. */
    private fun run(
        seconds: Double = 8.0,
        config: MatchConfig = MatchConfig(),
        clarity: Double = 0.95,
        passedGate: Boolean = true,
        player: Player
    ): Run {
        val machine = MatchStateMachine(config)
        machine.start(TARGET, 0.0)
        val statuses = (1..(seconds / HOP).toInt()).map { k ->
            val t = k * HOP
            val cents = player.cents(t)
            val estimate = if (cents == null) {
                PitchEstimate.unvoiced(-60.0)
            } else {
                PitchEstimate(TARGET * 2.0.pow(cents / 1200), clarity, -20.0)
            }
            machine.onFrame(PitchFrame(t, estimate), passedGate)
        }
        return Run(statuses, machine.result())
    }

    @Test
    fun `phases run from pre-listen to done for a player in tune`() {
        val r = run { 0.0 }
        assertEquals(MatchPhase.PRE_LISTEN, r.phaseAt(0.98), "frames during pre-listen are ignored")
        assertEquals(0.0, r.statuses.first { it.timeSec > 0.5 }.holdProgress)
        assertEquals(MatchPhase.IN_RANGE, r.phaseAt(1.0))
        assertEquals(0.5, r.statuses.first { it.timeSec >= 2.5 - 1e-9 }.holdProgress, 1e-6)
        assertEquals(4.0, r.hitTime!!, 1e-6)
        assertEquals(MatchPhase.HIT, r.phaseAt(4.98))
        assertEquals(MatchPhase.PAUSE, r.phaseAt(5.0))
        assertEquals(MatchPhase.PAUSE, r.phaseAt(6.98))
        assertEquals(MatchPhase.DONE, r.phaseAt(7.0))
        assertEquals(1.0, r.statuses.last().holdProgress)
        assertTrue(r.result.hit)
        assertEquals(3.0, r.result.timeToHitSec!!, 1e-6)
    }

    @Test
    fun `tolerance boundaries`() {
        for (cents in listOf(4.99, -4.99)) assertTrue(run { cents }.result.hit, "$cents ct must hit")
        for (cents in listOf(5.01, -5.01)) {
            val r = run { cents }
            assertFalse(r.result.hit, "$cents ct must not hit")
            val expected = if (cents > 0) PitchVerdict.SHARP else PitchVerdict.FLAT
            assertEquals(expected, r.statuses.last().verdict)
        }
        assertTrue(run(config = MatchConfig(toleranceCents = 10.0)) { 9.0 }.result.hit)
    }

    @Test
    fun `dropout shorter than the grace time keeps the hold`() {
        val r = run { t -> if (t >= 2.0 - 1e-9 && t < 2.2 - 1e-9) null else 0.0 }
        assertEquals(4.0, r.hitTime!!, 1e-6)
        assertTrue(r.statuses.filter { it.timeSec in 2.0..2.19 }.all { it.phase == MatchPhase.IN_RANGE })
    }

    @Test
    fun `dropout longer than the grace time restarts the hold`() {
        val r = run { t -> if (t >= 2.0 - 1e-9 && t < 2.5 - 1e-9) null else 0.0 }
        assertEquals(MatchPhase.LISTENING, r.phaseAt(2.4))
        assertEquals(5.5, r.hitTime!!, 1e-6)
        assertEquals(4.5, r.result.timeToHitSec!!, 1e-6)
    }

    @Test
    fun `short excursion out of the tolerance is forgiven, a long one is not`() {
        val short = run { t -> if (t >= 2.0 - 1e-9 && t < 2.2 - 1e-9) 20.0 else 0.0 }
        assertEquals(4.0, short.hitTime!!, 1e-6)
        assertTrue(short.statuses.any { it.verdict == PitchVerdict.SHARP })

        val long = run { t -> if (t >= 2.0 - 1e-9 && t < 2.5 - 1e-9) 20.0 else 0.0 }
        assertEquals(MatchPhase.LISTENING, long.phaseAt(2.45))
        // The median needs 3 of 5 frames back in tune: in range again at 2.54 s.
        assertEquals(5.54, long.hitTime!!, 1e-6)
    }

    @Test
    fun `median smoothing hides single outlier frames`() {
        // Every 10th frame jumps to +30 ct (not the first listening frame, which would delay entering the range).
        val r = run { t -> if ((t / HOP).roundToInt() % 10 == 5) 30.0 else 0.0 }
        assertEquals(4.0, r.hitTime!!, 1e-6)
        assertTrue(
            r.statuses.filter {
                it.phase == MatchPhase.IN_RANGE
            }.all { it.verdict == PitchVerdict.IN_TOLERANCE }
        )
    }

    @Test
    fun `settling into the note starts the hold only within the tolerance`() {
        // Glide from -40 ct to 0 within 0.6 s after listening starts, then stay.
        val r = run { t -> if (t < 1.6) -40.0 + 40.0 * (t - 1.0).coerceAtLeast(0.0) / 0.6 else 0.0 }
        val enteredAt = r.statuses.first { it.phase == MatchPhase.IN_RANGE }.timeSec
        assertTrue(enteredAt in 1.5..1.6, "entered the range at $enteredAt")
        assertEquals(enteredAt + 3.0, r.hitTime!!, 1e-6)
        // Median of the first five raw deviations: the start of the glide.
        assertEquals(-37.33, r.result.entryCents!!, 0.01)
        assertEquals(MatchPhase.LISTENING, r.phaseAt(1.2))
        assertEquals(PitchVerdict.FLAT, r.statuses.first { it.timeSec >= 1.2 - 1e-9 }.verdict)
    }

    @Test
    fun `wrong octave and wrong note never hit and are measured`() {
        for (cents in listOf(1200.0, -1200.0, 2405.0)) {
            val r = run { cents }
            assertFalse(r.result.hit)
            assertEquals(PitchVerdict.WRONG_OCTAVE, r.statuses.last().verdict, "$cents ct")
            assertEquals(7.0, r.result.wrongOctaveSec, 0.03, "$cents ct")
            assertEquals(0.0, r.result.wrongNoteSec)
            assertEquals(cents, r.statuses.last().centsDeviation!!, 1e-6)
        }
        val fifth = run { 700.0 }
        assertEquals(PitchVerdict.WRONG_NOTE, fifth.statuses.last().verdict)
        assertEquals(7.0, fifth.result.wrongNoteSec, 0.03)
        assertEquals(0.0, fifth.result.wrongOctaveSec)
    }

    @Test
    fun `player switching from the wrong octave to the right one hits`() {
        val r = run { t -> if (t < 2.0) -1200.0 else 1.0 }
        assertTrue(r.result.hit)
        assertEquals(1.0, r.result.wrongOctaveSec, 0.05)
        assertEquals(-1200.0, r.result.entryCents!!, 1e-6)
    }

    @Test
    fun `drone alone - gate closed - and unclear frames never hit`() {
        val drone = run(passedGate = false) { 0.0 }
        assertFalse(drone.result.hit)
        assertTrue(drone.statuses.all { it.verdict == PitchVerdict.SILENT && it.centsDeviation == null })
        assertEquals(MatchPhase.LISTENING, drone.statuses.last().phase)
        assertNull(drone.result.entryCents)

        val unclear = run(clarity = 0.7) { 0.0 }
        assertFalse(unclear.result.hit)
        assertTrue(run(clarity = 0.8) { 0.0 }.result.hit)
    }

    @Test
    fun `hold statistics describe the successful hold`() {
        val steady = run { 3.0 }.result
        assertEquals(3.0, steady.holdMeanCents!!, 1e-9)
        assertEquals(0.0, steady.holdSpreadCents!!, 1e-6)
        assertEquals(3.0, steady.entryCents!!, 1e-9)

        // Alternating ±2 ct survives the median of 5 (it alternates between +2 and -2).
        val wobbly = run { t -> if ((t / HOP).roundToInt() % 2 == 0) 2.0 else -2.0 }.result
        assertEquals(0.0, wobbly.holdMeanCents!!, 0.05)
        // Slightly below 2: the first windows (2 and 4 frames) average +2 and -2 to 0.
        assertEquals(2.0, wobbly.holdSpreadCents!!, 0.03)

        val missed = run { 30.0 }.result
        assertNull(missed.timeToHitSec)
        assertNull(missed.holdMeanCents)
        assertNull(missed.holdSpreadCents)
    }

    @Test
    fun `start resets everything for the next note`() {
        val machine = MatchStateMachine(MatchConfig(preListenSec = 0.0, holdSec = 0.1))
        machine.start(TARGET, 0.0)
        var t = 0.0
        while (machine.phase != MatchPhase.DONE) {
            t += HOP
            machine.onFrame(PitchFrame(t, PitchEstimate(2 * TARGET, 0.95, -20.0)), true)
            machine.onFrame(PitchFrame(t + HOP / 2, PitchEstimate(TARGET, 0.95, -20.0)), true)
            check(t < 10.0)
        }
        assertTrue(machine.result().hit)
        assertTrue(machine.result().wrongOctaveSec > 0.0)

        machine.start(440.0, t)
        assertEquals(MatchPhase.PRE_LISTEN, machine.phase)
        val fresh = machine.result()
        assertEquals(440.0, fresh.targetHz)
        assertFalse(fresh.hit)
        assertEquals(0.0, fresh.wrongOctaveSec)
        assertNull(fresh.entryCents)
    }

    @Test
    fun `validation`() {
        val machine = MatchStateMachine()
        assertThrows<IllegalStateException> { machine.result() }
        assertThrows<IllegalStateException> { machine.onFrame(PitchFrame(0.0, PitchEstimate.unvoiced(-60.0)), true) }
        assertThrows<IllegalArgumentException> { machine.start(0.0, 0.0) }
        assertThrows<IllegalArgumentException> { MatchConfig(toleranceCents = 0.0) }
        assertThrows<IllegalArgumentException> { MatchConfig(toleranceCents = 50.0) }
        assertThrows<IllegalArgumentException> { MatchConfig(holdSec = 0.0) }
        assertThrows<IllegalArgumentException> { MatchConfig(graceSec = -0.1) }
        assertThrows<IllegalArgumentException> { MatchConfig(minClarity = 1.1) }
        assertThrows<IllegalArgumentException> { MatchConfig(medianFrames = 0) }
    }

    private companion object {
        const val TARGET = 58.27
        const val HOP = 0.02
    }
}
