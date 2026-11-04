package io.github.blanaboo.stimmzug.dsp.signal

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Small level and spectrum helpers shared by detectors, generators and tests. */
object SignalMath {
    /** Level used for digital silence, to keep dB values finite. */
    const val SILENCE_DB = -160.0

    fun dbToAmplitude(db: Double): Double = 10.0.pow(db / 20.0)

    fun amplitudeToDb(amplitude: Double): Double {
        if (amplitude <= 0.0) return SILENCE_DB
        return max(SILENCE_DB, 20.0 * log10(amplitude))
    }

    fun rms(x: DoubleArray, from: Int = 0, to: Int = x.size): Double {
        require(to > from) { "empty range" }
        var sum = 0.0
        for (i in from until to) sum += x[i] * x[i]
        return sqrt(sum / (to - from))
    }

    fun rmsDb(x: DoubleArray, from: Int = 0, to: Int = x.size): Double = amplitudeToDb(rms(x, from, to))

    fun peak(x: DoubleArray, from: Int = 0, to: Int = x.size): Double {
        var p = 0.0
        for (i in from until to) p = max(p, abs(x[i]))
        return p
    }

    /**
     * Amplitude of the sinusoid at [frequencyHz] in x[from, to) via a Hann-windowed single-bin DFT.
     * Returns the peak amplitude for a stationary sine whose frequency is resolved by the window length.
     */
    fun toneAmplitude(
        x: DoubleArray,
        frequencyHz: Double,
        sampleRate: Double,
        from: Int = 0,
        to: Int = x.size
    ): Double {
        val n = to - from
        require(n > 1) { "need at least 2 samples" }
        var re = 0.0
        var im = 0.0
        var windowSum = 0.0
        val w = 2 * PI * frequencyHz / sampleRate
        for (i in 0 until n) {
            val hann = 0.5 - 0.5 * cos(2 * PI * i / (n - 1))
            val v = x[from + i] * hann
            re += v * cos(w * i)
            im -= v * sin(w * i)
            windowSum += hann
        }
        return 2.0 * sqrt(re * re + im * im) / windowSum
    }

    fun toDoubleArray(x: FloatArray): DoubleArray = DoubleArray(x.size) { x[it].toDouble() }
}
