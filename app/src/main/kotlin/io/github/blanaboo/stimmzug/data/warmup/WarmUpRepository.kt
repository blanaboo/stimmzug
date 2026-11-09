package io.github.blanaboo.stimmzug.data.warmup

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The answer to the warm-up hint before the first exercise of a day (#44). */
enum class WarmUpAnswer {
    /** "Bin eingespielt". */
    WARMED_UP,

    /** "Jetzt einspielen": warmed up with the app's timer. */
    WARMED_UP_NOW
}

/** Today's warm-up: the answer, when it was given (epoch ms) and how long the timer ran, if it did. */
data class WarmUp(val answer: WarmUpAnswer, val atMillis: Long, val durationSec: Int? = null)

private val Context.warmUpStore: DataStore<Preferences> by preferencesDataStore(name = "warm_up")

/**
 * Remembers the latest warm-up answer (#44), so the hint comes once a day: [isToday] tells whether it was given
 * today. Only the latest answer is kept; the session log keeps the history.
 */
class WarmUpRepository(
    private val store: DataStore<Preferences>,
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault
) {
    constructor(context: Context) : this(context.applicationContext.warmUpStore)

    val latest: Flow<WarmUp?> = store.data.map(::decode)

    /** Whether [warmUp] was answered today; checked at the moment of asking, as the day may change meanwhile. */
    fun isToday(warmUp: WarmUp?): Boolean = warmUp != null && dayOf(warmUp.atMillis) == dayOf(now())

    suspend fun answer(answer: WarmUpAnswer, durationSec: Int? = null): WarmUp {
        val warmUp = WarmUp(answer, now(), durationSec)
        store.edit { prefs ->
            prefs[ANSWER] = answer.name
            prefs[AT] = warmUp.atMillis
            if (durationSec != null) prefs[DURATION] = durationSec else prefs.remove(DURATION)
        }
        return warmUp
    }

    /** Forgets today's answer, e.g. for tests. */
    suspend fun clear() {
        store.edit { it.clear() }
    }

    private fun decode(prefs: Preferences): WarmUp? {
        val answer = WarmUpAnswer.entries.firstOrNull { it.name == prefs[ANSWER] } ?: return null
        val at = prefs[AT] ?: return null
        return WarmUp(answer, at, prefs[DURATION])
    }

    private fun dayOf(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(zone()).toLocalDate()

    private companion object {
        val ANSWER = stringPreferencesKey("answer")
        val AT = longPreferencesKey("answered_at")
        val DURATION = intPreferencesKey("duration_sec")
    }
}
