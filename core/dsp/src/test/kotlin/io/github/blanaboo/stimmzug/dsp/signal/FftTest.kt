package io.github.blanaboo.stimmzug.dsp.signal

import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class FftTest {

    @Test
    fun `forward transform matches naive DFT`() {
        val n = 64
        val random = Random(7)
        val re = DoubleArray(n) { random.nextGaussian() }
        val im = DoubleArray(n) { random.nextGaussian() }
        val (expRe, expIm) = naiveDft(re, im)
        Fft(n).forward(re, im)
        for (k in 0 until n) {
            assertEquals(expRe[k], re[k], 1e-9)
            assertEquals(expIm[k], im[k], 1e-9)
        }
    }

    @Test
    fun `inverse restores the input`() {
        val n = 4096
        val random = Random(3)
        val original = DoubleArray(n) { random.nextGaussian() }
        val re = original.copyOf()
        val im = DoubleArray(n)
        val fft = Fft(n)
        fft.forward(re, im)
        fft.inverse(re, im)
        var maxError = 0.0
        for (i in 0 until n) maxError = max(maxError, max(abs(re[i] - original[i]), abs(im[i])))
        assertEquals(0.0, maxError, 1e-10)
    }

    @Test
    fun `pure tone lands in its bin`() {
        val n = 256
        val re = DoubleArray(n) { cos(2 * PI * 10 * it / n) }
        val im = DoubleArray(n)
        Fft(n).forward(re, im)
        assertEquals(n / 2.0, re[10], 1e-9)
        assertEquals(n / 2.0, re[n - 10], 1e-9)
        assertEquals(0.0, abs(re[11]) + abs(im[11]), 1e-9)
    }

    @Test
    fun `size must be a power of two`() {
        assertThrows<IllegalArgumentException> { Fft(1000) }
        assertThrows<IllegalArgumentException> { Fft(1) }
        assertThrows<IllegalArgumentException> { Fft(8).forward(DoubleArray(4), DoubleArray(4)) }
    }

    @Test
    fun `next power of two`() {
        assertEquals(2, Fft.nextPowerOfTwo(1))
        assertEquals(1024, Fft.nextPowerOfTwo(1024))
        assertEquals(2048, Fft.nextPowerOfTwo(1025))
        assertThrows<IllegalArgumentException> { Fft.nextPowerOfTwo(0) }
    }

    private fun naiveDft(re: DoubleArray, im: DoubleArray): Pair<DoubleArray, DoubleArray> {
        val n = re.size
        val outRe = DoubleArray(n)
        val outIm = DoubleArray(n)
        for (k in 0 until n) {
            for (t in 0 until n) {
                val angle = -2 * PI * k * t / n
                outRe[k] += re[t] * cos(angle) - im[t] * sin(angle)
                outIm[k] += re[t] * sin(angle) + im[t] * cos(angle)
            }
        }
        return outRe to outIm
    }
}
