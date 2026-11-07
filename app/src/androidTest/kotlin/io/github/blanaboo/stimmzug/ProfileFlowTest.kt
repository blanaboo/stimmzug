package io.github.blanaboo.stimmzug

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.blanaboo.stimmzug.data.profile.ProfileRepository
import io.github.blanaboo.stimmzug.ui.home.TAG_HOME_PROFILES
import io.github.blanaboo.stimmzug.ui.profile.TAG_PROFILE_ACTIVE
import io.github.blanaboo.stimmzug.ui.profile.TAG_PROFILE_ADD
import io.github.blanaboo.stimmzug.ui.profile.TAG_WIZARD_MAKER
import io.github.blanaboo.stimmzug.ui.profile.TAG_WIZARD_MODEL
import io.github.blanaboo.stimmzug.ui.profile.TAG_WIZARD_NAME
import io.github.blanaboo.stimmzug.ui.profile.TAG_WIZARD_NEXT
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileFlowTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    /** Unique, so the test neither depends on nor disturbs profiles from other runs. */
    private val name = "Testtuba ${System.currentTimeMillis()}"

    @After
    fun removeTestProfile() {
        runBlocking {
            val repository = ProfileRepository(InstrumentationRegistry.getInstrumentation().targetContext)
            repository.state.first().profiles.filter { it.name == name }.forEach { repository.delete(it.id) }
        }
    }

    @Test
    fun ownersFTubaIsSetUpAndBecomesActive() {
        // The instrument card appears once the stored profiles are loaded.
        compose.waitUntil(TIMEOUT_MS) { exists(hasTestTag(TAG_HOME_PROFILES)) }
        compose.onNodeWithTag(TAG_HOME_PROFILES).performScrollTo().performClick()
        compose.onNodeWithTag(TAG_PROFILE_ADD).performClick()

        compose.onNodeWithText("F-Tuba").performClick()
        next()
        compose.onNodeWithTag(TAG_WIZARD_MAKER).performTextInput("Melton")
        compose.onNodeWithTag(TAG_WIZARD_MODEL).performTextInput("Tradition 4250")
        next()
        compose.onNodeWithText("6").performClick()
        // There is no common layout for a 6th valve: the player has to choose one before going on.
        compose.onNodeWithTag(TAG_WIZARD_NEXT).assertIsNotEnabled()
        compose.onNodeWithText("5").performClick()
        compose.onNodeWithTag(TAG_WIZARD_NEXT).assertIsEnabled()
        next()
        next() // compensation: no
        // "No trigger" is the answer until a slide is chosen (#46).
        compose.onNodeWithText("Kein Trigger").assertIsOn()
        compose.onNodeWithText("2. Ventilzug").performClick()
        compose.onNodeWithText("Kein Trigger").assertIsOff()
        next()
        compose.onNodeWithTag(TAG_WIZARD_NAME).assertTextContains("F-Tuba Melton Tradition 4250")
        compose.onNodeWithTag(TAG_WIZARD_NAME).performTextReplacement(name)
        next() // saves and returns to the list

        val savedAndActive = hasTestTag(TAG_PROFILE_ACTIVE) and hasAnyDescendant(hasText(name))
        compose.waitUntil(TIMEOUT_MS) { exists(savedAndActive) }
        compose.onNodeWithTag(TAG_PROFILE_ACTIVE)
            .assert(hasAnyDescendant(hasText("5 Ventile", substring = true)))
            .assert(hasAnyDescendant(hasText("Trigger: 2. Ventilzug", substring = true)))
    }

    private fun next() = compose.onNodeWithTag(TAG_WIZARD_NEXT).performClick()

    private fun exists(matcher: androidx.compose.ui.test.SemanticsMatcher) =
        compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
