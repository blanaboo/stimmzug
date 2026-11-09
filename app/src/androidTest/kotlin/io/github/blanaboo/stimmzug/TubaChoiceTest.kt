package io.github.blanaboo.stimmzug

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.blanaboo.stimmzug.data.settings.PracticeSettings
import io.github.blanaboo.stimmzug.dsp.exercise.Instrument
import io.github.blanaboo.stimmzug.dsp.music.InstrumentProfile
import io.github.blanaboo.stimmzug.dsp.music.TubaKey
import io.github.blanaboo.stimmzug.practice.ExerciseChoice
import io.github.blanaboo.stimmzug.ui.practice.PracticeSetupScreen
import io.github.blanaboo.stimmzug.ui.practice.SetupActions
import io.github.blanaboo.stimmzug.ui.practice.SetupState
import io.github.blanaboo.stimmzug.ui.practice.TAG_PRACTICE_TUBA
import io.github.blanaboo.stimmzug.ui.practice.TAG_PRACTICE_TUBA_ITEM
import io.github.blanaboo.stimmzug.ui.theme.StimmzugTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** „Heute mit: … · ändern“ on „Übung wählen“ (#44). */
@RunWith(AndroidJUnit4::class)
class TubaChoiceTest {

    @get:Rule
    val compose = createComposeRule()

    private val b = InstrumentProfile.default("b", "B-Tuba GR55")
    private val f = InstrumentProfile("f", "F-Tuba Melton", TubaKey.F)

    private fun show(profiles: List<InstrumentProfile>, actions: SetupActions) = compose.setContent {
        StimmzugTheme {
            PracticeSetupScreen(
                state = SetupState(
                    choice = ExerciseChoice(),
                    noteCount = 15,
                    settings = PracticeSettings(),
                    profile = profiles.firstOrNull(),
                    profiles = profiles,
                    range = Instrument.B_FLAT_TUBA.range
                ),
                actions = actions
            )
        }
    }

    @Test
    fun withSeveralTubasOneTapPicksAnother() {
        val chosen = ArrayList<String>()
        var managed = 0
        show(listOf(b, f), SetupActions(chooseProfile = { chosen += it }, openProfiles = { managed++ }))
        compose.onNodeWithText("B-Tuba GR55").assertIsDisplayed()
        compose.onNodeWithTag(TAG_PRACTICE_TUBA).performClick()
        compose.onNodeWithTag(TAG_PRACTICE_TUBA_ITEM + "f").performClick()
        assertEquals(listOf("f"), chosen)

        compose.onNodeWithTag(TAG_PRACTICE_TUBA).performClick()
        compose.onNodeWithText("Instrumente verwalten").performClick()
        assertEquals(1, managed)
    }

    @Test
    fun withOneTubaChangeOpensTheInstruments() {
        var managed = 0
        show(listOf(b), SetupActions(openProfiles = { managed++ }))
        compose.onNodeWithTag(TAG_PRACTICE_TUBA).performClick()
        assertEquals(1, managed)
    }
}
