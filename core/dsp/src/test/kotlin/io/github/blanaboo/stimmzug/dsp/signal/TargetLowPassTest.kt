package io.github.blanaboo.stimmzug.dsp.signal

import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class TargetLowPassTest {
    private val fs = 48_000.0

    /** Steady-state gain of [filter] for a sine at [frequencyHz], in dB. */
    private fun gainDb(filter: TargetLowPass, frequencyHz: Double): Double {
        val n = (fs * 2).toInt()
        val y = DoubleArray(n) { filter.process(sin(2 * PI * frequencyHz * it / fs)) }
        return SignalMath.rmsDb(y, n / 2, n) - SignalMath.amplitudeToDb(1 / sqrt(2.0))
    }

    private fun filterFor(targetHz: Double) = TargetLowPass(fs).apply { this.targetHz = targetHz }

    @ParameterizedTest(name = "target {0} Hz -> cutoff {1} Hz")
    @CsvSource("29.1, 250", "58.27, 250", "174.6, 250", "233.1, 326.34", "349.2, 488.88", "698.5, 977.9")
    fun `cutoff is 250 Hz or 1,4 times the target`(targetHz: Double, cutoffHz: Double) {
        assertEquals(cutoffHz, TargetLowPass.cutoffFor(targetHz), 0.01)
        assertEquals(cutoffHz, filterFor(targetHz).cutoffHz!!, 0.01)
    }

    @ParameterizedTest(name = "target {0} Hz")
    @CsvSource("58.27", "233.1", "698.5")
    fun `4th-order Butterworth response around the target`(targetHz: Double) {
        val cutoff = TargetLowPass.cutoffFor(targetHz)
        assertEquals(0.0, gainDb(filterFor(targetHz), targetHz), 0.3, "target itself")
        assertEquals(-3.01, gainDb(filterFor(targetHz), cutoff), 0.1, "at cutoff")
        assertEquals(-24.1, gainDb(filterFor(targetHz), 2 * cutoff), 0.5, "one octave above cutoff")
    }

    @Test
    fun `without target the input passes unchanged`() {
        val filter = TargetLowPass(fs)
        assertNull(filter.cutoffHz)
        assertEquals(0.123, filter.process(0.123))
    }

    @Test
    fun `changing the target rebuilds the filter, null switches it off`() {
        val filter = filterFor(58.27)
        filter.targetHz = 349.2
        assertEquals(488.88, filter.cutoffHz!!, 0.01)
        assertEquals(-3.01, gainDb(filter, 488.88), 0.1)
        filter.targetHz = null
        assertNull(filter.cutoffHz)
        assertEquals(-0.5, filter.process(-0.5))
    }

    @Test
    fun `setting the same target keeps the filter state`() {
        val filter = filterFor(100.0)
        val first = filter.process(1.0)
        filter.targetHz = 100.0
        // A rebuilt filter would answer the impulse again with `first`; the running one continues its response.
        assertEquals(false, filter.process(1.0) == first)
    }

    @Test
    fun `no filter when the cutoff would come close to Nyquist`() {
        val filter = TargetLowPass(1_000.0).apply { targetHz = 400.0 }
        assertNull(filter.cutoffHz)
        assertEquals(0.7, filter.process(0.7))
    }

    @Test
    fun `rejects invalid arguments`() {
        assertThrows<IllegalArgumentException> { TargetLowPass(0.0) }
        assertThrows<IllegalArgumentException> { TargetLowPass(fs).targetHz = -1.0 }
    }
}
