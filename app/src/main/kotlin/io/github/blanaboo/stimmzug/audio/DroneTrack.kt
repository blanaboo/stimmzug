package io.github.blanaboo.stimmzug.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import io.github.blanaboo.stimmzug.dsp.session.DroneControl
import io.github.blanaboo.stimmzug.dsp.synth.DroneConfig
import io.github.blanaboo.stimmzug.dsp.synth.DroneSynth
import io.github.blanaboo.stimmzug.dsp.synth.DroneTimbre
import java.io.Closeable
import kotlin.math.max

/** What kind of device the drone sounds on, for the practice screen and the session log. */
enum class OutputKind { SPEAKER, HEADPHONES, OTHER, UNKNOWN }

/** Where the drone actually goes; [name] is the product name of headphones, if the system knows it. */
data class OutputRoute(val kind: OutputKind, val name: String? = null) {
    companion object {
        val UNKNOWN = OutputRoute(OutputKind.UNKNOWN)

        /** Media outputs that sit on or in the ears: wired, USB, Bluetooth (classic and LE), hearing aids. */
        private val headphones = setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            TYPE_HEARING_AID,
            TYPE_BLE_HEADSET
        )

        /** `AudioDeviceInfo.TYPE_HEARING_AID` (API 28) and `TYPE_BLE_HEADSET` (API 31), as plain values for API 26. */
        private const val TYPE_HEARING_AID = 23
        private const val TYPE_BLE_HEADSET = 26

        /** The route of a device of [type] named [productName]; pure, so it can be tested without a device. */
        fun of(type: Int?, productName: CharSequence?): OutputRoute = when (type) {
            null -> UNKNOWN
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> OutputRoute(OutputKind.SPEAKER)
            in headphones -> OutputRoute(OutputKind.HEADPHONES, productName?.toString()?.takeIf { it.isNotBlank() })
            else -> OutputRoute(OutputKind.OTHER, productName?.toString()?.takeIf { it.isNotBlank() })
        }
    }
}

/**
 * The drone on the media stream (ADR 0005, #61): a [DroneSynth] rendered on its own thread into an [AudioTrack].
 * Silent until [play]. With [preferSpeaker] the track asks for the built-in loudspeaker even if headphones are
 * connected; otherwise Android routes it (headphones when connected). [route] tells where it actually goes.
 * [timbre]: the recorded B♭ tuba or the synthetic model (#72).
 */
class DroneTrack(
    context: Context,
    preferSpeaker: Boolean,
    timbre: DroneTimbre = DroneTimbre.RECORDED,
    val sampleRate: Int = SAMPLE_RATE
) : DroneControl,
    Closeable {

    private val synth = DroneSynth(sampleRate.toDouble(), DroneConfig(timbre = timbre))
    private val track = AudioTrack.Builder()
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        )
        .setAudioFormat(
            AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                .build()
        )
        .setBufferSizeInBytes(
            max(
                AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT),
                BUFFER_BLOCKS * block() * Float.SIZE_BYTES
            )
        )
        .setTransferMode(AudioTrack.MODE_STREAM)
        .build()

    @Volatile private var running = true
    private val thread = Thread(::renderLoop, "stimmzug-drone")

    init {
        if (preferSpeaker) {
            context.getSystemService(AudioManager::class.java)
                .getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                ?.let(track::setPreferredDevice)
        }
        track.play()
        thread.start()
    }

    /** Where the drone is played right now. */
    val route: OutputRoute
        get() = track.routedDevice.let { device ->
            OutputRoute.of(device?.type, device?.productName)
        }

    override fun play(frequencyHz: Double) = synth.play(frequencyHz)

    override fun stop() = synth.stop()

    override fun playConfirmation() = synth.playConfirmation()

    private fun renderLoop() {
        val buffer = FloatArray(block())
        while (running) {
            synth.render(buffer)
            if (track.write(buffer, 0, buffer.size, AudioTrack.WRITE_BLOCKING) < 0) break
        }
    }

    private fun block() = sampleRate / BLOCKS_PER_SECOND

    override fun close() {
        running = false
        thread.join(JOIN_TIMEOUT_MS)
        track.stop()
        track.release()
    }

    companion object {
        const val SAMPLE_RATE = 48_000

        /** 20 ms per block. */
        private const val BLOCKS_PER_SECOND = 50
        private const val BUFFER_BLOCKS = 4
        private const val JOIN_TIMEOUT_MS = 500L
    }
}
