package io.github.blanaboo.stimmzug.dsp.synth

import io.github.blanaboo.stimmzug.dsp.signal.SignalMath
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min

/**
 * Simple spectral model of a tuba tone: harmonic levels follow an envelope that is flat between [LOW_KNEE_HZ] and a
 * dynamic-dependent knee and rolls off below and above it. Low notes therefore get a comparatively weak
 * fundamental and strong 2nd–6th harmonics, high notes become nearly sinusoidal – roughly what tubas do.
 *
 * The model only needs to be plausible: it drives test signals and the drone timbre, it is not a physical model.
 */
object TubaSpectrum {
    /** Below this frequency harmonics fall off with [LOW_ROLLOFF_DB_PER_OCTAVE]. */
    const val LOW_KNEE_HZ = 100.0
    const val LOW_ROLLOFF_DB_PER_OCTAVE = 6.0

    enum class Dynamic(val kneeHz: Double, val rolloffDbPerOctave: Double) {
        PIANO(kneeHz = 200.0, rolloffDbPerOctave = 18.0),
        MEZZO(kneeHz = 300.0, rolloffDbPerOctave = 12.0),
        FORTE(kneeHz = 500.0, rolloffDbPerOctave = 9.0)
    }

    /** Envelope level in dB (0 dB = flat region) at [frequencyHz]. */
    fun envelopeDb(frequencyHz: Double, dynamic: Dynamic): Double = when {
        frequencyHz < LOW_KNEE_HZ -> -LOW_ROLLOFF_DB_PER_OCTAVE * log2(LOW_KNEE_HZ / frequencyHz)
        frequencyHz <= dynamic.kneeHz -> 0.0
        else -> -dynamic.rolloffDbPerOctave * log2(frequencyHz / dynamic.kneeHz)
    }

    /**
     * Linear amplitudes of harmonics 1..n (index 0 = fundamental), normalized to a maximum of 1.
     * Harmonics are limited to [maxFrequencyHz] and to 45 % of [sampleRate] (no aliasing).
     */
    fun harmonicAmplitudes(
        fundamentalHz: Double,
        sampleRate: Double,
        dynamic: Dynamic = Dynamic.MEZZO,
        maxFrequencyHz: Double = 6000.0,
        maxHarmonics: Int = 80
    ): DoubleArray {
        require(fundamentalHz > 0) { "fundamental must be positive" }
        val limit = min(maxFrequencyHz, 0.45 * sampleRate)
        val count = max(1, min(maxHarmonics, (limit / fundamentalHz).toInt()))
        val levels = DoubleArray(count) { SignalMath.dbToAmplitude(envelopeDb(fundamentalHz * (it + 1), dynamic)) }
        val peak = levels.max()
        return DoubleArray(count) { levels[it] / peak }
    }
}
