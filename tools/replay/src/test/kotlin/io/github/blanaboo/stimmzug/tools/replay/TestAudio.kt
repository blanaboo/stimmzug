package io.github.blanaboo.stimmzug.tools.replay

import io.github.blanaboo.stimmzug.dsp.music.NoteMath
import io.github.blanaboo.stimmzug.dsp.synth.ToneSpec
import io.github.blanaboo.stimmzug.dsp.synth.TubaToneGenerator
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import kotlin.math.roundToLong

/** Helpers to write test recordings. */
object TestAudio {
    const val FS = 48_000

    /** Drone bleed + phone-mic noise all along; the tuba enters at [tubaStartSec] (null = drone only). */
    fun scene(midi: Int, tubaHz: Double?, tubaStartSec: Double = 2.0, toneSec: Double = 5.0): FloatArray =
        TubaToneGenerator.render(
            ToneSpec(
                frequencyHz = tubaHz ?: NoteMath.frequency(midi),
                leadInSec = tubaStartSec,
                toneSec = toneSec,
                releaseSec = 0.0,
                tonePeakDbfs = if (tubaHz == null) null else -12.0,
                jitterCents = 1.0,
                micHighPassHz = 120.0,
                noiseRmsDbfs = -55.0,
                bleedFrequencyHz = NoteMath.frequency(midi),
                bleedPeakDbfs = -24.0
            )
        )

    /** Writes integer PCM ([bits] = 8 unsigned, 16/24/32 signed) with [channels] identical channels. */
    fun write(
        file: File,
        samples: FloatArray,
        bits: Int = 16,
        channels: Int = 1,
        type: AudioFileFormat.Type = AudioFileFormat.Type.WAVE,
        bigEndian: Boolean = type == AudioFileFormat.Type.AIFF
    ) {
        val bytesPerSample = bits / 8
        val signed = bits > 8
        val format = AudioFormat(FS.toFloat(), bits, channels, signed, bigEndian)
        val buffer = ByteBuffer.allocate(samples.size * bytesPerSample * channels)
        for (s in samples) {
            // Same scaling as the reader (and most decoders): 2^(bits-1), clamped to the largest positive value.
            val scale = 1L shl (bits - 1)
            var v = (s.toDouble() * scale).roundToLong().coerceIn(-scale, scale - 1)
            if (!signed) v += 128
            repeat(channels) {
                for (b in 0 until bytesPerSample) {
                    val shift = 8 * if (bigEndian) bytesPerSample - 1 - b else b
                    buffer.put(((v shr shift) and 0xFF).toByte())
                }
            }
        }
        val stream = AudioInputStream(ByteArrayInputStream(buffer.array()), format, samples.size.toLong())
        AudioSystem.write(stream, type, file)
    }

    fun floatBytes(samples: FloatArray, order: ByteOrder): ByteArray {
        val buffer = ByteBuffer.allocate(samples.size * 4).order(order)
        samples.forEach { buffer.putFloat(it) }
        return buffer.array()
    }
}
