package io.github.blanaboo.stimmzug.tools.replay

import io.github.blanaboo.stimmzug.dsp.music.NoteMath
import io.github.blanaboo.stimmzug.dsp.synth.ToneSpec
import io.github.blanaboo.stimmzug.dsp.synth.TubaToneGenerator
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import kotlin.math.pow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class LabReportTest {
    @TempDir
    lateinit var dir: File

    private val kontraB = NoteMath.frequency(34)

    /** 2 s room noise, then 5 s Kontra-B 10 ct high; [highPassHz] mimics a phone microphone without bass. */
    private fun tuba(highPassHz: Double?) = TubaToneGenerator.render(
        ToneSpec(
            frequencyHz = kontraB * 2.0.pow(10.0 / 1200),
            leadInSec = 2.0,
            toneSec = 5.0,
            releaseSec = 0.0,
            noiseRmsDbfs = -60.0,
            micHighPassHz = highPassHz
        )
    )

    /** A session as the audio lab writes it: two good takes and one the device refused. */
    private fun session(): File {
        val session = File(dir, "20261106-101500-tuba").apply { mkdirs() }
        TestAudio.write(File(session, "T01-voice_recognition-unten-bb1.wav"), tuba(highPassHz = null))
        TestAudio.write(File(session, "T02-mic-unten-bb1.wav"), tuba(highPassHz = 150.0))
        File(session, "takes.csv").writeText(
            """
            take,file,status,source,mic,note,note_hz,drone_octave,routed_input,active_mics,agc,ns,aec,dsp_load
            T01,T01-voice_recognition-unten-bb1.wav,ok,VOICE_RECOGNITION,unten,Kontra-B,58.270,0,"BUILTIN_MIC bottom (#5)",Built-In Mic,aus,aus,–,0.03
            T02,T02-mic-unten-bb1.wav,ok,MIC,unten,Kontra-B,58.270,0,"BUILTIN_MIC bottom (#5)",Built-In Mic,an,an,–,0.03
            T03,T03-unprocessed-unten-bb1.wav,"Fehler: AudioRecord nicht initialisiert, Quelle 9",UNPROCESSED,unten,Kontra-B,58.270,0,,,,,,
            """.trimIndent() + "\n"
        )
        File(session, "segments.csv").writeText(
            """
            take,index,segment,start_s,end_s,player_plays,drone
            T01,0,quiet,0.00,2.00,0,0
            T01,1,play,2.00,7.00,1,0
            T02,0,quiet,0.00,2.00,0,0
            T02,1,play,2.00,7.00,1,0
            T03,0,quiet,0.00,2.00,0,0
            T03,1,play,2.00,7.00,1,0
            """.trimIndent() + "\n"
        )
        File(session, "session.txt").writeText("Gerät: Test\n")
        return session
    }

    @Test
    fun `measures pitch, level and harmonics per segment`() {
        val session = session()
        val out = File(dir, "out")

        val outcome = LabReport.run(session, out)

        assertEquals(2, outcome.analyzed)
        assertEquals(listOf("T03: Fehler: AudioRecord nicht initialisiert, Quelle 9"), outcome.skipped)
        val rows = LabReport.readCsv(outcome.csv).associateBy { it.getValue("take") + "/" + it.getValue("segment") }
        assertEquals(4, rows.size)

        val quiet = rows.getValue("T01/quiet")
        val play = rows.getValue("T01/play")
        assertTrue(quiet.getValue("level_dbfs").toDouble() < -55.0, quiet.toString())
        assertTrue(play.getValue("level_dbfs").toDouble() > -25.0, play.toString())
        assertEquals(10.0, play.getValue("cents_median").toDouble(), 1.0)
        assertTrue(play.getValue("voiced").toDouble() > 0.95, play.toString())
        assertEquals(0.0, play.getValue("octave_errors").toDouble())
        assertTrue(play.getValue("trend_db").toDouble() in -1.0..1.0, play.toString())

        // The high-pass weakens the fundamental against the second harmonic.
        val full = play.getValue("h1_db").toDouble() - play.getValue("h2_db").toDouble()
        val highPassed = rows.getValue("T02/play").let {
            it.getValue("h1_db").toDouble() -
                it.getValue("h2_db").toDouble()
        }
        assertTrue(highPassed < full - 6.0, "H1−H2 ohne Hochpass $full dB, mit $highPassed dB")

        val markdown = outcome.markdown.readText()
        assertTrue(markdown.startsWith("# Audio-Labor: 20261106-101500-tuba"), markdown)
        assertTrue(markdown.contains("Gerät: Test"), markdown)
        assertTrue(markdown.contains("| T02 | MIC | unten | Kontra-B | play |"), markdown)
        assertTrue(File(out, "charts/T01-voice_recognition-unten-bb1.png").isFile)
        assertTrue(File(out, "charts/T02-mic-unten-bb1.csv").isFile)
    }

    @Test
    fun `command line evaluates a session folder`() {
        val session = session()
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()

        val code = run(listOf(session.path), PrintStream(stdout, true, Charsets.UTF_8), PrintStream(stderr, true))

        assertEquals(0, code, stderr.toString())
        val text = stdout.toString(Charsets.UTF_8)
        assertTrue(text.contains("Labor-Sitzung 20261106-101500-tuba: 2 Aufnahmen ausgewertet"), text)
        assertTrue(File(session, "auswertung/report.md").isFile)
    }

    @Test
    fun `a folder without session files or with a target is rejected`() {
        val err = ByteArrayOutputStream()
        assertEquals(2, run(listOf(dir.path), PrintStream(ByteArrayOutputStream()), PrintStream(err, true)))
        assertTrue(err.toString().contains("kein Ordner einer Labor-Sitzung"), err.toString())

        assertEquals(
            2,
            run(
                listOf(session().path, "--target", "Kontra-B"),
                PrintStream(ByteArrayOutputStream()),
                PrintStream(ByteArrayOutputStream())
            )
        )
    }

    @Test
    fun `parses quoted CSV cells`() {
        val records = LabReport.parseCsv("a,\"b,c\",\"say \"\"hi\"\"\"\r\n\"two\nlines\",,x\n")

        assertEquals(listOf(listOf("a", "b,c", "say \"hi\""), listOf("two\nlines", "", "x")), records)
        assertEquals(listOf(listOf("last", "")), LabReport.parseCsv("last,"))
        assertNotNull(LabReport.readCsv(File(dir, "empty.csv").apply { writeText("") }))
    }
}
