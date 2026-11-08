package io.github.blanaboo.stimmzug.dsp.synth

import io.github.blanaboo.stimmzug.dsp.music.NoteMath
import io.github.blanaboo.stimmzug.dsp.signal.SignalMath
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class DroneSynthTest {

    private class Event(val timeSec: Double, val action: (DroneSynth) -> Unit)

    private fun at(timeSec: Double, action: (DroneSynth) -> Unit) = Event(timeSec, action)

    /** Renders [seconds] in blocks of [block] samples; events fire exactly at their sample position. */
    private fun render(
        seconds: Double,
        vararg events: Event,
        block: Int = 480,
        synth: DroneSynth = DroneSynth(FS)
    ): FloatArray {
        val total = (seconds * FS).toInt()
        val out = FloatArray(total)
        val pending = events.sortedBy { it.timeSec }.toMutableList()
        var pos = 0
        while (pos < total) {
            while (pending.isNotEmpty() && sample(pending[0].timeSec) <= pos) pending.removeAt(0).action(synth)
            val next = pending.firstOrNull()?.let { sample(it.timeSec) } ?: total
            val n = minOf(block, next - pos, total - pos)
            synth.render(out, pos, n)
            pos += n
        }
        return out
    }

    private fun sample(timeSec: Double) = (timeSec * FS).toInt()

    /** Phase of the component at [f] in a Hann window of 1 s centred at [centerSec], relative to t = 0. */
    private fun phase(x: FloatArray, f: Double, centerSec: Double): Double {
        val len = FS.toInt()
        val start = sample(centerSec) - len / 2
        var re = 0.0
        var im = 0.0
        for (i in 0 until len) {
            val n = start + i
            val w = 0.5 - 0.5 * cos(2 * PI * i / (len - 1))
            val arg = 2 * PI * f * n / FS
            re += x[n] * w * cos(arg)
            im -= x[n] * w * sin(arg)
        }
        return atan2(im, re)
    }

    /** Mean frequency between two window centres from the phase advance, refined from [guess] in two steps. */
    private fun measureFrequency(x: FloatArray, guess: Double, fromSec: Double, toSec: Double): Double {
        var f = guess
        for (baseline in listOf(1.0, toSec - fromSec)) {
            var d = phase(x, f, fromSec + baseline) - phase(x, f, fromSec)
            while (d > PI) d -= 2 * PI
            while (d < -PI) d += 2 * PI
            f += d / (2 * PI * baseline)
        }
        return f
    }

    private fun rms(x: FloatArray, fromSec: Double, toSec: Double) =
        SignalMath.rms(SignalMath.toDoubleArray(x), sample(fromSec), sample(toSec))

    private fun maxStep(x: FloatArray, fromSec: Double = 0.0, toSec: Double = x.size / FS): Double {
        var m = 0.0
        for (i in max(1, sample(fromSec)) until minOf(x.size, sample(toSec))) {
            m =
                max(m, abs(x[i] - x[i - 1]).toDouble())
        }
        return m
    }

    /** Largest sample-to-sample step of the steady drone on [f] at full volume. */
    private fun steadyStep(f: Double) = maxStep(render(1.5, at(0.0) { it.play(f) }), 0.5, 1.5)

    @Test
    fun `frequency stays exact over 60 seconds`() {
        val cases = listOf(
            NoteMath.frequency(22) to 0, // Subkontra-B, 29.14 Hz
            NoteMath.frequency(34) to 0, // Kontra-B, 58.27 Hz
            NoteMath.frequency(65) to 0, // f', 349.23 Hz
            NoteMath.frequency(41) to 2, // großes F two octaves up, 349.23 Hz
            443.0 * 2.0.pow(-21.0 / 12) to 1 // C at a4 = 443 Hz, one octave up
        )
        for ((note, octaves) in cases) {
            val expected = note * 2.0.pow(octaves)
            val x = render(
                61.0,
                at(0.0) {
                    it.setOctaveOffset(octaves)
                    it.play(note)
                }
            )
            val measured = measureFrequency(x, expected, 1.0, 60.0)
            val errorCents = NoteMath.cents(measured, expected)
            assertTrue(abs(errorCents) < 0.1, "$expected Hz: $errorCents ct")
            // In fact the error is far below what any listener or detector could notice.
            assertTrue(abs(errorCents) < 0.001, "$expected Hz: $errorCents ct")
        }
    }

    @Test
    fun `output does not depend on the block size`() {
        val events = arrayOf(
            at(0.0) { it.play(NoteMath.frequency(34)) },
            at(0.7) { it.play(NoteMath.frequency(41)) },
            at(1.1) { it.setOctaveOffset(1) },
            at(1.4) { it.setVolume(0.4) },
            at(1.6) { it.playConfirmation() },
            at(2.0) { it.stop() }
        )
        val reference = render(3.0, *events, block = (3.0 * FS).toInt())
        for (block in listOf(1, 7, 256, 480, 4096)) {
            assertArrayEquals(reference, render(3.0, *events, block = block), "block size $block")
        }
    }

    @Test
    fun `no jumps at start, note change, octave change, volume change, restart and stop`() {
        val low = NoteMath.frequency(34)
        val high = NoteMath.frequency(41)
        val x = render(
            6.0,
            at(0.0) { it.play(low) },
            at(1.0) { it.play(high) },
            at(2.0) { it.setOctaveOffset(1) },
            at(3.0) { it.setVolume(0.3) },
            at(3.5) { it.setVolume(1.0) },
            at(4.0) { it.stop() },
            at(4.1) { it.play(low) }, // during the fade-out
            at(5.0) { it.stop() }
        )
        // Equal-power crossfade: the slope is at most √2 × that of the higher note; allow 1.5×.
        val threshold = 1.5 * steadyStep(2 * high)
        assertTrue(maxStep(x) <= threshold, "max step ${maxStep(x)} > $threshold")
        // The threshold is meaningful: a hard start or cut would jump by up to the full peak.
        assertTrue(x.maxOf { abs(it) } > 5 * threshold)
        assertTrue(x.all { abs(it) <= 1f })
    }

    @Test
    fun `fades in and out within the configured time and ends in exact silence`() {
        val f = NoteMath.frequency(41)
        val synth = DroneSynth(FS)
        assertTrue(synth.isSilent)
        val x = render(2.0, at(0.0) { it.play(f) }, at(1.0) { it.stop() }, synth = synth)
        val full = rms(x, 0.4, 0.9)
        assertTrue(abs(x[0]) < 1e-3, "starts from silence")
        assertTrue(rms(x, 0.1, 0.15) in 0.1 * full..0.9 * full, "fading in at 0.1 s")
        assertEquals(full, rms(x, 0.3, 0.8), 0.01 * full)
        assertTrue((sample(1.3) until x.size).all { x[it] == 0f }, "silent after the fade-out")
        assertTrue(synth.isSilent)
        synth.play(f)
        assertFalse(synth.isSilent, "a queued note is not silent")
    }

    @Test
    fun `crossfade replaces the old note`() {
        val old = NoteMath.frequency(34)
        val new = NoteMath.frequency(39)
        val x = SignalMath.toDoubleArray(render(2.0, at(0.0) { it.play(old) }, at(1.0) { it.play(new) }))
        fun amplitude(f: Double, fromSec: Double, toSec: Double) =
            SignalMath.toneAmplitude(x, f, FS, sample(fromSec), sample(toSec))
        val oldBefore = amplitude(old, 0.5, 1.0)
        val newAfter = amplitude(new, 1.4, 1.9)
        assertTrue(amplitude(old, 1.4, 1.9) < 0.01 * oldBefore, "old note gone after the crossfade")
        assertTrue(amplitude(new, 0.5, 1.0) < 0.01 * newAfter, "new note absent before")
        // Equal power: the overall level does not dip during the crossfade.
        val levelDuring = rms(x.map { it.toFloat() }.toFloatArray(), 1.05, 1.15)
        assertTrue(levelDuring > 0.8 * rms(x.map { it.toFloat() }.toFloatArray(), 0.5, 1.0))
    }

    @Test
    fun `octave offset transposes the drone`() {
        val f = NoteMath.frequency(34)
        val x =
            render(4.0, at(0.0) { it.play(f) }, at(1.0) { it.setOctaveOffset(2) }, at(2.5) { it.setOctaveOffset(2) })
        // Windows (1 s) centred at 2.0 and 3.5 s: after the crossfade (1.0–1.2 s), so the old note does not bias them.
        assertEquals(0.0, NoteMath.cents(measureFrequency(x, 4 * f, 2.0, 3.5), 4 * f), 0.001)

        val preset = DroneSynth(FS).apply { setOctaveOffset(1) }
        val y = render(3.0, at(0.0) { it.play(f) }, synth = preset)
        assertEquals(0.0, NoteMath.cents(measureFrequency(y, 2 * f, 0.6, 2.4), 2 * f), 0.001)

        assertThrows<IllegalArgumentException> { DroneSynth(FS).setOctaveOffset(3) }
        assertThrows<IllegalArgumentException> { DroneSynth(FS).setOctaveOffset(-1) }
        assertThrows<IllegalArgumentException> { DroneSynth(FS).apply { setOctaveOffset(2) }.play(6000.0) }
    }

    @Test
    fun `volume scales the level with a ramp`() {
        val f = NoteMath.frequency(41)
        val x = render(2.0, at(0.0) { it.play(f) }, at(1.0) { it.setVolume(0.5) })
        val ratioDb = 20 * log10(rms(x, 1.2, 1.9) / rms(x, 0.4, 0.9))
        assertEquals(-6.02, ratioDb, 0.05)
        assertThrows<IllegalArgumentException> { DroneSynth(FS).setVolume(1.5) }
    }

    @Test
    fun `playing the same note again changes nothing`() {
        val f = NoteMath.frequency(34)
        val once = render(2.0, at(0.0) { it.play(f) })
        val twice = render(2.0, at(0.0) { it.play(f) }, at(1.0) { it.play(f) })
        assertArrayEquals(once, twice)
    }

    @Test
    fun `confirmation chime is mixed in on the note's pitch class and fades to silence`() {
        val f = NoteMath.frequency(34) // B, chime on 932.3 Hz
        // stop() comes first: the chime must still use the note that was playing.
        val events = arrayOf(at(0.0) { it.play(f) }, at(1.0) { it.stop() })
        val withChime = render(4.5, *events, at(1.0) { it.playConfirmation() })
        val without = render(4.5, *events)
        val chime = DoubleArray(withChime.size) { (withChime[it] - without[it]).toDouble() }

        assertTrue((0 until sample(1.0)).all { chime[it] == 0.0 })
        assertTrue(chime.maxOf { abs(it) } <= ConfirmationChime.PEAK + 1e-6)
        val base = SignalMath.toneAmplitude(chime, 16 * f, FS, sample(1.0), sample(1.5))
        assertTrue(base > 0.02, "chime fundamental $base")
        assertTrue(SignalMath.toneAmplitude(chime, 1046.5, FS, sample(1.0), sample(1.5)) < 0.1 * base)
        assertTrue((sample(1.0 + ConfirmationChime.LENGTH_SEC) until chime.size).all { withChime[it] == 0f })
        assertTrue(maxStep(withChime) <= 1.5 * steadyStep(f) + 2 * PI * 5.4 * 16 * f / FS * ConfirmationChime.PEAK)

        // Without a note the chime sounds on C6; a retrigger while sounding is ignored.
        val alone = SignalMath.toDoubleArray(
            render(
                3.0,
                at(0.0) {
                    it.playConfirmation()
                },
                at(0.5) { it.playConfirmation() }
            )
        )
        assertTrue(SignalMath.toneAmplitude(alone, 1046.5, FS, 0, sample(0.5)) > 0.02)
        assertTrue(SignalMath.rms(alone, sample(0.6), sample(0.7)) < SignalMath.rms(alone, sample(0.05), sample(0.15)))
    }

    @Test
    fun `peak stays within full scale during a crossfade with chime`() {
        var peak = 0f
        for (midi in listOf(22, 34, 46, 58, 70)) {
            val x = render(
                1.5,
                at(0.0) { it.play(NoteMath.frequency(midi)) },
                at(0.5) {
                    it.play(NoteMath.frequency(midi + 5))
                    it.playConfirmation()
                }
            )
            peak = max(peak, x.maxOf { abs(it) })
        }
        assertTrue(peak <= 1f)
        assertTrue(peak > DroneSynth.DRONE_PEAK * 0.9f, "drone uses its headroom: peak $peak")
    }

    @Test
    fun `drone wavetable has the lower crest factor, much lower for low notes`() {
        for (f in listOf(29.14, 58.27, 116.5, 233.1)) {
            val amplitudes = TubaSpectrum.harmonicAmplitudes(f, FS)
            fun crest(table: Wavetable): Double {
                val samples = DoubleArray(table.size) { table.valueAt(it.toDouble() / table.size) }
                return 1.0 / SignalMath.rms(samples) // tables are peak-normalized
            }
            val drone = crest(DroneSynth.droneWavetable(f, FS, DroneConfig(timbre = DroneTimbre.SYNTHETIC)))
            val zeroPhase = crest(Wavetable(amplitudes))
            assertTrue(drone <= zeroPhase, "$f Hz: crest $drone vs $zeroPhase")
            if (f < 100.0) assertTrue(drone < 0.8 * zeroPhase, "$f Hz: crest $drone vs $zeroPhase")
        }
    }

    @Test
    fun `the recorded timbre sounds with the measured harmonic levels`() {
        for (midi in listOf(29, 36, 46, 53)) {
            val f = NoteMath.frequency(midi, 442.0)
            val synth = DroneSynth(FS, DroneConfig(fadeSec = 0.0))
            val x = SignalMath.toDoubleArray(render(1.0, at(0.0) { it.play(f) }, synth = synth))
            val levels = Gr55Spectrum.spectrum.levelsFor(f)
            val strongest = levels.indices.maxBy { levels[it] }
            val reference = SignalMath.toneAmplitude(x, (strongest + 1) * f, FS, sample(0.2), sample(1.0))
            for (k in 0 until minOf(8, levels.size)) {
                if (levels[k] < -40.0) continue
                val measured =
                    20 * log10(SignalMath.toneAmplitude(x, (k + 1) * f, FS, sample(0.2), sample(1.0)) / reference)
                assertEquals(levels[k], measured, 0.3, "MIDI $midi, harmonic ${k + 1}")
            }
        }
    }

    @Test
    fun `the synthetic timbre stays available`() {
        val f = NoteMath.frequency(34)
        val synthetic = DroneSynth.harmonicAmplitudes(f, FS, DroneConfig(timbre = DroneTimbre.SYNTHETIC))
        assertArrayEquals(TubaSpectrum.harmonicAmplitudes(f, FS), synthetic, 1e-12)
        assertEquals(DroneTimbre.RECORDED, DroneConfig().timbre)
    }

    @Test
    fun `validation`() {
        assertThrows<IllegalArgumentException> { DroneSynth(0.0) }
        assertThrows<IllegalArgumentException> { DroneSynth(FS).play(0.0) }
        assertThrows<IllegalArgumentException> { DroneConfig(fadeSec = -1.0) }
        assertThrows<IllegalArgumentException> { DroneSynth(FS).render(FloatArray(10), 5, 10) }
    }

    private companion object {
        const val FS = 48_000.0
    }
}
