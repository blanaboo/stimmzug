package io.github.blanaboo.stimmzug.dsp.music

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class InstrumentProfileTest {

    private val melton = InstrumentProfile(
        id = "f",
        name = "F-Tuba Melton Tradition 4250",
        key = TubaKey.F,
        maker = "Melton",
        model = "Tradition 4250",
        extraValves = listOf(ValveInterval.FOURTH, ValveInterval.LARGE_WHOLE_TONE),
        triggerValves = setOf(2)
    )

    @Test
    fun `valves 1 to 3 are fixed and extra valves follow in order`() {
        assertEquals(5, melton.valveCount)
        assertEquals(
            listOf(
                ValveInterval.WHOLE_TONE,
                ValveInterval.SEMITONE,
                ValveInterval.MINOR_THIRD,
                ValveInterval.FOURTH,
                ValveInterval.LARGE_WHOLE_TONE
            ),
            melton.valves
        )
        assertEquals(listOf(2, 1, 3, 5, 2), melton.valves.map { it.semitones })
    }

    @Test
    fun `pedal notes of the four keys`() {
        assertEquals("Subkontra-B", NoteNames.germanOctaveName(TubaKey.B_FLAT.pedalMidi))
        assertEquals("Kontra-F", NoteNames.germanOctaveName(TubaKey.F.pedalMidi))
        assertEquals("Kontra-Es", NoteNames.germanOctaveName(TubaKey.E_FLAT.pedalMidi))
        assertEquals("Kontra-C", NoteNames.germanOctaveName(TubaKey.C.pedalMidi))
    }

    @Test
    fun `default is a four-valve BBb tuba without extras`() {
        val profile = InstrumentProfile.default("x", "Meine Tuba")

        assertEquals(TubaKey.B_FLAT, profile.key)
        assertEquals(4, profile.valveCount)
        assertEquals(ValveInterval.FOURTH, profile.valves.last())
        assertEquals(false, profile.compensating || profile.mainSlideTrigger || profile.triggerValves.isNotEmpty())
    }

    @Test
    fun `proposals for extra valves`() {
        assertEquals(ValveInterval.FOURTH, InstrumentProfile.proposedInterval(4))
        assertEquals(ValveInterval.LARGE_WHOLE_TONE, InstrumentProfile.proposedInterval(5))
        assertNull(InstrumentProfile.proposedInterval(6))
    }

    @Test
    fun `invalid profiles are rejected`() {
        assertThrows<IllegalArgumentException> { melton.copy(name = " ") }
        assertThrows<IllegalArgumentException> { melton.copy(extraValves = List(4) { ValveInterval.SEMITONE }) }
        assertThrows<IllegalArgumentException> { melton.copy(triggerValves = setOf(6)) }
        assertThrows<IllegalArgumentException> { melton.copy(triggerValves = setOf(0)) }
        assertThrows<IllegalArgumentException> { melton.copy(extraValves = emptyList(), compensating = true) }
        assertEquals(true, melton.copy(compensating = true).compensating)
    }
}
