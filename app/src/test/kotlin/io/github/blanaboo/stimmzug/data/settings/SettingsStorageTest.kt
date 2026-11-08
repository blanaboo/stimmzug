package io.github.blanaboo.stimmzug.data.settings

import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.blanaboo.stimmzug.data.MemoryStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SettingsStorageTest {

    @Test
    fun defaultsAreTheOwnersChoices() {
        val defaults = PracticeSettings()
        assertEquals(442, defaults.a4Hz)
        assertEquals(5, defaults.toleranceCents)
        assertEquals(5, defaults.holdSec)
        assertEquals(DisplayMode.EAR, defaults.display)
        assertEquals(DroneOutput.AUTO, defaults.droneOutput)
    }

    @Test
    fun settingsBecomeTheSessionConfig() {
        val config = PracticeSettings(a4Hz = 440, toleranceCents = 8, holdSec = 4).toSessionConfig()
        assertEquals(440.0, config.a4Hz, 0.0)
        assertEquals(8.0, config.match.toleranceCents, 0.0)
        assertEquals(4.0, config.match.holdSec, 0.0)
    }

    @Test
    fun valuesOutsideTheRangesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { PracticeSettings(a4Hz = 429) }
        assertThrows(IllegalArgumentException::class.java) { PracticeSettings(toleranceCents = 16) }
        assertThrows(IllegalArgumentException::class.java) { PracticeSettings(holdSec = 1) }
    }

    @Test
    fun repositoryStoresUpdatesAndResets() = runTest {
        val repository = SettingsRepository(MemoryStore())
        assertEquals(PracticeSettings(), repository.settings.first())

        repository.update { it.copy(holdSec = 7, display = DisplayMode.TUNER) }
        repository.update { it.copy(a4Hz = 440, droneOutput = DroneOutput.SPEAKER) }
        assertEquals(
            PracticeSettings(a4Hz = 440, holdSec = 7, display = DisplayMode.TUNER, droneOutput = DroneOutput.SPEAKER),
            repository.settings.first()
        )

        repository.reset()
        assertEquals(PracticeSettings(), repository.settings.first())
    }

    @Test
    fun brokenStoredValuesFallBackToDefaults() = runTest {
        val stored = mutablePreferencesOf(
            intPreferencesKey("a4_hz") to 500,
            intPreferencesKey("tolerance_cents") to 9,
            intPreferencesKey("hold_sec") to 0,
            stringPreferencesKey("display") to "HOLOGRAM",
            stringPreferencesKey("drone_output") to "SPEAKER"
        )
        val settings = SettingsRepository(MemoryStore(stored)).settings.first()
        assertEquals(PracticeSettings(toleranceCents = 9, droneOutput = DroneOutput.SPEAKER), settings)
    }
}
