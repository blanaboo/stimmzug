package io.github.blanaboo.stimmzug.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.blanaboo.stimmzug.data.settings.PracticeSettings
import io.github.blanaboo.stimmzug.data.settings.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The stored practice settings (#68); `null` while loading. */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SettingsRepository(application)

    val settings: StateFlow<PracticeSettings?> =
        repository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    fun update(change: (PracticeSettings) -> PracticeSettings) {
        viewModelScope.launch { repository.update(change) }
    }

    fun reset() {
        viewModelScope.launch { repository.reset() }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
