package io.github.blanaboo.stimmzug.data.log

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One practice session in the session log (#37): what was played with which instrument and settings, and every
 * attempt with its measurements. Stored on the device only, one JSON object per line. [version] lets later
 * versions add fields (with defaults) and still read older lines.
 */
@Serializable
data class LoggedSession(
    val version: Int = VERSION,
    /** Local date and time of the start, ISO 8601 ("2026-11-09T18:30:05"). */
    @SerialName("started_at") val startedAt: String,
    @SerialName("duration_sec") val durationSec: Double,
    /** The active instrument profile, `null` without one (the app then uses a B♭ tuba). */
    val profile: LoggedProfile? = null,
    @SerialName("a4_hz") val a4Hz: Double,
    @SerialName("tolerance_cents") val toleranceCents: Double,
    @SerialName("hold_sec") val holdSec: Double,
    /** Microphone source in use, e.g. "UNPROCESSED". */
    @SerialName("audio_source") val audioSource: String? = null,
    /** Where the drone sounded: HEADPHONES, SPEAKER, OTHER or UNKNOWN, and the device name if known. */
    @SerialName("drone_route") val droneRoute: String,
    @SerialName("drone_timbre") val droneTimbre: String,
    /** Room and drone level of the calibration and the resulting gate threshold, in dBFS. */
    @SerialName("ambient_db") val ambientDb: Double? = null,
    @SerialName("threshold_db") val thresholdDb: Double? = null,
    /** Today's answer to the warm-up hint (#44): WARMED_UP or WARMED_UP_NOW, `null` if not given today. */
    @SerialName("warm_up") val warmUp: String? = null,
    /** How long the warm-up timer ran (WARMED_UP_NOW). */
    @SerialName("warm_up_sec") val warmUpSec: Int? = null,
    /** Minutes from the answer to the start of this session. */
    @SerialName("warm_up_age_min") val warmUpAgeMin: Double? = null,
    /** The exercise as chosen, readable for a person. */
    val exercise: String,
    val attempts: List<LoggedAttempt> = emptyList()
) {
    companion object {
        const val VERSION = 1
    }
}

/** The instrument a session was played on. */
@Serializable
data class LoggedProfile(
    val name: String,
    /** B_FLAT, F, E_FLAT or C. */
    val key: String,
    val valves: Int,
    @SerialName("extra_valves") val extraValves: List<String> = emptyList(),
    val compensating: Boolean = false,
    @SerialName("trigger_valves") val triggerValves: List<Int> = emptyList(),
    @SerialName("main_slide_trigger") val mainSlideTrigger: Boolean = false
)

/** One attempt at a note, see `NoteAttempt` in the core. */
@Serializable
data class LoggedAttempt(
    /** 1, 2, … in the order of the session. */
    val number: Int,
    /** Position of the note in the exercise. */
    val index: Int,
    val midi: Int,
    @SerialName("target_hz") val targetHz: Double,
    /** Natural tone and valves of the standard fingering for the profile, `null` if there is none. */
    val partial: Int? = null,
    val valves: String? = null,
    /** Session time when the note started (drone on). */
    @SerialName("start_sec") val startSec: Double,
    /** The note of the attempt before, for the interval. */
    @SerialName("previous_midi") val previousMidi: Int? = null,
    /** HIT, SKIPPED (player went on or back) or STOPPED (session stopped or paused). */
    val status: String,
    @SerialName("time_to_hit_sec") val timeToHitSec: Double? = null,
    @SerialName("entry_cents") val entryCents: Double? = null,
    @SerialName("hold_mean_cents") val holdMeanCents: Double? = null,
    @SerialName("hold_spread_cents") val holdSpreadCents: Double? = null,
    @SerialName("wrong_octave_sec") val wrongOctaveSec: Double = 0.0,
    @SerialName("wrong_note_sec") val wrongNoteSec: Double = 0.0,
    /** Time from the end of the pre-listen to the hit or the end of the attempt. */
    @SerialName("listened_sec") val listenedSec: Double = 0.0,
    /** What the screen showed while the player played: EAR, TUNER or MIXED. */
    val display: String,
    /** The drone sounded during the attempt (always so far). */
    val drone: Boolean = true
)
