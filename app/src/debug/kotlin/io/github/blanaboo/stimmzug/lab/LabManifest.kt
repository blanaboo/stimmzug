package io.github.blanaboo.stimmzug.lab

import java.io.File
import java.util.Locale

/** What the platform reported about a take's input and output path. Text values are shown as recorded. */
data class PathReport(
    val sampleRate: Int,
    val bufferFrames: Int,
    /** Device the system actually routed the recording to, e.g. "BUILTIN_MIC bottom (#5)". */
    val routedInput: String,
    /** Physical microphones in use (`AudioRecord.getActiveMicrophones`). */
    val activeMics: String,
    /** Pre-processing state: "an", "aus" or "–" (not available on the device). */
    val agc: String,
    val ns: String,
    val aec: String,
    val droneOutput: String,
    /** Media volume as "step/max". */
    val mediaVolume: String
)

/** Outcome of one take; [status] is "ok", "abgebrochen" or an error message. */
data class TakeResult(
    val take: Take,
    val status: String,
    val path: PathReport?,
    val recordedSec: Double,
    /** Pitch detection time per audio time (0.1 = 10 % of real time). */
    val dspLoad: Double?,
    val startedAt: String,
    /** The drone sounded on another kind of device than [Take.output] asked for. */
    val misrouted: Boolean = false
)

/** Writes `takes.csv` and `segments.csv` of a session; both are rewritten completely after every take. */
object LabManifest {

    val TAKE_COLUMNS = listOf(
        "take", "file", "status", "source", "mic", "note", "note_hz", "a4_hz", "drone_octave", "output", "duration_s",
        "recorded_s", "sample_rate", "buffer_frames", "routed_input", "active_mics", "agc", "ns", "aec", "drone_output",
        "output_matched", "media_volume", "dsp_load", "started_at"
    )

    val SEGMENT_COLUMNS = listOf("take", "index", "segment", "start_s", "end_s", "player_plays", "drone")

    fun write(dir: File, results: List<TakeResult>) {
        File(dir, "takes.csv").writeText(csv(TAKE_COLUMNS, results.map(::takeRow)), Charsets.UTF_8)
        File(dir, "segments.csv").writeText(csv(SEGMENT_COLUMNS, results.flatMap(::segmentRows)), Charsets.UTF_8)
    }

    fun takeRow(r: TakeResult): List<String> {
        val t = r.take
        val p = r.path
        val output = if (t.usesDrone) t.output.key else ""
        val matched = if (t.usesDrone && p != null) flag(!r.misrouted) else ""
        return listOf(
            t.id, t.fileName, r.status, t.source.name, t.mic.key, t.noteName, num(t.noteHz, 3), num(t.a4Hz, 1),
            t.droneOctave.toString(), output, num(t.durationSec, 2), num(r.recordedSec, 2),
            p?.sampleRate?.toString().orEmpty(), p?.bufferFrames?.toString().orEmpty(), p?.routedInput.orEmpty(),
            p?.activeMics.orEmpty(), p?.agc.orEmpty(), p?.ns.orEmpty(), p?.aec.orEmpty(), p?.droneOutput.orEmpty(),
            matched, p?.mediaVolume.orEmpty(), r.dspLoad?.let { num(it, 4) }.orEmpty(), r.startedAt
        )
    }

    fun segmentRows(r: TakeResult): List<List<String>> {
        val bounds = r.take.segmentBoundsSec
        return r.take.segments.mapIndexed { i, s ->
            listOf(
                r.take.id,
                i.toString(),
                s.key,
                num(bounds[i], 2),
                num(bounds[i + 1], 2),
                flag(s.playerPlays),
                flag(s.drone)
            )
        }
    }

    fun csv(columns: List<String>, rows: List<List<String>>): String = buildString {
        appendLine(columns.joinToString(",") { cell(it) })
        for (row in rows) appendLine(row.joinToString(",") { cell(it) })
    }

    /** RFC 4180 quoting: cells with comma, quote or line break are quoted, inner quotes doubled. */
    fun cell(value: String): String = if (value.any { it in NEEDS_QUOTES }) {
        "\"" + value.replace("\"", "\"\"") + "\""
    } else {
        value
    }

    private const val NEEDS_QUOTES = ",\"\n\r"

    private fun num(v: Double, decimals: Int) = String.format(Locale.ROOT, "%.${decimals}f", v)

    private fun flag(v: Boolean) = if (v) "1" else "0"
}
