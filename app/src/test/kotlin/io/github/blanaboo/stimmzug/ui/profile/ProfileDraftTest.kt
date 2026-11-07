package io.github.blanaboo.stimmzug.ui.profile

import io.github.blanaboo.stimmzug.dsp.music.InstrumentProfile
import io.github.blanaboo.stimmzug.dsp.music.TubaKey
import io.github.blanaboo.stimmzug.dsp.music.ValveInterval
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileDraftTest {

    private val draft = ProfileDraft(id = "x")

    @Test
    fun compensationIsOnlyAskedWithFourOrMoreValves() {
        assertEquals(WizardStep.entries, draft.steps)
        val three = draft.withValveCount(3)
        assertFalse(WizardStep.COMPENSATION in three.steps)
        assertEquals(WizardStep.TRIGGERS, three.next(WizardStep.VALVES))
        assertEquals(WizardStep.VALVES, three.previous(WizardStep.TRIGGERS))
        assertNull(three.next(WizardStep.NAME))
        assertNull(three.previous(WizardStep.KEY))
    }

    @Test
    fun moreValvesGetProposalsAndKeepEarlierChoices() {
        val five = draft.withExtraValve(4, ValveInterval.MAJOR_THIRD).withValveCount(5)
        assertEquals(listOf(ValveInterval.MAJOR_THIRD, ValveInterval.LARGE_WHOLE_TONE), five.extraValves)

        val six = five.withValveCount(6)
        assertNull(six.extraValves.last())
        assertFalse(six.isComplete(WizardStep.VALVES, "x"))
        assertTrue(six.withExtraValve(6, ValveInterval.SEMITONE).isComplete(WizardStep.VALVES, "x"))
    }

    @Test
    fun fewerValvesDropTriggersAndCompensation() {
        val five = draft.withValveCount(5).copy(compensating = true).toggleTrigger(1).toggleTrigger(5)

        val three = five.withValveCount(3)
        assertEquals(setOf(1), three.triggerValves)
        assertFalse(three.compensating)
        assertEquals(emptySet<Int>(), three.toggleTrigger(1).triggerValves)
    }

    @Test
    fun theOwnersFTubaBecomesAProfile() {
        val melton = draft.copy(key = TubaKey.F, maker = " Melton ", model = "Tradition 4250")
            .withValveCount(5)
            .toggleTrigger(2)
        val suggestion = ProfileDraft.suggestName("F-Tuba", melton.maker, melton.model)
        assertEquals("F-Tuba Melton Tradition 4250", suggestion)

        val profile = melton.toProfile(suggestion)
        assertEquals("F-Tuba Melton Tradition 4250", profile.name)
        assertEquals("Melton", profile.maker)
        assertEquals(5, profile.valveCount)
        assertEquals(setOf(2), profile.triggerValves)
        assertEquals(ProfileDraft.from(profile).copy(maker = " Melton "), melton.copy(name = profile.name))
    }

    @Test
    fun anEditedNameWinsOverTheSuggestion() {
        assertEquals("B-Tuba", draft.nameOr("B-Tuba"))
        val named = draft.copy(name = "Meine Große")
        assertEquals("Meine Große", named.toProfile("B-Tuba").name)
        assertFalse(draft.copy(name = " ").isComplete(WizardStep.NAME, "B-Tuba"))
        assertEquals("B-Tuba", ProfileDraft.suggestName("B-Tuba", "", " "))
    }

    @Test
    fun makerSuggestionsFollowTheTypedPrefix() {
        assertEquals(listOf("Meinl Weston", "Melton"), ProfileDraft.makerSuggestions("me"))
        assertEquals(emptyList<String>(), ProfileDraft.makerSuggestions("Melton"))
        assertEquals(MAKER_SUGGESTIONS, ProfileDraft.makerSuggestions(""))
    }

    @Test(expected = IllegalArgumentException::class)
    fun valveCountOutsideTheRangeIsRejected() {
        draft.withValveCount(InstrumentProfile.MAX_VALVES + 1)
    }
}
