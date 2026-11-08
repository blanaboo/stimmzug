package io.github.blanaboo.stimmzug.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** DataStore in memory: the file-based one cannot replace files atomically on Windows development machines. */
internal class MemoryStore(initial: Preferences = emptyPreferences()) : DataStore<Preferences> {
    private val flow = MutableStateFlow(initial)
    override val data: Flow<Preferences> = flow

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
        transform(flow.value).also { flow.value = it }
}
