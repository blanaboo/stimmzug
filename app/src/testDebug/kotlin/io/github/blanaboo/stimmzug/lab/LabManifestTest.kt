package io.github.blanaboo.stimmzug.lab

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LabManifestTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val take = LabProtocols.tuba(listOf(MicChoice.SYSTEM), sdk = 33).takes.last()

    private val path = PathReport(
        sampleRate = 48_000,
        bufferFrames = 9600,
        routedInput = "BUILTIN_MIC bottom (#5)",
        activeMics = "Built-In Mic bottom",
        agc = "aus",
        ns = "an",
        aec = "–",
        droneOutput = "BUILTIN_SPEAKER (#2)",
        mediaVolume = "10/15"
    )

    @Test
    fun cellsWithSeparatorsAreQuoted() {
        assertEquals("plain", LabManifest.cell("plain"))
        assertEquals("\"a,b\"", LabManifest.cell("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", LabManifest.cell("say \"hi\""))
        assertEquals("\"two\nlines\"", LabManifest.cell("two\nlines"))
    }

    @Test
    fun takeRowHasOneCellPerColumn() {
        val ok = TakeResult(take, "ok", path, 19.0, 0.0425, "10:15:00")
        val failed = TakeResult(take, "Fehler: AudioRecord nicht initialisiert", null, 0.0, null, "10:16:00")

        val row = LabManifest.takeRow(ok)
        assertEquals(LabManifest.TAKE_COLUMNS.size, row.size)
        assertEquals("UNPROCESSED", row[LabManifest.TAKE_COLUMNS.indexOf("source")])
        assertEquals("58.270", row[LabManifest.TAKE_COLUMNS.indexOf("note_hz")])
        assertEquals("440.0", row[LabManifest.TAKE_COLUMNS.indexOf("a4_hz")])
        assertEquals("system", row[LabManifest.TAKE_COLUMNS.indexOf("output")])
        assertEquals("1", row[LabManifest.TAKE_COLUMNS.indexOf("output_matched")])
        assertEquals("0.0425", row[LabManifest.TAKE_COLUMNS.indexOf("dsp_load")])
        assertEquals(LabManifest.TAKE_COLUMNS.size, LabManifest.takeRow(failed).size)
        assertEquals("", LabManifest.takeRow(failed)[LabManifest.TAKE_COLUMNS.indexOf("routed_input")])
    }

    @Test
    fun misroutedDroneIsFlagged() {
        val bluetooth = LabProtocols.drone(listOf(MicChoice.SYSTEM)).takes[4]
        val row = LabManifest.takeRow(TakeResult(bluetooth, "ok", path, 19.0, 0.04, "10:15:00", misrouted = true))

        assertEquals("bluetooth", row[LabManifest.TAKE_COLUMNS.indexOf("output")])
        assertEquals("442.0", row[LabManifest.TAKE_COLUMNS.indexOf("a4_hz")])
        assertEquals("0", row[LabManifest.TAKE_COLUMNS.indexOf("output_matched")])
    }

    @Test
    fun writesTakesAndSegments() {
        val dir = temp.newFolder()
        LabManifest.write(dir, listOf(TakeResult(take, "ok", path, 19.0, 0.04, "10:15:00")))

        val takes = File(dir, "takes.csv").readLines()
        assertEquals(LabManifest.TAKE_COLUMNS.joinToString(","), takes[0])
        assertEquals(2, takes.size)
        val segments = File(dir, "segments.csv").readLines()
        assertEquals(
            listOf(
                LabManifest.SEGMENT_COLUMNS.joinToString(","),
                "T13,0,drone,0.00,3.00,0,1",
                "T13,1,low,3.00,8.00,1,1",
                "T13,2,clean,8.00,14.00,1,1",
                "T13,3,high,14.00,19.00,1,1"
            ),
            segments
        )
    }
}
