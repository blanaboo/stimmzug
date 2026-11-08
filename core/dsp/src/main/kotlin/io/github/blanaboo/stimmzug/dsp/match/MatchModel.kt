package io.github.blanaboo.stimmzug.dsp.match

/** Parameters of the hit logic for one note. All times are audio sample time in seconds. */
data class MatchConfig(
    /** The smoothed deviation must stay within ±this many cents. */
    val toleranceCents: Double = 5.0,
    /** How long the note must be held within the tolerance for a hit (the owner prefers 5 s to 3 s, #68). */
    val holdSec: Double = DEFAULT_HOLD_SEC,
    /** Dropouts and excursions out of the tolerance shorter than this do not reset the hold. */
    val graceSec: Double = 0.3,
    /** Frames with a lower clarity count as dropouts. */
    val minClarity: Double = 0.8,
    /** Median smoothing over this many player frames (5 frames = 100 ms at the 20 ms hop). */
    val medianFrames: Int = 5,
    /** The drone sounds alone first so the player can hear the note; frames are ignored meanwhile. */
    val preListenSec: Double = 1.0,
    /** Confirmation after the hit (bell, green ring, drone fades out). */
    val confirmSec: Double = 1.0,
    /** Breathing pause before the next note. */
    val pauseSec: Double = 2.0
) {
    init {
        require(toleranceCents > 0.0 && toleranceCents < SAME_NOTE_CENTS) {
            "tolerance must be in (0, $SAME_NOTE_CENTS) cents, was $toleranceCents"
        }
        require(holdSec > 0.0) { "hold time must be positive, was $holdSec" }
        require(graceSec >= 0.0 && preListenSec >= 0.0 && confirmSec >= 0.0 && pauseSec >= 0.0) {
            "grace, pre-listen, confirm and pause times must not be negative"
        }
        require(minClarity in 0.0..1.0) { "minimum clarity must be in 0..1, was $minClarity" }
        require(medianFrames >= 1) { "median window must hold at least one frame, was $medianFrames" }
    }

    companion object {
        const val DEFAULT_HOLD_SEC = 5.0

        /** Deviations below this (half a semitone) still belong to the target note. */
        const val SAME_NOTE_CENTS = 50.0
    }
}

/** Phases of one note: Vorhören → Hören → Im Bereich → Treffer → Pause → done. */
enum class MatchPhase {
    /** Drone only, the player waits. */
    PRE_LISTEN,

    /** Waiting for the player to reach the tolerance. */
    LISTENING,

    /** Within the tolerance, the hold time is running. */
    IN_RANGE,

    /** Held long enough: confirmation. */
    HIT,

    /** Breathing pause before the next note. */
    PAUSE,

    /** The note is finished; the next one can start. */
    DONE
}

/** What the player currently plays relative to the target. */
enum class PitchVerdict {
    /** Nobody heard: gate closed, unvoiced or not clear enough. */
    SILENT,
    IN_TOLERANCE,

    /** Right note, too low. */
    FLAT,

    /** Right note, too high. */
    SHARP,

    /** The target note in another octave. */
    WRONG_OCTAVE,
    WRONG_NOTE
}

/** Live feedback after each frame. */
data class MatchStatus(
    val phase: MatchPhase,
    val timeSec: Double,
    /** Smoothed deviation from the target in cents (sign: + = sharp), `null` while nobody is heard. */
    val centsDeviation: Double?,
    val verdict: PitchVerdict,
    /** Progress of the hold time in 0..1 (1 from the hit on). */
    val holdProgress: Double
)

/** Measurements of one note for the session summary. */
data class NoteResult(
    val targetHz: Double,
    val hit: Boolean,
    /** From the end of the pre-listen phase to the hit, `null` without a hit. */
    val timeToHitSec: Double?,
    /** Median deviation of the first player frames: where the player started. */
    val entryCents: Double?,
    /** Mean and standard deviation of the smoothed deviation during the hold that led to the hit. */
    val holdMeanCents: Double?,
    val holdSpreadCents: Double?,
    /** Time the player was heard in another octave or on another note. */
    val wrongOctaveSec: Double,
    val wrongNoteSec: Double
)
