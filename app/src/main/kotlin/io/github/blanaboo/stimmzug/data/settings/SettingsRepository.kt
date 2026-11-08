package io.github.blanaboo.stimmzug.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** One DataStore per process, as DataStore requires. */
private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "practice_settings")

/** The practice settings (#68), stored on the device only. Missing or out-of-range values fall back to defaults. */
class SettingsRepository(private val store: DataStore<Preferences>) {

    constructor(context: Context) : this(context.applicationContext.settingsStore)

    val settings: Flow<PracticeSettings> = store.data.map(::decode)

    suspend fun update(change: (PracticeSettings) -> PracticeSettings) {
        store.edit { prefs -> encode(change(decode(prefs)), prefs) }
    }

    suspend fun reset() = update { PracticeSettings() }

    private fun decode(prefs: Preferences): PracticeSettings {
        val defaults = PracticeSettings()
        return PracticeSettings(
            a4Hz = prefs[A4].within(PracticeSettings.A4_RANGE, defaults.a4Hz),
            toleranceCents = prefs[TOLERANCE].within(PracticeSettings.TOLERANCE_RANGE, defaults.toleranceCents),
            holdSec = prefs[HOLD].within(PracticeSettings.HOLD_RANGE, defaults.holdSec),
            display = enumOr(prefs[DISPLAY], defaults.display),
            droneOutput = enumOr(prefs[DRONE_OUTPUT], defaults.droneOutput)
        )
    }

    private fun encode(settings: PracticeSettings, prefs: MutablePreferences) {
        prefs[A4] = settings.a4Hz
        prefs[TOLERANCE] = settings.toleranceCents
        prefs[HOLD] = settings.holdSec
        prefs[DISPLAY] = settings.display.name
        prefs[DRONE_OUTPUT] = settings.droneOutput.name
    }

    private fun Int?.within(range: IntRange, default: Int): Int = this?.takeIf { it in range } ?: default

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: default

    private companion object {
        val A4 = intPreferencesKey("a4_hz")
        val TOLERANCE = intPreferencesKey("tolerance_cents")
        val HOLD = intPreferencesKey("hold_sec")
        val DISPLAY = stringPreferencesKey("display")
        val DRONE_OUTPUT = stringPreferencesKey("drone_output")
    }
}
