package io.github.blanaboo.stimmzug.lab

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.AudioRecord
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import io.github.blanaboo.stimmzug.BuildConfig
import io.github.blanaboo.stimmzug.dsp.music.NoteMath
import io.github.blanaboo.stimmzug.dsp.music.NoteNames
import io.github.blanaboo.stimmzug.dsp.pitch.PitchFrame
import io.github.blanaboo.stimmzug.dsp.pitch.PitchTracker
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Semaphore
import kotlin.math.min

enum class LabStatus { IDLE, WAITING, PREPARING, RECORDING, DONE, CANCELLED, FAILED }

/** Live readout of the recording, ~10 times per second. */
data class LiveReading(val levelDb: Double, val note: String?, val cents: Double?, val clarity: Double)

data class LabUiState(
    val status: LabStatus = LabStatus.IDLE,
    val protocolTitle: String = "",
    /** 1-based number of the current take. */
    val takeNumber: Int = 0,
    val takeCount: Int = 0,
    /** Source and microphone of the current take. */
    val takeTitle: String = "",
    val instruction: String = "",
    val noteText: String = "",
    val playerPlays: Boolean = false,
    val remainingSec: Int = 0,
    val live: LiveReading? = null,
    /** Pause text before a part, or the error / result of the session. */
    val message: String? = null,
    val sessionDir: String? = null,
    /** One line per finished take. */
    val log: List<String> = emptyList()
)

/**
 * Runs a [LabProtocol] on a worker thread: per take a countdown, then the recording with its segments (drone on
 * and off at the segment bounds), written to WAV while a [PitchTracker] feeds the live readout. Survives
 * configuration changes; [onCleared] stops a running session.
 */
class LabViewModel(application: Application) : AndroidViewModel(application) {

    var state by mutableStateOf(LabUiState())
        private set

    private val audioManager = application.getSystemService(AudioManager::class.java)
    private val recorder = LabRecorder(audioManager)
    private val main = Handler(Looper.getMainLooper())

    @Volatile private var cancelled = false

    @Volatile private var repeat = false
    private val proceed = Semaphore(0)
    private var worker: Thread? = null

    val isRunning: Boolean get() = worker?.isAlive == true

    fun hasMicPermission(): Boolean = ContextCompat.checkSelfPermission(
        getApplication(),
        Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

    fun protocol(key: String): LabProtocol? {
        val mics = recorder.micChoices()
        return when (key) {
            "selbsttest" -> LabProtocols.selfTest(mics, Build.VERSION.SDK_INT)
            "tuba" -> LabProtocols.tuba(mics, Build.VERSION.SDK_INT)
            else -> null
        }
    }

    fun start(protocol: LabProtocol) {
        if (isRunning) return
        if (!hasMicPermission()) {
            state = state.copy(status = LabStatus.FAILED, message = "Keine Mikrofon-Berechtigung")
            return
        }
        cancelled = false
        repeat = false
        proceed.drainPermits()
        state =
            LabUiState(status = LabStatus.PREPARING, protocolTitle = protocol.title, takeCount = protocol.takes.size)
        worker = Thread({ runSession(protocol) }, "lab-session").apply { start() }
    }

    /** Continues after a pause before a part. */
    fun proceed() = proceed.release()

    /** Restarts the current take; between takes (countdown or pause) the previous one. */
    fun repeatTake() {
        repeat = true
        proceed.release()
    }

    fun cancel() {
        cancelled = true
        proceed.release()
    }

    override fun onCleared() {
        cancel()
        worker?.join(STOP_TIMEOUT_MS)
    }

    private fun update(change: (LabUiState) -> LabUiState) {
        main.post { state = change(state) }
    }

    private fun runSession(protocol: LabProtocol) {
        val dir = sessionDir(protocol)
        val results = sortedMapOf<String, TakeResult>()
        update { it.copy(sessionDir = dir.absolutePath) }
        try {
            File(dir, "session.txt").writeText(sessionText(protocol).joinToString("\n", postfix = "\n"))
            var index = 0
            while (index < protocol.takes.size && !cancelled) {
                repeat = false
                index = step(protocol, index, dir, results)
            }
            val finished = results.values.count { it.status == STATUS_OK }
            update {
                it.copy(
                    status = if (cancelled) LabStatus.CANCELLED else LabStatus.DONE,
                    message = "$finished von ${protocol.takes.size} Aufnahmen gespeichert.",
                    live = null
                )
            }
        } catch (e: java.io.IOException) {
            update { it.copy(status = LabStatus.FAILED, message = "Speichern fehlgeschlagen: ${e.message}") }
        }
    }

    /**
     * Runs take [index] and returns the index to continue with: the next take; the same one if the player asked to
     * repeat it while recording; the previous one if they asked between takes; the same one on cancel (the loop
     * ends).
     */
    private fun step(protocol: LabProtocol, index: Int, dir: File, results: MutableMap<String, TakeResult>): Int {
        val take = protocol.takes[index]
        if (!waitBefore(protocol, take, index)) return if (repeat) maxOf(0, index - 1) else index
        val result = record(take, dir) ?: return index
        results[take.id] = result
        LabManifest.write(dir, results.values.toList())
        update { it.copy(log = it.log + logLine(result)) }
        return index + 1
    }

    /** Pause (if the take has one) and countdown. False if the take must not start: cancel or repeat. */
    private fun waitBefore(protocol: LabProtocol, take: Take, index: Int): Boolean {
        val header = header(protocol, take, index)
        take.pauseBefore?.let { text ->
            update { header(it).copy(status = LabStatus.WAITING, message = text, instruction = "Bereit?") }
            proceed.drainPermits()
            if (!cancelled && !repeat) proceed.acquireUninterruptibly()
        }
        val end = SystemClock.elapsedRealtime() + (protocol.prepSec * MS_PER_SEC).toLong()
        while (!cancelled && !repeat) {
            val left = end - SystemClock.elapsedRealtime()
            if (left <= 0) break
            update {
                header(it).copy(
                    status = LabStatus.PREPARING,
                    instruction = "Gleich: ${take.segments.first().instruction}",
                    remainingSec = ((left + MS_PER_SEC - 1) / MS_PER_SEC).toInt(),
                    playerPlays = false,
                    message = null
                )
            }
            SystemClock.sleep(min(left, TICK_MS))
        }
        return !cancelled && !repeat
    }

    private fun header(protocol: LabProtocol, take: Take, index: Int): (LabUiState) -> LabUiState = {
        it.copy(
            takeNumber = index + 1,
            takeCount = protocol.takes.size,
            takeTitle = "${take.id} · ${take.source.name} · ${take.mic.label}",
            noteText = noteText(take),
            live = null
        )
    }

    /** Records one take. Returns `null` if the player asked to repeat it. */
    @SuppressLint("MissingPermission") // checked in start()
    private fun record(take: Take, dir: File): TakeResult? {
        val startedAt = SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(Date())
        val file = File(dir, take.fileName)
        val drone = if (take.usesDrone) DronePlayer() else null
        val record = try {
            recorder.start(take)
        } catch (e: IllegalStateException) {
            drone?.close()
            return TakeResult(take, "Fehler: ${e.message}", null, 0.0, null, startedAt)
        } catch (e: UnsupportedOperationException) {
            drone?.close()
            return TakeResult(take, "Fehler: ${e.message}", null, 0.0, null, startedAt)
        }
        try {
            val path = recorder.report(record, drone)
            val capture = WavWriter(file, record.sampleRate).use { wav -> capture(take, record, drone, wav) }
            if (repeat) {
                file.delete()
                return null
            }
            val status = when {
                capture.error != null -> "Fehler: ${capture.error}"
                cancelled -> "abgebrochen"
                else -> STATUS_OK
            }
            return TakeResult(take, status, path, capture.seconds, capture.dspLoad, startedAt)
        } finally {
            record.stop()
            record.release()
            drone?.close()
        }
    }

    private class Capture(val seconds: Double, val dspLoad: Double, val error: String?)

    private fun capture(take: Take, record: AudioRecord, drone: DronePlayer?, wav: WavWriter): Capture {
        val rate = record.sampleRate
        val bounds = take.segmentBoundsSec.map { (it * rate).toLong() }
        val total = bounds.last()
        val shorts = ShortArray(rate / CHUNKS_PER_SEC)
        val floats = FloatArray(shorts.size)
        val tracker = PitchTracker(rate.toDouble()).apply { targetHz = take.noteHz }
        var lastFrame: PitchFrame? = null
        var segment = -1
        var dspNanos = 0L
        var lastUiUpdate = 0L
        while (wav.samples < total && !cancelled && !repeat) {
            val current = bounds.indexOfLast { it <= wav.samples }
            if (current != segment) {
                segment = current
                val s = take.segments[segment]
                if (s.drone) drone?.play(take.noteHz, take.droneOctave) else drone?.stop()
                update {
                    it.copy(status = LabStatus.RECORDING, instruction = s.instruction, playerPlays = s.playerPlays)
                }
            }
            val n = record.read(shorts, 0, min(shorts.size.toLong(), total - wav.samples).toInt())
            if (n < 0) return Capture(wav.samples.toDouble() / rate, 0.0, "AudioRecord.read: $n")
            wav.write(shorts, n)
            for (i in 0 until n) floats[i] = shorts[i] / PCM_SCALE
            val t0 = System.nanoTime()
            tracker.process(floats, n) { lastFrame = it }
            dspNanos += System.nanoTime() - t0
            if (wav.samples - lastUiUpdate >= rate / UI_UPDATES_PER_SEC) {
                lastUiUpdate = wav.samples
                val remaining = ((bounds[segment + 1] - wav.samples + rate - 1) / rate).toInt()
                val reading = lastFrame?.let { live(it, take) }
                update { it.copy(remainingSec = remaining, live = reading) }
            }
        }
        val seconds = wav.samples.toDouble() / rate
        val load = if (seconds > 0) dspNanos / (seconds * NANOS_PER_SEC) else 0.0
        return Capture(seconds, load, null)
    }

    private fun live(frame: PitchFrame, take: Take): LiveReading {
        val e = frame.estimate
        if (!e.isVoiced) return LiveReading(e.levelDb, null, null, e.clarity)
        val midi = NoteMath.nearestMidi(e.frequencyHz)
        val cents = if (midi == take.noteMidi) NoteMath.cents(e.frequencyHz, take.noteHz) else null
        return LiveReading(e.levelDb, NoteNames.germanOctaveName(midi), cents, e.clarity)
    }

    private fun sessionDir(protocol: LabProtocol): File {
        val root = getApplication<Application>().getExternalFilesDir("lab")
            ?: File(getApplication<Application>().filesDir, "lab")
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())
        return File(root, "$stamp-${protocol.key}").apply { mkdirs() }
    }

    private fun sessionText(protocol: LabProtocol): List<String> = buildList {
        add("Stimmzug Audio-Labor (#33), App ${BuildConfig.VERSION_NAME}")
        add("Ablauf: ${protocol.title} (${protocol.takes.size} Aufnahmen)")
        add("Beginn: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date())}")
        addAll(recorder.sessionInfo())
    }

    private fun logLine(r: TakeResult): String {
        val load = r.dspLoad?.let { String.format(Locale.GERMANY, ", Rechenzeit %.0f %%", it * PERCENT) }.orEmpty()
        val input = r.path?.let { " → ${it.routedInput}, ${it.activeMics}" }.orEmpty()
        return "${r.take.id} ${r.take.source.name} ${r.take.mic.key} ${r.take.noteName}: ${r.status}$input$load"
    }

    private fun noteText(take: Take): String {
        val hz = String.format(Locale.GERMANY, "%.2f Hz", take.noteHz)
        val drone = when {
            !take.usesDrone -> ""
            take.droneOctave > 0 -> " · Drone ${take.droneOctave} Oktave höher"
            else -> " · mit Drone"
        }
        return "${take.noteName} ($hz)$drone"
    }

    private companion object {
        const val STATUS_OK = "ok"
        const val CHUNKS_PER_SEC = 50
        const val UI_UPDATES_PER_SEC = 10
        const val PCM_SCALE = 32768f
        const val MS_PER_SEC = 1000L
        const val TICK_MS = 100L
        const val NANOS_PER_SEC = 1e9
        const val PERCENT = 100.0
        const val STOP_TIMEOUT_MS = 2000L
    }
}
