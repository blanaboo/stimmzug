package io.github.blanaboo.stimmzug.dsp.match

import io.github.blanaboo.stimmzug.dsp.level.LevelCalibration
import io.github.blanaboo.stimmzug.dsp.level.LevelGate
import io.github.blanaboo.stimmzug.dsp.music.NoteMath
import io.github.blanaboo.stimmzug.dsp.pitch.PitchTracker
import io.github.blanaboo.stimmzug.dsp.synth.ToneSpec
import io.github.blanaboo.stimmzug.dsp.synth.TubaToneGenerator
import kotlin.math.abs
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * End-to-end acceptance of issue #14 on synthetic recordings through the real chain
 * PitchTracker → LevelCalibration/LevelGate → MatchStateMachine.
 *
 * Time line: drone bleed and phone-mic noise all along; calibration until [NOTE_START]; the note starts there with
 * 1 s pre-listen; the tuba (if any) enters when listening starts ([TUBA_START]).
 */
class MatchScenarioTest {

    private class Outcome(val statuses: List<MatchStatus>, val result: NoteResult)

    private fun scene(midi: Int, tubaHz: Double?, tubaCents: (Double) -> Double = { 0.0 }, jitterCents: Double = 0.0) =
        ToneSpec(
            frequencyHz = tubaHz ?: NoteMath.frequency(midi),
            leadInSec = TUBA_START,
            toneSec = TONE_SEC,
            releaseSec = 0.0,
            tonePeakDbfs = if (tubaHz == null) null else -12.0,
            centsContour = tubaCents,
            jitterCents = jitterCents,
            micHighPassHz = 120.0,
            noiseRmsDbfs = -55.0,
            bleedFrequencyHz = NoteMath.frequency(midi),
            bleedPeakDbfs = -24.0,
            seed = midi.toLong()
        )

    private fun play(midi: Int, spec: ToneSpec): Outcome {
        val target = NoteMath.frequency(midi)
        val tracker = PitchTracker(FS).apply { targetHz = target }
        val frames = tracker.processAll(TubaToneGenerator.render(spec))
        val calibration = LevelCalibration()
        frames.filter { it.timeSec in 0.5..NOTE_START }.forEach { calibration.add(it.estimate.levelDb) }
        val gate = LevelGate(calibration.result())
        val machine = MatchStateMachine().apply { start(target, NOTE_START) }
        val statuses = frames.filter { it.timeSec > NOTE_START }.map { machine.onFrame(it, gate.passes(it.estimate)) }
        return Outcome(statuses, machine.result())
    }

    @ParameterizedTest(name = "MIDI {0}")
    @ValueSource(ints = [34, 41, 53, 65])
    fun `drone alone never counts as the player`(midi: Int) {
        val outcome = play(midi, scene(midi, tubaHz = null))
        assertFalse(outcome.result.hit)
        assertTrue(outcome.statuses.all { it.verdict == PitchVerdict.SILENT }, "drone was taken for the player")
    }

    @ParameterizedTest(name = "MIDI {0}")
    @ValueSource(ints = [34, 41, 53, 65])
    fun `player in tune with natural pitch wander hits after the hold time`(midi: Int) {
        val f = NoteMath.frequency(midi)
        val result = play(midi, scene(midi, tubaHz = f, jitterCents = 1.5)).result
        assertTrue(result.hit, "no hit: $result")
        // Hold time 3 s plus onset (attack, analysis window, median).
        assertTrue(result.timeToHitSec!! in 3.0..3.5, "time to hit ${result.timeToHitSec}")
        assertTrue(abs(result.holdMeanCents!!) < 2.0 && result.holdSpreadCents!! < 2.5, "hold stats: $result")
        assertTrue(result.wrongOctaveSec == 0.0 && result.wrongNoteSec == 0.0, "misclassified: $result")
    }

    @ParameterizedTest(name = "MIDI {0}")
    @ValueSource(ints = [34, 41, 53, 65])
    fun `player an octave too low never hits and is told so`(midi: Int) {
        val outcome = play(midi, scene(midi, tubaHz = NoteMath.frequency(midi - 12)))
        assertFalse(outcome.result.hit)
        assertTrue(
            outcome.result.wrongOctaveSec > TONE_SEC - 0.5,
            "wrong octave only ${outcome.result.wrongOctaveSec} s"
        )
        val heard = outcome.statuses.filter { it.verdict != PitchVerdict.SILENT }
        assertTrue(heard.all { it.verdict == PitchVerdict.WRONG_OCTAVE && it.centsDeviation!! < -1150 })
    }

    @ParameterizedTest(name = "MIDI {0}")
    @ValueSource(ints = [34, 41, 53, 65])
    fun `player settling from 30 cents flat hits once in tune`(midi: Int) {
        val f = NoteMath.frequency(midi)
        val glide: (Double) -> Double = { t -> if (t < 0.8) -30.0 * (1.0 - t / 0.8) else 0.0 }
        val outcome = play(midi, scene(midi, tubaHz = f, tubaCents = glide))
        assertTrue(outcome.result.hit)
        // In tune after ~0.7 s of the glide, then 3 s hold.
        assertTrue(outcome.result.timeToHitSec!! in 3.6..4.1, "time to hit ${outcome.result.timeToHitSec}")
        assertTrue(outcome.result.entryCents!! < -20.0, "entry ${outcome.result.entryCents}")
        assertTrue(outcome.statuses.any { it.verdict == PitchVerdict.FLAT })
    }

    private companion object {
        const val FS = 48_000.0
        const val NOTE_START = 2.0
        const val TUBA_START = NOTE_START + 1.0 // = end of the default pre-listen
        const val TONE_SEC = 5.0
    }
}
