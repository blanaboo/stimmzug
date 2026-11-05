package io.github.blanaboo.stimmzug.dsp.level

import kotlin.math.log10
import kotlin.math.pow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class LevelCalibrationTest {

    private fun calibrate(vararg levels: Double) = LevelCalibration().apply { levels.forEach(::add) }

    @Test
    fun `ambient level is the 90th percentile of the calibration frames`() {
        val calibration = calibrate(*DoubleArray(100) { -99.0 + it })
        assertEquals(100, calibration.frameCount)
        assertEquals(-10.0, calibration.result().ambientDb, 1e-9)
    }

    @Test
    fun `a short cough during calibration does not raise the ambient level`() {
        val calibration = calibrate(*DoubleArray(140) { -45.0 }, *DoubleArray(10) { -10.0 })
        assertEquals(-45.0, calibration.result().ambientDb, 1e-9)
    }

    @Test
    fun `digital silence is floored so that nothing opens the gate`() {
        val result = calibrate(*DoubleArray(50) { -160.0 }).result()
        assertEquals(LevelCalibration.MIN_AMBIENT_DB, result.ambientDb, 1e-9)
    }

    @Test
    fun `gate opens at the worst-case level of a tuba one margin above ambient`() {
        // Tuba 10 dB above the drone, all harmonics in opposite phase: (3.16 - 1)^2 = +6.70 dB, minus 1 dB tolerance.
        assertEquals(5.698, CalibrationResult.openOffsetDb(10.0), 1e-3)
        val result = calibrate(*DoubleArray(20) { -40.0 }).result()
        assertEquals(10.0, result.marginDb)
        assertEquals(-40.0 + 5.698, result.openThresholdDb, 1e-3)

        for (margin in listOf(10.0, 12.0, 20.0, 30.0)) {
            val worstCase = 20 * log10(10.0.pow(margin / 20) - 1)
            assertEquals(worstCase - CalibrationResult.LEVEL_TOLERANCE_DB, CalibrationResult.openOffsetDb(margin), 1e-9)
        }
    }

    @Test
    fun `small margins keep a minimum distance to the ambient level`() {
        assertEquals(CalibrationResult.MIN_OPEN_OFFSET_DB, CalibrationResult.openOffsetDb(6.0))
        assertEquals(CalibrationResult.MIN_OPEN_OFFSET_DB, CalibrationResult.openOffsetDb(0.1))
    }

    @Test
    fun `custom margin and percentile`() {
        val calibration = LevelCalibration(marginDb = 20.0, percentile = 0.5)
        DoubleArray(11) { -60.0 + it }.forEach(calibration::add)
        val result = calibration.result()
        assertEquals(-55.0, result.ambientDb, 1e-9)
        assertEquals(20.0, result.marginDb)
    }

    @Test
    fun `validation and reset`() {
        assertThrows<IllegalArgumentException> { LevelCalibration(marginDb = 0.0) }
        assertThrows<IllegalArgumentException> { LevelCalibration(percentile = 1.5) }
        val calibration = calibrate(*DoubleArray(LevelCalibration.MIN_FRAMES - 1) { -50.0 })
        assertThrows<IllegalArgumentException> { calibration.result() }
        assertThrows<IllegalArgumentException> { calibration.add(Double.NaN) }
        calibration.add(-50.0)
        assertEquals(-50.0, calibration.result().ambientDb, 1e-9)
        calibration.reset()
        assertEquals(0, calibration.frameCount)
    }
}
