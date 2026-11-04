package io.github.blanaboo.stimmzug.dsp.synth

import io.github.blanaboo.stimmzug.dsp.signal.Biquad
import io.github.blanaboo.stimmzug.dsp.signal.SignalMath
import kotlin.math.abs
import kotlin.math.max
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TubaToneGeneratorTest {
    private val fs = 48_000

    @Test
    fun `steady tone repeats exactly with its period`() {
        // 60 Hz at 48 kHz = 800 samples per period
        val x = TubaToneGenerator.renderDouble(ToneSpec(frequencyHz = 60.0, toneSec = 1.0, attackSec = 0.0))
        var maxDiff = 0.0
        for (i in 10_000 until 40_000) maxDiff = max(maxDiff, abs(x[i] - x[i + 800]))
        assertEquals(0.0, maxDiff, 1e-9)
    }

    @Test
    fun `cents contour shifts the pitch`() {
        // 48 Hz + 1200 cents = 96 Hz = 500 samples per period
        val x = TubaToneGenerator.renderDouble(
            ToneSpec(frequencyHz = 48.0, toneSec = 1.0, attackSec = 0.0, centsContour = { 1200.0 })
        )
        var maxDiff = 0.0
        for (i in 10_000 until 40_000) maxDiff = max(maxDiff, abs(x[i] - x[i + 500]))
        assertEquals(0.0, maxDiff, 1e-6)
    }

    @Test
    fun `peak level and envelope timing`() {
        val spec = ToneSpec(frequencyHz = 87.31, toneSec = 1.0, leadInSec = 0.5, tailSec = 0.25, tonePeakDbfs = -12.0)
        val x = TubaToneGenerator.renderDouble(spec)
        assertEquals(((0.5 + 1.0 + 0.15 + 0.25) * fs).toInt(), x.size)
        assertEquals(0.0, SignalMath.peak(x, 0, fs / 2), 0.0, "lead-in is silent")
        assertEquals(-12.0, SignalMath.amplitudeToDb(SignalMath.peak(x, fs, (1.4 * fs).toInt())), 0.1)
        assertEquals(0.0, SignalMath.peak(x, (1.66 * fs).toInt(), x.size), 0.0, "tail is silent")
    }

    @Test
    fun `harmonic levels follow the tuba spectrum`() {
        val f0 = 50.0 // Kontra-G-ish, harmonics at 50, 100, 150 ... Hz
        val x = TubaToneGenerator.renderDouble(ToneSpec(frequencyHz = f0, toneSec = 2.0, attackSec = 0.0))
        val amplitudes = TubaSpectrum.harmonicAmplitudes(f0, fs.toDouble())
        val measured = (1..8).map { SignalMath.toneAmplitude(x, f0 * it, fs.toDouble(), 0, 2 * fs) }
        // Ratios between harmonics must match the model (the absolute scale depends on peak normalization).
        for (h in 1 until 8) {
            val expectedDb = SignalMath.amplitudeToDb(amplitudes[h] / amplitudes[0])
            val actualDb = SignalMath.amplitudeToDb(measured[h] / measured[0])
            assertEquals(expectedDb, actualDb, 0.3, "harmonic ${h + 1}")
        }
        assertTrue(measured[0] < measured[2], "low notes have a weaker fundamental than the 3rd harmonic")
    }

    @Test
    fun `microphone high-pass attenuates the fundamental`() {
        val f0 = 41.2 // Kontra-E
        val flat = TubaToneGenerator.renderDouble(ToneSpec(frequencyHz = f0, toneSec = 2.0, attackSec = 0.0))
        val filtered = TubaToneGenerator.renderDouble(
            ToneSpec(frequencyHz = f0, toneSec = 2.0, attackSec = 0.0, micHighPassHz = 120.0)
        )
        val from = fs / 2
        val to = 2 * fs
        val attenuationDb = SignalMath.amplitudeToDb(
            SignalMath.toneAmplitude(filtered, f0, fs.toDouble(), from, to) /
                SignalMath.toneAmplitude(flat, f0, fs.toDouble(), from, to)
        )
        assertEquals(SignalMath.amplitudeToDb(Biquad.butterworthHighPassGain(f0, 120.0)), attenuationDb, 0.5)
    }

    @Test
    fun `noise, bleed only and clipping`() {
        val noise = TubaToneGenerator.renderDouble(
            ToneSpec(frequencyHz = 58.27, tonePeakDbfs = null, noiseRmsDbfs = -50.0, toneSec = 1.0)
        )
        assertEquals(-50.0, SignalMath.rmsDb(noise), 0.3)

        val bleed = TubaToneGenerator.renderDouble(
            ToneSpec(frequencyHz = 58.27, tonePeakDbfs = null, bleedFrequencyHz = 116.54, bleedPeakDbfs = -30.0)
        )
        assertEquals(-30.0, SignalMath.amplitudeToDb(SignalMath.peak(bleed)), 0.1)

        val clipped = TubaToneGenerator.renderDouble(ToneSpec(frequencyHz = 58.27, tonePeakDbfs = 0.0, clipDbfs = -6.0))
        assertEquals(-6.0, SignalMath.amplitudeToDb(SignalMath.peak(clipped)), 0.01)
    }

    @Test
    fun `jitter is reproducible per seed`() {
        val spec =
            ToneSpec(
                frequencyHz = 58.27,
                toneSec = 1.0,
                jitterCents = 5.0,
                vibratoRateHz = 5.0,
                vibratoDepthCents = 3.0
            )
        val a = TubaToneGenerator.render(spec)
        val b = TubaToneGenerator.render(spec)
        val c = TubaToneGenerator.render(spec.copy(seed = 2))
        assertArrayEquals(a, b)
        assertFalse(a.contentEquals(c))
    }

    @Test
    fun `spectrum model is normalized and dynamic dependent`() {
        val mezzo = TubaSpectrum.harmonicAmplitudes(58.27, 48_000.0)
        val forte = TubaSpectrum.harmonicAmplitudes(58.27, 48_000.0, TubaSpectrum.Dynamic.FORTE)
        val piano = TubaSpectrum.harmonicAmplitudes(58.27, 48_000.0, TubaSpectrum.Dynamic.PIANO)
        assertEquals(1.0, mezzo.max(), 1e-12)
        assertTrue(forte[15] > mezzo[15] && mezzo[15] > piano[15], "louder = brighter")
        assertEquals(1, TubaSpectrum.harmonicAmplitudes(5000.0, 48_000.0).size)
        assertTrue(TubaSpectrum.harmonicAmplitudes(29.14, 8_000.0).size * 29.14 <= 3600.0)
    }
}
