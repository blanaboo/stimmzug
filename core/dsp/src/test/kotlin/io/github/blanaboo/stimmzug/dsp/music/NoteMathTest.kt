package io.github.blanaboo.stimmzug.dsp.music

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class NoteMathTest {

    @ParameterizedTest(name = "MIDI {0} = {1} Hz at A4 = 440")
    @CsvSource(
        "69, 440.0",
        "22, 29.135", // Subkontra-B: BBb tuba pedal
        "29, 43.654", // Kontra-F: F tuba pedal
        "34, 58.270", // Kontra-B
        "41, 87.307", // großes F
        "53, 174.614", // kleines f
        "65, 349.228" // f'
    )
    fun `equal tempered frequencies`(midi: Int, expectedHz: Double) {
        assertEquals(expectedHz, NoteMath.frequency(midi), 0.001)
    }

    @Test
    fun `reference pitch shifts all notes proportionally`() {
        assertEquals(443.0, NoteMath.frequency(69, a4Hz = 443.0), 1e-9)
        assertEquals(58.27 * 443.0 / 440.0, NoteMath.frequency(34, a4Hz = 443.0), 0.001)
    }

    @Test
    fun `cents between frequencies`() {
        assertEquals(1200.0, NoteMath.cents(880.0, 440.0), 1e-9)
        assertEquals(-1200.0, NoteMath.cents(220.0, 440.0), 1e-9)
        assertEquals(3.930, NoteMath.cents(441.0, 440.0), 0.001)
    }

    @Test
    fun `one cent at low tuba notes is a tiny frequency difference`() {
        val kontraE = NoteMath.frequency(28)
        val oneCentSharp = kontraE * Math.pow(2.0, 1.0 / 1200.0)
        assertEquals(0.0238, oneCentSharp - kontraE, 0.0005)
        assertEquals(1.0, NoteMath.centsFromNote(oneCentSharp, 28), 1e-9)
    }

    @Test
    fun `nearest note snaps within half a semitone`() {
        assertEquals(34, NoteMath.nearestMidi(58.27))
        assertEquals(34, NoteMath.nearestMidi(58.27 * Math.pow(2.0, 49.0 / 1200.0)))
        assertEquals(35, NoteMath.nearestMidi(58.27 * Math.pow(2.0, 51.0 / 1200.0)))
        assertEquals(69, NoteMath.nearestMidi(443.0, a4Hz = 443.0))
    }

    @Test
    fun `invalid input is rejected`() {
        assertThrows<IllegalArgumentException> { NoteMath.frequency(69, a4Hz = 0.0) }
        assertThrows<IllegalArgumentException> { NoteMath.cents(0.0, 440.0) }
        assertThrows<IllegalArgumentException> { NoteMath.cents(440.0, -1.0) }
        assertThrows<IllegalArgumentException> { NoteMath.nearestMidi(0.0) }
    }
}
