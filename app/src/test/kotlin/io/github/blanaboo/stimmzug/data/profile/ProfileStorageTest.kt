package io.github.blanaboo.stimmzug.data.profile

import io.github.blanaboo.stimmzug.data.MemoryStore
import io.github.blanaboo.stimmzug.dsp.music.InstrumentProfile
import io.github.blanaboo.stimmzug.dsp.music.TubaKey
import io.github.blanaboo.stimmzug.dsp.music.ValveInterval
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileStorageTest {

    private val gr55 = InstrumentProfile(id = "b", name = "B-Tuba B&S GR55", key = TubaKey.B_FLAT, maker = "B&S")
    private val melton = InstrumentProfile(
        id = "f",
        name = "F-Tuba Melton Tradition 4250",
        key = TubaKey.F,
        maker = "Melton",
        model = "Tradition 4250",
        extraValves = listOf(ValveInterval.FOURTH, ValveInterval.LARGE_WHOLE_TONE),
        compensating = true,
        triggerValves = setOf(2),
        mainSlideTrigger = true
    )

    @Test
    fun codecRoundTripKeepsEveryField() {
        val state = ProfileState(listOf(gr55, melton), activeId = "f")

        val text = ProfileCodec.encode(state)
        assertTrue(text.contains("\"version\":${ProfileCodec.VERSION}"))
        assertEquals(state, ProfileCodec.decode(text))
    }

    @Test
    fun brokenOrUnknownDataDoesNotCrash() {
        assertEquals(ProfileState(), ProfileCodec.decode(null))
        assertEquals(ProfileState(), ProfileCodec.decode("not json"))
        assertEquals(ProfileState(), ProfileCodec.decode("{\"profiles\":[]}"))
        val unknownKey = ProfileCodec.encode(ProfileState(listOf(gr55, melton))).replace("\"F\"", "\"G\"")
        assertEquals(listOf(gr55), ProfileCodec.decode(unknownKey).profiles)
        val newerField = "{\"version\":2,\"profiles\":[{\"id\":\"b\",\"name\":\"B\",\"key\":\"B_FLAT\",\"bore\":20}]}"
        assertEquals(TubaKey.B_FLAT, ProfileCodec.decode(newerField).active?.key)
    }

    @Test
    fun activeProfileFallsBackToTheFirst() {
        assertEquals(gr55, ProfileState(listOf(gr55, melton), activeId = "gone").active)
        assertEquals(null, ProfileState().active)
    }

    @Test
    fun repositorySavesSelectsAndDeletes() = runTest {
        val repository = ProfileRepository(MemoryStore())
        assertEquals(ProfileState(), repository.state.first())

        repository.save(gr55)
        repository.save(melton)
        assertEquals(melton, repository.state.first().active)

        repository.save(gr55.copy(model = "GR55"))
        val edited = repository.state.first()
        assertEquals("GR55", edited.profiles.first().model)
        assertEquals("f", edited.activeId)

        repository.setActive("b")
        repository.setActive("missing")
        assertEquals("b", repository.state.first().activeId)

        repository.delete("b")
        val left = repository.state.first()
        assertEquals(listOf(melton), left.profiles)
        assertEquals(melton, left.active)
    }
}
