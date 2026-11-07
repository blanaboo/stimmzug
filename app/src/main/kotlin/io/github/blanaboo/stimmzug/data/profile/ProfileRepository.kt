package io.github.blanaboo.stimmzug.data.profile

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.blanaboo.stimmzug.dsp.music.InstrumentProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** One DataStore per process, as DataStore requires. */
private val Context.profileStore: DataStore<Preferences> by preferencesDataStore(name = "instrument_profiles")

/** The player's instrument profiles (#40), stored on the device only. */
class ProfileRepository(private val store: DataStore<Preferences>) {

    constructor(context: Context) : this(context.applicationContext.profileStore)

    val state: Flow<ProfileState> = store.data.map { ProfileCodec.decode(it[KEY]) }

    /** Adds [profile] or replaces the one with the same id; a new profile becomes the active one. */
    suspend fun save(profile: InstrumentProfile) = update { state ->
        val exists = state.profiles.any { it.id == profile.id }
        val replaced = state.profiles.map { if (it.id == profile.id) profile else it }
        ProfileState(if (exists) replaced else replaced + profile, if (exists) state.activeId else profile.id)
    }

    suspend fun delete(id: String) = update { state ->
        ProfileState(state.profiles.filterNot { it.id == id }, state.activeId?.takeUnless { it == id })
    }

    suspend fun setActive(id: String) = update { state ->
        if (state.profiles.any { it.id == id }) state.copy(activeId = id) else state
    }

    private suspend fun update(change: (ProfileState) -> ProfileState) {
        store.edit { prefs -> prefs[KEY] = ProfileCodec.encode(change(ProfileCodec.decode(prefs[KEY]))) }
    }

    private companion object {
        val KEY = stringPreferencesKey("profiles_json")
    }
}
