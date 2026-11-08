package io.github.blanaboo.stimmzug.audio

import android.media.AudioDeviceInfo
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioIoTest {

    /** A source that returns [value] in every sample, or nothing at all. */
    private class FakePcm(private val value: Short, private val fails: Boolean = false) : PcmSource {
        var closed = false
        var reads = 0

        override fun read(buffer: ShortArray): Int {
            reads++
            if (fails) return -1
            buffer.fill(value)
            return buffer.size
        }

        override fun close() {
            closed = true
        }
    }

    /** Opens what [sources] holds per source; records the order of the attempts. */
    private class FakeOpener(private val sources: Map<MicSource, PcmSource?>) : RecordOpener {
        val tried = ArrayList<MicSource>()

        override fun open(source: MicSource): PcmSource? {
            tried += source
            return sources[source]
        }
    }

    @Test
    fun unprocessedIsUsedWhenItOpensAndDeliversSound() {
        val opener = FakeOpener(mapOf(MicSource.UNPROCESSED to FakePcm(3), MicSource.VOICE_RECOGNITION to FakePcm(9)))
        val mic = openMic(opener, probeSize = 100)
        assertEquals(MicSource.UNPROCESSED, mic.source)
        assertEquals(listOf(MicSource.UNPROCESSED), opener.tried)
        assertEquals(100, mic.probeCount)
        assertEquals(3, mic.probe[0].toInt())
    }

    @Test
    fun voiceRecognitionIsTheFallbackWhenUnprocessedDoesNotOpen() {
        val opener = FakeOpener(mapOf(MicSource.UNPROCESSED to null, MicSource.VOICE_RECOGNITION to FakePcm(9)))
        assertEquals(MicSource.VOICE_RECOGNITION, openMic(opener, probeSize = 100).source)
        assertEquals(listOf(MicSource.UNPROCESSED, MicSource.VOICE_RECOGNITION), opener.tried)
    }

    @Test
    fun digitalSilenceCountsAsNotWorking() {
        val silent = FakePcm(0)
        val opener = FakeOpener(mapOf(MicSource.UNPROCESSED to silent, MicSource.VOICE_RECOGNITION to FakePcm(9)))
        assertEquals(MicSource.VOICE_RECOGNITION, openMic(opener, probeSize = 100).source)
        assertTrue("silent source closed", silent.closed)

        // The last source is kept even if silent: a quiet room must not stop the practice.
        val bothSilent =
            FakeOpener(mapOf(MicSource.UNPROCESSED to FakePcm(0), MicSource.VOICE_RECOGNITION to FakePcm(0)))
        assertEquals(MicSource.VOICE_RECOGNITION, openMic(bothSilent, probeSize = 100).source)
    }

    @Test
    fun noMicrophoneAtAllIsAnError() {
        val opener = FakeOpener(emptyMap())
        assertThrows(IllegalStateException::class.java) { openMic(opener, probeSize = 100) }
    }

    @Test
    fun micInputDeliversFloatsOnItsThreadAndStops() {
        val opener = FakeOpener(mapOf(MicSource.UNPROCESSED to FakePcm(16_384)))
        val input = MicInput(opener, sampleRate = 4_800)
        val chunks = CountDownLatch(5)
        var first = Float.NaN
        var sizes = 0
        input.start(
            onChunk = { samples, count ->
                if (first.isNaN()) first = samples[0]
                sizes = count
                chunks.countDown()
            },
            onError = { throw AssertionError(it) }
        )
        assertTrue(chunks.await(2, TimeUnit.SECONDS))
        input.close()
        assertEquals(0.5f, first, 1e-6f)
        assertEquals(48, sizes)
        assertEquals("UNPROCESSED", input.sourceName)
    }

    @Test
    fun aBrokenInputReportsAnError() {
        // Delivers sound once (passes the probe), then fails.
        val failing = RecordOpener {
            object : PcmSource {
                var calls = 0
                override fun read(buffer: ShortArray): Int = if (calls++ == 0) {
                    buffer.fill(5)
                    buffer.size
                } else {
                    -3
                }

                override fun close() = Unit
            }
        }
        val error = CountDownLatch(1)
        var message: String? = null
        MicInput(failing, sampleRate = 4_800).apply {
            start(onChunk = { _, _ -> }, onError = {
                message = it.message
                error.countDown()
            })
            assertTrue(error.await(2, TimeUnit.SECONDS))
            close()
        }
        assertTrue(message!!.contains("keine Daten"))
    }

    @Test
    fun outputRoutesAreNamedForThePlayer() {
        assertEquals(OutputRoute(OutputKind.SPEAKER), OutputRoute.of(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, "Nord 2"))
        assertEquals(
            OutputRoute(OutputKind.HEADPHONES, "Bose QC Earbuds II"),
            OutputRoute.of(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, "Bose QC Earbuds II")
        )
        assertEquals(OutputKind.HEADPHONES, OutputRoute.of(26, null).kind)
        assertNull(OutputRoute.of(AudioDeviceInfo.TYPE_WIRED_HEADPHONES, " ").name)
        assertEquals(OutputKind.OTHER, OutputRoute.of(AudioDeviceInfo.TYPE_USB_DEVICE, "DAC").kind)
        assertEquals(OutputRoute.UNKNOWN, OutputRoute.of(null, null))
    }
}
