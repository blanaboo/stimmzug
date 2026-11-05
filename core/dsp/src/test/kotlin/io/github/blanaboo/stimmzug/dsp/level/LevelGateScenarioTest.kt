package io.github.blanaboo.stimmzug.dsp.level

import io.github.blanaboo.stimmzug.dsp.music.NoteMath
import io.github.blanaboo.stimmzug.dsp.pitch.PitchTestSupport.NOTES
import io.github.blanaboo.stimmzug.dsp.pitch.PitchTracker
import io.github.blanaboo.stimmzug.dsp.signal.SignalMath
import io.github.blanaboo.stimmzug.dsp.synth.ToneSpec
import io.github.blanaboo.stimmzug.dsp.synth.TubaToneGenerator
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

/**
 * Acceptance scenarios of issue #13 on synthetic recordings: [CALIBRATION_SEC] of drone bleed and noise (player
 * silent) calibrate the gate, then either only the drone continues or the tuba enters for [TONE_SEC].
 */
class LevelGateScenarioTest {

    /** Drone (at [droneFactor] × the note) and phone-mic noise all along; the tuba, if any, after the calibration. */
    private fun scene(
        midi: Int,
        droneFactor: Double,
        seed: Long,
        dronePeakDbfs: Double = -24.0,
        tubaCents: Double = 0.0
    ) = ToneSpec(
        frequencyHz = NoteMath.frequency(midi),
        leadInSec = CALIBRATION_SEC,
        toneSec = TONE_SEC,
        tailSec = TAIL_SEC,
        tonePeakDbfs = null,
        centsContour = { tubaCents },
        micHighPassHz = 120.0,
        noiseRmsDbfs = -55.0,
        bleedFrequencyHz = droneFactor * NoteMath.frequency(midi),
        bleedPeakDbfs = dronePeakDbfs,
        seed = seed
    )

    /**
     * Sets the tuba peak level so that the tuba (RMS, after the mic high-pass) is [excessDb] louder than the drone as
     * heard during the calibration of the same recording. The generator normalizes waveforms to their peak, so the RMS
     * depends on the seeded harmonic phases: the drone must be measured in the very recording that is gated, not in a
     * separate rendering (which would draw other phases).
     */
    private fun withTubaAbove(spec: ToneSpec, excessDb: Double): ToneSpec {
        val reference = -12.0
        val recording = TubaToneGenerator.renderDouble(spec.copy(tonePeakDbfs = reference))
        // Same seed and the tuba is rendered first, so this is the identical tuba without drone and noise.
        val tuba = TubaToneGenerator.renderDouble(
            spec.copy(tonePeakDbfs = reference, bleedFrequencyHz = null, noiseRmsDbfs = null)
        )
        val drone = SignalMath.rmsDb(recording, (CALIBRATION_START_SEC * FS).toInt(), (CALIBRATION_SEC * FS).toInt())
        val tubaLevel =
            SignalMath.rmsDb(tuba, ((CALIBRATION_SEC + 0.5) * FS).toInt(), ((CALIBRATION_SEC + TONE_SEC) * FS).toInt())
        return spec.copy(tonePeakDbfs = reference + excessDb - (tubaLevel - drone))
    }

    /** Times of the frames after the calibration and whether each passed the gate (voiced and loud enough). */
    private fun gatedFrames(spec: ToneSpec): List<Pair<Double, Boolean>> {
        val tracker = PitchTracker(FS.toDouble(), hopSec = HOP_SEC).apply { targetHz = spec.frequencyHz }
        val frames = tracker.processAll(TubaToneGenerator.render(spec))
        val calibration = LevelCalibration()
        frames.filter {
            it.timeSec in CALIBRATION_START_SEC..CALIBRATION_SEC
        }.forEach { calibration.add(it.estimate.levelDb) }
        val gate = LevelGate(calibration.result())
        return frames.filter { it.timeSec > CALIBRATION_SEC }.map { it.timeSec to gate.passes(it.estimate) }
    }

    private fun assertDroneNeverPasses(spec: ToneSpec, label: String) {
        val passed = gatedFrames(spec).filter { it.second }
        assertTrue(
            passed.isEmpty(),
            "$label: ${passed.size} drone frames passed, first at ${passed.firstOrNull()?.first}"
        )
    }

    private fun assertTubaPasses(spec: ToneSpec, label: String) {
        val frames = gatedFrames(spec)
        val onset = CALIBRATION_SEC + ONSET_SEC
        val end = CALIBRATION_SEC + TONE_SEC
        val missed = frames.filter { (t, passed) -> t in onset..end && !passed }
        assertTrue(
            missed.isEmpty(),
            "$label: ${missed.size} tuba frames blocked, first at ${missed.firstOrNull()?.first}"
        )
        // Once the tuba has died away (release + longest window), only the drone is left and the gate is closed.
        val afterwards = frames.filter { (t, passed) -> t > end + 0.15 + 0.25 && passed }
        assertTrue(afterwards.isEmpty(), "$label: gate still passing at ${afterwards.firstOrNull()?.first}")
    }

    @ParameterizedTest(name = "MIDI {0}")
    @MethodSource("notes")
    fun `drone alone never passes the gate`(midi: Int) {
        var seed = 0L
        for (droneFactor in listOf(1.0, 2.0)) {
            for (dronePeak in listOf(-24.0, -12.0)) {
                seed++
                val label = "drone x$droneFactor at $dronePeak dBFS, seed $seed"
                assertDroneNeverPasses(scene(midi, droneFactor, seed, dronePeak), label)
            }
        }
    }

    /**
     * The critical case: in tune means a fixed phase to the drone, so each seed is a different (partial) cancellation.
     */
    @ParameterizedTest(name = "MIDI {0}")
    @MethodSource("notes")
    fun `tuba 10 dB above an in-tune unison drone passes whatever the phase`(midi: Int) {
        for (seed in 1L..6L) {
            assertTubaPasses(withTubaAbove(scene(midi, 1.0, seed), 10.0), "seed $seed")
        }
    }

    @ParameterizedTest(name = "MIDI {0}")
    @MethodSource("notes")
    fun `tuba 10 dB above the drone passes while beating and below an octave drone`(midi: Int) {
        for (seed in 1L..2L) {
            assertTubaPasses(withTubaAbove(scene(midi, 1.0, seed, tubaCents = 10.0), 10.0), "unison +10 ct, seed $seed")
            assertTubaPasses(withTubaAbove(scene(midi, 2.0, seed), 10.0), "drone an octave up, seed $seed")
        }
    }

    companion object {
        const val FS = 48_000
        const val CALIBRATION_START_SEC = 0.5
        const val CALIBRATION_SEC = 2.0
        const val TONE_SEC = 2.0
        const val TAIL_SEC = 0.8

        /** Coarser than the app's 20 ms: the gate only looks at levels, and the detector dominates the test time. */
        const val HOP_SEC = 0.05

        /** Attack (80 ms) plus the longest analysis window (~190 ms at 27.5 Hz). */
        const val ONSET_SEC = 0.35

        @JvmStatic
        fun notes() = NOTES
    }
}
