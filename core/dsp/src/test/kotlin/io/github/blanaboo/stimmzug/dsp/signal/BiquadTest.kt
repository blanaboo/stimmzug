package io.github.blanaboo.stimmzug.dsp.signal

import kotlin.math.PI
import kotlin.math.sin
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class BiquadTest {
    private val fs = 48_000.0

    /** Steady-state gain of [filter] for a sine at [frequencyHz], in dB. */
    private fun gainDb(filter: Biquad, frequencyHz: Double): Double {
        val n = (fs * 2).toInt()
        val x = DoubleArray(n) { sin(2 * PI * frequencyHz * it / fs) }
        filter.processInPlace(x)
        val settled = n / 2
        return SignalMath.rmsDb(x, settled, n) - SignalMath.amplitudeToDb(1 / Math.sqrt(2.0))
    }

    @ParameterizedTest(name = "high-pass 120 Hz at {0} Hz")
    @CsvSource("30", "60", "120", "240", "1000")
    fun `high-pass follows the Butterworth response`(frequencyHz: Double) {
        val expected = SignalMath.amplitudeToDb(Biquad.butterworthHighPassGain(frequencyHz, 120.0))
        assertEquals(expected, gainDb(Biquad.highPass(120.0, fs), frequencyHz), 0.3)
    }

    @Test
    fun `low-pass is -3 dB at cutoff and attenuates above`() {
        assertEquals(-3.01, gainDb(Biquad.lowPass(1000.0, fs), 1000.0), 0.1)
        assertEquals(0.0, gainDb(Biquad.lowPass(1000.0, fs), 100.0), 0.1)
        assertEquals(-24.0, gainDb(Biquad.lowPass(1000.0, fs), 4000.0), 1.0)
    }

    @Test
    fun `reset clears the state`() {
        val f = Biquad.lowPass(500.0, fs)
        val first = f.process(1.0)
        f.process(1.0)
        f.reset()
        assertEquals(first, f.process(1.0), 1e-15)
    }

    @Test
    fun `invalid parameters are rejected`() {
        assertThrows<IllegalArgumentException> { Biquad.lowPass(30_000.0, fs) }
        assertThrows<IllegalArgumentException> { Biquad.highPass(0.0, fs) }
        assertThrows<IllegalArgumentException> { Biquad.highPass(100.0, fs, q = 0.0) }
    }
}
