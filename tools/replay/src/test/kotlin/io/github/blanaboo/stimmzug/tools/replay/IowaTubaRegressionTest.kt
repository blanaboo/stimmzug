package io.github.blanaboo.stimmzug.tools.replay

import io.github.blanaboo.stimmzug.dsp.music.NoteMath
import io.github.blanaboo.stimmzug.dsp.music.NoteNames
import java.io.File
import java.util.Locale
import kotlin.math.abs
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

/**
 * Regression test on real recordings: the University of Iowa MIS tuba notes (ff, C1–C4, Mirafone CC tuba), downloaded
 * by the Gradle task `downloadIowaTuba` (see docs/testdata.md). Skipped when the recordings are not available.
 *
 * Only the steady part of each note is evaluated (frames within [STEADY_DB] of the loudest one, voiced with the hit
 * logic's minimum clarity). The player's intonation is part of the data, so the deviations are compared with a
 * committed baseline; a report goes to build/reports/iowa/.
 */
class IowaTubaRegressionTest {

    private data class Measurement(
        val midi: Int,
        val file: String,
        val steadyFrames: Int,
        val medianCents: Double,
        val p10Cents: Double,
        val p90Cents: Double,
        val voicedRatio: Double,
        val octaveErrors: Int,
        /** Median deviation from the nominal note with the target an octave higher / lower (null if out of range). */
        val targetUpMedian: Double,
        val targetUpOctaveErrors: Int,
        val targetDownMedian: Double?,
        val targetDownOctaveErrors: Int
    )

    private data class Baseline(val medianCents: Double, val voicedRatio: Double, val octaveErrors: Int)

    private fun recordings(): List<Pair<Int, File>> {
        val dir = System.getProperty("stimmzug.iowa.dir")?.let(::File)
        val names = resource("tuba-ff.sha256").filter { it.isNotBlank() && !it.startsWith("#") }
            .map { it.substringAfter("  ") }
        val files = names.map { name ->
            val midi = NoteNames.parseMidi(name.removePrefix("Tuba.ff.").removeSuffix(".stereo.aif"))
                ?: error("unknown note in $name")
            midi to File(dir ?: File("."), name)
        }
        assumeTrue(dir != null && files.all { it.second.isFile }) {
            "Iowa-Aufnahmen fehlen in $dir (offline?) – Regressionstest übersprungen"
        }
        return files.sortedBy { it.first }
    }

    private fun resource(name: String): List<String> =
        javaClass.getResourceAsStream("/iowa/$name")?.bufferedReader()?.readLines() ?: error("resource $name missing")

    /** Deviations (cents from the nominal note) of the steady frames, total steady frames. */
    private fun steadyCents(clip: AudioClip, nominal: Double, targetHz: Double): Pair<List<Double>, Int> {
        val result = Replay.run(clip.samples, clip.sampleRate, ReplayOptions(targetHz = targetHz))
        val loudest = result.rows.maxOf { it.frame.estimate.levelDb }
        val steady = result.rows.filter { it.frame.estimate.levelDb >= loudest - STEADY_DB }
        val clear = steady.filter { it.frame.estimate.isVoiced && it.frame.estimate.clarity >= MIN_CLARITY }
        return clear.map { NoteMath.cents(it.frame.estimate.frequencyHz, nominal) }.sorted() to steady.size
    }

    private fun measure(midi: Int, file: File): Measurement {
        val clip = AudioFileReader.read(file)
        val nominal = NoteMath.frequency(midi)
        val (cents, steady) = steadyCents(clip, nominal, nominal)
        val (up, _) = steadyCents(clip, nominal, 2 * nominal)
        val down = if (nominal / 2 >= MIN_DETECTOR_HZ) steadyCents(clip, nominal, nominal / 2).first else null
        return Measurement(
            midi, file.name, steady, percentile(cents, 0.5), percentile(cents, 0.1), percentile(cents, 0.9),
            cents.size.toDouble() / steady, cents.count { abs(it) > 600 },
            percentile(up, 0.5), up.count { abs(it) > 600 },
            down?.let { percentile(it, 0.5) }, down?.count { abs(it) > 600 } ?: 0
        )
    }

    @Test
    fun `detection on the Iowa tuba recordings matches the baseline`() {
        val measurements = recordings().map { (midi, file) -> measure(midi, file) }
        writeReport(measurements)
        val baseline = resource("tuba-ff-baseline.csv").drop(1).associate { line ->
            val c = line.split(",")
            c[0] to Baseline(c[1].toDouble(), c[2].toDouble(), c[3].toInt())
        }
        assertEquals(37, measurements.size)
        for (m in measurements) {
            val note = NoteNames.scientific(m.midi)
            // Right note or its neighbour: player intonation (and one odd recording, see docs) stays below 100 ct.
            assertTrue(abs(m.medianCents) < 100, "$note: median ${m.medianCents} ct")
            assertTrue(m.octaveErrors <= 1, "$note: ${m.octaveErrors} octave errors")
            // Target an octave off: the detector must still report the pitch that is actually played.
            assertEquals(m.medianCents, m.targetUpMedian, SHIFTED_TARGET_CENTS, "$note with target an octave higher")
            assertEquals(0, m.targetUpOctaveErrors, "$note with target an octave higher")
            m.targetDownMedian?.let {
                assertEquals(m.medianCents, it, SHIFTED_TARGET_CENTS, "$note with target an octave lower")
            }
            assertEquals(0, m.targetDownOctaveErrors, "$note with target an octave lower")

            val b = baseline[m.file] ?: error("no baseline for ${m.file}")
            assertEquals(b.medianCents, m.medianCents, BASELINE_CENTS, "$note: median moved from the baseline")
            assertTrue(
                m.voicedRatio >= b.voicedRatio - BASELINE_VOICED,
                "$note: voiced ${m.voicedRatio} < ${b.voicedRatio}"
            )
            assertTrue(m.octaveErrors <= b.octaveErrors, "$note: ${m.octaveErrors} octave errors > ${b.octaveErrors}")
        }
    }

    private fun writeReport(measurements: List<Measurement>) {
        val dir = File("build/reports/iowa").apply { mkdirs() }
        File(dir, "tuba-ff-baseline.csv").writeText(
            "file,median_cents,voiced_ratio,octave_errors\n" + measurements.joinToString("") {
                String.format(
                    Locale.ROOT,
                    "%s,%.2f,%.3f,%d\n",
                    it.file,
                    it.medianCents,
                    it.voicedRatio,
                    it.octaveErrors
                )
            }
        )
        val text = buildString {
            appendLine("# Regressionstest: Tuba-Aufnahmen der University of Iowa")
            appendLine()
            appendLine("Mirafone 1865U CC-Tuba, ff, C1–C4 (Quelle und Bedingungen: [testdata.md](../testdata.md)).")
            appendLine("Ziel = Nominalton bei a′ = 440 Hz. Ausgewertet wird der stabile Teil jedes Tons:")
            appendLine("Pegel höchstens ${STEADY_DB.toInt()} dB unter dem Maximum,")
            appendLine("stimmhaft mit Klarheit ≥ ${fmt(MIN_CLARITY, 1)}.")
            appendLine(
                "Erkennung wie in der App mit Tiefpass vor der Erkennung bei max(250 Hz, 1,4 × Ziel) " +
                    "([ADR 0005](../adr/0005-audio-io.md))."
            )
            appendLine("Die Abweichungen sind die **Intonation des Spielers**, nicht Messfehler der Erkennung.")
            appendLine()
            appendLine(
                "| Ton | Nominal Hz | Median ct | 10 % ct | 90 % ct | stimmhaft | Oktavfehler | " +
                    "Ziel 8va höher | Ziel 8va tiefer |"
            )
            appendLine("|---|---:|---:|---:|---:|---:|---:|---:|---:|")
            for (m in measurements) {
                val cells = listOf(
                    "${NoteNames.germanOctaveName(m.midi)} (${NoteNames.scientific(m.midi)})",
                    fmt(NoteMath.frequency(m.midi), 2),
                    signed(m.medianCents),
                    signed(m.p10Cents),
                    signed(m.p90Cents),
                    String.format(Locale.ROOT, "%.0f %%", 100 * m.voicedRatio),
                    m.octaveErrors.toString(),
                    signed(m.targetUpMedian),
                    m.targetDownMedian?.let { signed(it) } ?: "–"
                )
                appendLine(cells.joinToString(" | ", prefix = "| ", postfix = " |"))
            }
            val medians = measurements.map { it.medianCents }.sorted()
            appendLine()
            appendLine(
                "Median über alle Töne: ${signed(percentile(medians, 0.5))} ct (der Spieler hat etwa auf a′ = " +
                    "${fmt(440.0 * Math.pow(2.0, percentile(medians, 0.5) / 1200), 0)} Hz gestimmt). " +
                    "„Ziel 8va höher/tiefer“: gemessene Abweichung, wenn das Ziel eine Oktave neben " +
                    "dem gespielten Ton " +
                    "liegt – die Erkennung muss trotzdem die gespielte Tonhöhe melden."
            )
        }
        File(dir, "tuba-ff.md").writeText(text)
    }

    private companion object {
        const val STEADY_DB = 12.0
        const val MIN_CLARITY = 0.8
        const val MIN_DETECTOR_HZ = 25.0
        const val BASELINE_CENTS = 1.0
        const val BASELINE_VOICED = 0.05

        /**
         * With the target an octave off, the analysis window (sized for the target) differs and averages slightly
         * different frames; the lowest notes wander by ~40 ct within the note, so medians differ by up to ~3 ct.
         * An octave error would be 1200 ct.
         */
        const val SHIFTED_TARGET_CENTS = 5.0

        fun percentile(sorted: List<Double>, p: Double) = sorted[((sorted.size - 1) * p).toInt()]

        fun fmt(v: Double, decimals: Int) = String.format(Locale.GERMANY, "%.${decimals}f", v)

        fun signed(v: Double) = String.format(Locale.GERMANY, "%+.1f", v)
    }
}
