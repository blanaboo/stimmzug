package io.github.blanaboo.stimmzug.tools.replay

import io.github.blanaboo.stimmzug.tools.replay.TestAudio.FS
import java.io.File
import java.nio.ByteOrder
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioFormat
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir

class AudioFileReaderTest {
    @TempDir
    lateinit var dir: File

    private val sine = FloatArray(4800) { (0.8 * sin(2 * PI * 110.0 * it / FS)).toFloat() }

    private fun maxError(a: FloatArray, b: FloatArray) = a.indices.maxOf { abs(a[it] - b[it]) }

    @Test
    fun `reads WAV and AIFF in common formats`() {
        val cases = listOf(
            Triple(16, 1, AudioFileFormat.Type.WAVE) to 1e-4,
            Triple(16, 2, AudioFileFormat.Type.WAVE) to 1e-4,
            Triple(24, 1, AudioFileFormat.Type.WAVE) to 1e-6,
            Triple(32, 1, AudioFileFormat.Type.WAVE) to 1e-6,
            Triple(8, 1, AudioFileFormat.Type.WAVE) to 1e-2,
            Triple(16, 1, AudioFileFormat.Type.AIFF) to 1e-4,
            Triple(24, 2, AudioFileFormat.Type.AIFF) to 1e-6
        )
        for ((case, tolerance) in cases) {
            val (bits, channels, type) = case
            val file = File(dir, "t-$bits-$channels.${type.extension}")
            TestAudio.write(file, sine, bits, channels, type)
            val clip = AudioFileReader.read(file)
            assertEquals(FS.toDouble(), clip.sampleRate, "$case")
            assertEquals(sine.size, clip.samples.size, "$case")
            assertEquals(0.1, clip.durationSec, 1e-9)
            assertTrue(maxError(sine, clip.samples) < tolerance, "$case: error ${maxError(sine, clip.samples)}")
        }
    }

    @Test
    fun `decodes float and mixes channels to mono`() {
        val values = floatArrayOf(0.5f, -0.25f, 1f, -1f)
        for (order in listOf(ByteOrder.LITTLE_ENDIAN, ByteOrder.BIG_ENDIAN)) {
            val format = AudioFormat(
                AudioFormat.Encoding.PCM_FLOAT,
                48_000f,
                32,
                1,
                4,
                48_000f,
                order == ByteOrder.BIG_ENDIAN
            )
            assertArrayEquals(values, AudioFileReader.decode(TestAudio.floatBytes(values, order), format))
        }
        // Stereo: left 0.5, right -0.25 -> 0.125.
        val stereo = AudioFormat(AudioFormat.Encoding.PCM_FLOAT, 48_000f, 32, 2, 8, 48_000f, false)
        assertArrayEquals(
            floatArrayOf(0.125f),
            AudioFileReader.decode(TestAudio.floatBytes(floatArrayOf(0.5f, -0.25f), ByteOrder.LITTLE_ENDIAN), stereo)
        )
    }

    @Test
    fun `rejects missing files and unsupported formats`() {
        assertThrows<IllegalArgumentException> { AudioFileReader.read(File(dir, "fehlt.wav")) }
        val ulaw = AudioFormat(AudioFormat.Encoding.ULAW, 8000f, 8, 1, 1, 8000f, false)
        assertThrows<IllegalArgumentException> { AudioFileReader.decode(ByteArray(4), ulaw) }
        val long = AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 8000f, 64, 1, 8, 8000f, false)
        assertThrows<IllegalArgumentException> { AudioFileReader.decode(ByteArray(16), long) }
    }
}
