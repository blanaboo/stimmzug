package io.github.blanaboo.stimmzug.dsp.signal

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Second-order IIR filter (RBJ audio EQ cookbook), transposed direct form II.
 *
 * Used to model the low-frequency roll-off of phone microphones and as anti-aliasing filter before decimation.
 * Instances hold filter state and are not thread-safe.
 */
class Biquad private constructor(
    private val b0: Double,
    private val b1: Double,
    private val b2: Double,
    private val a1: Double,
    private val a2: Double
) {
    private var z1 = 0.0
    private var z2 = 0.0

    fun process(x: Double): Double {
        val y = b0 * x + z1
        z1 = b1 * x - a1 * y + z2
        z2 = b2 * x - a2 * y
        return y
    }

    fun processInPlace(buffer: DoubleArray, from: Int = 0, to: Int = buffer.size) {
        for (i in from until to) buffer[i] = process(buffer[i])
    }

    fun reset() {
        z1 = 0.0
        z2 = 0.0
    }

    companion object {
        const val BUTTERWORTH_Q = 0.7071067811865476

        fun lowPass(cutoffHz: Double, sampleRate: Double, q: Double = BUTTERWORTH_Q): Biquad {
            val (cosW, alpha) = prewarp(cutoffHz, sampleRate, q)
            return normalized(
                b0 = (1 - cosW) / 2,
                b1 = 1 - cosW,
                b2 = (1 - cosW) / 2,
                a0 = 1 + alpha,
                a1 = -2 * cosW,
                a2 = 1 - alpha
            )
        }

        fun highPass(cutoffHz: Double, sampleRate: Double, q: Double = BUTTERWORTH_Q): Biquad {
            val (cosW, alpha) = prewarp(cutoffHz, sampleRate, q)
            return normalized(
                b0 = (1 + cosW) / 2,
                b1 = -(1 + cosW),
                b2 = (1 + cosW) / 2,
                a0 = 1 + alpha,
                a1 = -2 * cosW,
                a2 = 1 - alpha
            )
        }

        private fun prewarp(cutoffHz: Double, sampleRate: Double, q: Double): Pair<Double, Double> {
            require(cutoffHz > 0 && cutoffHz < sampleRate / 2) { "cutoff must be in (0, fs/2), was $cutoffHz" }
            require(q > 0) { "q must be positive" }
            val w0 = 2 * PI * cutoffHz / sampleRate
            return cos(w0) to sin(w0) / (2 * q)
        }

        @Suppress("LongParameterList") // the six coefficients of the transfer function
        private fun normalized(b0: Double, b1: Double, b2: Double, a0: Double, a1: Double, a2: Double) =
            Biquad(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)

        /** Magnitude of a 2nd-order Butterworth high-pass at [frequencyHz] (analog prototype), for tests/docs. */
        fun butterworthHighPassGain(frequencyHz: Double, cutoffHz: Double): Double {
            val ratio = cutoffHz / frequencyHz
            return 1.0 / sqrt(1.0 + ratio * ratio * ratio * ratio)
        }
    }
}
