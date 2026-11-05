package io.github.blanaboo.stimmzug.tools.replay

import io.github.blanaboo.stimmzug.dsp.match.PitchVerdict
import io.github.blanaboo.stimmzug.dsp.music.NoteMath
import io.github.blanaboo.stimmzug.tools.replay.TestAudio.FS
import kotlin.math.abs
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ReplayTest {
    private val target = NoteMath.frequency(34)

    @Test
    fun `player in tune after calibration hits`() {
        val samples = TestAudio.scene(34, tubaHz = target)
        val result = Replay.run(samples, FS.toDouble(), ReplayOptions(targetHz = target, calibrationSec = 1.5))
        val note = result.note!!
        assertTrue(note.hit, "$note")
        // Tuba from 2.0 s, machine from 1.5 s: 0.5 s waiting + onset + 3 s hold.
        assertTrue(note.timeToHitSec!! in 3.5..4.0, "time to hit ${note.timeToHitSec}")
        assertNotNull(result.calibration)
        assertTrue(result.rows.filter { it.frame.timeSec <= 1.5 }.all { it.inCalibration && it.status == null })
        assertTrue(
            result.rows.filter {
                it.frame.timeSec in 1.6..1.9
            }.none { it.passedGate },
            "drone stays below the gate"
        )
        assertEquals(7.0, result.durationSec, 1e-9)
        // A frame every 20 ms once the detector has enough history (~0.1 s for Kontra-B).
        assertTrue(result.rows.size in 340..350, "${result.rows.size} frames")
        assertEquals(7.0, result.rows.last().frame.timeSec, 1e-9)
    }

    @Test
    fun `drone alone without the gate looks like a perfect player, with the gate it does not`() {
        val drone = TestAudio.scene(34, tubaHz = null)
        val ungated = Replay.run(drone, FS.toDouble(), ReplayOptions(targetHz = target))
        assertTrue(ungated.note!!.hit, "without a gate the drone is taken for the player")
        val gated = Replay.run(drone, FS.toDouble(), ReplayOptions(targetHz = target, calibrationSec = 1.5))
        assertFalse(gated.note!!.hit)
        assertTrue(gated.rows.mapNotNull { it.status }.all { it.verdict == PitchVerdict.SILENT })
    }

    @Test
    fun `free mode tracks the pitch without hit logic, YIN works as well`() {
        val samples = TestAudio.scene(41, tubaHz = NoteMath.frequency(41))
        for (detector in DetectorChoice.entries) {
            val result = Replay.run(samples, FS.toDouble(), ReplayOptions(detector = detector))
            assertNull(result.note)
            assertNull(result.calibration)
            assertTrue(result.rows.all { it.status == null && !it.inCalibration })
            val steady = result.rows.filter { it.frame.timeSec in 3.0..6.5 && it.passedGate }
            assertTrue(steady.size > 150, "$detector: ${steady.size} voiced frames")
            assertTrue(steady.all { abs(NoteMath.centsFromNote(it.frame.estimate.frequencyHz, 41)) < 10 })
        }
    }

    @Test
    fun `validation`() {
        assertThrows<IllegalArgumentException> { ReplayOptions(targetHz = 0.0) }
        assertThrows<IllegalArgumentException> { ReplayOptions(calibrationSec = -1.0) }
        val short = FloatArray(FS) // 1 s; frames start after ~0.2 s, calibration of 0.1 s is too short
        assertThrows<IllegalArgumentException> { Replay.run(short, FS.toDouble(), ReplayOptions(calibrationSec = 0.1)) }
    }
}
