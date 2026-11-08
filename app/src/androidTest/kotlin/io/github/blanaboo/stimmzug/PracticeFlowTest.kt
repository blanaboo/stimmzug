package io.github.blanaboo.stimmzug

import android.Manifest
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.blanaboo.stimmzug.data.settings.DisplayMode
import io.github.blanaboo.stimmzug.data.settings.PracticeSettings
import io.github.blanaboo.stimmzug.data.settings.SettingsRepository
import io.github.blanaboo.stimmzug.dsp.music.NoteNames
import io.github.blanaboo.stimmzug.practice.ExerciseKind
import io.github.blanaboo.stimmzug.practice.PracticeInputs
import io.github.blanaboo.stimmzug.practice.PracticeRecorder
import io.github.blanaboo.stimmzug.practice.RecordingInput
import io.github.blanaboo.stimmzug.practice.VirtualTubistInput
import io.github.blanaboo.stimmzug.ui.home.TAG_HOME_PRACTICE
import io.github.blanaboo.stimmzug.ui.practice.TAG_PRACTICE_HIGH
import io.github.blanaboo.stimmzug.ui.practice.TAG_PRACTICE_KIND
import io.github.blanaboo.stimmzug.ui.practice.TAG_PRACTICE_LOW
import io.github.blanaboo.stimmzug.ui.practice.TAG_PRACTICE_NOTE
import io.github.blanaboo.stimmzug.ui.practice.TAG_PRACTICE_START
import io.github.blanaboo.stimmzug.ui.practice.TAG_SUMMARY_TITLE
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * E2E (#67): a short exercise from "Übung wählen" to the summary, played by the virtual tubist instead of the
 * microphone, with the real drone and the real session.
 */
@RunWith(AndroidJUnit4::class)
class PracticeFlowTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val settings = SettingsRepository(context)
    private lateinit var before: PracticeSettings

    @Before
    fun virtualTubistAndShortHold() = runBlocking {
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .grantRuntimePermission(context.packageName, Manifest.permission.RECORD_AUDIO)
        PracticeInputs.override = { snapshot -> VirtualTubistInput(snapshot) }
        before = settings.settings.first()
        settings.update { PracticeSettings(holdSec = 2) }
    }

    @After
    fun restore() = runBlocking {
        PracticeInputs.override = null
        settings.update { before }
    }

    @Test
    fun twoChromaticNotesAreHitAndSummarized() {
        compose.waitUntil(TIMEOUT_MS) { exists(TAG_HOME_PRACTICE) }
        compose.onNodeWithTag(TAG_HOME_PRACTICE).performScrollTo().performClick()

        compose.waitUntil(TIMEOUT_MS) { exists(TAG_PRACTICE_START) }
        screenshot("1-uebung-waehlen")
        compose.onNodeWithTag(TAG_PRACTICE_KIND + ExerciseKind.CHROMATIC.name).performClick()
        pick(TAG_PRACTICE_LOW, GROSSES_F)
        pick(TAG_PRACTICE_HIGH, GROSSES_GES)
        compose.onNodeWithTag(TAG_PRACTICE_START).performScrollTo().performClick()

        // Calibration 3 s, then two notes with pre-listen, glide, 2 s hold, confirmation and breathing pause.
        if (screenshots) timedScreenshots()
        compose.waitUntil(EXERCISE_TIMEOUT_MS) { exists(TAG_SUMMARY_TITLE) }
        compose.onNodeWithTag(TAG_SUMMARY_TITLE).assertTextEquals("2 von 2 sauber")
        screenshot("7-zusammenfassung")
        assertRecorded()
    }

    /** Debug builds record every session (#75). */
    private fun assertRecorded() {
        val recorder = PracticeInputs.tap as PracticeRecorder
        fun session() = recorder.recordings().lastOrNull()?.let { File(it, RecordingInput.SESSION_FILE) }
        // The view model closes the input right after the summary appears; the recording is complete after that.
        compose.waitUntil(TIMEOUT_MS) { session()?.takeIf { it.exists() }?.readText()?.contains("Ende:") == true }
        val folder = checkNotNull(recorder.recordings().lastOrNull())
        val text = File(folder, RecordingInput.SESSION_FILE).readText()
        assertTrue(text, text.contains("Eingang: VIRTUAL_TUBIST") && text.contains("Ergebnis: 2 von 2 sauber"))
        assertTrue(File(folder, RecordingInput.TIMELINE_FILE).readLines().any { it.contains(",HIT,") })
        val audioSec = (File(folder, RecordingInput.AUDIO_FILE).length() - WAV_HEADER) / (2.0 * SAMPLE_RATE)
        assertTrue("$audioSec s of audio", audioSec > MIN_AUDIO_SEC)
    }

    /** Screenshots for looking at the screens, only with the runner argument `screenshots=true`. */
    private val screenshots = InstrumentationRegistry.getArguments().getString("screenshots") == "true"

    /**
     * While the exercise runs the screen updates 100 times a second and the Compose test never sees it idle, so
     * the moments are taken from the session's fixed time line instead: calibration 0–3 s, pre-listen 3–4 s, the
     * virtual tubist in tolerance from about 4.8 s, hit at about 6.8 s, second note in tolerance from about 11.6 s.
     */
    private fun timedScreenshots() {
        val start = SystemClock.elapsedRealtime()

        // waitUntil advances the test clock frame by frame, so the screen keeps drawing; a sleep would freeze it.
        fun at(seconds: Double) = compose.waitUntil(EXERCISE_TIMEOUT_MS) {
            SystemClock.elapsedRealtime() >= start + (seconds * MILLIS_PER_SECOND).toLong()
        }
        at(1.5)
        screenshot("2-kalibrierung")
        at(3.5)
        screenshot("3-vorhoeren")
        at(6.0)
        screenshot("4-ohr-modus")
        at(8.0)
        screenshot("5-treffer")
        runBlocking { settings.update { it.copy(display = DisplayMode.TUNER) } }
        at(12.6)
        screenshot("6-stimmgeraet")
    }

    private fun screenshot(name: String) {
        if (!screenshots) return
        // Let the screen draw what the composition already shows.
        val drawn = SystemClock.elapsedRealtime() + DRAW_MS
        compose.waitUntil(TIMEOUT_MS) { SystemClock.elapsedRealtime() >= drawn }
        val dir = File(context.getExternalFilesDir(null), "screens").apply { mkdirs() }
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        FileOutputStream(File(dir, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it) }
    }

    private fun pick(picker: String, midi: Int) {
        compose.onNodeWithTag(picker).performScrollTo().performClick()
        compose.waitUntil(TIMEOUT_MS) { exists(TAG_PRACTICE_NOTE + midi) }
        // Let the menu finish opening, or the click lands where the item was during the animation.
        compose.waitForIdle()
        compose.onNodeWithTag(TAG_PRACTICE_NOTE + midi).performScrollTo()
        compose.waitForIdle()
        compose.onNodeWithTag(TAG_PRACTICE_NOTE + midi).performClick()
        compose.waitUntil(TIMEOUT_MS) { !exists(TAG_PRACTICE_NOTE + midi) }
        compose.onNodeWithTag(picker).assertTextContains(NoteNames.germanOctaveName(midi))
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    private companion object {
        const val TIMEOUT_MS = 10_000L
        const val EXERCISE_TIMEOUT_MS = 60_000L
        const val GROSSES_F = 41
        const val GROSSES_GES = 42
        const val PNG_QUALITY = 100
        const val MILLIS_PER_SECOND = 1000
        const val DRAW_MS = 300L
        const val WAV_HEADER = 44
        const val SAMPLE_RATE = 48_000

        /** Calibration 3 s plus two notes of at least 5 s each. */
        const val MIN_AUDIO_SEC = 10.0
    }
}
