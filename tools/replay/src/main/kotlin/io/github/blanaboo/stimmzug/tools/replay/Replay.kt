package io.github.blanaboo.stimmzug.tools.replay

import io.github.blanaboo.stimmzug.dsp.level.CalibrationResult
import io.github.blanaboo.stimmzug.dsp.level.LevelCalibration
import io.github.blanaboo.stimmzug.dsp.level.LevelGate
import io.github.blanaboo.stimmzug.dsp.match.MatchConfig
import io.github.blanaboo.stimmzug.dsp.match.MatchStateMachine
import io.github.blanaboo.stimmzug.dsp.match.MatchStatus
import io.github.blanaboo.stimmzug.dsp.match.NoteResult
import io.github.blanaboo.stimmzug.dsp.pitch.MpmPitchDetector
import io.github.blanaboo.stimmzug.dsp.pitch.PitchFrame
import io.github.blanaboo.stimmzug.dsp.pitch.PitchTracker
import io.github.blanaboo.stimmzug.dsp.pitch.YinPitchDetector

enum class DetectorChoice { MPM, YIN }

data class ReplayOptions(
    /** Note the player should play; `null` = free detection without hit logic. */
    val targetHz: Double? = null,
    val detector: DetectorChoice = DetectorChoice.MPM,
    val hopSec: Double = 0.02,
    /** The first seconds (nobody plays) calibrate the level gate; 0 = no gate, every voiced frame counts. */
    val calibrationSec: Double = 0.0,
    /** Hit logic; no pre-listen by default, the player's timing is in the recording. */
    val match: MatchConfig = MatchConfig(preListenSec = 0.0),
    /** Low-pass just above the target before the detector, as in the app (ADR 0005); `false` for comparisons. */
    val targetLowPass: Boolean = true
) {
    init {
        require(targetHz == null || targetHz > 0.0) { "Zielton muss positiv sein" }
        require(calibrationSec >= 0.0) { "Kalibrierdauer darf nicht negativ sein" }
    }
}

/** One analysis frame; [status] is `null` in free mode and during the calibration. */
data class ReplayRow(
    val frame: PitchFrame,
    val inCalibration: Boolean,
    val passedGate: Boolean,
    val status: MatchStatus?
)

class ReplayResult(
    val sampleRate: Double,
    val durationSec: Double,
    val options: ReplayOptions,
    val rows: List<ReplayRow>,
    val calibration: CalibrationResult?,
    /** Hit logic outcome, `null` in free mode. */
    val note: NoteResult?,
    /** Cutoff of the low-pass before the detector, `null` if the input was not filtered. */
    val lowPassCutoffHz: Double? = null
)

/**
 * Runs a recording through the same chain as the app: PitchTracker with the target low-pass → (LevelCalibration →
 * LevelGate) →
 * MatchStateMachine. Pure function of the samples and options, so it can be called repeatedly for parameter
 * sweeps.
 */
object Replay {

    fun run(samples: FloatArray, sampleRate: Double, options: ReplayOptions): ReplayResult {
        val detector = when (options.detector) {
            DetectorChoice.MPM -> MpmPitchDetector()
            DetectorChoice.YIN -> YinPitchDetector()
        }
        val tracker = PitchTracker(sampleRate, detector, options.hopSec, useTargetLowPass = options.targetLowPass)
            .apply { targetHz = options.targetHz }
        val frames = tracker.processAll(samples)

        val calibrating = options.calibrationSec > 0.0
        val calibration = if (calibrating) {
            val levels = frames.filter { it.timeSec <= options.calibrationSec }.map { it.estimate.levelDb }
            require(levels.size >= LevelCalibration.MIN_FRAMES) {
                "Kalibrierung zu kurz: ${levels.size} Frames, mindestens ${LevelCalibration.MIN_FRAMES} nötig"
            }
            LevelCalibration().apply { levels.forEach(::add) }.result()
        } else {
            null
        }
        val gate = calibration?.let { LevelGate(it) }
        val machine = options.targetHz?.let { target ->
            MatchStateMachine(options.match).apply { start(target, options.calibrationSec) }
        }

        val rows = frames.map { frame ->
            if (calibrating && frame.timeSec <= options.calibrationSec) {
                ReplayRow(frame, inCalibration = true, passedGate = false, status = null)
            } else {
                val passed = gate?.passes(frame.estimate) ?: frame.estimate.isVoiced
                ReplayRow(frame, inCalibration = false, passedGate = passed, status = machine?.onFrame(frame, passed))
            }
        }
        return ReplayResult(
            sampleRate,
            samples.size / sampleRate,
            options,
            rows,
            calibration,
            machine?.result(),
            tracker.lowPassCutoffHz
        )
    }
}
