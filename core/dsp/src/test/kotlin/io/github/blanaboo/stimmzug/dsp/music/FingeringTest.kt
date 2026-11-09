package io.github.blanaboo.stimmzug.dsp.music

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class FingeringTest {

    /** "partial:valves" per German note name, e.g. "großes C" to "3:4". */
    private fun chart(profile: InstrumentProfile, notes: IntRange): Map<String, String> = notes.associate { midi ->
        val f = Fingering.standard(profile, midi)
        NoteNames.germanOctaveName(midi) to (f?.let { "${it.partial}:${it.valveText}" } ?: "-")
    }

    @Test
    fun `B-flat tuba with four valves follows the usual chart`() {
        val bb = InstrumentProfile.default("b", "B-Tuba")
        val expected = mapOf(
            "Subkontra-B" to "1:0", "Kontra-C" to "2:1+3+4", "Kontra-Es" to "2:1+4", "Kontra-E" to "2:2+4",
            "Kontra-F" to "2:4", "Kontra-G" to "2:1+2", "Kontra-A" to "2:2", "Kontra-B" to "2:0",
            "Kontra-H" to "3:2+4", "großes C" to "3:4", "großes D" to "3:1+2", "großes Es" to "3:1",
            "großes E" to "3:2", "großes F" to "3:0", "großes Ges" to "4:2+3", "großes G" to "4:1+2",
            "großes As" to "4:1", "großes A" to "4:2", "großes B" to "4:0", "kleines c" to "5:1",
            "kleines d" to "5:0", "kleines es" to "6:1", "kleines f" to "6:0", "kleines g" to "8:1+2",
            "kleines as" to "8:1", "kleines a" to "8:2", "kleines b" to "8:0"
        )
        val chart = chart(bb, 22..58)
        for ((note, fingering) in expected) assertEquals(fingering, chart[note], note)
    }

    @Test
    fun `three valves fall back to 1+3 and 1+2+3`() {
        val three = InstrumentProfile("3", "Drei Ventile", TubaKey.B_FLAT, extraValves = emptyList())
        assertEquals(Fingering(3, listOf(1, 3)), Fingering.standard(three, 36))
        assertEquals(Fingering(3, listOf(1, 2, 3)), Fingering.standard(three, 35))
        assertEquals(Fingering(3, listOf(1, 2)), Fingering.standard(three, 38))
        // Below Kontra-E (2nd natural tone minus 1+2+3) nothing is left but the pedal itself.
        assertNull(Fingering.standard(three, 27))
        assertEquals(Fingering(1, emptyList()), Fingering.standard(three, 22))
    }

    @Test
    fun `F tuba with five valves`() {
        val f = InstrumentProfile(
            "f",
            "F-Tuba",
            TubaKey.F,
            extraValves = listOf(ValveInterval.FOURTH, ValveInterval.LARGE_WHOLE_TONE)
        )
        assertEquals("2:4", Fingering.standard(f, 36)!!.let { "${it.partial}:${it.valveText}" })
        assertEquals("2:1+4", Fingering.standard(f, 34)!!.let { "${it.partial}:${it.valveText}" })
        assertEquals("3:1", Fingering.standard(f, 46)!!.let { "${it.partial}:${it.valveText}" })
        assertEquals("3:4", Fingering.standard(f, 43)!!.let { "${it.partial}:${it.valveText}" })
        assertEquals("4:1+2", Fingering.standard(f, 50)!!.let { "${it.partial}:${it.valveText}" })
        assertEquals("0", Fingering.standard(f, 41)!!.valveText)
    }
}
