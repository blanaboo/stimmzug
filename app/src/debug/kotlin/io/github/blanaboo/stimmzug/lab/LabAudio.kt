package io.github.blanaboo.stimmzug.lab

import android.Manifest
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.MicrophoneInfo
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AudioEffect
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import io.github.blanaboo.stimmzug.dsp.synth.DroneSynth
import java.io.Closeable
import java.io.IOException
import java.util.Locale
import kotlin.math.max

/** Sample rate of all lab recordings: the native rate of phone audio hardware, so nothing is resampled. */
const val LAB_SAMPLE_RATE = 48_000

/** Opens recordings for lab takes and describes the device's audio inputs. */
class LabRecorder(private val audioManager: AudioManager) {

    /**
     * Built-in microphones that can be chosen. The system's choice on devices that list only one, and below API 28,
     * where the device address (bottom, back) that tells them apart is not available.
     */
    fun micChoices(): List<MicChoice> {
        val mics = builtInMics()
        if (mics.size < 2 || Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return listOf(MicChoice.SYSTEM)
        return mics.map { device ->
            val (key, label) = when (device.address) {
                "bottom" -> "unten" to "Mikro unten"
                "back" -> "hinten" to "Mikro hinten"
                "top" -> "oben" to "Mikro oben"
                else -> "mic${device.id}" to "Mikro ${device.address.ifEmpty { "#${device.id}" }}"
            }
            MicChoice(key, label, device.id)
        }
    }

    /**
     * Creates and starts the recording for [take]. Throws [IllegalStateException] or
     * [UnsupportedOperationException] if the device refuses the source or format.
     */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start(take: Take): AudioRecord {
        val minBuffer = AudioRecord.getMinBufferSize(LAB_SAMPLE_RATE, CHANNEL, ENCODING)
        check(minBuffer > 0) { "AudioRecord.getMinBufferSize: $minBuffer" }
        val record = AudioRecord.Builder()
            .setAudioSource(androidSource(take.source))
            .setAudioFormat(
                AudioFormat.Builder().setSampleRate(LAB_SAMPLE_RATE).setChannelMask(CHANNEL).setEncoding(ENCODING)
                    .build()
            )
            // Room for 200 ms, so a slow UI frame never makes the recording drop samples.
            .setBufferSizeInBytes(max(minBuffer, LAB_SAMPLE_RATE / 5 * 2))
            .build()
        try {
            check(record.state == AudioRecord.STATE_INITIALIZED) { "AudioRecord nicht initialisiert" }
            take.mic.deviceId?.let { id ->
                val device = builtInMics().firstOrNull { it.id == id }
                check(device != null && record.setPreferredDevice(device)) { "Mikrofon #$id nicht wählbar" }
            }
            record.startRecording()
            check(record.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "Aufnahme startet nicht" }
        } catch (e: IllegalStateException) {
            record.release()
            throw e
        }
        return record
    }

    /** Reads what the platform did with the request; call after [start]. */
    fun report(record: AudioRecord, drone: DronePlayer?): PathReport {
        val session = record.audioSessionId
        return PathReport(
            sampleRate = record.sampleRate,
            bufferFrames = record.bufferSizeInFrames,
            routedInput = record.routedDevice?.let(::describe) ?: "?",
            activeMics = activeMics(record),
            agc = effectState(AutomaticGainControl.isAvailable()) { AutomaticGainControl.create(session) },
            ns = effectState(NoiseSuppressor.isAvailable()) { NoiseSuppressor.create(session) },
            aec = effectState(AcousticEchoCanceler.isAvailable()) { AcousticEchoCanceler.create(session) },
            droneOutput = drone?.routedDevice.orEmpty(),
            mediaVolume = mediaVolume()
        )
    }

    fun mediaVolume(): String {
        val stream = AudioManager.STREAM_MUSIC
        return "${audioManager.getStreamVolume(stream)}/${audioManager.getStreamMaxVolume(stream)}"
    }

    /** Device and audio facts for `session.txt`. */
    fun sessionInfo(): List<String> = buildList {
        add("Gerät: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE}), Android ${Build.VERSION.RELEASE}")
        add("API: ${Build.VERSION.SDK_INT}, Build: ${Build.DISPLAY}")
        add("UNPROCESSED zugesichert: ${audioManager.getProperty(PROPERTY_UNPROCESSED) ?: "nein"}")
        add(
            "Ausgabe: ${audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)} Hz, " +
                "${audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)} Frames je Puffer"
        )
        add("Medienlautstärke: ${mediaVolume()}")
        add("Eingänge:")
        audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS).forEach { add("  ${describe(it)}") }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            add("Mikrofone (AudioManager.getMicrophones):")
            try {
                audioManager.microphones.forEach { add("  ${describe(it)}") }
            } catch (e: IOException) {
                add("  Fehler: ${e.message}")
            }
        }
    }

    /** The device for [output]; `null` for [DroneOutput.SYSTEM] or if it is not connected. */
    fun outputDevice(output: DroneOutput): AudioDeviceInfo? {
        if (output == DroneOutput.SYSTEM) return null
        return audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { matches(output, it) }
    }

    /** What the player has to do before a take with [output] can start, or `null` if the output is there. */
    fun missingOutput(output: DroneOutput): String? = when {
        output == DroneOutput.SYSTEM || outputDevice(output) != null -> null
        else -> "${output.label} nicht verbunden. Bitte verbinden, dann „Weiter“ tippen."
    }

    private fun builtInMics(): List<AudioDeviceInfo> =
        audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS).filter { it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC }

    private fun activeMics(record: AudioRecord): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return "?"
        return try {
            record.activeMicrophones.joinToString(" + ") { "${it.description} ${it.address}" }.ifEmpty { "–" }
        } catch (e: IOException) {
            "Fehler: ${e.message}"
        }
    }

    /**
     * Whether the platform's default pre-processing switched the effect on for this session. Creating an effect
     * of a type that is already attached returns a handle to it; a newly created one starts disabled and is
     * removed again on release.
     */
    @Suppress("TooGenericExceptionCaught") // AudioEffect throws a plain RuntimeException if no engine can be created
    private fun effectState(available: Boolean, create: () -> AudioEffect?): String {
        if (!available) return "–"
        return try {
            create()?.let { effect ->
                val enabled = effect.enabled
                effect.release()
                if (enabled) "an" else "aus"
            } ?: "?"
        } catch (e: RuntimeException) {
            "Fehler: ${e.message}"
        }
    }

    companion object {
        private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        private const val PROPERTY_UNPROCESSED = AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED

        /** `AudioDeviceInfo.TYPE_BLE_HEADSET` (API 31), only used as a label. */
        private const val TYPE_BLE_HEADSET = 26

        /** VOICE_PERFORMANCE is filtered out by [LabSource.minSdk] below API 29. */
        @Suppress("InlinedApi")
        fun androidSource(source: LabSource): Int = when (source) {
            LabSource.UNPROCESSED -> MediaRecorder.AudioSource.UNPROCESSED
            LabSource.VOICE_RECOGNITION -> MediaRecorder.AudioSource.VOICE_RECOGNITION
            LabSource.VOICE_PERFORMANCE -> MediaRecorder.AudioSource.VOICE_PERFORMANCE
            LabSource.MIC -> MediaRecorder.AudioSource.MIC
            LabSource.CAMCORDER -> MediaRecorder.AudioSource.CAMCORDER
            LabSource.VOICE_COMMUNICATION -> MediaRecorder.AudioSource.VOICE_COMMUNICATION
        }

        /** Media outputs that count as Bluetooth headphones: classic A2DP and, from API 31, LE Audio. */
        private val bluetoothOutputs: Set<Int> = buildSet {
            add(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(AudioDeviceInfo.TYPE_BLE_HEADSET)
                add(AudioDeviceInfo.TYPE_BLE_SPEAKER)
            }
        }

        /** Whether [device] is the kind of output [output] asks for; [DroneOutput.SYSTEM] accepts any. */
        fun matches(output: DroneOutput, device: AudioDeviceInfo?): Boolean = when (output) {
            DroneOutput.SYSTEM -> true
            DroneOutput.SPEAKER -> device?.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
            DroneOutput.BLUETOOTH -> device?.type in bluetoothOutputs
        }

        fun describe(device: AudioDeviceInfo): String {
            val type = when (device.type) {
                AudioDeviceInfo.TYPE_BUILTIN_MIC -> "BUILTIN_MIC"
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "BUILTIN_SPEAKER"
                AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "BUILTIN_EARPIECE"
                AudioDeviceInfo.TYPE_WIRED_HEADSET -> "WIRED_HEADSET"
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "WIRED_HEADPHONES"
                AudioDeviceInfo.TYPE_USB_DEVICE -> "USB_DEVICE"
                AudioDeviceInfo.TYPE_USB_HEADSET -> "USB_HEADSET"
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "BLUETOOTH_SCO"
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "BLUETOOTH_A2DP"
                TYPE_BLE_HEADSET -> "BLE_HEADSET"
                AudioDeviceInfo.TYPE_TELEPHONY -> "TELEPHONY"
                else -> "TYPE_${device.type}"
            }
            val address = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) " ${device.address}" else ""
            return "$type$address (#${device.id})".replace("  ", " ")
        }

        @RequiresApi(Build.VERSION_CODES.P)
        private fun describe(mic: MicrophoneInfo): String {
            val p = mic.position
            val position = if (p == MicrophoneInfo.POSITION_UNKNOWN) {
                "?"
            } else {
                String.format(Locale.ROOT, "%.3f/%.3f/%.3f m", p.x, p.y, p.z)
            }
            val sensitivity = if (mic.sensitivity ==
                MicrophoneInfo.SENSITIVITY_UNKNOWN
            ) {
                "?"
            } else {
                "${mic.sensitivity} dBFS/Pa"
            }
            val response = mic.frequencyResponse.joinToString(" ") { "${it.first.toInt()}Hz:${it.second}dB" }
            return "${mic.description} ${mic.address}, Ort ${mic.location}, Position $position, " +
                "Empfindlichkeit $sensitivity, Frequenzgang [${response.ifEmpty { "–" }}]"
        }
    }
}

/**
 * Plays the drone through the media stream, rendered by [DroneSynth] on its own thread. Silent until [play].
 * Without [preferred] Android routes it (speaker unless headphones are connected); with it the track asks for that
 * device, which [routedOutput] confirms or not.
 */
class DronePlayer(preferred: AudioDeviceInfo? = null) : Closeable {
    private val synth = DroneSynth(LAB_SAMPLE_RATE.toDouble())
    private val track = AudioTrack.Builder()
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        )
        .setAudioFormat(
            AudioFormat.Builder()
                .setSampleRate(LAB_SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                .build()
        )
        .setBufferSizeInBytes(
            max(
                AudioTrack.getMinBufferSize(
                    LAB_SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_FLOAT
                ),
                4 * BLOCK * Float.SIZE_BYTES
            )
        )
        .setTransferMode(AudioTrack.MODE_STREAM)
        .build()

    @Volatile private var running = true
    private val thread = Thread(::renderLoop, "lab-drone")

    init {
        preferred?.let(track::setPreferredDevice)
        track.play()
        thread.start()
    }

    val routedOutput: AudioDeviceInfo? get() = track.routedDevice

    val routedDevice: String get() = routedOutput?.let(LabRecorder::describe) ?: "?"

    fun play(frequencyHz: Double, octaves: Int) {
        synth.setOctaveOffset(octaves)
        synth.play(frequencyHz)
    }

    fun stop() = synth.stop()

    private fun renderLoop() {
        val block = FloatArray(BLOCK)
        while (running) {
            synth.render(block)
            if (track.write(block, 0, block.size, AudioTrack.WRITE_BLOCKING) < 0) break
        }
    }

    override fun close() {
        running = false
        thread.join(JOIN_TIMEOUT_MS)
        track.stop()
        track.release()
    }

    private companion object {
        /** 20 ms per block. */
        const val BLOCK = LAB_SAMPLE_RATE / 50
        const val JOIN_TIMEOUT_MS = 500L
    }
}
