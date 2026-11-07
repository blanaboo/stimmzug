package io.github.blanaboo.stimmzug.dsp.pitch

import io.github.blanaboo.stimmzug.dsp.music.NoteMath
import io.github.blanaboo.stimmzug.dsp.pitch.PitchTestSupport.FS
import io.github.blanaboo.stimmzug.dsp.pitch.PitchTestSupport.NOTES
import io.github.blanaboo.stimmzug.dsp.signal.SignalMath
import io.github.blanaboo.stimmzug.dsp.synth.ToneSpec
import io.github.blanaboo.stimmzug.dsp.synth.TubaToneGenerator
import kotlin.math.abs
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.MethodSource

/**
 * The target low-pass before the detector (ADR 0005, #60) on synthetic recordings.
 *
 * Speaker case: the drone plays the target through the phone's loudspeaker, modelled as a 4th-order high-pass at
 * [SPEAKER_HZ] that reproduces the drone partials measured on the Nord 2 in spike #32 within a few dB. The tuba plays
 * clearly off the target and reaches the microphone about as loud as the drone, as in the measurement.
 */
class SpeakerBleedTest {

    /** Tuba [tubaCents] off [midi], plus the drone on [midi] through the speaker, [droneExcessDb] louder (RMS). */
    private fun speakerScene(midi: Int, tubaCents: Double, droneExcessDb: Double): ToneSpec {
        val f = NoteMath.frequency(midi)
        val base = ToneSpec(
            frequencyHz = f,
            toneSec = TONE_SEC,
            tonePeakDbfs = REFERENCE_DBFS,
            centsContour = { tubaCents },
            micHighPassHz = MIC_HIGH_PASS_HZ,
            bleedFrequencyHz = f,
            bleedPeakDbfs = REFERENCE_DBFS,
            bleedHighPassHz = SPEAKER_HZ,
            seed = midi.toLong()
        )
        // Same seed and the tuba is rendered first, so the drone keeps its waveform when only its level changes.
        val mix = TubaToneGenerator.renderDouble(base)
        val tuba = TubaToneGenerator.renderDouble(base.copy(bleedFrequencyHz = null))
        val drone = DoubleArray(mix.size) { mix[it] - tuba[it] }
        val from = (MEASURE_FROM_SEC * FS).toInt()
        val to = (TONE_SEC * FS).toInt()
        val shift = SignalMath.rmsDb(tuba, from, to) - SignalMath.rmsDb(drone, from, to) + droneExcessDb
        return base.copy(bleedPeakDbfs = REFERENCE_DBFS + shift, noiseRmsDbfs = NOISE_DBFS)
    }

    /** Median of the voiced estimates after the attack, in cents from [targetHz]; `null` if none is voiced. */
    private fun medianCents(spec: ToneSpec, targetHz: Double, lowPass: Boolean): Double? {
        val tracker = PitchTracker(FS.toDouble(), useTargetLowPass = lowPass).apply { this.targetHz = targetHz }
        val cents = tracker.processAll(TubaToneGenerator.render(spec))
            .filter { it.timeSec in MEASURE_FROM_SEC..TONE_SEC && it.estimate.isVoiced }
            .map { NoteMath.cents(it.estimate.frequencyHz, targetHz) }
            .sorted()
        return cents.getOrNull(cents.size / 2)
    }

    @ParameterizedTest(name = "midi {0}, tuba {1} ct")
    @CsvSource("21, -20", "34, -20", "34, 20", "41, -20", "53, -25", "58, -20", "58, 20")
    fun `with the low-pass the detector follows the tuba, without it the drone`(midi: Int, tubaCents: Double) {
        val spec = speakerScene(midi, tubaCents, droneExcessDb = 0.0)
        val f = spec.frequencyHz

        val filtered = checkNotNull(medianCents(spec, f, lowPass = true)) { "no voiced frames" }
        assertTrue(
            abs(filtered - tubaCents) <= MAX_RESIDUAL_PULL * abs(tubaCents),
            "with low-pass: $filtered ct, tuba at $tubaCents ct"
        )
        // Without the filter the drone wins: the reading would even count as a hit within ±5 ct.
        val unfiltered = checkNotNull(medianCents(spec, f, lowPass = false)) { "no voiced frames" }
        assertTrue(abs(unfiltered) < abs(tubaCents) / 4, "without low-pass: $unfiltered ct, tuba at $tubaCents ct")
    }

    @ParameterizedTest(name = "midi {0}, tuba {1} ct")
    @CsvSource("34, -20", "53, -25", "58, -20")
    fun `a drone 6 dB louder than the tuba still leaves most of the deviation visible`(midi: Int, tubaCents: Double) {
        val spec = speakerScene(midi, tubaCents, droneExcessDb = 6.0)
        val filtered = checkNotNull(medianCents(spec, spec.frequencyHz, lowPass = true)) { "no voiced frames" }
        assertTrue(filtered / tubaCents >= 0.5, "with low-pass: $filtered ct, tuba at $tubaCents ct")
    }

    @ParameterizedTest(name = "midi {0}")
    @MethodSource("notes")
    fun `without drone the low-pass does not change the reading`(midi: Int) {
        val f = NoteMath.frequency(midi)
        val spec = ToneSpec(
            frequencyHz = f,
            toneSec = TONE_SEC,
            centsContour = { -20.0 },
            micHighPassHz = MIC_HIGH_PASS_HZ,
            noiseRmsDbfs = NOISE_DBFS,
            seed = midi.toLong()
        )
        val filtered = checkNotNull(medianCents(spec, f, lowPass = true)) { "no voiced frames" }
        val unfiltered = checkNotNull(medianCents(spec, f, lowPass = false)) { "no voiced frames" }
        assertEquals(unfiltered, filtered, 1.0)
        assertEquals(-20.0, filtered, 1.0)
    }

    /** For midi 58 and 65 the octave lies above the cutoff and arrives about 11–12 dB weaker. */
    @ParameterizedTest(name = "target midi {0}")
    @CsvSource("34", "58", "65")
    fun `a tone one octave above the target is still reported as such`(midi: Int) {
        val target = NoteMath.frequency(midi)
        val spec = ToneSpec(
            frequencyHz = 2 * target,
            toneSec = TONE_SEC,
            micHighPassHz = MIC_HIGH_PASS_HZ,
            noiseRmsDbfs = NOISE_DBFS
        )
        val filtered = checkNotNull(medianCents(spec, target, lowPass = true)) { "no voiced frames" }
        assertEquals(1200.0, filtered, 2.0)
    }

    private companion object {
        const val TONE_SEC = 2.0
        const val MEASURE_FROM_SEC = 0.5
        const val REFERENCE_DBFS = -18.0
        const val NOISE_DBFS = -65.0
        const val MIC_HIGH_PASS_HZ = 120.0
        const val SPEAKER_HZ = 500.0

        /** Share of the tuba's deviation the drone may still pull towards the target with the low-pass on. */
        const val MAX_RESIDUAL_PULL = 0.15

        @JvmStatic
        fun notes() = NOTES
    }
}
