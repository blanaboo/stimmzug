package io.github.blanaboo.stimmzug.practice

import io.github.blanaboo.stimmzug.audio.AudioInput
import io.github.blanaboo.stimmzug.dsp.music.NoteNames
import io.github.blanaboo.stimmzug.lab.WavWriter
import java.io.File
import java.io.IOException
import java.io.Writer
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Records every practice session of debug builds (#75), so a finding from the field test can be replayed at the
 * desk: the input as `input.wav`, the course of the session as `timeline.csv` and what it was about as
 * `session.txt`, each session in its own folder under [root]. Only the newest [keep] recordings stay.
 */
class PracticeRecorder(
    private val root: File,
    /** First lines of `session.txt`: app version, device. */
    private val header: List<String>,
    private val keep: Int = KEEP,
    private val now: () -> LocalDateTime = LocalDateTime::now
) : PracticeTap {
    init {
        require(keep >= 1) { "keep at least one recording, was $keep" }
    }

    override fun wrap(input: AudioInput, info: TapInfo, state: () -> EngineState): AudioInput {
        prune()
        return RecordingInput(input, newFolder(), header + describe(info), state, now)
    }

    /** The recordings on disk, oldest first. */
    fun recordings(): List<File> =
        root.listFiles { f -> f.isDirectory && FOLDER_NAME.matches(f.name) }.orEmpty().sortedBy { it.name }

    /** Makes room for one more recording. */
    private fun prune() {
        recordings().dropLast(keep - 1).forEach { it.deleteRecursively() }
    }

    private fun newFolder(): File {
        val stamp = STAMP.format(now())
        var folder = File(root, stamp)
        var n = 2
        while (folder.exists()) folder = File(root, "$stamp-${n++}")
        return folder
    }

    private fun describe(info: TapInfo): List<String> {
        val match = info.config.match
        return listOf(
            "Beginn: ${READABLE.format(now())}",
            "a′: ${number(info.config.a4Hz)} Hz, Toleranz ±${number(match.toleranceCents)} ct, " +
                "Haltedauer ${number(match.holdSec)} s, Gnadenzeit ${number(match.graceSec)} s",
            "Übung: ${info.exercise}",
            "Töne: " + info.notes.joinToString(", ") { "${NoteNames.germanOctaveName(it)} ($it)" }
        )
    }

    companion object {
        /** Folder of the recordings inside the app's external files. */
        const val FOLDER = "practice"
        const val KEEP = 5

        private val FOLDER_NAME = Regex("""\d{8}-\d{6}(-\d+)?""")
        private val STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT)
        internal val READABLE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT)

        internal fun number(value: Double): String =
            if (value == value.roundToInt().toDouble()) value.roundToInt().toString() else value.toString()
    }
}

/**
 * Delivers what [inner] delivers and writes it to [folder] on the way: the audio as 16-bit WAV and, after each
 * chunk has gone through the session, the session's state every [ROW_MS] ms and whenever note, phase or verdict
 * change. Failing to write never stops the practice: recording ends and [failure] tells why.
 */
class RecordingInput(
    private val inner: AudioInput,
    val folder: File,
    private val header: List<String>,
    private val state: () -> EngineState,
    private val now: () -> LocalDateTime = LocalDateTime::now
) : AudioInput {
    override val sampleRate: Int get() = inner.sampleRate
    override val sourceName: String? get() = inner.sourceName

    private val lock = Any()
    private var wav: WavWriter? = null
    private var timeline: Writer? = null
    private var pcm = ShortArray(0)
    private var nextRow = 0L
    private var nextSync = 0L
    private var lastKey: String? = null
    private var lastState: EngineState? = null

    /** Why the recording ended early, if it did. */
    @Volatile
    var failure: IOException? = null
        private set

    override fun start(onChunk: (FloatArray, Int) -> Unit, onError: (Exception) -> Unit) {
        record {
            folder.mkdirs()
            File(folder, SESSION_FILE).writeText(header.joinToString("\n", postfix = "\n"), Charsets.UTF_8)
            wav = WavWriter(File(folder, AUDIO_FILE), inner.sampleRate)
            timeline = File(folder, TIMELINE_FILE).bufferedWriter(Charsets.UTF_8).apply { write(TIMELINE_HEADER) }
        }
        inner.start(
            { samples, count ->
                record { writeAudio(samples, count) }
                onChunk(samples, count)
                record { writeState(state()) }
            },
            onError
        )
    }

    override fun close() {
        inner.close()
        record { finish() }
    }

    private fun record(block: () -> Unit) = synchronized(lock) {
        if (failure != null) return@synchronized
        try {
            block()
        } catch (e: IOException) {
            failure = e
            runCatching { timeline?.close() }
            runCatching { wav?.close() }
            timeline = null
            wav = null
        }
    }

    private fun writeAudio(samples: FloatArray, count: Int) {
        val wav = wav ?: return
        if (pcm.size < count) pcm = ShortArray(count)
        for (i in 0 until count) pcm[i] = (samples[i].coerceIn(-1f, 1f) * Short.MAX_VALUE).roundToInt().toShort()
        wav.write(pcm, count)
        if (wav.samples >= nextSync) {
            wav.sync()
            timeline?.flush()
            nextSync = wav.samples + inner.sampleRate
        }
    }

    private fun writeState(s: EngineState) {
        val out = timeline ?: return
        lastState = s
        val snapshot = s.snapshot
        val note = snapshot.note
        val key = "${snapshot.index}|${snapshot.phase}|${note?.phase}|${note?.verdict}"
        val written = wav?.samples ?: 0L
        if (key == lastKey && written < nextRow) return
        lastKey = key
        nextRow = written + inner.sampleRate * ROW_MS / MILLIS_PER_SECOND
        val cells = listOf(
            String.format(Locale.ROOT, "%.3f", snapshot.timeSec),
            snapshot.index.toString(),
            snapshot.midi?.let(NoteNames::germanOctaveName).orEmpty(),
            snapshot.midi?.toString().orEmpty(),
            snapshot.targetHz?.let { String.format(Locale.ROOT, "%.3f", it) }.orEmpty(),
            snapshot.phase.name,
            note?.phase?.name.orEmpty(),
            note?.verdict?.name.orEmpty(),
            note?.centsDeviation?.let { String.format(Locale.ROOT, "%.1f", it) }.orEmpty(),
            note?.let { String.format(Locale.ROOT, "%.3f", it.holdProgress) }.orEmpty(),
            s.route.kind.name
        )
        out.write(cells.joinToString(",", postfix = "\n"))
    }

    private fun finish() {
        timeline?.close()
        timeline = null
        val seconds = (wav?.samples ?: 0L).toDouble() / inner.sampleRate
        wav?.close()
        wav = null
        val s = lastState
        val lines = listOfNotNull(
            "Eingang: ${inner.sourceName ?: "–"}",
            "Ausgabe: ${s?.route?.let { r -> listOfNotNull(r.kind.name, r.name).joinToString(" ") } ?: "–"}",
            "Ende: ${PracticeRecorder.READABLE.format(now())}, Audio ${String.format(Locale.ROOT, "%.1f", seconds)} s",
            s?.summary?.let { "Ergebnis: ${it.hits} von ${it.outcomes.size} sauber" },
            s?.error?.let { "Fehler: $it" }
        )
        File(folder, SESSION_FILE).appendText(lines.joinToString("\n", postfix = "\n"), Charsets.UTF_8)
    }

    companion object {
        const val AUDIO_FILE = "input.wav"
        const val TIMELINE_FILE = "timeline.csv"
        const val SESSION_FILE = "session.txt"
        const val TIMELINE_HEADER =
            "time_s,index,note,midi,target_hz,session_phase,note_phase,verdict,cents,hold,route\n"

        /** A timeline row at least this often (ms of audio). */
        const val ROW_MS = 50
        private const val MILLIS_PER_SECOND = 1000
    }
}
