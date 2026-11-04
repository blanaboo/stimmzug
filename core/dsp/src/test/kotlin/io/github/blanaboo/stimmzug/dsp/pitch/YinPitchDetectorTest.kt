package io.github.blanaboo.stimmzug.dsp.pitch

import io.github.blanaboo.stimmzug.dsp.music.NoteMath
import io.github.blanaboo.stimmzug.dsp.pitch.PitchTestSupport.detectAt
import io.github.blanaboo.stimmzug.dsp.pitch.PitchTestSupport.frequency
import io.github.blanaboo.stimmzug.dsp.pitch.PitchTestSupport.render
import io.github.blanaboo.stimmzug.dsp.synth.ToneSpec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class YinPitchDetectorTest {
    private val detector = YinPitchDetector()

    @Test
    fun `targeted and free search find tuba notes`() {
        for (midi in listOf(29, 34, 41, 53, 65)) {
            val f = frequency(midi)
            val samples = render(ToneSpec(f, toneSec = 1.0, micHighPassHz = 100.0))
            assertEquals(0.0, NoteMath.cents(detectAt(detector, samples, f, 0.8).frequencyHz, f), 1.0, "targeted $midi")
            assertEquals(0.0, NoteMath.cents(detectAt(detector, samples, null, 0.8).frequencyHz, f), 2.0, "free $midi")
        }
    }

    @Test
    fun `noise is unvoiced`() {
        val noise = render(ToneSpec(100.0, tonePeakDbfs = null, noiseRmsDbfs = -30.0, toneSec = 1.0))
        assertFalse(detectAt(detector, noise, null, 0.8).isVoiced)
        assertFalse(detectAt(detector, noise, frequency(34), 0.8).isVoiced)
    }
}
