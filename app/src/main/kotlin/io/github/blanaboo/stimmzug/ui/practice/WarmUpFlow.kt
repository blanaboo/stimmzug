package io.github.blanaboo.stimmzug.ui.practice

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.blanaboo.stimmzug.data.warmup.WarmUp
import io.github.blanaboo.stimmzug.data.warmup.WarmUpAnswer
import io.github.blanaboo.stimmzug.data.warmup.WarmUpRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The warm-up hint before the first exercise of a day (#44): asks once a day, offers a timer for warming up, and
 * hands today's answer to the exercise so the session log keeps it. Compose state; use from the main thread.
 */
class WarmUpFlow(
    private val repository: WarmUpRepository,
    private val scope: CoroutineScope,
    private val clock: () -> Long = SystemClock::elapsedRealtime,
    /** Starts the exercise with today's warm-up. */
    private val begin: (WarmUp?) -> Unit
) {
    private val latest = repository.latest.stateIn(scope, SharingStarted.Eagerly, null)
    private var answered: WarmUp? = null

    /** The question „Schon eingespielt?“ is shown. */
    var asking by mutableStateOf(false)
        private set

    /** Start of the warm-up timer ([clock] time) while the player warms up, else `null`. */
    var timerStart by mutableStateOf<Long?>(null)
        private set

    /** Today's answer, `null` before the first exercise of the day. */
    fun today(): WarmUp? = (answered ?: latest.value).takeIf(repository::isToday)

    /** Starts the exercise, after the question if it is the first one today. */
    fun request() {
        val warmUp = today()
        if (warmUp != null) begin(warmUp) else asking = true
    }

    /** „Bin eingespielt“. */
    fun ready() = answer(WarmUpAnswer.WARMED_UP, durationSec = null)

    /** „Jetzt einspielen“: the timer runs until [finish]. */
    fun warmUpNow() {
        asking = false
        timerStart = clock()
    }

    /** Warmed up with the timer: the exercise starts. */
    fun finish() {
        val start = timerStart ?: return
        timerStart = null
        answer(WarmUpAnswer.WARMED_UP_NOW, ((clock() - start) / MILLIS_PER_SECOND).toInt())
    }

    /** Leaves the timer without starting; the question comes again next time. */
    fun cancel() {
        timerStart = null
    }

    /** Closes the question without an answer. */
    fun dismiss() {
        asking = false
    }

    private fun answer(answer: WarmUpAnswer, durationSec: Int?) {
        asking = false
        scope.launch {
            val warmUp = repository.answer(answer, durationSec)
            answered = warmUp
            begin(warmUp)
        }
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1000L
    }
}
