package io.github.blanaboo.stimmzug.dsp.level

import io.github.blanaboo.stimmzug.dsp.pitch.PitchEstimate

/**
 * Decides per frame whether the input is loud enough to be the player rather than room noise or drone bleed.
 *
 * Opens at [openThresholdDb] and closes again only below [closeThresholdDb] (= open − [hysteresisDb]), so a tone
 * hovering around the threshold does not flicker. Feed it every frame in time order. Not thread-safe.
 */
class LevelGate(openThresholdDb: Double, val hysteresisDb: Double = DEFAULT_HYSTERESIS_DB) {

    constructor(calibration: CalibrationResult, hysteresisDb: Double = DEFAULT_HYSTERESIS_DB) :
        this(calibration.openThresholdDb, hysteresisDb)

    /** Level at which the gate opens; may be moved while running (per-note threshold, #24), the state is kept. */
    var openThresholdDb: Double = openThresholdDb
        set(value) {
            require(value.isFinite()) { "open threshold must be finite, was $value" }
            field = value
        }

    init {
        require(openThresholdDb.isFinite()) { "open threshold must be finite, was $openThresholdDb" }
        require(hysteresisDb >= 0.0) { "hysteresis must not be negative, was $hysteresisDb" }
    }

    val closeThresholdDb: Double get() = openThresholdDb - hysteresisDb

    var isOpen: Boolean = false
        private set

    /** Feeds the level of the next frame; returns whether the gate is open for it. */
    fun update(levelDb: Double): Boolean {
        isOpen = levelDb >= if (isOpen) closeThresholdDb else openThresholdDb
        return isOpen
    }

    /** Feeds the frame's level; true if the frame is voiced and above the gate, i.e. it can be the player. */
    fun passes(estimate: PitchEstimate): Boolean = update(estimate.levelDb) && estimate.isVoiced

    fun reset() {
        isOpen = false
    }

    companion object {
        const val DEFAULT_HYSTERESIS_DB = 3.0
    }
}
