package io.github.blanaboo.stimmzug.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.blanaboo.stimmzug.data.log.SessionLogRepository
import io.github.blanaboo.stimmzug.data.settings.PracticeSettings
import io.github.blanaboo.stimmzug.data.settings.SettingsRepository
import java.io.File
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The stored practice settings (#68) and the session log (#37); `null` while loading. */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SettingsRepository(application)
    private val log = SessionLogRepository(application)

    val settings: StateFlow<PracticeSettings?> =
        repository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /** Number of sessions in the log, read again whenever the settings screen shows. */
    val logCount: StateFlow<Int?> = flow { emit(log.readAll().size) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    fun update(change: (PracticeSettings) -> PracticeSettings) {
        viewModelScope.launch { repository.update(change) }
    }

    fun reset() {
        viewModelScope.launch { repository.reset() }
    }

    /** Writes the log as CSV into the cache folder shared by the file provider; `null` while the log is empty. */
    suspend fun exportLog(): File? = log.exportCsv(File(getApplication<Application>().cacheDir, EXPORT_DIR))

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        /** Matches `res/xml/shared_files.xml`. */
        const val EXPORT_DIR = "exports"
    }
}
