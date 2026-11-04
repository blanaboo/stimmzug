package io.github.blanaboo.stimmzug.dsp.signal

/**
 * Linear (non-circular) autocorrelation r(τ) = Σ_{j=0}^{n−1−τ} x[j]·x[j+τ] computed via FFT in O(n log n).
 *
 * Zero padding to at least 2n avoids wrap-around. Instances keep work buffers and are **not** thread-safe.
 */
class Autocorrelation(val maxLength: Int) {
    private val fft = Fft(Fft.nextPowerOfTwo(2 * maxLength))
    private val re = DoubleArray(fft.size)
    private val im = DoubleArray(fft.size)

    init {
        require(maxLength >= 2) { "maxLength must be >= 2, was $maxLength" }
    }

    /** Writes r(0..maxLag) of the first [length] samples of [x] into [out]. */
    fun compute(x: DoubleArray, length: Int, maxLag: Int, out: DoubleArray) {
        require(length in 2..maxLength) { "length must be in 2..$maxLength, was $length" }
        require(length <= x.size) { "x holds only ${x.size} samples" }
        require(maxLag in 0 until length) { "maxLag must be in 0 until $length, was $maxLag" }
        require(out.size > maxLag) { "out must hold ${maxLag + 1} values" }

        System.arraycopy(x, 0, re, 0, length)
        re.fill(0.0, length, fft.size)
        im.fill(0.0)
        fft.forward(re, im)
        for (i in 0 until fft.size) {
            re[i] = re[i] * re[i] + im[i] * im[i]
            im[i] = 0.0
        }
        fft.inverse(re, im)
        System.arraycopy(re, 0, out, 0, maxLag + 1)
    }

    companion object {
        /** Reference O(n·maxLag) implementation, used by tests. */
        fun direct(x: DoubleArray, length: Int, maxLag: Int): DoubleArray = DoubleArray(maxLag + 1) { lag ->
            var sum = 0.0
            for (j in 0 until length - lag) sum += x[j] * x[j + lag]
            sum
        }
    }
}
