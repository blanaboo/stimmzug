package io.github.blanaboo.stimmzug.tools.replay

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem

/** Mono audio in -1..1. */
class AudioClip(val samples: FloatArray, val sampleRate: Double) {
    val durationSec: Double get() = samples.size / sampleRate
}

/**
 * Reads WAV and AIFF files (anything javax.sound understands) and decodes PCM itself, because the JDK converts
 * only some formats: signed/unsigned integer with 8–32 bits, 32/64-bit float, either byte order. Channels are
 * averaged to mono.
 */
object AudioFileReader {

    fun read(file: File): AudioClip {
        require(file.isFile) { "Datei nicht gefunden: $file" }
        AudioSystem.getAudioInputStream(file).use { stream ->
            return AudioClip(decode(stream.readAllBytes(), stream.format), stream.format.sampleRate.toDouble())
        }
    }

    fun decode(bytes: ByteArray, format: AudioFormat): FloatArray {
        val bytesPerSample = format.sampleSizeInBits / 8
        val channels = format.channels
        require(format.sampleSizeInBits % 8 == 0 && bytesPerSample in 1..8 && channels >= 1) {
            "Format nicht unterstützt: $format"
        }
        val frameSize = bytesPerSample * channels
        val frames = bytes.size / frameSize
        val buffer = ByteBuffer.wrap(
            bytes
        ).order(if (format.isBigEndian) ByteOrder.BIG_ENDIAN else ByteOrder.LITTLE_ENDIAN)
        val integer = format.encoding == AudioFormat.Encoding.PCM_SIGNED ||
            format.encoding == AudioFormat.Encoding.PCM_UNSIGNED
        require(!integer || bytesPerSample <= 4) { "Format nicht unterstützt (mehr als 32 Bit): $format" }
        val sample: (Int) -> Double = when (format.encoding) {
            AudioFormat.Encoding.PCM_SIGNED -> { offset -> signed(bytes, offset, bytesPerSample, format.isBigEndian) }

            AudioFormat.Encoding.PCM_UNSIGNED -> { offset ->
                unsigned(bytes, offset, bytesPerSample, format.isBigEndian) * 2.0 - 1.0
            }

            AudioFormat.Encoding.PCM_FLOAT -> when (bytesPerSample) {
                4 -> { offset -> buffer.getFloat(offset).toDouble() }
                8 -> { offset -> buffer.getDouble(offset) }
                else -> throw IllegalArgumentException("Format nicht unterstützt: $format")
            }

            else -> throw IllegalArgumentException("Format nicht unterstützt: $format")
        }
        return FloatArray(frames) { frame ->
            var sum = 0.0
            for (c in 0 until channels) sum += sample(frame * frameSize + c * bytesPerSample)
            (sum / channels).toFloat()
        }
    }

    /** Two's-complement integer of [size] bytes scaled to -1..1. */
    private fun signed(bytes: ByteArray, offset: Int, size: Int, bigEndian: Boolean): Double {
        var value = 0L
        for (i in 0 until size) {
            val b = bytes[offset + if (bigEndian) i else size - 1 - i].toLong() and 0xFF
            value = (value shl 8) or b
        }
        val bits = 8 * size
        if (value and (1L shl (bits - 1)) != 0L) value -= 1L shl bits
        return value.toDouble() / (1L shl (bits - 1))
    }

    /** Unsigned integer of [size] bytes scaled to 0..1. */
    private fun unsigned(bytes: ByteArray, offset: Int, size: Int, bigEndian: Boolean): Double {
        var value = 0L
        for (i in 0 until size) {
            val b = bytes[offset + if (bigEndian) i else size - 1 - i].toLong() and 0xFF
            value = (value shl 8) or b
        }
        return value.toDouble() / (1L shl (8 * size))
    }
}
