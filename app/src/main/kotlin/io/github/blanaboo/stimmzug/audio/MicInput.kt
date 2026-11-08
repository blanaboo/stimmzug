package io.github.blanaboo.stimmzug.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.Closeable
import kotlin.math.max

/** Where the practice audio comes from: the microphone, or a synthetic player in tests. */
interface AudioInput : Closeable {
    val sampleRate: Int

    /** The source in use, for the session log (#37); `null` until the input runs. */
    val sourceName: String?

    /**
     * Starts delivering audio on a background thread: [onChunk] gets samples in −1..1 and their count, [onError]
     * is called once if the input cannot start or breaks off. Call [close] to stop.
     */
    fun start(onChunk: (FloatArray, Int) -> Unit, onError: (Exception) -> Unit)
}

/** The microphone sources in the order they are tried (ADR 0005). */
enum class MicSource(val androidSource: Int) {
    /** Raw path: no limiter, no high-pass, no automatic gain; not every device guarantees it. */
    UNPROCESSED(MediaRecorder.AudioSource.UNPROCESSED),

    /** Fallback: processed, but without noise suppression and automatic gain by definition. */
    VOICE_RECOGNITION(MediaRecorder.AudioSource.VOICE_RECOGNITION)
}

/** 16-bit PCM from an opened source. */
interface PcmSource : Closeable {
    /** Blocks until [buffer] is filled or the source fails; returns the count read, negative on error. */
    fun read(buffer: ShortArray): Int
}

/** Opens a microphone source; `null` if the device cannot open it. */
fun interface RecordOpener {
    fun open(source: MicSource): PcmSource?
}

/** A source that opened and delivered sound, with the samples read while checking it. */
class OpenedMic(val source: MicSource, val pcm: PcmSource, val probe: ShortArray, val probeCount: Int)

/**
 * Opens the first [MicSource] that works (ADR 0005). Whether `UNPROCESSED` works is found out by opening it, not by
 * `PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED`, which the owner's phone leaves unset although the source is the best
 * one there. A source that only delivers digital silence during the first [probeSize] samples counts as not working.
 */
fun openMic(opener: RecordOpener, probeSize: Int): OpenedMic {
    val last = MicSource.entries.last()
    for (source in MicSource.entries) {
        val mic = opener.open(source)?.let { probe(source, it, probeSize) }
        // The last source is kept even if silent: a very quiet room must not stop the practice.
        if (mic != null && (mic.hasSound || source == last)) return mic
        mic?.pcm?.close()
    }
    error("Kein Mikrofon verfügbar")
}

private fun probe(source: MicSource, pcm: PcmSource, size: Int): OpenedMic {
    val samples = ShortArray(size)
    val count = pcm.read(samples)
    return OpenedMic(source, pcm, samples, max(0, count))
}

private val OpenedMic.hasSound: Boolean get() = (0 until probeCount).any { probe[it].toInt() != 0 }

/** The phone's microphone, mono 16 bit at [sampleRate], read on its own thread. */
class MicInput(private val opener: RecordOpener, override val sampleRate: Int = SAMPLE_RATE) : AudioInput {

    constructor(context: Context) : this(
        AndroidRecordOpener(SAMPLE_RATE, context.getSystemService(AudioManager::class.java))
    )

    @Volatile private var running = false
    private var thread: Thread? = null

    @Volatile override var sourceName: String? = null
        private set

    override fun start(onChunk: (FloatArray, Int) -> Unit, onError: (Exception) -> Unit) {
        check(thread == null) { "already started" }
        running = true
        thread = Thread({ run(onChunk, onError) }, "stimmzug-mic").also { it.start() }
    }

    @Suppress("TooGenericExceptionCaught") // any failure of the audio stack ends the input with a message
    private fun run(onChunk: (FloatArray, Int) -> Unit, onError: (Exception) -> Unit) {
        try {
            val mic = openMic(opener, sampleRate / PROBES_PER_SECOND)
            sourceName = mic.source.name
            mic.pcm.use { pcm ->
                val floats = FloatArray(max(mic.probe.size, sampleRate / CHUNKS_PER_SECOND))
                deliver(mic.probe, mic.probeCount, floats, onChunk)
                val buffer = ShortArray(sampleRate / CHUNKS_PER_SECOND)
                while (running) {
                    val count = pcm.read(buffer)
                    check(count >= 0) { "Mikrofon liefert keine Daten mehr ($count)" }
                    deliver(buffer, count, floats, onChunk)
                }
            }
        } catch (e: Exception) {
            if (running) onError(e)
        }
    }

    private fun deliver(pcm: ShortArray, count: Int, floats: FloatArray, onChunk: (FloatArray, Int) -> Unit) {
        if (count <= 0 || !running) return
        for (i in 0 until count) floats[i] = pcm[i] / FULL_SCALE
        onChunk(floats, count)
    }

    override fun close() {
        running = false
        thread?.join(JOIN_TIMEOUT_MS)
    }

    companion object {
        const val SAMPLE_RATE = 48_000

        /** 10 ms chunks: the detector analyses every 20 ms. */
        private const val CHUNKS_PER_SECOND = 100

        /** 0.25 s to tell a working source from digital silence. */
        private const val PROBES_PER_SECOND = 4
        private const val FULL_SCALE = 32_768f
        private const val JOIN_TIMEOUT_MS = 1_000L
    }
}

/** Opens [AudioRecord]s; never the Bluetooth headset microphone, which would switch the headphones to call mode. */
class AndroidRecordOpener(private val sampleRate: Int, private val audioManager: AudioManager) : RecordOpener {

    @SuppressLint("MissingPermission") // the practice screen asks for the permission before it starts the input
    override fun open(source: MicSource): PcmSource? {
        val minBuffer = AudioRecord.getMinBufferSize(sampleRate, CHANNEL, ENCODING)
        if (minBuffer <= 0) return null
        // Devices throw various runtime exceptions for sources they do not support; that just means "not this one".
        val record = runCatching {
            AudioRecord.Builder()
                .setAudioSource(source.androidSource)
                .setAudioFormat(
                    AudioFormat.Builder().setSampleRate(
                        sampleRate
                    ).setChannelMask(CHANNEL).setEncoding(ENCODING).build()
                )
                .setBufferSizeInBytes(max(minBuffer, sampleRate / BUFFERS_PER_SECOND * Short.SIZE_BYTES))
                .build()
        }.getOrNull() ?: return null
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return null
        }
        record.startRecording()
        if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            record.release()
            return null
        }
        avoidBluetoothMic(record)
        return RecordPcm(record)
    }

    private fun avoidBluetoothMic(record: AudioRecord) {
        if (record.routedDevice?.type != AudioDeviceInfo.TYPE_BLUETOOTH_SCO) return
        audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC }
            ?.let(record::setPreferredDevice)
    }

    private class RecordPcm(private val record: AudioRecord) : PcmSource {
        override fun read(buffer: ShortArray): Int = record.read(buffer, 0, buffer.size)

        override fun close() {
            record.stop()
            record.release()
        }
    }

    private companion object {
        const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        const val ENCODING = AudioFormat.ENCODING_PCM_16BIT

        /** At least 0.2 s of buffer, so a busy main thread does not lose audio. */
        const val BUFFERS_PER_SECOND = 5
    }
}
