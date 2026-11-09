package io.github.blanaboo.stimmzug.data.log

import io.github.blanaboo.stimmzug.audio.OutputRoute
import io.github.blanaboo.stimmzug.data.settings.DisplayMode
import io.github.blanaboo.stimmzug.dsp.level.CalibrationResult
import io.github.blanaboo.stimmzug.dsp.music.Fingering
import io.github.blanaboo.stimmzug.dsp.music.InstrumentProfile
import io.github.blanaboo.stimmzug.dsp.session.NoteAttempt
import io.github.blanaboo.stimmzug.dsp.session.SessionConfig
import io.github.blanaboo.stimmzug.dsp.session.SessionSummary
import io.github.blanaboo.stimmzug.dsp.synth.DroneTimbre

/**
 * Which display the player had when, in session time: the display can be switched during an exercise, and whoever
 * sees the needle corrects faster (#37). Thread-safe: changes come from the UI, reads at the end of the session.
 */
class DisplayTimeline(initial: DisplayMode) {
    private val changes = mutableListOf(0.0 to initial)

    @Synchronized
    fun change(atSec: Double, mode: DisplayMode) {
        if (changes.last().second != mode) changes += atSec to mode
    }

    /** EAR or TUNER if only that was shown in [fromSec, toSec], else MIXED. */
    @Synchronized
    fun during(fromSec: Double, toSec: Double): String {
        val atStart = changes.last { it.first <= fromSec }.second
        val modes = setOf(atStart) + changes.filter { it.first > fromSec && it.first < toSec }.map { it.second }
        return modes.singleOrNull()?.name ?: MIXED
    }

    companion object {
        const val MIXED = "MIXED"
    }
}

/** Today's warm-up (#44) as the log keeps it: the answer, how long the timer ran, minutes since the answer. */
data class WarmUpInfo(val answer: String, val durationSec: Int?, val ageMin: Double)

/** What the session log needs to know about a session besides its summary. */
class SessionContext(
    val startedAt: String,
    val profile: InstrumentProfile?,
    val config: SessionConfig,
    val droneTimbre: DroneTimbre,
    val exercise: String,
    val display: DisplayTimeline,
    val warmUp: WarmUpInfo? = null
)

/** Turns a finished session into its log entry (#37). */
object SessionLogBuilder {

    fun build(
        context: SessionContext,
        summary: SessionSummary,
        calibration: CalibrationResult?,
        audioSource: String?,
        route: OutputRoute
    ): LoggedSession = LoggedSession(
        startedAt = context.startedAt,
        durationSec = summary.durationSec,
        profile = context.profile?.let(::profile),
        a4Hz = context.config.a4Hz,
        toleranceCents = context.config.match.toleranceCents,
        holdSec = context.config.match.holdSec,
        audioSource = audioSource,
        droneRoute = listOfNotNull(route.kind.name, route.name).joinToString(" "),
        droneTimbre = context.droneTimbre.name,
        ambientDb = calibration?.ambientDb,
        thresholdDb = calibration?.openThresholdDb,
        warmUp = context.warmUp?.answer,
        warmUpSec = context.warmUp?.durationSec,
        warmUpAgeMin = context.warmUp?.ageMin,
        exercise = context.exercise,
        attempts = summary.attempts.mapIndexed { i, attempt ->
            attempt(context, attempt, summary.attempts.getOrNull(i - 1)?.midi)
        }
    )

    private fun attempt(context: SessionContext, a: NoteAttempt, previousMidi: Int?): LoggedAttempt {
        val fingering = context.profile?.let { Fingering.standard(it, a.midi) }
        val listenStart = a.startSec + context.config.match.preListenSec
        val r = a.result
        return LoggedAttempt(
            number = a.number,
            index = a.index,
            midi = a.midi,
            targetHz = a.targetHz,
            partial = fingering?.partial,
            valves = fingering?.valveText,
            startSec = a.startSec,
            previousMidi = previousMidi,
            status = a.status.name,
            timeToHitSec = r.timeToHitSec,
            entryCents = r.entryCents,
            holdMeanCents = r.holdMeanCents,
            holdSpreadCents = r.holdSpreadCents,
            wrongOctaveSec = r.wrongOctaveSec,
            wrongNoteSec = r.wrongNoteSec,
            listenedSec = r.listenedSec,
            display = context.display.during(listenStart, listenStart + r.listenedSec)
        )
    }

    private fun profile(p: InstrumentProfile) = LoggedProfile(
        name = p.name,
        key = p.key.name,
        valves = p.valveCount,
        extraValves = p.extraValves.map { it.name },
        compensating = p.compensating,
        triggerValves = p.triggerValves.sorted(),
        mainSlideTrigger = p.mainSlideTrigger
    )
}
