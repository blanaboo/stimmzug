package io.github.blanaboo.stimmzug.dsp.signal

import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class AutocorrelationTest {

    @Test
    fun `fft result equals direct computation`() {
        val random = Random(11)
        val length = 3000
        val x = DoubleArray(length) { random.nextGaussian() }
        val maxLag = 1700
        val out = DoubleArray(maxLag + 1)
        Autocorrelation(4096).compute(x, length, maxLag, out)
        val expected = Autocorrelation.direct(x, length, maxLag)
        for (lag in 0..maxLag) {
            assertEquals(expected[lag], out[lag], 1e-9 * expected[0], "lag $lag")
        }
    }

    @Test
    fun `periodic signal peaks at its period`() {
        val period = 400
        val length = 4 * period
        val x = DoubleArray(length) { sin(2 * PI * it / period) }
        val out = DoubleArray(length)
        Autocorrelation(length).compute(x, length, length - 1, out)
        val lagRange = (period / 2)..(3 * period / 2)
        // For a pure sine spanning whole periods the overlap bias and the edge term cancel exactly.
        assertEquals(period, lagRange.maxBy { out[it] })
        assertTrue(abs(out[period / 2] + out[0] * 0.875) < 0.01 * out[0], "half-period is anti-correlated")
    }

    @Test
    fun `invalid arguments are rejected`() {
        val ac = Autocorrelation(16)
        val x = DoubleArray(32)
        assertThrows<IllegalArgumentException> { ac.compute(x, 32, 4, DoubleArray(5)) }
        assertThrows<IllegalArgumentException> { ac.compute(x, 16, 16, DoubleArray(17)) }
        assertThrows<IllegalArgumentException> { ac.compute(x, 16, 4, DoubleArray(4)) }
        assertThrows<IllegalArgumentException> { ac.compute(DoubleArray(8), 16, 4, DoubleArray(5)) }
        assertThrows<IllegalArgumentException> { Autocorrelation(1) }
    }
}
