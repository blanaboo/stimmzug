package io.github.blanaboo.stimmzug.dsp.level

import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Measures room noise plus drone bleed while nobody plays and derives the [LevelGate] threshold.
 *
 * Feed it the frame levels (`PitchEstimate.levelDb`) of the calibration phase: drone sounding, player silent,
 * ~3 s. The ambient level is a high [percentile] of these levels, so the steady drone is fully covered while a
 * short click or cough barely moves it.
 */
class LevelCalibration(
    /** How much louder than the ambient level the tuba must be to be recognized for sure. */
    val marginDb: Double = 10.0,
    /** Percentile (0..1) of the calibration frame levels taken as ambient level. */
    val percentile: Double = 0.9
) {
    private val levels = ArrayList<Double>()

    init {
        require(marginDb > 0.0) { "margin must be positive, was $marginDb" }
        require(percentile in 0.0..1.0) { "percentile must be in 0..1, was $percentile" }
    }

    val frameCount: Int get() = levels.size

    fun add(levelDb: Double) {
        require(!levelDb.isNaN()) { "level must not be NaN" }
        levels += levelDb
    }

    fun reset() = levels.clear()

    fun result(): CalibrationResult {
        require(levels.size >= MIN_FRAMES) { "need at least $MIN_FRAMES frames, got ${levels.size}" }
        val sorted = levels.sorted()
        val ambient = sorted[((sorted.size - 1) * percentile).roundToInt()]
        return CalibrationResult(max(ambient, MIN_AMBIENT_DB), marginDb)
    }

    companion object {
        const val MIN_FRAMES = 10

        /** Floor for the ambient level: a muted or digitally silent input must not let anything through. */
        const val MIN_AMBIENT_DB = -70.0
    }
}

/**
 * Outcome of a [LevelCalibration]: the [ambientDb] level (room noise plus drone bleed) and the gate threshold.
 *
 * The gate does not open at ambient + margin. A tuba in tune with the drone keeps a fixed phase relation to it
 * (unison or octave: their harmonics coincide), and depending on that phase the two partly cancel. A tuba
 * [marginDb] louder than the ambient level then reaches, in the worst case of all coinciding harmonics in opposite
 * phase, only 20·log10(10^(margin/20) − 1) dB above it: 6.7 dB for a 10 dB margin. The gate therefore opens at that
 * worst case minus [LEVEL_TOLERANCE_DB]. Drone and noise alone stay at the ambient level, well below. See ADR 0003.
 */
data class CalibrationResult(val ambientDb: Double, val marginDb: Double) {
    /** Combined input level at which the gate opens. */
    val openThresholdDb: Double get() = ambientDb + openOffsetDb(marginDb)

    companion object {
        /** Scatter of frame levels of a steady sound (windows are not whole periods, noise, percentile). */
        const val LEVEL_TOLERANCE_DB = 1.0

        /** Lower bound of the open offset, so that drone or noise alone never opens the gate for tiny margins. */
        const val MIN_OPEN_OFFSET_DB = 3.0

        /** Open threshold relative to the ambient level for a required tuba excess of [marginDb]. */
        fun openOffsetDb(marginDb: Double): Double {
            val worstCase = 20.0 * log10(10.0.pow(marginDb / 20.0) - 1.0)
            return max(MIN_OPEN_OFFSET_DB, worstCase - LEVEL_TOLERANCE_DB)
        }
    }
}
