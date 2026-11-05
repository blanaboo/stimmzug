package io.github.blanaboo.stimmzug.dsp.synth

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/**
 * Short bell-like confirmation sound (a few partials with exponential decay), rendered sample by sample and mixed
 * into the drone output by [DroneSynth]. Its peak never exceeds [PEAK]. Audio-thread only.
 */
internal class ConfirmationChime(private val sampleRate: Double) {
    private val phases = DoubleArray(RATIOS.size)
    private val increments = DoubleArray(RATIOS.size)
    private val envelopes = DoubleArray(RATIOS.size)
    private val decayFactors = DoubleArray(RATIOS.size) { exp(-1.0 / (DECAY_SEC[it] * sampleRate)) }
    private val lengthSamples = (LENGTH_SEC * sampleRate).toInt()
    private val attackSamples = (ATTACK_SEC * sampleRate).toInt()
    private val releaseSamples = (RELEASE_SEC * sampleRate).toInt()
    private var position = -1

    val isActive: Boolean get() = position >= 0

    /** Starts the chime on [baseHz]; ignored while a chime is still sounding (a restart would click). */
    fun trigger(baseHz: Double) {
        if (isActive) return
        for (p in RATIOS.indices) {
            phases[p] = 0.0
            increments[p] = baseHz * RATIOS[p] / sampleRate
            envelopes[p] = AMPLITUDES[p]
        }
        position = 0
    }

    fun next(): Double {
        if (position < 0) return 0.0
        var s = 0.0
        for (p in RATIOS.indices) {
            s += envelopes[p] * sin(2 * PI * phases[p])
            envelopes[p] *= decayFactors[p]
            phases[p] += increments[p]
            if (phases[p] >= 1.0) phases[p] -= 1.0
        }
        val attack = if (position < attackSamples) 0.5 - 0.5 * cos(PI * position / attackSamples) else 1.0
        val toEnd = lengthSamples - position
        val release = if (toEnd < releaseSamples) 0.5 - 0.5 * cos(PI * toEnd / releaseSamples) else 1.0
        position = if (position + 1 >= lengthSamples) -1 else position + 1
        return PEAK / AMPLITUDE_SUM * s * attack * release
    }

    companion object {
        /** Upper bound of the chime's absolute value. */
        const val PEAK = 0.15
        const val LENGTH_SEC = 2.5
        private const val ATTACK_SEC = 0.005
        private const val RELEASE_SEC = 0.2

        /** Bell-like partials: fundamental, octave, a slightly inharmonic "tierce" region and a bright overtone. */
        private val RATIOS = doubleArrayOf(1.0, 2.0, 2.76, 5.4)
        private val AMPLITUDES = doubleArrayOf(1.0, 0.5, 0.35, 0.2)
        private val DECAY_SEC = doubleArrayOf(0.6, 0.4, 0.25, 0.12)
        private val AMPLITUDE_SUM = AMPLITUDES.sum()

        /** The chime sits on the drone's pitch class, transposed into 880..1760 Hz; C6 without a note. */
        fun baseFrequencyFor(noteHz: Double?): Double {
            var f = noteHz ?: return 1046.5
            while (f < 880.0) f *= 2.0
            while (f >= 1760.0) f /= 2.0
            return f
        }
    }
}
