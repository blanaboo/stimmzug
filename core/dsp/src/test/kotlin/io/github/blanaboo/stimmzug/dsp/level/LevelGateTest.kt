package io.github.blanaboo.stimmzug.dsp.level

import io.github.blanaboo.stimmzug.dsp.pitch.PitchEstimate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class LevelGateTest {

    @Test
    fun `opens at the threshold and closes only below the hysteresis`() {
        val gate = LevelGate(openThresholdDb = -30.0, hysteresisDb = 3.0)
        assertEquals(-33.0, gate.closeThresholdDb)
        assertFalse(gate.update(-30.1))
        assertTrue(gate.update(-30.0))
        assertTrue(gate.update(-32.9))
        assertTrue(gate.update(-33.0))
        assertFalse(gate.update(-33.1))
        assertFalse(gate.update(-31.0), "closed gate needs the open threshold again")
        assertTrue(gate.update(-12.0))
        assertTrue(gate.isOpen)
    }

    @Test
    fun `a frame passes only when it is voiced and above the gate`() {
        val gate = LevelGate(openThresholdDb = -30.0)
        assertFalse(gate.passes(PitchEstimate(58.27, 0.95, -40.0)), "voiced but too quiet")
        assertTrue(gate.passes(PitchEstimate(58.27, 0.95, -20.0)))
        assertFalse(gate.passes(PitchEstimate.unvoiced(-20.0)), "loud but unvoiced")
        assertTrue(gate.isOpen)
    }

    @Test
    fun `is built from a calibration`() {
        val gate = LevelGate(CalibrationResult(ambientDb = -40.0, marginDb = 10.0))
        assertEquals(-40.0 + CalibrationResult.openOffsetDb(10.0), gate.openThresholdDb, 1e-12)
        assertEquals(LevelGate.DEFAULT_HYSTERESIS_DB, gate.hysteresisDb)
    }

    @Test
    fun `reset closes the gate and parameters are validated`() {
        val gate = LevelGate(openThresholdDb = -30.0, hysteresisDb = 0.0)
        assertTrue(gate.update(-30.0))
        assertFalse(gate.update(-30.1), "no hysteresis")
        gate.update(0.0)
        gate.reset()
        assertFalse(gate.isOpen)
        assertThrows<IllegalArgumentException> { LevelGate(openThresholdDb = Double.NEGATIVE_INFINITY) }
        assertThrows<IllegalArgumentException> { LevelGate(openThresholdDb = -30.0, hysteresisDb = -1.0) }
    }
}
