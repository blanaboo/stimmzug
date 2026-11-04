package io.github.blanaboo.stimmzug.dsp.signal

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * In-place iterative radix-2 complex FFT of a fixed power-of-two [size].
 *
 * Twiddle and bit-reversal tables are precomputed and immutable, so one instance may be shared between threads;
 * the transformed arrays belong to the caller.
 */
class Fft(val size: Int) {
    private val cosTable: DoubleArray
    private val sinTable: DoubleArray
    private val reversed: IntArray

    init {
        require(size >= 2 && (size and (size - 1)) == 0) { "size must be a power of two >= 2, was $size" }
        val levels = Integer.numberOfTrailingZeros(size)
        cosTable = DoubleArray(size / 2) { cos(2.0 * PI * it / size) }
        sinTable = DoubleArray(size / 2) { sin(2.0 * PI * it / size) }
        reversed = IntArray(size) { Integer.reverse(it) ushr (Int.SIZE_BITS - levels) }
    }

    /** Forward transform: X[k] = Σ x[n]·e^(−2πikn/N). */
    fun forward(re: DoubleArray, im: DoubleArray) = transform(re, im, sign = -1.0)

    /** Inverse transform including the 1/N scaling. */
    fun inverse(re: DoubleArray, im: DoubleArray) {
        transform(re, im, sign = 1.0)
        val scale = 1.0 / size
        for (i in 0 until size) {
            re[i] *= scale
            im[i] *= scale
        }
    }

    private fun transform(re: DoubleArray, im: DoubleArray, sign: Double) {
        require(re.size >= size && im.size >= size) { "arrays must hold at least $size values" }
        for (i in 0 until size) {
            val j = reversed[i]
            if (j > i) {
                val tr = re[i]
                re[i] = re[j]
                re[j] = tr
                val ti = im[i]
                im[i] = im[j]
                im[j] = ti
            }
        }
        var half = 1
        while (half < size) {
            val step = size / (2 * half)
            var start = 0
            while (start < size) {
                var k = 0
                for (j in start until start + half) {
                    val wr = cosTable[k]
                    val wi = sign * sinTable[k]
                    val l = j + half
                    val tr = wr * re[l] - wi * im[l]
                    val ti = wr * im[l] + wi * re[l]
                    re[l] = re[j] - tr
                    im[l] = im[j] - ti
                    re[j] += tr
                    im[j] += ti
                    k += step
                }
                start += 2 * half
            }
            half *= 2
        }
    }

    companion object {
        /** Smallest power of two that is >= [n] (and >= 2). */
        fun nextPowerOfTwo(n: Int): Int {
            require(n in 1..(1 shl 30)) { "n out of range: $n" }
            var p = 2
            while (p < n) p = p shl 1
            return p
        }
    }
}
