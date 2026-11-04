package io.github.blanaboo.stimmzug.dsp.pitch

import io.github.blanaboo.stimmzug.dsp.music.NoteMath
import io.github.blanaboo.stimmzug.dsp.synth.ToneSpec
import io.github.blanaboo.stimmzug.dsp.synth.TubaToneGenerator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PitchTrackerTest {

    @Test
    fun `emits a frame every hop on the sample time line`() {
        val f = NoteMath.frequency(41)
        val audio = TubaToneGenerator.render(ToneSpec(f, toneSec = 2.0, micHighPassHz = 120.0))
        val tracker = PitchTracker(48_000.0).apply { targetHz = f }
        val frames = tracker.processAll(audio, chunkSize = 256)

        assertTrue(frames.size > 90, "expected ~100 frames, got ${frames.size}")
        frames.zipWithNext().forEach { (a, b) -> assertEquals(0.02, b.timeSec - a.timeSec, 1e-9) }
        assertEquals(audio.size.toLong(), tracker.samplesProcessed)
        val steady = frames.filter { it.timeSec in 0.4..1.9 }
        assertTrue(steady.all { it.estimate.isVoiced })
        steady.forEach { assertEquals(0.0, NoteMath.cents(it.estimate.frequencyHz, f), 1.0, "t=${it.timeSec}") }
    }

    @Test
    fun `target can change while streaming`() {
        val low = NoteMath.frequency(34)
        val high = NoteMath.frequency(46)
        val tracker = PitchTracker(48_000.0).apply { targetHz = low }
        val first = tracker.processAll(TubaToneGenerator.render(ToneSpec(low, toneSec = 1.0)))
        tracker.targetHz = high
        val second = tracker.processAll(TubaToneGenerator.render(ToneSpec(high, toneSec = 1.0)))
        assertEquals(0.0, NoteMath.cents(first.last { it.timeSec < 0.9 }.estimate.frequencyHz, low), 1.0)
        val settled = second.filter { it.timeSec > 1.6 && it.timeSec < 2.0 }
        assertTrue(settled.isNotEmpty() && settled.all { it.estimate.isVoiced })
        settled.forEach { assertEquals(0.0, NoteMath.cents(it.estimate.frequencyHz, high), 1.0) }
    }

    @Test
    fun `no frames before enough history and validation`() {
        val tracker = PitchTracker(48_000.0, hopSec = 0.01).apply { targetHz = 29.14 }
        assertTrue(tracker.processAll(FloatArray(2_000)).isEmpty())
        assertThrows<IllegalArgumentException> { PitchTracker(48_000.0, hopSec = 0.0) }
        val tooShort = PitchTracker(48_000.0, historySec = 0.05).apply { targetHz = 29.14 }
        assertThrows<IllegalArgumentException> { tooShort.processAll(FloatArray(4_800)) }
    }
}
