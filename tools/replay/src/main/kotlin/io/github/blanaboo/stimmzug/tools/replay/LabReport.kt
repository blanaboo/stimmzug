package io.github.blanaboo.stimmzug.tools.replay

import io.github.blanaboo.stimmzug.dsp.music.NoteMath
import io.github.blanaboo.stimmzug.dsp.pitch.PitchFrame
import io.github.blanaboo.stimmzug.dsp.signal.SignalMath
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow

/** One segment of a lab take, from `segments.csv`. */
data class LabSegment(
    val take: String,
    val key: String,
    val startSec: Double,
    val endSec: Double,
    val playerPlays: Boolean,
    val drone: Boolean
)

/**
 * Measurements of one segment, taken after [LabReport.SETTLE_SEC] (attack, drone fade-in, reaction time).
 * Cents are relative to the take's note; `null` where there is nothing to measure.
 */
data class SegmentStats(
    /** Median frame level in dBFS. */
    val levelDb: Double?,
    /**
     * Median level of the last second minus that of the first: a steady tone that gets quieter hints at AGC or
     * noise suppression.
     */
    val trendDb: Double?,
    /** Share of voiced frames. */
    val voiced: Double,
    val clarity: Double?,
    /** Median deviation of the voiced frames within ±100 ct of the note. */
    val centsMedian: Double?,
    /** Interquartile range of these deviations. */
    val centsIqr: Double?,
    /** Share of voiced frames an octave above or below the note. */
    val octaveErrors: Double,
    /** Level of harmonics 1..4 relative to the whole signal in dB (0 dB = all energy in that harmonic). */
    val harmonicsDb: List<Double?>
)

/** One line of the segment table: a take (its `takes.csv` row), one of its segments and the measurements. */
class LabRow(val take: Map<String, String>, val segment: LabSegment, val stats: SegmentStats)

/**
 * Evaluates a session of the audio lab in the debug app (#33): runs every take through [Replay] (target = the
 * take's note, calibration = its first segment if nobody plays in it; detector and hit logic from the template)
 * and measures each segment. Writes
 * `report.md` (German) and `report.csv`, plus the usual chart and frame CSV per take in `charts/`.
 */
object LabReport {
    const val TAKES_FILE = "takes.csv"
    const val SEGMENTS_FILE = "segments.csv"
    const val SETTLE_SEC = 0.5
    private const val TREND_WINDOW_SEC = 1.0
    private const val HARMONICS = 4
    private const val BLOCK_SEC = 1.0
    private const val NEAR_CENTS = 100.0
    private const val OCTAVE_CENTS = 1200.0

    val CSV_COLUMNS = listOf(
        "take", "source", "mic", "note", "segment", "level_dbfs", "trend_db", "voiced", "clarity", "cents_median",
        "cents_iqr", "octave_errors", "h1_db", "h2_db", "h3_db", "h4_db"
    )

    /** Columns of `takes.csv` shown in the report, with German headers. */
    private val takeColumns = listOf(
        "take" to "Aufnahme", "source" to "Quelle", "mic" to "Mikro", "note" to "Ton", "status" to "Status",
        "routed_input" to "Eingang", "active_mics" to "Aktive Mikrofone", "agc" to "AGC", "ns" to "NS",
        "aec" to "AEC", "drone_output" to "Drone über", "media_volume" to "Lautstärke", "dsp_load" to "Rechenzeit"
    )

    fun isSession(dir: File) = File(dir, TAKES_FILE).isFile && File(dir, SEGMENTS_FILE).isFile

    class Outcome(val analyzed: Int, val skipped: List<String>, val markdown: File, val csv: File)

    fun run(dir: File, outDir: File, template: ReplayOptions = ReplayOptions()): Outcome {
        val takes = readCsv(File(dir, TAKES_FILE))
        val segments = readCsv(File(dir, SEGMENTS_FILE)).map(::segment).groupBy { it.take }
        val charts = File(outDir, "charts").apply { mkdirs() }
        val rows = ArrayList<LabRow>()
        val skipped = ArrayList<String>()
        for (take in takes) {
            val id = take.getValue("take")
            val wav = File(dir, take.getValue("file"))
            if (take["status"] != "ok" || !wav.isFile) {
                skipped += "$id: " + if (take["status"] != "ok") take["status"] else "Datei fehlt"
                continue
            }
            val clip = AudioFileReader.read(wav)
            val targetHz = take.getValue("note_hz").toDouble()
            val takeSegments = segments[id].orEmpty()
            val calibration = takeSegments.firstOrNull()?.takeUnless { it.playerPlays }?.endSec ?: 0.0
            val options = template.copy(targetHz = targetHz, calibrationSec = calibration)
            val result = Replay.run(clip.samples, clip.sampleRate, options)
            val name = wav.nameWithoutExtension
            // Sessions before #35 have no a4_hz column; they were all recorded at 440 Hz.
            val a4 = take["a4_hz"]?.toDoubleOrNull() ?: NoteMath.DEFAULT_A4_HZ
            Reports.writeCsv(result, File(charts, "$name.csv"), a4)
            Chart.render(result, "$name – ${take["note"]}", a4, File(charts, "$name.png"))
            val frames = result.rows.map { it.frame }
            for (s in takeSegments) {
                rows += LabRow(take, s, segmentStats(clip.samples, clip.sampleRate, frames, s, targetHz))
            }
        }
        val csv = File(outDir, "report.csv")
        csv.writeText(csvText(rows), Charsets.UTF_8)
        val markdown = File(outDir, "report.md")
        markdown.writeText(markdown(dir, takes, rows, skipped), Charsets.UTF_8)
        return Outcome(takes.size - skipped.size, skipped, markdown, csv)
    }

    fun segmentStats(
        samples: FloatArray,
        sampleRate: Double,
        frames: List<PitchFrame>,
        segment: LabSegment,
        targetHz: Double
    ): SegmentStats {
        val from = segment.startSec + if (segment.endSec - segment.startSec > 2 * SETTLE_SEC) SETTLE_SEC else 0.0
        val inside = frames.filter { it.timeSec > from && it.timeSec <= segment.endSec }
        val levels = inside.map { it.estimate.levelDb }
        val voiced = inside.filter { it.estimate.isVoiced }
        val cents = voiced.map { NoteMath.cents(it.estimate.frequencyHz, targetHz) }
        val near = cents.filter { abs(it) < NEAR_CENTS }.sorted()
        val octaves = cents.count { abs(abs(it) - OCTAVE_CENTS) < NEAR_CENTS }
        val f0 = near.takeIf { it.isNotEmpty() }?.let { targetHz * 2.0.pow(median(it) / OCTAVE_CENTS) } ?: targetHz
        return SegmentStats(
            levelDb = levels.takeIf { it.isNotEmpty() }?.let(::median),
            trendDb = trend(inside, from, segment.endSec),
            voiced = if (inside.isEmpty()) 0.0 else voiced.size.toDouble() / inside.size,
            clarity = voiced.map { it.estimate.clarity }.takeIf { it.isNotEmpty() }?.let(::median),
            centsMedian = near.takeIf { it.isNotEmpty() }?.let(::median),
            centsIqr = near.takeIf { it.size >= 4 }?.let { quantile(it, 0.75) - quantile(it, 0.25) },
            octaveErrors = if (voiced.isEmpty()) 0.0 else octaves.toDouble() / voiced.size,
            harmonicsDb = harmonicLevels(samples, sampleRate, from, segment.endSec, f0)
        )
    }

    private fun trend(frames: List<PitchFrame>, from: Double, to: Double): Double? {
        if (to - from < 2.5 * TREND_WINDOW_SEC) return null
        val first = frames.filter { it.timeSec <= from + TREND_WINDOW_SEC }.map { it.estimate.levelDb }
        val last = frames.filter { it.timeSec > to - TREND_WINDOW_SEC }.map { it.estimate.levelDb }
        if (first.isEmpty() || last.isEmpty()) return null
        return median(last) - median(first)
    }

    /** Power of harmonics 1..[HARMONICS] of [f0], averaged over 1 s blocks, relative to the total power. */
    private fun harmonicLevels(
        samples: FloatArray,
        rate: Double,
        fromSec: Double,
        toSec: Double,
        f0: Double
    ): List<Double?> {
        val from = (fromSec * rate).toInt().coerceIn(0, samples.size)
        val to = (toSec * rate).toInt().coerceIn(from, samples.size)
        val block = (BLOCK_SEC * rate).toInt()
        val x = DoubleArray(to - from) { samples[from + it].toDouble() }
        val rms = if (x.isEmpty()) 0.0 else SignalMath.rms(x)
        return (1..HARMONICS).map { k ->
            val frequency = k * f0
            if (x.size < block || rms <= 0.0 || frequency >= rate / 2) return@map null
            val powers = (0..x.size - block step block).map { start ->
                val a = SignalMath.toneAmplitude(x, frequency, rate, start, start + block)
                a * a / 2
            }
            10 * log10(powers.average() / (rms * rms))
        }
    }

    private fun segment(row: Map<String, String>) = LabSegment(
        take = row.getValue("take"),
        key = row.getValue("segment"),
        startSec = row.getValue("start_s").toDouble(),
        endSec = row.getValue("end_s").toDouble(),
        playerPlays = row["player_plays"] == "1",
        drone = row["drone"] == "1"
    )

    private fun csvText(rows: List<LabRow>): String = buildString {
        appendLine(CSV_COLUMNS.joinToString(","))
        for (row in rows) {
            val take = row.take
            val s = row.stats
            val cells = listOf(take.getValue("take"), take["source"], take["mic"], take["note"], row.segment.key) +
                listOf(s.levelDb, s.trendDb, s.voiced, s.clarity, s.centsMedian, s.centsIqr, s.octaveErrors)
                    .map { it?.let { v -> String.format(Locale.ROOT, "%.3f", v) } } +
                s.harmonicsDb.map { it?.let { v -> String.format(Locale.ROOT, "%.1f", v) } }
            appendLine(cells.joinToString(",") { csvCell(it.orEmpty()) })
        }
    }

    private fun markdown(
        dir: File,
        takes: List<Map<String, String>>,
        rows: List<LabRow>,
        skipped: List<String>
    ): String = buildString {
        appendLine("# Audio-Labor: ${dir.name}")
        appendLine()
        File(dir, "session.txt").takeIf { it.isFile }?.let {
            appendLine("```")
            append(it.readText(Charsets.UTF_8).trimEnd())
            appendLine()
            appendLine("```")
            appendLine()
        }
        appendLine("## Aufnahmen")
        appendLine()
        append(table(takeColumns.map { it.second }, takes.map { t -> takeColumns.map { t[it.first].orEmpty() } }))
        if (skipped.isNotEmpty()) {
            appendLine()
            appendLine("Nicht ausgewertet: ${skipped.joinToString("; ")}")
        }
        appendLine()
        appendLine("## Abschnitte")
        appendLine()
        appendLine(
            "Gemessen ab ${de(SETTLE_SEC, 1)} s nach Abschnittsbeginn. Trend: Pegel der letzten minus der ersten " +
                "Sekunde. Abweichung und Streuung (Interquartilsabstand) in Cent zum Ton, nur Frames innerhalb " +
                "±100 ct. H1–H4: Anteil des 1. bis 4. Teiltons an der Gesamtleistung in dB."
        )
        appendLine()
        val header = listOf(
            "Aufnahme", "Quelle", "Mikro", "Ton", "Abschnitt", "Pegel dBFS", "Trend dB", "stimmhaft", "Klarheit",
            "Abw. ct", "Streuung ct", "Oktavfehler", "H1", "H2", "H3", "H4"
        )
        append(table(header, rows.map(::markdownRow)))
    }

    private fun markdownRow(row: LabRow): List<String> {
        val take = row.take
        val s = row.stats
        return listOf(take.getValue("take"), take["source"].orEmpty(), take["mic"].orEmpty(), take["note"].orEmpty()) +
            listOf(
                row.segment.key,
                de(s.levelDb, 1),
                signed(s.trendDb),
                percent(s.voiced),
                de(s.clarity, 2),
                signed(s.centsMedian),
                de(s.centsIqr, 1),
                percent(s.octaveErrors)
            ) + s.harmonicsDb.map { de(it, 1) }
    }

    private fun table(header: List<String>, rows: List<List<String>>): String = buildString {
        appendLine("| " + header.joinToString(" | ") + " |")
        appendLine("|" + header.joinToString("|") { "---" } + "|")
        for (row in rows) appendLine("| " + row.joinToString(" | ") { it.replace("|", "\\|") } + " |")
    }

    /** Parses RFC 4180 CSV (quoted cells may contain commas, quotes and line breaks) into header-keyed rows. */
    fun readCsv(file: File): List<Map<String, String>> {
        val records = parseCsv(file.readText(Charsets.UTF_8))
        val header = records.firstOrNull() ?: return emptyList()
        return records.drop(1).filter { it != listOf("") }.map { header.zip(it).toMap() }
    }

    fun parseCsv(text: String): List<List<String>> {
        val records = ArrayList<List<String>>()
        val record = ArrayList<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        fun endCell() {
            record += cell.toString()
            cell.clear()
        }
        while (i < text.length) {
            val c = text[i++]
            if (quoted) {
                when {
                    c != '"' -> cell.append(c)

                    text.getOrNull(i) == '"' -> cell.append(text[i++])

                    // "" inside quotes is one quote
                    else -> quoted = false
                }
            } else {
                when (c) {
                    '"' -> quoted = true

                    ',' -> endCell()

                    '\n' -> {
                        endCell()
                        records += record.toList()
                        record.clear()
                    }

                    '\r' -> Unit

                    else -> cell.append(c)
                }
            }
        }
        if (cell.isNotEmpty() || record.isNotEmpty()) {
            endCell()
            records += record
        }
        return records
    }

    private fun csvCell(value: String) =
        if (value.any { it in ",\"\n\r" }) "\"" + value.replace("\"", "\"\"") + "\"" else value

    private fun median(values: List<Double>): Double = quantile(values.sorted(), 0.5)

    /** Linear interpolation between the closest ranks of an ascending list. */
    private fun quantile(sorted: List<Double>, q: Double): Double {
        val position = q * (sorted.size - 1)
        val lower = position.toInt()
        val upper = minOf(lower + 1, sorted.size - 1)
        return sorted[lower] + (sorted[upper] - sorted[lower]) * (position - lower)
    }

    private fun de(v: Double?, decimals: Int) = v?.let { String.format(Locale.GERMANY, "%.${decimals}f", it) } ?: "–"

    private fun signed(v: Double?) = v?.let { String.format(Locale.GERMANY, "%+.1f", it) } ?: "–"

    private fun percent(v: Double) = String.format(Locale.GERMANY, "%.0f %%", v * 100)
}
