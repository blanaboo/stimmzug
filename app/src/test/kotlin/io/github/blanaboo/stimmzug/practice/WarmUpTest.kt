package io.github.blanaboo.stimmzug.practice

import io.github.blanaboo.stimmzug.data.MemoryStore
import io.github.blanaboo.stimmzug.data.warmup.WarmUp
import io.github.blanaboo.stimmzug.data.warmup.WarmUpAnswer
import io.github.blanaboo.stimmzug.data.warmup.WarmUpRepository
import io.github.blanaboo.stimmzug.ui.practice.WarmUpFlow
import io.github.blanaboo.stimmzug.ui.practice.elapsedText
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WarmUpTest {

    private val zone = ZoneId.of("Europe/Berlin")
    private var wallClock = millis(LocalDateTime.of(2026, 11, 9, 18, 0))
    private var elapsed = 0L

    private fun millis(t: LocalDateTime) = t.atZone(zone).toInstant().toEpochMilli()

    private fun repository() = WarmUpRepository(MemoryStore(), now = { wallClock }, zone = { zone })

    @Test
    fun theAnswerCountsForTheDayItWasGiven() = runTest {
        val repository = repository()
        assertFalse(repository.isToday(repository.latest.first()))
        val given = repository.answer(WarmUpAnswer.WARMED_UP_NOW, durationSec = 240)
        assertEquals(WarmUp(WarmUpAnswer.WARMED_UP_NOW, wallClock, 240), repository.latest.first())
        assertTrue(repository.isToday(given))

        wallClock = millis(LocalDateTime.of(2026, 11, 9, 23, 59))
        assertTrue("still the same day", repository.isToday(given))
        wallClock = millis(LocalDateTime.of(2026, 11, 10, 0, 1))
        assertFalse("a new day asks again", repository.isToday(given))

        repository.answer(WarmUpAnswer.WARMED_UP)
        assertNull("no timer, no duration", repository.latest.first()!!.durationSec)
        repository.clear()
        assertNull(repository.latest.first())
    }

    private fun TestScope.flow(repository: WarmUpRepository, started: MutableList<WarmUp?>) =
        WarmUpFlow(repository, backgroundScope, clock = { elapsed }) { started += it }

    @Test
    fun theQuestionComesBeforeTheFirstExerciseOfTheDayOnly() = runTest(UnconfinedTestDispatcher()) {
        val started = mutableListOf<WarmUp?>()
        val flow = flow(repository(), started)

        flow.request()
        assertTrue(flow.asking)
        assertTrue(started.isEmpty())

        flow.ready()
        runCurrent()
        assertFalse(flow.asking)
        assertEquals(listOf(WarmUpAnswer.WARMED_UP), started.map { it!!.answer })

        flow.request()
        assertFalse("answered today: straight to the exercise", flow.asking)
        assertEquals(2, started.size)
    }

    @Test
    fun warmingUpWithTheTimerStartsTheExerciseAndKeepsTheDuration() = runTest(UnconfinedTestDispatcher()) {
        val started = mutableListOf<WarmUp?>()
        val flow = flow(repository(), started)
        flow.request()
        flow.warmUpNow()
        assertFalse(flow.asking)
        assertEquals(0L, flow.timerStart)

        elapsed = 125_400L
        flow.finish()
        runCurrent()
        assertNull(flow.timerStart)
        assertEquals(WarmUpAnswer.WARMED_UP_NOW, started.single()!!.answer)
        assertEquals(125, started.single()!!.durationSec)
    }

    @Test
    fun leavingTheTimerOrTheQuestionStartsNothing() = runTest(UnconfinedTestDispatcher()) {
        val started = mutableListOf<WarmUp?>()
        val flow = flow(repository(), started)
        flow.request()
        flow.dismiss()
        assertFalse(flow.asking)
        flow.request()
        flow.warmUpNow()
        flow.cancel()
        flow.finish()
        runCurrent()
        assertNull(flow.timerStart)
        assertTrue(started.isEmpty())
        flow.request()
        assertTrue("asked again next time", flow.asking)
    }

    @Test
    fun theTimerReadsMinutesAndSeconds() {
        assertEquals("0:00", elapsedText(0))
        assertEquals("0:09", elapsedText(9))
        assertEquals("4:07", elapsedText(247))
        assertEquals("12:00", elapsedText(720))
        assertEquals("0:00", elapsedText(-3))
    }
}
