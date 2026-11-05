package io.github.blanaboo.stimmzug.dsp.match

import io.github.blanaboo.stimmzug.dsp.music.NoteMath
import io.github.blanaboo.stimmzug.dsp.pitch.PitchFrame
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Hit logic for one note at a time: decides from the stream of pitch frames when the player has held the target
 * within the tolerance long enough, and collects the per-note measurements.
 *
 * Feed every frame in time order with [onFrame]; whether it passed the level gate is decided outside, because the
 * gate sees all frames of the session. Time is the frames' sample time, so results are deterministic.
 * Call [start] for each note. Not thread-safe.
 */
class MatchStateMachine(val config: MatchConfig = MatchConfig()) {

    var phase: MatchPhase = MatchPhase.DONE
        private set

    private var targetHz = 0.0
    private var started = false
    private var listenStart = 0.0
    private var lastFrameTime: Double? = null

    private val window = ArrayDeque<Double>()
    private var lastPresentTime: Double? = null
    private val entry = ArrayList<Double>()

    private var holdStart = 0.0
    private var lastInRange = 0.0
    private var hitTime: Double? = null
    private var holdCount = 0
    private var holdSum = 0.0
    private var holdSumSquares = 0.0

    private var wrongOctaveSec = 0.0
    private var wrongNoteSec = 0.0

    /** Starts a new note at [timeSec] (sample time); everything from the previous note is reset. */
    fun start(targetHz: Double, timeSec: Double) {
        require(targetHz > 0.0) { "target must be positive, was $targetHz" }
        this.targetHz = targetHz
        started = true
        phase = MatchPhase.PRE_LISTEN
        listenStart = timeSec + config.preListenSec
        lastFrameTime = null
        window.clear()
        lastPresentTime = null
        entry.clear()
        hitTime = null
        resetHold(0.0)
        wrongOctaveSec = 0.0
        wrongNoteSec = 0.0
    }

    /** Processes the next frame; [passedGate] = the frame was loud enough to be the player (see LevelGate). */
    fun onFrame(frame: PitchFrame, passedGate: Boolean): MatchStatus {
        check(started) { "start() a note first" }
        val t = frame.timeSec
        val dt = lastFrameTime?.let { max(0.0, t - it) } ?: 0.0
        lastFrameTime = t
        advanceTimedPhases(t)
        if (phase != MatchPhase.LISTENING && phase != MatchPhase.IN_RANGE) {
            return MatchStatus(phase, t, null, PitchVerdict.SILENT, holdProgress(t))
        }

        val estimate = frame.estimate
        val present = passedGate && estimate.isVoiced && estimate.clarity >= config.minClarity
        val smoothed = if (present) smooth(NoteMath.cents(estimate.frequencyHz, targetHz), t) else null
        val verdict = classify(smoothed)
        when (verdict) {
            PitchVerdict.WRONG_OCTAVE -> wrongOctaveSec += dt
            PitchVerdict.WRONG_NOTE -> wrongNoteSec += dt
            else -> Unit
        }

        val inRange = verdict == PitchVerdict.IN_TOLERANCE
        if (phase == MatchPhase.LISTENING) {
            if (inRange) {
                phase = MatchPhase.IN_RANGE
                resetHold(t)
                addToHold(smoothed!!)
            }
        } else {
            if (smoothed != null) addToHold(smoothed)
            if (inRange) {
                lastInRange = t
                if (t - holdStart >= config.holdSec - EPS) {
                    phase = MatchPhase.HIT
                    hitTime = t
                }
            } else if (t - lastInRange > config.graceSec + EPS) {
                phase = MatchPhase.LISTENING
            }
        }
        return MatchStatus(phase, t, smoothed, verdict, holdProgress(t))
    }

    /** Measurements of the current note so far. */
    fun result(): NoteResult {
        check(started) { "start() a note first" }
        val hit = hitTime != null
        val mean = if (hit && holdCount > 0) holdSum / holdCount else null
        val spread = mean?.let { sqrt(max(0.0, holdSumSquares / holdCount - it * it)) }
        return NoteResult(
            targetHz = targetHz,
            hit = hit,
            timeToHitSec = hitTime?.let { it - listenStart },
            entryCents = if (entry.isEmpty()) null else median(entry),
            holdMeanCents = mean,
            holdSpreadCents = spread,
            wrongOctaveSec = wrongOctaveSec,
            wrongNoteSec = wrongNoteSec
        )
    }

    private fun advanceTimedPhases(t: Double) {
        if (phase == MatchPhase.PRE_LISTEN && t >= listenStart - EPS) phase = MatchPhase.LISTENING
        val hit = hitTime ?: return
        if (phase == MatchPhase.HIT && t >= hit + config.confirmSec - EPS) phase = MatchPhase.PAUSE
        if (phase == MatchPhase.PAUSE && t >= hit + config.confirmSec + config.pauseSec - EPS) phase = MatchPhase.DONE
    }

    /** Median of the last player frames; a dropout longer than the grace time starts the window afresh. */
    private fun smooth(cents: Double, t: Double): Double {
        val last = lastPresentTime
        if (last != null && t - last > config.graceSec + EPS) window.clear()
        lastPresentTime = t
        if (entry.size < config.medianFrames) entry += cents
        window.addLast(cents)
        while (window.size > config.medianFrames) window.removeFirst()
        return median(window)
    }

    private fun classify(cents: Double?): PitchVerdict {
        if (cents == null) return PitchVerdict.SILENT
        val distance = abs(cents)
        if (distance <= config.toleranceCents + EPS) return PitchVerdict.IN_TOLERANCE
        if (distance < MatchConfig.SAME_NOTE_CENTS) return if (cents < 0) PitchVerdict.FLAT else PitchVerdict.SHARP
        val octaves = (cents / CENTS_PER_OCTAVE).roundToInt()
        val offOctave = abs(cents - octaves * CENTS_PER_OCTAVE)
        return if (offOctave < MatchConfig.SAME_NOTE_CENTS) PitchVerdict.WRONG_OCTAVE else PitchVerdict.WRONG_NOTE
    }

    private fun holdProgress(t: Double): Double = when (phase) {
        MatchPhase.IN_RANGE -> min(1.0, (t - holdStart) / config.holdSec)
        MatchPhase.HIT, MatchPhase.PAUSE -> 1.0
        MatchPhase.DONE -> if (hitTime != null) 1.0 else 0.0
        else -> 0.0
    }

    private fun resetHold(t: Double) {
        holdStart = t
        lastInRange = t
        holdCount = 0
        holdSum = 0.0
        holdSumSquares = 0.0
    }

    private fun addToHold(cents: Double) {
        holdCount++
        holdSum += cents
        holdSumSquares += cents * cents
    }

    private companion object {
        const val CENTS_PER_OCTAVE = 1200.0

        /** Frame times are sums of hops; tolerate rounding at phase boundaries. */
        const val EPS = 1e-9

        fun median(values: Collection<Double>): Double {
            val sorted = values.sorted()
            val mid = sorted.size / 2
            return if (sorted.size % 2 == 1) sorted[mid] else 0.5 * (sorted[mid - 1] + sorted[mid])
        }
    }
}
