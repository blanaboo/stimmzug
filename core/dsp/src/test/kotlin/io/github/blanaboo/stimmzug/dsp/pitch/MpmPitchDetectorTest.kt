package io.github.blanaboo.stimmzug.dsp.pitch

import io.github.blanaboo.stimmzug.dsp.music.NoteMath
import io.github.blanaboo.stimmzug.dsp.pitch.PitchTestSupport.CLEAN
import io.github.blanaboo.stimmzug.dsp.pitch.PitchTestSupport.FS
import io.github.blanaboo.stimmzug.dsp.pitch.PitchTestSupport.HARSH
import io.github.blanaboo.stimmzug.dsp.pitch.PitchTestSupport.NOTES
import io.github.blanaboo.stimmzug.dsp.pitch.PitchTestSupport.PHONE
import io.github.blanaboo.stimmzug.dsp.pitch.PitchTestSupport.detectAt
import io.github.blanaboo.stimmzug.dsp.pitch.PitchTestSupport.frequency
import io.github.blanaboo.stimmzug.dsp.pitch.PitchTestSupport.render
import io.github.blanaboo.stimmzug.dsp.synth.ToneSpec
import kotlin.math.abs
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class MpmPitchDetectorTest {
    private val detector = MpmPitchDetector()

    private fun errorCents(scenario: PitchTestSupport.Scenario, midi: Int, endSec: Double = 0.8): Double {
        val f = frequency(midi)
        val estimate = detectAt(detector, render(scenario.spec(f)), f, endSec)
        assertTrue(estimate.isVoiced, "${scenario.name}, MIDI $midi: unvoiced (clarity ${estimate.clarity})")
        return NoteMath.cents(estimate.frequencyHz, f)
    }

    @ParameterizedTest(name = "MIDI {0}")
    @MethodSource("notes")
    fun `clean tones are measured within 1 cent`(midi: Int) {
        assertEquals(0.0, errorCents(CLEAN, midi), 1.0)
    }

    @ParameterizedTest(name = "MIDI {0}")
    @MethodSource("notes")
    fun `phone microphone with roll-off and noise stays within 2 cents`(midi: Int) {
        assertEquals(0.0, errorCents(PHONE, midi), 2.0)
    }

    @ParameterizedTest(name = "MIDI {0}")
    @MethodSource("notes")
    fun `harsh conditions with clipping stay within 2 cents`(midi: Int) {
        assertEquals(0.0, errorCents(HARSH, midi), 2.0)
    }

    @Test
    fun `median error on clean tones is below half a cent`() {
        val errors = NOTES.map { abs(errorCents(CLEAN, it)) }.sorted()
        assertTrue(errors[errors.size / 2] < 0.5, "median ${errors[errors.size / 2]} ct")
    }

    @Test
    fun `detuned tone reports its deviation`() {
        val target = frequency(34) // Kontra-B
        val estimate = detectAt(detector, render(ToneSpec(target, toneSec = 1.0, centsContour = { 17.0 })), target, 0.8)
        assertEquals(17.0, NoteMath.cents(estimate.frequencyHz, target), 1.0)
    }

    @ParameterizedTest(name = "target MIDI {0}")
    @MethodSource("octaveUpTargets")
    fun `tone an octave above the target is reported an octave up`(midi: Int) {
        val target = frequency(midi)
        val estimate = detectAt(detector, render(PHONE.spec(2 * target)), target, 0.8)
        assertTrue(estimate.isVoiced, "unvoiced (clarity ${estimate.clarity})")
        assertEquals(1200.0, NoteMath.cents(estimate.frequencyHz, target), 2.0)
    }

    @ParameterizedTest(name = "target MIDI {0}")
    @MethodSource("octaveDownTargets")
    fun `tone an octave below the target is reported an octave down`(midi: Int) {
        val target = frequency(midi)
        val estimate = detectAt(detector, render(PHONE.spec(target / 2)), target, 0.8)
        assertTrue(estimate.isVoiced, "unvoiced (clarity ${estimate.clarity})")
        assertEquals(-1200.0, NoteMath.cents(estimate.frequencyHz, target), 2.0)
    }

    @Test
    fun `fifth above the target is reported as such`() {
        val target = frequency(41) // target großes F, the player plays a fifth above (kleines c)
        val played = frequency(48)
        val estimate = detectAt(detector, render(ToneSpec(played, toneSec = 1.0)), target, 0.8)
        assertEquals(0.0, NoteMath.cents(estimate.frequencyHz, played), 2.0)
    }

    @Test
    fun `subharmonic below the detector range is not taken for the octave below`() {
        // Period doubling as in the Iowa recordings of E1/F1 ff: a weaker component at half the frequency, so the
        // tone stays clearly periodic (clarity ~0.85) but the octave below is even more periodic.
        fun withSubharmonic(f: Double) =
            render(ToneSpec(f, toneSec = 1.0, bleedFrequencyHz = f / 2, bleedPeakDbfs = -22.0))
        val e1 = frequency(28) // 41.2 Hz, the subharmonic at 20.6 Hz is below minHz = 25
        assertEquals(0.0, NoteMath.cents(detectAt(detector, withSubharmonic(e1), e1, 0.8).frequencyHz, e1), 5.0)
        // The guard is what makes the difference: with a range down to 15 Hz the octave below wins.
        val wideRange = MpmPitchDetector(DetectorConfig(minHz = 15.0))
        assertEquals(-1200.0, NoteMath.cents(detectAt(wideRange, withSubharmonic(e1), e1, 0.8).frequencyHz, e1), 5.0)
        // Within the range the octave below is still reported: there a player can really be an octave too low.
        val a1 = frequency(33) // 55 Hz, subharmonic at 27.5 Hz
        assertEquals(-1200.0, NoteMath.cents(detectAt(detector, withSubharmonic(a1), a1, 0.8).frequencyHz, a1), 5.0)
    }

    @Test
    fun `free search finds tuba notes without a target`() {
        for (midi in listOf(29, 34, 41, 53, 65)) {
            val f = frequency(midi)
            val estimate = detectAt(detector, render(ToneSpec(f, toneSec = 1.0, micHighPassHz = 100.0)), null, 0.8)
            assertEquals(0.0, NoteMath.cents(estimate.frequencyHz, f), 2.0, "MIDI $midi")
        }
    }

    @Test
    fun `noise and silence are unvoiced`() {
        val noise = render(ToneSpec(100.0, tonePeakDbfs = null, noiseRmsDbfs = -30.0, toneSec = 1.0))
        val noisy = detectAt(detector, noise, frequency(34), 0.8)
        assertFalse(noisy.isVoiced)
        assertTrue(noisy.clarity < 0.5)
        assertEquals(-30.0, noisy.levelDb, 1.0)
        assertFalse(detectAt(detector, noise, null, 0.8).isVoiced)

        val silence = detectAt(detector, DoubleArray(FS), frequency(34), 0.9)
        assertFalse(silence.isVoiced)
        assertTrue(silence.levelDb < -150)
    }

    @Test
    fun `window planning decimates low notes and validates input`() {
        val low = detector.requiredSamples(48_000.0, 29.14)
        val high = detector.requiredSamples(48_000.0, 349.2)
        assertTrue(low > high, "low notes need longer windows ($low vs $high)")
        assertTrue(low < 48_000 * 0.25, "lowest notes need less than 250 ms ($low samples)")
        assertThrows<IllegalArgumentException> { detector.requiredSamples(48_000.0, 10.0) }
        assertThrows<IllegalArgumentException> { detector.detect(DoubleArray(100), 48_000.0, 58.27) }
        assertThrows<IllegalArgumentException> { DetectorConfig(minHz = 100.0, maxHz = 50.0) }
    }

    @Test
    fun `works at 44_1 kHz`() {
        val f = frequency(34)
        val samples = render(ToneSpec(f, toneSec = 1.0, sampleRate = 44_100, micHighPassHz = 120.0))
        val estimate = detector.detect(samples, 44_100.0, f, end = (0.8 * 44_100).toInt())
        assertEquals(0.0, NoteMath.cents(estimate.frequencyHz, f), 1.0)
    }

    companion object {
        @JvmStatic
        fun notes() = NOTES

        /** Targets whose upper octave is still inside the detector range. */
        @JvmStatic
        fun octaveUpTargets() = NOTES.filter { 2 * frequency(it) <= DetectorConfig().maxHz }

        /** Targets whose lower octave is still inside the detector range. */
        @JvmStatic
        fun octaveDownTargets() = NOTES.filter { frequency(it) / 2 >= DetectorConfig().minHz }
    }
}
