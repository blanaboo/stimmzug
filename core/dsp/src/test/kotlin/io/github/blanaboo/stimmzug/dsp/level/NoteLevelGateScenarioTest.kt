package io.github.blanaboo.stimmzug.dsp.level

import io.github.blanaboo.stimmzug.dsp.match.MatchStateMachine
import io.github.blanaboo.stimmzug.dsp.match.NoteResult
import io.github.blanaboo.stimmzug.dsp.match.PitchVerdict
import io.github.blanaboo.stimmzug.dsp.music.NoteMath
import io.github.blanaboo.stimmzug.dsp.pitch.PitchFrame
import io.github.blanaboo.stimmzug.dsp.pitch.PitchTracker
import io.github.blanaboo.stimmzug.dsp.signal.SignalMath
import io.github.blanaboo.stimmzug.dsp.synth.ToneSpec
import io.github.blanaboo.stimmzug.dsp.synth.TubaToneGenerator
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * Acceptance of issue #24 through the real chain PitchTracker → LevelCalibration/NoteLevelGate → MatchStateMachine.
 *
 * Time line: phone-mic noise all along. The drone sounds at the calibration level until [NOTE_START], then
 * [droneStepDb] louder (a note the loudspeaker reproduces better, or a turned-up volume). The note starts at
 * [NOTE_START] with 1 s pre-listen, listening starts at [LISTEN_START].
 */
class NoteLevelGateScenarioTest {

    private class Outcome(val result: NoteResult, val silent: Boolean)

    /** One tuba phrase from [startSec] to [endSec]; `null` = no tuba. */
    private data class Phrase(val startSec: Double, val endSec: Double)

    private fun base(midi: Int) = ToneSpec(
        frequencyHz = NoteMath.frequency(midi),
        leadInSec = 0.0,
        toneSec = TOTAL_SEC,
        releaseSec = 0.0,
        tonePeakDbfs = null,
        micHighPassHz = 120.0,
        seed = midi.toLong()
    )

    private fun recording(midi: Int, droneStepDb: Double, phrases: List<Phrase>): FloatArray {
        val spec = base(midi)
        val drone = TubaToneGenerator.renderDouble(
            spec.copy(bleedFrequencyHz = spec.frequencyHz, bleedPeakDbfs = DRONE_DBFS + droneStepDb)
        )
        val before = SignalMath.dbToAmplitude(-droneStepDb)
        for (i in 0 until (NOTE_START * FS).toInt()) drone[i] *= before
        val noise = TubaToneGenerator.renderDouble(spec.copy(noiseRmsDbfs = -55.0, seed = 99))
        val mix = DoubleArray(drone.size) { drone[it] + noise[it] }
        for (p in phrases) {
            val tuba = TubaToneGenerator.renderDouble(
                spec.copy(
                    leadInSec = p.startSec,
                    toneSec = p.endSec - p.startSec,
                    releaseSec = 0.15,
                    tailSec = TOTAL_SEC - p.endSec - 0.15,
                    // The tuba stays 12 dB above the drone of this note (ADR 0003: at least the 10 dB margin).
                    tonePeakDbfs = TUBA_DBFS + droneStepDb,
                    centsContour = { 1.0 }
                )
            )
            for (i in mix.indices) mix[i] += tuba.getOrElse(i) { 0.0 }
        }
        return FloatArray(mix.size) { mix[it].toFloat() }
    }

    private fun play(midi: Int, samples: FloatArray, perNote: Boolean): Outcome {
        val target = NoteMath.frequency(midi)
        val frames = PitchTracker(FS.toDouble()).apply { targetHz = target }.processAll(samples)
        val calibration = LevelCalibration().apply {
            frames.filter { it.timeSec in 0.5..NOTE_START }.forEach { add(it.estimate.levelDb) }
        }.result()
        val machine = MatchStateMachine().apply { start(target, NOTE_START) }
        val passes: (PitchFrame) -> Boolean = if (perNote) {
            val gate = NoteLevelGate(calibration).apply { startNote(NOTE_START) }
            gate::passes
        } else {
            val gate = LevelGate(calibration)
            val sessionPasses: (PitchFrame) -> Boolean = { gate.passes(it.estimate) }
            sessionPasses
        }
        val statuses = frames.filter { it.timeSec > NOTE_START }.map { machine.onFrame(it, passes(it)) }
        return Outcome(machine.result(), statuses.all { it.verdict == PitchVerdict.SILENT })
    }

    @ParameterizedTest(name = "MIDI {0}")
    @ValueSource(ints = [34, 41, 53, 65])
    fun `a drone 6 dB louder than at the calibration does not count as the player`(midi: Int) {
        val samples = recording(midi, droneStepDb = 6.0, phrases = emptyList())
        val sessionGate = play(midi, samples, perNote = false)
        assertTrue(sessionGate.result.hit, "the session gate alone takes the louder drone for a player in tune")
        val noteGate = play(midi, samples, perNote = true)
        assertFalse(noteGate.result.hit, "${noteGate.result}")
        assertTrue(noteGate.silent, "drone was taken for the player")
    }

    @ParameterizedTest(name = "MIDI {0}")
    @ValueSource(ints = [34, 41, 53, 65])
    fun `a player entering during the pre-listen is still recognized`(midi: Int) {
        for (step in listOf(0.0, 6.0)) {
            val samples = recording(midi, step, listOf(Phrase(LISTEN_START - 0.3, TOTAL_SEC - 0.5)))
            val outcome = play(midi, samples, perNote = true)
            assertTrue(outcome.result.hit, "drone +$step dB: ${outcome.result}")
            assertTrue(outcome.result.timeToHitSec!! < 3.5, "drone +$step dB: ${outcome.result}")
        }
    }

    @ParameterizedTest(name = "MIDI {0}")
    @ValueSource(ints = [34, 41, 53, 65])
    fun `a player already playing at the note start is recognized after the first breath`(midi: Int) {
        val breath = Phrase(NOTE_START + 2.0, NOTE_START + 2.7)
        val samples = recording(
            midi,
            droneStepDb = 0.0,
            phrases = listOf(Phrase(NOTE_START, breath.startSec), Phrase(breath.endSec, TOTAL_SEC - 0.5))
        )
        val outcome = play(midi, samples, perNote = true)
        assertTrue(outcome.result.hit, "${outcome.result}")
    }

    private companion object {
        const val FS = 48_000
        const val NOTE_START = 2.0
        const val LISTEN_START = NOTE_START + 1.0
        const val TOTAL_SEC = 10.0
        const val DRONE_DBFS = -24.0
        const val TUBA_DBFS = -12.0
    }
}
