package io.github.blanaboo.stimmzug.lab

import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Streams 16-bit PCM mono into a WAV file. The header is written first with empty sizes and completed on [close],
 * so a recording of any length never sits in memory.
 */
class WavWriter(file: File, private val sampleRate: Int) : Closeable {
    private val out = RandomAccessFile(file, "rw")
    private var buffer = ByteBuffer.allocate(0)

    /** Number of samples written so far. */
    var samples: Long = 0
        private set

    init {
        require(sampleRate > 0) { "sample rate must be positive, was $sampleRate" }
        out.setLength(0)
        out.write(header(sampleRate, 0))
    }

    fun write(data: ShortArray, count: Int = data.size) {
        require(count in 0..data.size) { "invalid count $count" }
        if (buffer.capacity() < count * BYTES_PER_SAMPLE) {
            buffer = ByteBuffer.allocate(count * BYTES_PER_SAMPLE).order(ByteOrder.LITTLE_ENDIAN)
        }
        buffer.clear()
        for (i in 0 until count) buffer.putShort(data[i])
        out.write(buffer.array(), 0, count * BYTES_PER_SAMPLE)
        samples += count
    }

    /** Writes the header for the samples so far, so the file stays readable if [close] never comes. */
    fun sync() {
        val end = out.filePointer
        out.seek(0)
        out.write(header(sampleRate, samples * BYTES_PER_SAMPLE))
        out.seek(end)
    }

    override fun close() {
        out.use {
            it.seek(0)
            it.write(header(sampleRate, samples * BYTES_PER_SAMPLE))
        }
    }

    companion object {
        private const val BYTES_PER_SAMPLE = 2
        const val HEADER_BYTES = 44

        /** Canonical 44-byte RIFF/WAVE header for 16-bit PCM mono. */
        fun header(sampleRate: Int, dataBytes: Long): ByteArray {
            require(dataBytes in 0..UInt.MAX_VALUE.toLong() - HEADER_BYTES) { "WAV data too large: $dataBytes" }
            return ByteBuffer.allocate(HEADER_BYTES).order(ByteOrder.LITTLE_ENDIAN).apply {
                put("RIFF".toByteArray(Charsets.US_ASCII))
                putInt((HEADER_BYTES - 8 + dataBytes).toInt())
                put("WAVE".toByteArray(Charsets.US_ASCII))
                put("fmt ".toByteArray(Charsets.US_ASCII))
                putInt(16) // size of the fmt chunk
                putShort(1) // PCM
                putShort(1) // mono
                putInt(sampleRate)
                putInt(sampleRate * BYTES_PER_SAMPLE) // byte rate
                putShort(BYTES_PER_SAMPLE.toShort()) // block align
                putShort((8 * BYTES_PER_SAMPLE).toShort())
                put("data".toByteArray(Charsets.US_ASCII))
                putInt(dataBytes.toInt())
            }.array()
        }
    }
}
