package io.github.blanaboo.stimmzug.dsp.session

import io.github.blanaboo.stimmzug.dsp.match.MatchConfig
import io.github.blanaboo.stimmzug.dsp.match.MatchStatus
import io.github.blanaboo.stimmzug.dsp.match.NoteResult

/** What a practice session needs from the audio output. [io.github.blanaboo.stimmzug.dsp.synth.DroneSynth] has it. */
interface DroneControl {
    /** Starts the drone on [frequencyHz] or changes to it. */
    fun play(frequencyHz: Double)

    /** Fades the drone out. */
    fun stop()

    /** Sounds the confirmation chime after a hit. */
    fun playConfirmation()
}

/** Parameters of a practice session. Times in seconds of audio sample time. */
data class SessionConfig(
    /** Concert pitch; brass players usually tune to 442 Hz, the app's default. */
    val a4Hz: Double = DEFAULT_A4_HZ,
    /** The player stays silent while the drone sounds on the first note; room and drone bleed set the level gate. */
    val calibrationSec: Double = 3.0,
    /** Start of the calibration not measured: drone fade-in and the detector's first windows. */
    val calibrationSettleSec: Double = 0.5,
    /** Hit logic per note: pre-listen, tolerance, hold time, confirmation and breathing pause. */
    val match: MatchConfig = MatchConfig(),
    /** How much louder than room and drone the tuba must be to be heard for sure (ADR 0003). */
    val marginDb: Double = 10.0
) {
    init {
        require(a4Hz > 0.0) { "a4 must be positive, was $a4Hz" }
        require(calibrationSettleSec >= 0.0) { "settle time must not be negative" }
        require(calibrationSec - calibrationSettleSec >= MIN_CALIBRATION_SEC) {
            "calibration must measure at least $MIN_CALIBRATION_SEC s after the settle time"
        }
    }

    companion object {
        const val DEFAULT_A4_HZ = 442.0
        const val MIN_CALIBRATION_SEC = 0.5
    }
}

enum class SessionPhase {
    /** Drone on the first note, the player waits. */
    CALIBRATING,

    /** Notes one after the other; details in [SessionSnapshot.note]. */
    PLAYING,

    /** Stopped by the player; the drone is off. Resuming starts the note again with its pre-listen. */
    PAUSED,

    /** All notes done or stopped. */
    FINISHED
}

enum class NoteStatus {
    HIT,
    SKIPPED,

    /** The session was stopped while this note was being played. */
    STOPPED,

    /** The session ended before this note. */
    NOT_REACHED
}

/** How one note of the exercise went. [result] holds the measurements if the note was played at all. */
data class NoteOutcome(
    val index: Int,
    val midi: Int,
    val targetHz: Double,
    val status: NoteStatus,
    val result: NoteResult?
)

/** Everything a screen shows at one moment. */
data class SessionSnapshot(
    val timeSec: Double,
    val phase: SessionPhase,
    /** 0..1 during the calibration, 1 afterwards. */
    val calibrationProgress: Double,
    /** Current note (0-based); equals [noteCount] when finished. */
    val index: Int,
    val noteCount: Int,
    val midi: Int?,
    val targetHz: Double?,
    /** Live state of the current note while [phase] is [SessionPhase.PLAYING], `null` before its first frame. */
    val note: MatchStatus?,
    /** The current note's outcome once it is hit (shown during confirmation and breathing pause). */
    val hit: NoteOutcome?
)

/** Result of a whole session. */
data class SessionSummary(val outcomes: List<NoteOutcome>, val durationSec: Double) {
    val hits: Int get() = outcomes.count { it.status == NoteStatus.HIT }

    /** Mean time from the end of the pre-listen to the hit, over the hit notes. */
    val meanTimeToHitSec: Double?
        get() = outcomes.mapNotNull { it.result?.timeToHitSec.takeIf { _ -> it.status == NoteStatus.HIT } }
            .takeIf { it.isNotEmpty() }
            ?.average()

    /** The hit note that took longest, the one worth a look. */
    val slowest: NoteOutcome?
        get() = outcomes.filter { it.status == NoteStatus.HIT }.maxByOrNull { it.result?.timeToHitSec ?: 0.0 }
}
