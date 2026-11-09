package io.github.blanaboo.stimmzug.data.log

import io.github.blanaboo.stimmzug.audio.OutputKind
import io.github.blanaboo.stimmzug.audio.OutputRoute
import io.github.blanaboo.stimmzug.data.settings.DisplayMode
import io.github.blanaboo.stimmzug.dsp.level.CalibrationResult
import io.github.blanaboo.stimmzug.dsp.match.MatchConfig
import io.github.blanaboo.stimmzug.dsp.match.NoteResult
import io.github.blanaboo.stimmzug.dsp.music.InstrumentProfile
import io.github.blanaboo.stimmzug.dsp.music.TubaKey
import io.github.blanaboo.stimmzug.dsp.music.ValveInterval
import io.github.blanaboo.stimmzug.dsp.session.NoteAttempt
import io.github.blanaboo.stimmzug.dsp.session.NoteStatus
import io.github.blanaboo.stimmzug.dsp.session.SessionConfig
import io.github.blanaboo.stimmzug.dsp.session.SessionSummary
import io.github.blanaboo.stimmzug.dsp.synth.DroneTimbre
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SessionLogTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val melton = InstrumentProfile(
        id = "f",
        name = "Melton 4250",
        key = TubaKey.F,
        extraValves = listOf(ValveInterval.FOURTH, ValveInterval.LARGE_WHOLE_TONE),
        triggerValves = setOf(2)
    )
    private val config = SessionConfig(a4Hz = 442.0, match = MatchConfig(toleranceCents = 5.0, holdSec = 5.0))

    private fun result(hit: Boolean, listened: Double) = NoteResult(
        targetHz = 87.7,
        hit = hit,
        timeToHitSec = if (hit) listened else null,
        entryCents = -12.4,
        holdMeanCents = if (hit) 0.8 else null,
        holdSpreadCents = if (hit) 1.9 else null,
        wrongOctaveSec = 1.5,
        wrongNoteSec = 0.0,
        listenedSec = listened
    )

    private fun summary() = SessionSummary(
        outcomes = emptyList(),
        durationSec = 42.0,
        attempts = listOf(
            NoteAttempt(1, 0, 41, 87.7, startSec = 3.0, status = NoteStatus.HIT, result = result(true, 6.0)),
            NoteAttempt(2, 1, 36, 65.7, startSec = 13.0, status = NoteStatus.SKIPPED, result = result(false, 3.0))
        )
    )

    private fun built(display: DisplayTimeline = DisplayTimeline(DisplayMode.EAR), warmUp: WarmUpInfo? = null) =
        SessionLogBuilder.build(
            SessionContext(
                "2026-11-09T18:30:05",
                melton,
                config,
                DroneTimbre.RECORDED,
                "KEY_NOTES B-Dur",
                display,
                warmUp
            ),
            summary(),
            CalibrationResult(ambientDb = -62.0, marginDb = 10.0),
            audioSource = "UNPROCESSED",
            route = OutputRoute(OutputKind.HEADPHONES, "Bose QC Earbuds II")
        )

    @Test
    fun aSessionKeepsInstrumentSettingsAndEveryAttempt() {
        val s = built()
        assertEquals(LoggedSession.VERSION, s.version)
        assertEquals("Melton 4250", s.profile!!.name)
        assertEquals("F", s.profile.key)
        assertEquals(5, s.profile.valves)
        assertEquals(listOf(2), s.profile.triggerValves)
        assertEquals("HEADPHONES Bose QC Earbuds II", s.droneRoute)
        assertEquals(-62.0, s.ambientDb!!, 0.0)
        assertTrue(s.thresholdDb!! > s.ambientDb)
        assertNull(s.warmUp)

        val (first, second) = s.attempts
        assertEquals("HIT", first.status)
        assertNull(first.previousMidi)
        assertEquals(41, second.previousMidi)
        // großes F is the open 2nd natural tone of the F tuba, großes C its 4th valve.
        assertEquals(2 to "0", first.partial to first.valves)
        assertEquals(2 to "4", second.partial to second.valves)
        assertEquals("SKIPPED", second.status)
        assertEquals("EAR", first.display)
    }

    @Test
    fun theWarmUpOfTheDayIsLogged() {
        val s = built(warmUp = WarmUpInfo("WARMED_UP_NOW", durationSec = 240, ageMin = 12.5))
        assertEquals("WARMED_UP_NOW", s.warmUp)
        assertEquals(240, s.warmUpSec)
        assertEquals(12.5, s.warmUpAgeMin!!, 0.0)
        val header = SessionLogCsv.HEADER
        val row = header.zip(SessionLogCsv.write(listOf(s)).lines()[1].split(",")).toMap()
        val warmUpCells = listOf(row["warm_up"], row["warm_up_sec"], row["warm_up_age_min"])
        assertEquals(listOf("WARMED_UP_NOW", "240", "12.5"), warmUpCells)
    }

    @Test
    fun theDisplayIsMixedWhereItChangedDuringAnAttempt() {
        // Attempt 1 listens 4.0–10.0 s, attempt 2 from 14.0 s (start + 1 s pre-listen).
        val display = DisplayTimeline(DisplayMode.EAR).apply {
            change(7.0, DisplayMode.TUNER)
            change(7.5, DisplayMode.TUNER)
        }
        val attempts = built(display).attempts
        assertEquals(listOf("MIXED", "TUNER"), attempts.map { it.display })
    }

    @Test
    fun theLogIsAppendedAndReadBackSkippingDamagedLines() = runTest {
        val file = temp.newFile("log.jsonl")
        val log = SessionLogRepository(file)
        log.append(built())
        file.appendText("{kaputt\n")
        log.append(built().copy(startedAt = "2026-11-10T08:00:00", warmUp = "WARMED_UP"))
        val read = log.readAll()
        assertEquals(listOf("2026-11-09T18:30:05", "2026-11-10T08:00:00"), read.map { it.startedAt })
        assertEquals(built(), read[0])
        assertEquals("WARMED_UP", read[1].warmUp)
        assertEquals(emptyList<LoggedSession>(), SessionLogRepository(File(temp.root, "fehlt.jsonl")).readAll())

        val exported = log.exportCsv(File(temp.root, "exports"))!!
        assertTrue(exported.name.startsWith("stimmzug-uebungsprotokoll-") && exported.name.endsWith(".csv"))
        assertEquals(1 + 2 * 2, exported.readLines().count { it.isNotEmpty() })
        assertNull(SessionLogRepository(File(temp.root, "leer.jsonl")).exportCsv(File(temp.root, "exports")))
    }

    @Test
    fun linesOfOlderOrNewerVersionsStillLoad() = runTest {
        val file = temp.newFile("log.jsonl")
        file.writeText(
            """{"version":1,"started_at":"2026-11-09T07:00:00","duration_sec":10.0,"a4_hz":440.0,""" +
                """"tolerance_cents":5.0,"hold_sec":3.0,"drone_route":"SPEAKER","drone_timbre":"SYNTHETIC",""" +
                """"exercise":"x","future_field":42}""" + "\n"
        )
        val read = SessionLogRepository(file).readAll().single()
        assertEquals(440.0, read.a4Hz, 0.0)
        assertTrue(read.attempts.isEmpty())
    }

    @Test
    fun csvHasOneRowPerAttemptWithSharesAndIntervals() {
        val csv = SessionLogCsv.write(listOf(built())).lines().filter { it.isNotEmpty() }
        assertEquals(SessionLogCsv.HEADER.joinToString(","), csv[0])
        assertEquals(3, csv.size)
        val rows = csv.drop(1).map { line -> SessionLogCsv.HEADER.zip(line.split(",")).toMap() }
        assertEquals("großes F", rows[0]["note"])
        assertEquals("FOURTH+LARGE_WHOLE_TONE", rows[0]["extra_valves"])
        assertEquals("2", rows[0]["triggers"])
        assertEquals("442", rows[0]["a4_hz"])
        assertEquals("0.25", rows[0]["wrong_octave_share"])
        assertEquals("0.5", rows[1]["wrong_octave_share"])
        assertEquals("", rows[0]["interval"])
        assertEquals("-5", rows[1]["interval"])
        assertEquals("-12.4", rows[1]["entry_cents"])
        assertEquals("", rows[1]["hold_mean_cents"])
        assertEquals("true", rows[1]["drone"])
    }

    @Test
    fun csvQuotesCellsWithCommas() {
        val csv = SessionLogCsv.write(listOf(built().copy(exercise = "Töne, B-Dur \"leicht\"")))
        assertTrue(csv.contains(",\"Töne, B-Dur \"\"leicht\"\"\","))
    }
}
