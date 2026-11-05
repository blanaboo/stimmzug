package io.github.blanaboo.stimmzug.tools.replay

import io.github.blanaboo.stimmzug.dsp.music.NoteMath
import io.github.blanaboo.stimmzug.dsp.music.NoteNames
import java.io.File
import java.util.Locale

/** CSV with one line per frame (machine-readable: English column names, dot decimals) and a German summary. */
object Reports {

    val CSV_COLUMNS = listOf(
        "time_s", "frequency_hz", "clarity", "level_dbfs", "calibration", "passed_gate", "nearest_note",
        "cents_from_nearest", "cents_from_target", "smoothed_cents", "verdict", "phase", "hold_progress"
    )

    fun writeCsv(result: ReplayResult, file: File, a4Hz: Double) {
        file.bufferedWriter().use { out ->
            out.appendLine(CSV_COLUMNS.joinToString(","))
            for (row in result.rows) {
                val e = row.frame.estimate
                val voiced = e.isVoiced
                val nearest = if (voiced) NoteMath.nearestMidi(e.frequencyHz, a4Hz) else null
                val target = result.options.targetHz
                val cells = listOf(
                    num(row.frame.timeSec, 3),
                    if (voiced) num(e.frequencyHz, 4) else "",
                    num(e.clarity, 3),
                    num(e.levelDb, 1),
                    if (row.inCalibration) "1" else "0",
                    if (row.passedGate) "1" else "0",
                    nearest?.let { NoteNames.germanOctaveName(it) } ?: "",
                    nearest?.let { num(NoteMath.centsFromNote(e.frequencyHz, it, a4Hz), 2) } ?: "",
                    if (voiced && target != null) num(NoteMath.cents(e.frequencyHz, target), 2) else "",
                    row.status?.centsDeviation?.let { num(it, 2) } ?: "",
                    row.status?.verdict?.name ?: "",
                    row.status?.phase?.name ?: "",
                    row.status?.let { num(it.holdProgress, 3) } ?: ""
                )
                out.appendLine(cells.joinToString(",") { if (it.contains(',') || it.contains(' ')) "\"$it\"" else it })
            }
        }
    }

    fun summary(result: ReplayResult, name: String, targetName: String?): String = buildString {
        val rows = result.rows.filterNot { it.inCalibration }
        appendLine(
            "Datei: $name (${de(result.sampleRate, 0)} Hz, ${de(result.durationSec, 1)} s, ${result.rows.size} Frames)"
        )
        result.calibration?.let {
            appendLine(
                "Kalibrierung: Umgebung ${de(it.ambientDb, 1)} dBFS, Gate öffnet ab ${de(it.openThresholdDb, 1)} dBFS"
            )
        }
        appendLine(
            "Gehört: ${rows.count { it.frame.estimate.isVoiced }} stimmhafte Frames, " +
                "davon ${rows.count { it.passedGate }} über dem Gate"
        )
        val note = result.note
        if (note == null) {
            append("Freie Erkennung (kein Zielton angegeben)")
            return@buildString
        }
        val m = result.options.match
        appendLine(
            "Ziel: $targetName (${de(note.targetHz, 2)} Hz), Toleranz ±${de(m.toleranceCents, 1)} ct, " +
                "Haltedauer ${de(m.holdSec, 1)} s, Gnadenzeit ${de(m.graceSec, 2)} s"
        )
        if (note.hit) {
            append("Ergebnis: Treffer nach ${de(note.timeToHitSec!!, 2)} s")
            append(" | Halten: Mittel ${signed(note.holdMeanCents!!)} ct, Streuung ${de(note.holdSpreadCents!!, 1)} ct")
        } else {
            append("Ergebnis: kein Treffer")
        }
        note.entryCents?.let { append(" | Einstieg ${signed(it)} ct") }
        if (note.wrongOctaveSec >= SHOWN_SEC) append(" | falsche Oktave ${de(note.wrongOctaveSec, 1)} s")
        if (note.wrongNoteSec >= SHOWN_SEC) append(" | falscher Ton ${de(note.wrongNoteSec, 1)} s")
    }

    /** Shorter times (a single frame in the attack) would print as "0,0 s". */
    private const val SHOWN_SEC = 0.05

    private fun num(v: Double, decimals: Int) = String.format(Locale.ROOT, "%.${decimals}f", v)

    private fun de(v: Double, decimals: Int) = String.format(Locale.GERMANY, "%.${decimals}f", v)

    private fun signed(v: Double) = String.format(Locale.GERMANY, "%+.1f", v)
}
