package io.github.blanaboo.stimmzug

import android.Manifest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.blanaboo.stimmzug.audio.DroneTrack
import io.github.blanaboo.stimmzug.audio.MicInput
import io.github.blanaboo.stimmzug.audio.MicSource
import io.github.blanaboo.stimmzug.audio.OutputKind
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The real audio stack of the device (#61): the microphone opens with a fallback, the drone track plays. */
@RunWith(AndroidJUnit4::class)
class AudioIoDeviceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun grantMicrophone() {
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .grantRuntimePermission(context.packageName, Manifest.permission.RECORD_AUDIO)
    }

    @Test
    fun microphoneDeliversAudioFromOneOfTheSources() {
        val samples = AtomicInteger()
        val second = CountDownLatch(1)
        var failure: Exception? = null
        MicInput(context).use { mic ->
            mic.start(
                onChunk = { _, count -> if (samples.addAndGet(count) >= mic.sampleRate) second.countDown() },
                onError = {
                    failure = it
                    second.countDown()
                }
            )
            assertTrue("no second of audio within 5 s", second.await(5, TimeUnit.SECONDS))
            assertTrue("input failed: $failure", failure == null)
            assertTrue("source ${mic.sourceName}", mic.sourceName in MicSource.entries.map { it.name })
        }
    }

    @Test
    fun droneTrackPlaysAndReportsItsRoute() {
        DroneTrack(context, preferSpeaker = true).use { drone ->
            drone.play(110.5)
            Thread.sleep(300)
            assertNotEquals(OutputKind.UNKNOWN, drone.route.kind)
            drone.playConfirmation()
            drone.stop()
        }
    }
}
