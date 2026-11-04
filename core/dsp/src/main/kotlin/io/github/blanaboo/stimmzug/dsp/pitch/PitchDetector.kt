package io.github.blanaboo.stimmzug.dsp.pitch

/** Result of analysing one window of audio. */
data class PitchEstimate(
    /** Estimated fundamental in Hz, or 0.0 if the window is not periodic enough (unvoiced). */
    val frequencyHz: Double,
    /** Periodicity strength in 0..1 (1 = perfectly periodic). */
    val clarity: Double,
    /** RMS level of the analysed window in dBFS. */
    val levelDb: Double
) {
    val isVoiced: Boolean get() = frequencyHz > 0.0

    companion object {
        fun unvoiced(levelDb: Double, clarity: Double = 0.0) = PitchEstimate(0.0, clarity.coerceIn(0.0, 1.0), levelDb)
    }
}

/**
 * Estimates the fundamental frequency of the most recent audio.
 *
 * With a [targetHz] (the note the player should play) the search is restricted to ±6 semitones around the target
 * plus an octave check, which makes the detector robust against the octave errors that tuba tones with a weak
 * fundamental (phone microphones roll off below ~100 Hz) would otherwise cause. A tone one octave above or below
 * the target is reported at its true frequency so that callers can tell the player "wrong octave".
 */
interface PitchDetector {
    val name: String

    /** Number of most recent samples [detect] needs at [sampleRate] for [targetHz] (`null` = free search). */
    fun requiredSamples(sampleRate: Double, targetHz: Double?): Int

    /** Analyses samples[end − requiredSamples, end). */
    fun detect(samples: DoubleArray, sampleRate: Double, targetHz: Double?, end: Int = samples.size): PitchEstimate
}
