package io.github.blanaboo.stimmzug.data.log

import io.github.blanaboo.stimmzug.dsp.music.NoteNames
import java.util.Locale

/**
 * The session log as CSV for the field-test evaluation (#37): one row per attempt with its session's data in front.
 * Comma-separated with a decimal point (RFC 4180), so analysis tools read it as it is.
 */
object SessionLogCsv {
    val HEADER = listOf(
        "session_start", "profile", "tuba_key", "valve_count", "extra_valves", "compensating", "triggers",
        "a4_hz", "tolerance_cents", "hold_sec", "audio_source", "drone_route", "drone_timbre", "ambient_db",
        "threshold_db", "warm_up", "exercise", "attempt", "index", "note", "midi", "target_hz", "partial", "valves",
        "start_s", "previous_midi", "interval", "status", "time_to_hit_s", "entry_cents", "hold_mean_cents",
        "hold_spread_cents", "wrong_octave_share", "wrong_note_share", "listened_s", "display", "drone"
    )

    fun write(sessions: List<LoggedSession>): String = buildString {
        appendLine(HEADER.joinToString(","))
        for (s in sessions) {
            val session = sessionCells(s)
            for (a in s.attempts) appendLine((session + attemptCells(a)).joinToString(",") { cell(it) })
        }
    }

    private fun sessionCells(s: LoggedSession): List<Any?> {
        val p = s.profile
        return listOf(
            s.startedAt, p?.name, p?.key, p?.valves, p?.extraValves?.joinToString("+"), p?.compensating,
            p?.let { triggers(it) }, s.a4Hz, s.toleranceCents, s.holdSec, s.audioSource, s.droneRoute,
            s.droneTimbre, s.ambientDb, s.thresholdDb, s.warmUp, s.exercise
        )
    }

    private fun attemptCells(a: LoggedAttempt): List<Any?> = listOf(
        a.number, a.index, NoteNames.germanOctaveName(a.midi), a.midi, a.targetHz, a.partial, a.valves,
        a.startSec, a.previousMidi, a.previousMidi?.let { a.midi - it }, a.status, a.timeToHitSec, a.entryCents,
        a.holdMeanCents, a.holdSpreadCents, share(a.wrongOctaveSec, a.listenedSec),
        share(a.wrongNoteSec, a.listenedSec), a.listenedSec, a.display, a.drone
    )

    /** "main+2": main slide trigger and triggered valve slides. */
    private fun triggers(p: LoggedProfile): String {
        val main = listOfNotNull("main".takeIf { p.mainSlideTrigger })
        return (main + p.triggerValves.map { it.toString() }).joinToString("+")
    }

    private fun share(part: Double, whole: Double): Double? = if (whole > 0.0) part / whole else null

    private fun cell(value: Any?): String = when (value) {
        null -> ""
        is Double -> number(value)
        is String -> if (value.any { it in QUOTED }) "\"" + value.replace("\"", "\"\"") + "\"" else value
        else -> value.toString()
    }

    /** Up to three decimals without trailing zeros: 442, 0.5, -12.345. */
    private fun number(value: Double): String {
        val text = String.format(Locale.ROOT, "%.3f", value).trimEnd('0').trimEnd('.')
        return if (text == "-0") "0" else text
    }

    /** Characters that make a cell need quotes. */
    private val QUOTED = setOf(',', '"', '\n', '\r')
}
