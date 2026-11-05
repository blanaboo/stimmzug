package io.github.blanaboo.stimmzug.lab

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WavWriterTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun writtenFileIsReadableAsMono16BitPcm() {
        val file = temp.newFile("take.wav")
        val first = shortArrayOf(0, 1, -1, Short.MAX_VALUE, Short.MIN_VALUE)
        val second = ShortArray(1000) { (it * 31).toShort() }
        WavWriter(file, 48_000).use { wav ->
            wav.write(first)
            wav.write(second, count = 600)
            assertEquals(605L, wav.samples)
        }

        AudioSystem.getAudioInputStream(file).use { stream ->
            val format = stream.format
            assertEquals(AudioFormat.Encoding.PCM_SIGNED, format.encoding)
            assertEquals(48_000f, format.sampleRate)
            assertEquals(16, format.sampleSizeInBits)
            assertEquals(1, format.channels)
            assertEquals(605L, stream.frameLength)
            val samples = ByteBuffer.wrap(stream.readAllBytes()).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
            val read = ShortArray(samples.remaining()).also { samples.get(it) }
            assertArrayEquals(first + second.copyOf(600), read)
        }
    }

    @Test
    fun emptyRecordingHasConsistentHeader() {
        val file = temp.newFile("empty.wav")
        WavWriter(file, 44_100).close()

        assertEquals(WavWriter.HEADER_BYTES.toLong(), file.length())
        val header = ByteBuffer.wrap(file.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(36, header.getInt(4))
        assertEquals(44_100, header.getInt(24))
        assertEquals(0, header.getInt(40))
    }

    @Test
    fun existingFileIsReplaced() {
        val file = temp.newFile("old.wav").apply { writeBytes(ByteArray(10_000) { 7 }) }
        WavWriter(file, 48_000).use { it.write(shortArrayOf(5, 6)) }

        assertEquals(WavWriter.HEADER_BYTES + 4L, File(file.path).length())
    }
}
