package io.github.blanaboo.stimmzug.dsp.level

import io.github.blanaboo.stimmzug.dsp.pitch.PitchFrame
import kotlin.math.min

/**
 * Level gate for one note at a time (#24): the session calibration plus a raise for notes whose drone is louder.
 *
 * The calibration at the start of a session measures the drone of one note. Later notes can reach the microphone
 * louder: the loudspeaker reproduces them better, the volume was turned up, or the target low-pass in front of the
 * detector (ADR 0005) lets more of them through. In unison that drone alone would open the gate and count as a
 * player who is perfectly in tune.
 *
 * So the gate measures the drone of every note from the start of its pre-listen (drone on, player waiting):
 * - Frames from [settleSec] after the note start (drone fade-in, detector window) are grouped into chunks of
 *   [chunkSec]. The median level of each chunk is a candidate for the drone level.
 * - The **quietest chunk so far** is taken as the note's drone level. A player who enters during the pre-listen
 *   leaves the chunks before the entry at the drone level. One who already plays when the note starts shows the drone
 *   level at the first breath, and the threshold drops again.
 * - The threshold is raised by how much that level exceeds the calibration's ambient level, never lowered, and at
 *   most by [maxRaiseDb]. In the worst case, a quieter chunk that is no drone, the gate is just the session gate.
 *
 * Feed every frame from [startNote] on, in time order, with [passes]. Not thread-safe.
 */
class NoteLevelGate(
    val calibration: CalibrationResult,
    val maxRaiseDb: Double = DEFAULT_MAX_RAISE_DB,
    val settleSec: Double = DEFAULT_SETTLE_SEC,
    val chunkSec: Double = DEFAULT_CHUNK_SEC,
    hysteresisDb: Double = LevelGate.DEFAULT_HYSTERESIS_DB
) {
    private val gate = LevelGate(calibration.openThresholdDb, hysteresisDb)
    private val chunk = ArrayList<Double>()
    private var started = false
    private var measureFrom = 0.0
    private var chunkEnd = 0.0

    init {
        require(maxRaiseDb >= 0.0) { "maximum raise must not be negative, was $maxRaiseDb" }
        require(settleSec >= 0.0) { "settle time must not be negative, was $settleSec" }
        require(chunkSec > 0.0) { "chunk length must be positive, was $chunkSec" }
    }

    /** Drone level measured for the current note (quietest chunk so far), `null` before the first complete chunk. */
    var noteDroneDb: Double? = null
        private set

    /** How far the threshold of the current note lies above the session's. */
    val raiseDb: Double
        get() = noteDroneDb?.let { (it - calibration.ambientDb).coerceIn(0.0, maxRaiseDb) } ?: 0.0

    val openThresholdDb: Double get() = calibration.openThresholdDb + raiseDb

    /** Starts the next note at [timeSec] (sample time, the start of its pre-listen); the measurement starts afresh. */
    fun startNote(timeSec: Double) {
        started = true
        measureFrom = timeSec + settleSec
        chunkEnd = measureFrom + chunkSec
        chunk.clear()
        noteDroneDb = null
        gate.reset()
        gate.openThresholdDb = openThresholdDb
    }

    /** Feeds the next frame; true if it is voiced and loud enough to be the player. */
    fun passes(frame: PitchFrame): Boolean {
        check(started) { "startNote() first" }
        measure(frame)
        gate.openThresholdDb = openThresholdDb
        return gate.passes(frame.estimate)
    }

    private fun measure(frame: PitchFrame) {
        val t = frame.timeSec
        if (t < measureFrom - EPS) return
        while (t > chunkEnd + EPS) {
            closeChunk()
            chunkEnd += chunkSec
        }
        chunk += frame.estimate.levelDb
    }

    private fun closeChunk() {
        if (chunk.size >= MIN_CHUNK_FRAMES) {
            val median = chunk.sorted()[chunk.size / 2]
            noteDroneDb = noteDroneDb?.let { min(it, median) } ?: median
        }
        chunk.clear()
    }

    companion object {
        /** Covers a volume change of several steps; a player who enters too early cannot lift the gate further. */
        const val DEFAULT_MAX_RAISE_DB = 10.0

        /** Drone fade-in (0.3 s) plus the detector's analysis window. */
        const val DEFAULT_SETTLE_SEC = 0.4
        const val DEFAULT_CHUNK_SEC = 0.2

        /** A chunk with fewer frames (gap in the input) is not used. */
        const val MIN_CHUNK_FRAMES = 5

        private const val EPS = 1e-9
    }
}
