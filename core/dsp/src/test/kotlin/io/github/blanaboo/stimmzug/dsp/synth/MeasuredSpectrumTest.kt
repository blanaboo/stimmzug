package io.github.blanaboo.stimmzug.dsp.synth

import io.github.blanaboo.stimmzug.dsp.music.NoteMath
import io.github.blanaboo.stimmzug.dsp.signal.SignalMath
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class MeasuredSpectrumTest {

    private val low = doubleArrayOf(-20.0, -6.0, 0.0, -3.0)
    private val middle = doubleArrayOf(-6.0, 0.0, -10.0)
    private val high = doubleArrayOf(0.0, -20.0)
    private val spectrum = MeasuredSpectrum("Test", lowestMidi = 34, levelsDb = listOf(low, middle, high))

    @Test
    fun `levels are parsed from whole dB over several strings`() {
        assertArrayEquals(
            doubleArrayOf(-12.0, 0.0, -3.0, -40.0),
            MeasuredSpectrum.levels("-12 0  -3", " -40 ")
        )
    }

    @Test
    fun `the nearest measured note gives the levels, also at other concert pitches and outside the range`() {
        assertEquals(36, spectrum.highestMidi)
        assertSame(middle, spectrum.levelsFor(NoteMath.frequency(35)))
        assertSame(middle, spectrum.levelsFor(NoteMath.frequency(35, a4Hz = 450.0)))
        assertSame(middle, spectrum.levelsFor(NoteMath.frequency(35, a4Hz = 430.0)))
        assertSame(low, spectrum.levelsFor(NoteMath.frequency(22)))
        assertSame(high, spectrum.levelsFor(NoteMath.frequency(60)))
    }

    @Test
    fun `amplitudes are normalized and stay below the frequency limits`() {
        val amplitudes = spectrum.harmonicAmplitudes(NoteMath.frequency(34), 48_000.0)
        assertArrayEquals(low.map { SignalMath.dbToAmplitude(it) }.toDoubleArray(), amplitudes, 1e-12)
        assertEquals(1.0, amplitudes.max())

        val f0 = NoteMath.frequency(34)
        assertEquals(2, spectrum.harmonicAmplitudes(f0, 48_000.0, maxFrequencyHz = 2.5 * f0).size)
        // Above 45 % of the sample rate nothing is played; the strongest of what is left becomes 1.
        val limited = spectrum.harmonicAmplitudes(f0, sampleRate = 2.5 * f0 / 0.45)
        assertArrayEquals(doubleArrayOf(SignalMath.dbToAmplitude(-14.0), 1.0), limited, 1e-12)
    }

    @Test
    fun `validation`() {
        assertThrows<IllegalArgumentException> { MeasuredSpectrum("Leer", 30, emptyList()) }
        assertThrows<IllegalArgumentException> { MeasuredSpectrum("Leer", 30, listOf(DoubleArray(0))) }
        assertThrows<IllegalArgumentException> { spectrum.levelsFor(0.0) }
    }

    @Test
    fun `the GR55 table covers the practice range and sounds like a tuba`() {
        val gr55 = Gr55Spectrum.spectrum
        assertTrue(gr55.lowestMidi <= KONTRA_F && gr55.highestMidi >= KLEINES_F)
        for ((i, levels) in gr55.levelsDb.withIndex()) {
            assertEquals(0.0, levels.max(), "note ${gr55.lowestMidi + i}: strongest harmonic at 0 dB")
        }
        // Low notes: weak fundamental and many strong harmonics; from kleines f on the fundamental leads.
        val kontraF = gr55.levelsFor(NoteMath.frequency(KONTRA_F))
        assertTrue(kontraF[0] < -10.0 && kontraF.count { it > -20.0 } >= 10)
        val kleinesF = gr55.levelsFor(NoteMath.frequency(KLEINES_F))
        assertEquals(0.0, kleinesF[0])
        assertTrue(kleinesF.size < kontraF.size)
    }

    private companion object {
        const val KONTRA_F = 29
        const val KLEINES_F = 53
    }
}
