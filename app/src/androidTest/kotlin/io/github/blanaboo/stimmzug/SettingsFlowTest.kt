package io.github.blanaboo.stimmzug

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.blanaboo.stimmzug.data.settings.DisplayMode
import io.github.blanaboo.stimmzug.data.settings.PracticeSettings
import io.github.blanaboo.stimmzug.data.settings.SettingsRepository
import io.github.blanaboo.stimmzug.dsp.music.NoteMath
import io.github.blanaboo.stimmzug.ui.home.TAG_HOME_SETTINGS
import io.github.blanaboo.stimmzug.ui.home.TAG_REFERENCE_NOTE
import io.github.blanaboo.stimmzug.ui.settings.TAG_SETTINGS_A4
import io.github.blanaboo.stimmzug.ui.settings.TAG_SETTINGS_HOLD
import io.github.blanaboo.stimmzug.ui.settings.TAG_SETTINGS_HOLD_VALUE
import io.github.blanaboo.stimmzug.ui.settings.TAG_SETTINGS_RESET
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsFlowTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val repository = SettingsRepository(InstrumentationRegistry.getInstrumentation().targetContext)
    private lateinit var before: PracticeSettings

    /** Starts from the defaults and puts the player's settings back afterwards. */
    @Before
    fun startFromDefaults() = runBlocking {
        before = repository.settings.first()
        repository.reset()
    }

    @After
    fun restoreSettings() = runBlocking {
        repository.update { before }
    }

    private fun stored(): PracticeSettings = runBlocking { repository.settings.first() }

    private fun waitForStored(check: (PracticeSettings) -> Boolean) = compose.waitUntil(TIMEOUT_MS) { check(stored()) }

    @Test
    fun holdTimeDisplayAndConcertPitchAreStoredAndReset() {
        compose.waitUntil(TIMEOUT_MS) { exists(TAG_HOME_SETTINGS) }
        compose.onNodeWithTag(TAG_REFERENCE_NOTE).assertTextContains(hz(442), substring = true)
        compose.onNodeWithTag(TAG_HOME_SETTINGS).performScrollTo().performClick()

        compose.waitUntil(TIMEOUT_MS) { exists(TAG_SETTINGS_HOLD) }
        compose.onNodeWithTag(TAG_SETTINGS_HOLD_VALUE).assertTextEquals("5 s")
        compose.onNodeWithTag(TAG_SETTINGS_HOLD).performSemanticsAction(SemanticsActions.SetProgress) { it(7f) }
        compose.onNodeWithTag(TAG_SETTINGS_HOLD_VALUE).assertTextEquals("7 s")
        waitForStored { it.holdSec == 7 }

        compose.onNodeWithText("Stimmgerät").performScrollTo().performClick()
        waitForStored { it.display == DisplayMode.TUNER }

        compose.onNodeWithTag(TAG_SETTINGS_A4).performSemanticsAction(SemanticsActions.SetProgress) { it(440f) }
        waitForStored { it.a4Hz == 440 }
        compose.onNodeWithContentDescription("Zurück").performClick()
        compose.waitUntil(TIMEOUT_MS) { exists(TAG_REFERENCE_NOTE) }
        compose.onNodeWithTag(TAG_REFERENCE_NOTE).assertTextContains(hz(440), substring = true)

        compose.onNodeWithTag(TAG_HOME_SETTINGS).performScrollTo().performClick()
        compose.waitUntil(TIMEOUT_MS) { exists(TAG_SETTINGS_RESET) }
        compose.onNodeWithTag(TAG_SETTINGS_RESET).performScrollTo().performClick()
        waitForStored { it == PracticeSettings() }
        compose.onNodeWithTag(TAG_SETTINGS_HOLD_VALUE).assertTextEquals("5 s")
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    /** Kontra-B as the start screen prints it. */
    private fun hz(a4: Int) = String.format(Locale.getDefault(), "%.2f", NoteMath.frequency(KONTRA_B, a4.toDouble()))

    private companion object {
        const val TIMEOUT_MS = 10_000L
        const val KONTRA_B = 34
    }
}
