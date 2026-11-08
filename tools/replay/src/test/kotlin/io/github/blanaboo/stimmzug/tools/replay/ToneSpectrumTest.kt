package io.github.blanaboo.stimmzug.tools.replay

import io.github.blanaboo.stimmzug.dsp.music.NoteMath
import io.github.blanaboo.stimmzug.dsp.signal.SignalMath
import io.github.blanaboo.stimmzug.dsp.synth.MeasuredSpectrum
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.util.Random
import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.roundToInt
import kotlin.math.sin
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class ToneSpectrumTest {

    @TempDir
    lateinit var dir: File

    /** Held notes with known harmonic levels (dB relative to the strongest), as the owner played them for #72. */
    private val played = linkedMapOf(
        34 to doubleArrayOf(-12.0, 0.0, -3.0, -6.0, -10.0, -20.0, -30.0),
        41 to doubleArrayOf(-6.0, 0.0, -3.0, -12.0, -25.0),
        46 to doubleArrayOf(-1.0, 0.0, -2.0, -15.0, -30.0)
    )

    /** 2 s room noise, then each note steadily for 4 s, 1.5 s apart. */
    private fun recording(): FloatArray {
        val fs = TestAudio.FS.toDouble()
        val noise = Random(7)
        val parts = ArrayList<DoubleArray>()
        parts += DoubleArray((2.0 * fs).toInt())
        for ((midi, levels) in played) {
            val f0 = NoteMath.frequency(midi, A4)
            val tone = DoubleArray((4.0 * fs).toInt()) { i ->
                val t = i / fs
                var v = 0.0
                for (k in levels.indices) v += SignalMath.dbToAmplitude(levels[k]) * sin(2 * PI * (k + 1) * f0 * t + k)
                0.1 * v * minOf(1.0, t / 0.05, (4.0 - t) / 0.05)
            }
            parts += tone
            parts += DoubleArray((1.5 * fs).toInt())
        }
        val all = parts.flatMap { it.asList() }
        return FloatArray(all.size) { (all[it] + 1e-4 * noise.nextGaussian()).toFloat() }
    }

    @Test
    fun `finds the held notes and measures their harmonics`() {
        val notes = ToneSpectrum.findNotes(recording(), TestAudio.FS.toDouble(), SpectrumOptions(a4Hz = A4))
        assertEquals(played.keys.toList(), notes.map { it.midi })
        for (note in notes) {
            assertEquals(0.0, note.cents, 0.5, "pitch of ${note.midi}")
            assertTrue(note.spreadCents < 0.5, "steady tone: ${note.spreadCents} ct")
            val expected = played.getValue(note.midi)
            val peak = note.powerDb.max()
            for (k in expected.indices) {
                assertEquals(expected[k], note.powerDb[k] - peak, 0.3, "harmonic ${k + 1} of ${note.midi}")
            }
            assertTrue(note.snrDb[0] > 40.0)
        }
    }

    private fun take(
        midi: Int,
        levels: DoubleArray,
        spread: Double = 3.0,
        snr: DoubleArray = DoubleArray(levels.size) { 60.0 }
    ) = HeldNote(
        startSec = 0.0,
        endSec = 1.0,
        pitch = NotePitch(midi, NoteMath.frequency(midi, A4), cents = 0.0, spreadCents = spread),
        levelDbfs = -20.0,
        powerDb = levels,
        snrDb = snr
    )

    @Test
    fun `takes are averaged in power, wandering ones skipped and gaps filled from the neighbours`() {
        val levels = ToneSpectrum.combine(
            listOf(
                take(34, doubleArrayOf(0.0, -10.0)),
                take(34, doubleArrayOf(-20.0, -10.0)),
                take(34, doubleArrayOf(30.0, 0.0), spread = 40.0),
                take(37, doubleArrayOf(-4.0, 0.0, -6.0))
            )
        )
        assertEquals(listOf(34, 35, 36, 37), levels.map { it.midi })
        assertEquals(listOf(2, 0, 0, 1), levels.map { it.takes })
        val mean = 10 * log10((1.0 + 0.01) / 2)
        assertArrayEquals(doubleArrayOf(0.0, -10.0 - mean), levels[0].levelsDb, 1e-9)
        // Filled in: mean of 34 and 37 for the harmonics both have, normalized.
        val filled = doubleArrayOf((0.0 - 4.0) / 2, (-10.0 - mean + 0.0) / 2)
        assertArrayEquals(DoubleArray(2) { filled[it] - filled.max() }, levels[1].levelsDb, 1e-9)
        assertArrayEquals(levels[1].levelsDb, levels[2].levelsDb, 1e-9)
    }

    @Test
    fun `a table ends with the last harmonic above the floor and clear of the noise`() {
        val weakTail = take(41, doubleArrayOf(0.0, -10.0, -55.0, -40.0, -62.0, -70.0))
        assertArrayEquals(doubleArrayOf(0.0, -10.0, -55.0, -40.0), ToneSpectrum.combine(listOf(weakTail))[0].levelsDb)

        val noisy = take(41, doubleArrayOf(0.0, -10.0, -20.0), snr = doubleArrayOf(50.0, 40.0, 5.0))
        assertArrayEquals(doubleArrayOf(0.0, -10.0), ToneSpectrum.combine(listOf(noisy))[0].levelsDb)
    }

    @Test
    fun `the Kotlin table reads back as written`() {
        val levels = ToneSpectrum.combine(
            listOf(take(34, DoubleArray(45) { -it.toDouble() }), take(35, doubleArrayOf(-3.4, 0.0, -7.6)))
        )
        val file = File(dir, "TestSpectrum.kt")
        val code = SpectrumReport.kotlin(file, "Test-Tuba", "aufnahme.wav", A4, levels)
        assertTrue(code.contains("internal object TestSpectrum {"))
        assertTrue(code.contains("lowestMidi = 34,"))
        assertTrue(code.lines().all { it.length <= 120 }, "line length")
        val quoted = Regex("\"([^\"]*)\"")
        val strings = Regex("MeasuredSpectrum\\.levels\\(([^)]*)\\)").findAll(code).map { call ->
            val parts = quoted.findAll(call.groupValues[1]).map { it.groupValues[1] }.toList()
            MeasuredSpectrum.levels(*parts.toTypedArray())
        }.toList()
        assertEquals(2, strings.size)
        for ((written, note) in strings.zip(levels)) {
            assertArrayEquals(note.levelsDb.map { it.roundToInt().toDouble() }.toDoubleArray(), written)
        }
    }

    @Test
    fun `the CLI writes CSV, report and Kotlin table`() {
        val wav = File(dir, "tuba.wav")
        TestAudio.write(wav, recording())
        val kotlinFile = File(dir, "TubaSpectrum.kt")
        val out = ByteArrayOutputStream()
        val code = run(
            listOf("--spectrum", wav.path, "--a4", "442", "--name", "Test-Tuba", "--kotlin", kotlinFile.path),
            PrintStream(out, true, "UTF-8"),
            PrintStream(ByteArrayOutputStream())
        )
        assertEquals(0, code, out.toString("UTF-8"))
        assertTrue(out.toString("UTF-8").contains("3 gehaltene Töne gefunden, 13 Töne in der Tabelle"))
        val report = File(dir, "tuba-obertoene.md").readText()
        assertTrue(report.contains("| großes F | 1 | 87,7 | 5 |"), report)
        assertTrue(report.contains("| großes Ges | ergänzt | – |"), report)
        assertEquals(4, File(dir, "tuba-obertoene.csv").readLines().size)
        assertTrue(kotlinFile.readText().contains("name = \"Test-Tuba\""))
    }

    private companion object {
        const val A4 = 442.0
    }
}
