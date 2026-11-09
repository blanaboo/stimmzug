package io.github.blanaboo.stimmzug.dsp.session

import io.github.blanaboo.stimmzug.dsp.level.CalibrationResult
import io.github.blanaboo.stimmzug.dsp.level.LevelCalibration
import io.github.blanaboo.stimmzug.dsp.level.NoteLevelGate
import io.github.blanaboo.stimmzug.dsp.match.MatchPhase
import io.github.blanaboo.stimmzug.dsp.match.MatchStateMachine
import io.github.blanaboo.stimmzug.dsp.match.MatchStatus
import io.github.blanaboo.stimmzug.dsp.music.NoteMath
import io.github.blanaboo.stimmzug.dsp.pitch.MpmPitchDetector
import io.github.blanaboo.stimmzug.dsp.pitch.PitchDetector
import io.github.blanaboo.stimmzug.dsp.pitch.PitchFrame
import io.github.blanaboo.stimmzug.dsp.pitch.PitchTracker

/**
 * One practice session (#66): calibration, then the [notes] one after the other, each with pre-listen, listening,
 * hold, confirmation and breathing pause, then the summary.
 *
 * Feeds the microphone audio through [PitchTracker] (with the target low-pass, ADR 0005), [NoteLevelGate] (#24) and
 * [MatchStateMachine], and drives the drone through [DroneControl]. Time is the audio sample time of the input, so a
 * session is deterministic and can be tested with synthetic audio.
 *
 * Call [start] once, then [process] with every input chunk. [pause], [resume], [skip], [back] and [stop] act at the
 * current input time. Not thread-safe: call everything from the thread that feeds the audio.
 */
class PracticeSession(
    val notes: List<Int>,
    val sampleRate: Double,
    private val drone: DroneControl,
    val config: SessionConfig = SessionConfig(),
    detector: PitchDetector = MpmPitchDetector()
) {
    private val tracker = PitchTracker(sampleRate, detector)
    private val calibration = LevelCalibration(marginDb = config.marginDb)
    private var gate: NoteLevelGate? = null
    private val machine = MatchStateMachine(config.match)
    private val outcomes = notes.mapIndexed { i, midi ->
        NoteOutcome(i, midi, frequency(i), NoteStatus.NOT_REACHED, null)
    }.toMutableList()

    private var started = false
    private var droneHz: Double? = null
    private var index = 0
    private var phase = SessionPhase.CALIBRATING
    private var status: MatchStatus? = null

    // Whether the current attempt at the note was hit; a note played again after back() starts unhit.
    private var hitThisNote = false
    private val attempts = ArrayList<NoteAttempt>()
    private var attemptOpen = false
    private var attemptStart = 0.0
    private var finishedAt: Double? = null

    init {
        require(notes.isNotEmpty()) { "a session needs at least one note" }
        tracker.targetHz = frequency(0)
    }

    /** Room and drone level measured in the calibration, `null` before it ends. */
    var calibrationResult: CalibrationResult? = null
        private set

    /** Current input time in seconds. */
    val timeSec: Double get() = tracker.samplesProcessed / sampleRate

    val snapshot: SessionSnapshot
        get() = SessionSnapshot(
            timeSec = timeSec,
            phase = phase,
            calibrationProgress = if (phase == SessionPhase.CALIBRATING) {
                (timeSec / config.calibrationSec).coerceIn(0.0, 1.0)
            } else {
                1.0
            },
            index = index,
            noteCount = notes.size,
            midi = notes.getOrNull(index),
            targetHz = if (index < notes.size) frequency(index) else null,
            note = status.takeIf { phase == SessionPhase.PLAYING },
            hit = outcomes.getOrNull(index)?.takeIf { phase == SessionPhase.PLAYING && hitThisNote }
        )

    /** Starts the drone on the first note for the calibration. */
    fun start() {
        check(!started) { "session already started" }
        started = true
        playDrone(frequency(0))
    }

    /** Feeds the next input chunk; returns the state afterwards. */
    fun process(chunk: FloatArray, length: Int = chunk.size): SessionSnapshot {
        check(started) { "start() the session first" }
        tracker.process(chunk, length, ::onFrame)
        return snapshot
    }

    /** Stops the drone and ignores the input until [resume]. */
    fun pause() {
        if (phase != SessionPhase.PLAYING) return
        closeAttempt(NoteStatus.STOPPED)
        phase = SessionPhase.PAUSED
        stopDrone()
    }

    /** Continues after [pause]: the current note starts again, or the next one if the current was already hit. */
    fun resume() {
        if (phase != SessionPhase.PAUSED) return
        phase = SessionPhase.PLAYING
        if (hitThisNote) nextNote() else startNote()
    }

    /** Leaves the current note unplayed and goes on with the next. */
    fun skip() {
        if (phase != SessionPhase.PLAYING && phase != SessionPhase.PAUSED) return
        if (!hitThisNote && outcomes[index].status != NoteStatus.HIT) record(NoteStatus.SKIPPED)
        closeAttempt(NoteStatus.SKIPPED)
        phase = SessionPhase.PLAYING
        nextNote()
    }

    /** Plays the previous note again (the current one on the first note). Its earlier outcome is replaced. */
    fun back() {
        if (phase != SessionPhase.PLAYING && phase != SessionPhase.PAUSED) return
        closeAttempt(NoteStatus.SKIPPED)
        if (index > 0) index--
        phase = SessionPhase.PLAYING
        startNote()
    }

    /** Ends the session; the current note counts as stopped unless it was hit. */
    fun stop() {
        if (phase == SessionPhase.FINISHED) return
        if (phase != SessionPhase.CALIBRATING && !hitThisNote && outcomes[index].status != NoteStatus.HIT) {
            record(NoteStatus.STOPPED)
        }
        closeAttempt(NoteStatus.STOPPED)
        finish()
    }

    fun summary(): SessionSummary = SessionSummary(outcomes.toList(), finishedAt ?: timeSec, attempts.toList())

    private fun onFrame(frame: PitchFrame) {
        when (phase) {
            SessionPhase.CALIBRATING -> calibrate(frame)
            SessionPhase.PLAYING -> listen(frame)
            SessionPhase.PAUSED, SessionPhase.FINISHED -> Unit
        }
    }

    private fun calibrate(frame: PitchFrame) {
        if (frame.timeSec >= config.calibrationSettleSec) calibration.add(frame.estimate.levelDb)
        if (frame.timeSec >= config.calibrationSec) {
            val result = calibration.result()
            calibrationResult = result
            gate = NoteLevelGate(result)
            phase = SessionPhase.PLAYING
            startNote()
        }
    }

    private fun listen(frame: PitchFrame) {
        val noteGate = checkNotNull(gate) { "calibration missing" }
        val now = machine.onFrame(frame, noteGate.passes(frame))
        status = now
        if (now.phase == MatchPhase.HIT && !hitThisNote) {
            hitThisNote = true
            record(NoteStatus.HIT)
            closeAttempt(NoteStatus.HIT)
            drone.playConfirmation()
            stopDrone()
        }
        if (now.phase == MatchPhase.DONE) nextNote()
    }

    private fun startNote() {
        val hz = frequency(index)
        val t = timeSec
        tracker.targetHz = hz
        machine.start(hz, t)
        checkNotNull(gate) { "calibration missing" }.startNote(t)
        status = null
        hitThisNote = false
        attemptOpen = true
        attemptStart = t
        playDrone(hz)
    }

    private fun nextNote() {
        index++
        if (index >= notes.size) finish() else startNote()
    }

    private fun finish() {
        phase = SessionPhase.FINISHED
        finishedAt = timeSec
        status = null
        stopDrone()
    }

    private fun record(noteStatus: NoteStatus) {
        outcomes[index] = outcomes[index].copy(status = noteStatus, result = machine.result())
    }

    /** Ends the open attempt at the current note, if there is one. */
    private fun closeAttempt(noteStatus: NoteStatus) {
        if (!attemptOpen) return
        attemptOpen = false
        attempts += NoteAttempt(
            number = attempts.size + 1,
            index = index,
            midi = notes[index],
            targetHz = frequency(index),
            startSec = attemptStart,
            status = noteStatus,
            result = machine.result()
        )
    }

    private fun playDrone(hz: Double) {
        if (droneHz == hz) return
        droneHz = hz
        drone.play(hz)
    }

    private fun stopDrone() {
        if (droneHz == null) return
        droneHz = null
        drone.stop()
    }

    private fun frequency(i: Int) = NoteMath.frequency(notes[i], config.a4Hz)
}
