package io.github.blanaboo.stimmzug.dsp.signal

import kotlin.math.max

/**
 * Low-pass just above the target note, applied to the input before pitch detection (ADR 0005, #60).
 *
 * A drone from the phone's loudspeaker reaches the microphone mostly through its upper partials, because the speaker
 * hardly reproduces low fundamentals. Those partials are exactly periodic and make the detector report the drone
 * instead of the tuba. Cutting them off just above the target's fundamental leaves the tuba's strong low partials.
 *
 * The cutoff has to stay low: from about twice the target upwards the drone partials pull the estimate towards the
 * target again. In spike #32 a tuba 25 ct flat on "kleines f" read as 6 ct flat with a 700 Hz cutoff, which would
 * report a hit that is none.
 *
 * 4th-order Butterworth (two biquads), streamed sample by sample. Setting a different [targetHz] rebuilds the filter,
 * its state restarts from zero. Without a target the input passes unchanged. Not thread-safe.
 */
class TargetLowPass(val sampleRate: Double) {
    private var stage1: Biquad? = null
    private var stage2: Biquad? = null

    /** Cutoff in use, `null` while the filter passes everything. */
    var cutoffHz: Double? = null
        private set

    /** Note the player should play; `null` = no filtering. */
    var targetHz: Double? = null
        set(value) {
            if (value == field) return
            require(value == null || value > 0.0) { "target must be positive, was $value" }
            field = value
            // A cutoff close to Nyquist would not filter anything the detector cares about.
            val cutoff = value?.let(::cutoffFor)?.takeIf { it < MAX_CUTOFF_FRACTION * sampleRate }
            cutoffHz = cutoff
            stage1 = cutoff?.let { Biquad.lowPass(it, sampleRate, Biquad.BUTTERWORTH4_Q1) }
            stage2 = cutoff?.let { Biquad.lowPass(it, sampleRate, Biquad.BUTTERWORTH4_Q2) }
        }

    init {
        require(sampleRate > 0.0) { "sample rate must be positive" }
    }

    fun process(x: Double): Double {
        val first = stage1 ?: return x
        val second = stage2 ?: return x
        return second.process(first.process(x))
    }

    companion object {
        /** Lowest cutoff: keeps the first partials of low notes, whose fundamental the phone microphone damps. */
        const val MIN_CUTOFF_HZ = 250.0

        /** Cutoff relative to the target for higher notes. */
        const val CUTOFF_FACTOR = 1.4

        private const val MAX_CUTOFF_FRACTION = 0.45

        /** Cutoff for a target: `max(250 Hz, 1.4 × target)`. */
        fun cutoffFor(targetHz: Double): Double = max(MIN_CUTOFF_HZ, CUTOFF_FACTOR * targetHz)
    }
}
