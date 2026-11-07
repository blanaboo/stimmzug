package io.github.blanaboo.stimmzug.ui.profile

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import io.github.blanaboo.stimmzug.data.profile.ProfileRepository
import io.github.blanaboo.stimmzug.data.profile.ProfileState
import io.github.blanaboo.stimmzug.dsp.music.InstrumentProfile
import java.util.UUID
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The stored profiles for the home screen and the profile list; `null` while loading. */
class ProfilesViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ProfileRepository(application)

    val state: StateFlow<ProfileState?> =
        repository.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    fun setActive(id: String) {
        viewModelScope.launch { repository.setActive(id) }
    }

    fun delete(id: String) {
        viewModelScope.launch { repository.delete(id) }
    }

    /** The profile for players who skip the setup: BB♭ tuba with four valves, named [name]. */
    fun useDefault(name: String) {
        viewModelScope.launch { repository.save(InstrumentProfile.default(UUID.randomUUID().toString(), name)) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/**
 * The profile setup: a new profile, or an existing one when the route carries [ARG_PROFILE_ID]. The answers live
 * here, so they survive rotation.
 */
class ProfileWizardViewModel(application: Application, handle: SavedStateHandle) : AndroidViewModel(application) {
    private val repository = ProfileRepository(application)
    private val editId: String? = handle[ARG_PROFILE_ID]

    val isEdit: Boolean get() = editId != null

    var draft by mutableStateOf(ProfileDraft(id = editId ?: UUID.randomUUID().toString()))
        private set

    var step by mutableStateOf(WizardStep.KEY)
        private set

    /** False until an existing profile is loaded. */
    var ready by mutableStateOf(editId == null)
        private set

    init {
        editId?.let { id ->
            viewModelScope.launch {
                repository.state.first().profiles.firstOrNull { it.id == id }?.let { draft = ProfileDraft.from(it) }
                ready = true
            }
        }
    }

    fun update(change: (ProfileDraft) -> ProfileDraft) {
        draft = change(draft)
    }

    /** Goes to the next question; false on the last one. */
    fun next(): Boolean = draft.next(step)?.also { step = it } != null

    /** Goes to the previous question; false on the first one (the caller leaves the setup). */
    fun back(): Boolean = draft.previous(step)?.also { step = it } != null

    fun save(suggestedName: String, onSaved: () -> Unit) {
        val profile = draft.toProfile(suggestedName)
        viewModelScope.launch {
            repository.save(profile)
            onSaved()
        }
    }

    companion object {
        /** Name of the route argument, see [io.github.blanaboo.stimmzug.ui.ProfileWizardRoute]. */
        const val ARG_PROFILE_ID = "profileId"
    }
}
