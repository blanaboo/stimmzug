package io.github.blanaboo.stimmzug.data.profile

import io.github.blanaboo.stimmzug.dsp.music.InstrumentProfile
import io.github.blanaboo.stimmzug.dsp.music.TubaKey
import io.github.blanaboo.stimmzug.dsp.music.ValveInterval
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** All profiles of the player and which one is active. [active] falls back to the first profile. */
data class ProfileState(val profiles: List<InstrumentProfile> = emptyList(), val activeId: String? = null) {
    val active: InstrumentProfile? get() = profiles.firstOrNull { it.id == activeId } ?: profiles.firstOrNull()
}

/**
 * Storage format of the profiles: JSON with a version number, so later fields can be added (with defaults) and
 * older files still load. Enums are stored by name; a profile that no longer makes sense is dropped, not the file.
 */
object ProfileCodec {
    const val VERSION = 1

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(state: ProfileState): String =
        json.encodeToString(StoredProfiles(VERSION, state.activeId, state.profiles.map(::stored)))

    fun decode(text: String?): ProfileState {
        if (text.isNullOrBlank()) return ProfileState()
        // Unreadable data (damaged file, foreign format) starts empty instead of crashing the app.
        val stored = runCatching { json.decodeFromString<StoredProfiles>(text) }.getOrNull() ?: return ProfileState()
        return ProfileState(stored.profiles.mapNotNull(::profile), stored.activeId)
    }

    private fun stored(p: InstrumentProfile) = StoredProfile(
        id = p.id,
        name = p.name,
        key = p.key.name,
        maker = p.maker,
        model = p.model,
        extraValves = p.extraValves.map { it.name },
        compensating = p.compensating,
        triggerValves = p.triggerValves.sorted(),
        mainSlideTrigger = p.mainSlideTrigger
    )

    private fun profile(s: StoredProfile): InstrumentProfile? = runCatching {
        InstrumentProfile(
            id = s.id,
            name = s.name,
            key = TubaKey.valueOf(s.key),
            maker = s.maker,
            model = s.model,
            extraValves = s.extraValves.map(ValveInterval::valueOf),
            compensating = s.compensating,
            triggerValves = s.triggerValves.toSet(),
            mainSlideTrigger = s.mainSlideTrigger
        )
    }.getOrNull()
}

@Serializable
private data class StoredProfiles(
    val version: Int,
    @SerialName("active_id") val activeId: String? = null,
    val profiles: List<StoredProfile> = emptyList()
)

@Serializable
private data class StoredProfile(
    val id: String,
    val name: String,
    val key: String,
    val maker: String = "",
    val model: String = "",
    @SerialName("extra_valves") val extraValves: List<String> = emptyList(),
    val compensating: Boolean = false,
    @SerialName("trigger_valves") val triggerValves: List<Int> = emptyList(),
    @SerialName("main_slide_trigger") val mainSlideTrigger: Boolean = false
)
