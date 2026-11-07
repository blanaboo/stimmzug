package io.github.blanaboo.stimmzug.tools.replay

import io.github.blanaboo.stimmzug.dsp.music.NoteMath
import java.awt.Color
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import javax.imageio.ImageIO
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir

class MainTest {
    @TempDir
    lateinit var dir: File

    private class Outcome(val code: Int, val out: String, val err: String)

    private fun cli(vararg args: String): Outcome {
        val out = ByteArrayOutputStream()
        val err = ByteArrayOutputStream()
        val code = run(args.toList(), PrintStream(out, true, Charsets.UTF_8), PrintStream(err, true, Charsets.UTF_8))
        return Outcome(code, out.toString(Charsets.UTF_8), err.toString(Charsets.UTF_8))
    }

    private fun recording(name: String, tubaMidi: Int?): File {
        val file = File(dir, name)
        TestAudio.write(file, TestAudio.scene(34, tubaMidi?.let { NoteMath.frequency(it) }))
        return file
    }

    private fun colorCount(image: java.awt.image.BufferedImage, color: Color): Int {
        var n = 0
        for (x in 0 until image.width) for (y in 0 until image.height) if (image.getRGB(x, y) == color.rgb) n++
        return n
    }

    @Test
    fun `replays a recording with target and gate into CSV, PNG and a German summary`() {
        val wav = recording("kontra-b.wav", 34)
        val outDir = File(dir, "out")
        val r = cli(wav.path, "--target", "Kontra-B", "--calibrate", "1,5", "--out", outDir.path)
        assertEquals(0, r.code, r.err)
        assertTrue(r.out.contains("Ziel: Kontra-B (58,27 Hz)"), r.out)
        assertTrue(r.out.contains("Ergebnis: Treffer nach"), r.out)
        assertTrue(r.out.contains("Kalibrierung: Umgebung"), r.out)
        assertTrue(r.out.contains("Tiefpass vor der Erkennung: 250 Hz"), r.out)
        val txt = File(outDir, "kontra-b.txt").readText(Charsets.UTF_8)
        assertTrue(txt.contains("Gate öffnet ab") && txt.contains("Toleranz ±5,0 ct"), txt)

        val csv = File(outDir, "kontra-b.csv").readLines()
        assertEquals(Reports.CSV_COLUMNS.joinToString(","), csv[0])
        // A frame every 20 ms once the detector has enough history (~0.1 s for Kontra-B).
        assertTrue(csv.size - 1 in 340..350, "${csv.size - 1} rows")
        assertTrue(csv.last().startsWith("7.000,"), csv.last())
        val hitLine = csv.first { ",HIT," in it }.split(",")
        assertEquals("Kontra-B", hitLine[6])
        assertEquals("IN_TOLERANCE", hitLine[10])

        val png = ImageIO.read(File(outDir, "kontra-b.png"))
        assertEquals(Chart.WIDTH, png.width)
        assertEquals(Chart.HEIGHT, png.height)
        for ((color, name) in listOf(
            Chart.BAND to "band",
            Chart.SMOOTHED to "curve",
            Chart.HIT to "hit",
            Chart.GATE to "gate"
        )) {
            assertTrue(colorCount(png, color) > 50, "$name missing in the chart")
        }
    }

    @Test
    fun `wrong octave is reported and free mode works without target`() {
        val wav = recording("oktave.wav", 22)
        val r = cli(wav.path, "--target", "34", "--calibrate", "1.5", "--tolerance", "10", "--hold", "2")
        assertEquals(0, r.code, r.err)
        assertTrue(r.out.contains("kein Treffer") && r.out.contains("falsche Oktave"), r.out)
        assertTrue(r.out.contains("Toleranz ±10,0 ct"), r.out)
        assertTrue(File(dir, "oktave.png").isFile, "output next to the recording")
        val outsideMarks = colorCount(ImageIO.read(File(dir, "oktave.png")), Chart.OUTSIDE)
        assertTrue(outsideMarks > 50, "wrong-octave marks: $outsideMarks")

        val free = cli(wav.path, "--detector", "yin", "--out", File(dir, "frei").path)
        assertEquals(0, free.code, free.err)
        assertTrue(free.out.contains("Freie Erkennung"), free.out)
        assertTrue(File(dir, "frei/oktave.csv").readLines().drop(1).any { "Subkontra-B" in it })
    }

    @Test
    fun `targets as frequency, scientific name and with another a4`() {
        assertEquals(58.27, parseTargetHz("58.27Hz", 440.0)!!, 1e-9)
        assertEquals(58.27, parseTargetHz("58,27 hz", 440.0)!!, 1e-9)
        assertEquals(NoteMath.frequency(34), parseTargetHz("Bb1", 440.0)!!, 1e-9)
        assertEquals(NoteMath.frequency(34, 443.0), parseTargetHz("Kontra-B", 443.0)!!, 1e-9)
        assertNull(parseTargetHz("Tuba", 440.0))
        val args = parseArguments(
            listOf("x.wav", "--target", "f'", "--a4", "442", "--grace", "0.5", "--median", "3", "--min-clarity", "0.7")
        )
        assertEquals(NoteMath.frequency(65, 442.0), args.options.targetHz!!, 1e-9)
        assertEquals(0.5, args.options.match.graceSec)
        assertEquals(3, args.options.match.medianFrames)
        assertEquals(0.7, args.options.match.minClarity)
        assertEquals(0.0, args.options.match.preListenSec)
        assertTrue(args.options.targetLowPass)
        assertFalse(parseArguments(listOf("x.wav", "--no-lowpass")).options.targetLowPass)
    }

    @Test
    fun `the summary names the low-pass, which can be switched off`() {
        val wav = recording("kleines-b.wav", 34)
        val filtered = cli(wav.path, "--target", "kleines b", "--out", File(dir, "mit").path)
        assertEquals(0, filtered.code, filtered.err)
        assertTrue(filtered.out.contains("Tiefpass vor der Erkennung: 326 Hz"), filtered.out)
        val unfiltered = cli(wav.path, "--target", "34", "--no-lowpass", "--out", File(dir, "ohne").path)
        assertEquals(0, unfiltered.code, unfiltered.err)
        assertTrue(unfiltered.out.contains("Tiefpass vor der Erkennung: aus"), unfiltered.out)
    }

    @Test
    fun `errors give German messages and exit codes`() {
        val usage = cli()
        assertEquals(2, usage.code)
        assertTrue(usage.out.startsWith("Verwendung: replay"))
        assertEquals(0, cli("--help").code)
        listOf(
            arrayOf("a.wav", "--bogus") to "Unbekannte Option",
            arrayOf("a.wav", "--target") to "Wert fehlt",
            arrayOf("a.wav", "--target", "Tuba") to "Unbekannter Zielton",
            arrayOf("a.wav", "--tolerance", "viel") to "Keine Zahl",
            arrayOf("a.wav", "--detector", "fft") to "Unbekanntes Verfahren",
            arrayOf("a.wav", "b.wav") to "Nur eine Aufnahme",
            arrayOf("--target", "34") to "Keine Aufnahme",
            arrayOf("a.wav", "--a4", "0") to "Kammerton"
        ).forEach { (args, message) ->
            val r = cli(*args)
            assertEquals(2, r.code, args.joinToString(" "))
            assertTrue(r.err.contains(message), "${args.joinToString(" ")}: ${r.err}")
        }
        assertEquals(2, cli("a.wav", "--tolerance", "0").code, "invalid hit parameters")

        assertEquals(1, cli(File(dir, "fehlt.wav").path).code)
        val text = File(dir, "notiz.wav").apply { writeText("keine Aufnahme") }
        val r = cli(text.path)
        assertEquals(1, r.code)
        assertTrue(r.err.contains("Audioformat nicht lesbar"), r.err)
        assertThrows<UsageException> { parseArguments(emptyList()) }
    }
}
