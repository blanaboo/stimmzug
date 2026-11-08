package io.github.blanaboo.stimmzug.dsp.level

import io.github.blanaboo.stimmzug.dsp.pitch.PitchEstimate
import io.github.blanaboo.stimmzug.dsp.pitch.PitchFrame
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class NoteLevelGateTest {
    /** Ambient −50 dBFS, margin 10 dB: the session gate opens at −44,3 dBFS. */
    private val calibration = CalibrationResult(ambientDb = -50.0, marginDb = 10.0)

    private fun frame(t: Double, levelDb: Double) = PitchFrame(t, PitchEstimate(58.27, 0.99, levelDb))

    /** Frames every 20 ms from [from] (exclusive) to [to] (inclusive) at [levelDb]; returns the last gate decision. */
    private fun NoteLevelGate.feed(from: Double, to: Double, levelDb: Double): Boolean {
        var passed = false
        var t = from
        while (t < to - 1e-9) {
            t += 0.02
            passed = passes(frame(t, levelDb))
        }
        return passed
    }

    @Test
    fun `before the first complete chunk the session threshold applies`() {
        val gate = NoteLevelGate(calibration).apply { startNote(10.0) }
        assertEquals(calibration.openThresholdDb, gate.openThresholdDb, 1e-9)
        gate.feed(10.0, 10.6, -44.0)
        assertNull(gate.noteDroneDb, "settle 0.4 s + first chunk until 10.6 s")
        assertEquals(0.0, gate.raiseDb)
    }

    @Test
    fun `a louder drone raises the threshold by the difference`() {
        val gate = NoteLevelGate(calibration).apply { startNote(0.0) }
        // Drone 6 dB louder than at the calibration: it would pass the session gate (−44,3 dBFS).
        assertFalse(gate.feed(0.0, 1.0, -44.0), "drone passed")
        assertEquals(-44.0, gate.noteDroneDb!!, 1e-9)
        assertEquals(6.0, gate.raiseDb, 1e-9)
        assertEquals(calibration.openThresholdDb + 6.0, gate.openThresholdDb, 1e-9)
        assertTrue(gate.feed(1.0, 1.2, -36.0), "player 14 dB above the calibration")
    }

    @Test
    fun `a quieter drone does not lower the threshold`() {
        val gate = NoteLevelGate(calibration).apply { startNote(0.0) }
        gate.feed(0.0, 1.0, -60.0)
        assertEquals(0.0, gate.raiseDb)
        assertEquals(calibration.openThresholdDb, gate.openThresholdDb, 1e-9)
    }

    @Test
    fun `the raise is capped`() {
        val gate = NoteLevelGate(calibration, maxRaiseDb = 8.0).apply { startNote(0.0) }
        gate.feed(0.0, 1.0, -30.0)
        assertEquals(8.0, gate.raiseDb, 1e-9)
    }

    @Test
    fun `the quietest chunk counts, so a player entering later does not lift the gate`() {
        val gate = NoteLevelGate(calibration).apply { startNote(0.0) }
        gate.feed(0.0, 0.7, -47.0) // drone
        gate.feed(0.7, 1.0, -35.0) // player enters during the pre-listen
        assertEquals(3.0, gate.raiseDb, 1e-9)
        assertTrue(gate.feed(1.0, 1.1, -35.0))
    }

    @Test
    fun `a breath reveals the drone and lowers the threshold again`() {
        val gate = NoteLevelGate(calibration).apply { startNote(0.0) }
        gate.feed(0.0, 2.0, -38.0) // player from the start: looks like a drone 12 dB louder
        assertEquals(10.0, gate.raiseDb, 1e-9, "capped")
        assertFalse(gate.feed(2.0, 2.2, -38.0), "player below the raised threshold")
        gate.feed(2.2, 2.7, -50.0) // breath: drone at the calibration level
        assertEquals(0.0, gate.raiseDb, 1e-9)
        assertTrue(gate.feed(2.7, 2.8, -38.0), "player recognized after the breath")
    }

    @Test
    fun `frames during the settle time and too short chunks are ignored`() {
        val gate = NoteLevelGate(calibration).apply { startNote(0.0) }
        gate.feed(0.0, 0.4, -20.0) // fade-in transient
        gate.passes(frame(0.45, -50.0))
        gate.passes(frame(0.70, -50.0)) // closes the first chunk with a single frame
        assertNull(gate.noteDroneDb)
        gate.feed(0.70, 1.0, -48.0)
        assertEquals(-48.0, gate.noteDroneDb!!, 1e-9)
    }

    @Test
    fun `a new note starts the measurement afresh`() {
        val gate = NoteLevelGate(calibration).apply { startNote(0.0) }
        gate.feed(0.0, 1.0, -44.0)
        assertEquals(6.0, gate.raiseDb, 1e-9)
        gate.startNote(5.0)
        assertNull(gate.noteDroneDb)
        assertEquals(0.0, gate.raiseDb)
        gate.feed(5.0, 6.0, -48.0)
        assertEquals(2.0, gate.raiseDb, 1e-9)
    }

    @Test
    fun `the session gate threshold can be moved while running`() {
        val gate = LevelGate(-40.0)
        assertTrue(gate.update(-39.0))
        gate.openThresholdDb = -30.0
        assertTrue(gate.update(-32.0), "open gate stays open above the new close threshold (−33 dBFS)")
        assertFalse(gate.update(-34.0), "closes below it")
        assertFalse(gate.update(-31.0), "reopens only at the new open threshold")
        assertTrue(gate.update(-30.0))
    }

    @Test
    fun `rejects invalid use`() {
        assertThrows<IllegalStateException> { NoteLevelGate(calibration).passes(frame(0.0, -40.0)) }
        assertThrows<IllegalArgumentException> { NoteLevelGate(calibration, maxRaiseDb = -1.0) }
        assertThrows<IllegalArgumentException> { NoteLevelGate(calibration, settleSec = -0.1) }
        assertThrows<IllegalArgumentException> { NoteLevelGate(calibration, chunkSec = 0.0) }
        assertThrows<IllegalArgumentException> { LevelGate(-40.0).openThresholdDb = Double.NaN }
    }
}
