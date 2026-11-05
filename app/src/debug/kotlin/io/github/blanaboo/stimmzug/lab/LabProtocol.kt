package io.github.blanaboo.stimmzug.lab

import io.github.blanaboo.stimmzug.dsp.music.NoteMath
import io.github.blanaboo.stimmzug.dsp.music.NoteNames
import java.util.Locale

/**
 * Audio sources compared in the audio spike (#32), in the order they are recorded. [minSdk] is the API level that
 * introduced the source; the Android constant is mapped in [LabRecorder].
 */
enum class LabSource(val minSdk: Int = 1) {
    UNPROCESSED,
    VOICE_RECOGNITION,
    VOICE_PERFORMANCE(minSdk = 29),
    MIC,
    CAMCORDER,

    /** Applies echo cancellation, which might remove the drone; only in the self-test. */
    VOICE_COMMUNICATION
}

/** The microphone a take asks for. [deviceId] `null` leaves the choice to the system. [key] goes into file names. */
data class MicChoice(val key: String, val label: String, val deviceId: Int?) {
    companion object {
        val SYSTEM = MicChoice("system", "Mikro: Systemwahl", null)
    }
}

/** A stretch of a take with one instruction. [playerPlays] and [drone] describe what should be audible. */
data class Segment(
    /** Machine-readable name for the manifest, e.g. "quiet", "play", "low". */
    val key: String,
    /** What the screen tells the player. */
    val instruction: String,
    val durationSec: Double,
    val playerPlays: Boolean,
    val drone: Boolean = false
) {
    init {
        require(durationSec > 0.0) { "segment duration must be positive, was $durationSec" }
    }
}

/** One recording: one source, one microphone, one note, a fixed sequence of [segments]. */
data class Take(
    /** Unique within the protocol, sortable: "T01". */
    val id: String,
    val source: LabSource,
    val mic: MicChoice,
    val noteMidi: Int,
    val segments: List<Segment>,
    /** The drone sounds this many octaves above the note. */
    val droneOctave: Int = 0,
    /** Shown before the take; the run waits until the player continues. `null` = continue on its own. */
    val pauseBefore: String? = null
) {
    init {
        require(segments.isNotEmpty()) { "a take needs segments" }
    }

    val noteHz: Double get() = NoteMath.frequency(noteMidi)
    val noteName: String get() = NoteNames.germanOctaveName(noteMidi)
    val durationSec: Double get() = segments.sumOf { it.durationSec }
    val usesDrone: Boolean get() = segments.any { it.drone }

    /** e.g. "T03-voice_recognition-unten-f1.wav", "…-bb1.wav" for B♭1; ASCII only, so the files survive every tool. */
    val fileName: String
        get() {
            val note = NoteNames.scientific(noteMidi).lowercase().replace("♭", "b")
            val octave = if (droneOctave > 0) "-drone+$droneOctave" else ""
            return "$id-${source.name.lowercase()}-${mic.key}-$note$octave.wav"
        }

    /** Start times of the segments in seconds, plus the end of the take as last element. */
    val segmentBoundsSec: List<Double>
        get() = segments.runningFold(0.0) { start, segment -> start + segment.durationSec }
}

/** A named sequence of takes. [prepSec] is the countdown before each take, which is not recorded. */
data class LabProtocol(val key: String, val title: String, val takes: List<Take>, val prepSec: Double) {
    init {
        require(takes.map { it.id }.toSet().size == takes.size) { "take ids must be unique" }
    }
}

/** The two measurement runs of the spike, see docs/field-test/audio-spike.md. */
object LabProtocols {
    const val KONTRA_F = 29
    const val KONTRA_B = 34
    const val GROSSES_F = 41
    const val KLEINES_B = 58

    /** Sources for the tuba takes; VOICE_COMMUNICATION is only in the self-test. */
    private val tubaSources = LabSource.entries - LabSource.VOICE_COMMUNICATION

    /**
     * Without a player: per source and microphone 2 s room noise, then 4 s drone from the phone speaker. Shows
     * which sources open, which microphone they use, how loud the drone bleeds in and whether noise suppression or
     * AGC change a steady tone. Kontra-B is the drone a tuba player hears most, kleines b is what a phone speaker
     * still reproduces with its fundamental.
     */
    fun selfTest(mics: List<MicChoice>, sdk: Int): LabProtocol {
        val segments = listOf(
            Segment("quiet", "Stille", 2.0, playerPlays = false),
            Segment("drone", "Drone", 4.0, playerPlays = false, drone = true)
        )
        val takes = ArrayList<Take>()
        for (note in listOf(KONTRA_B, KLEINES_B)) {
            for (source in available(LabSource.entries, sdk)) {
                for (mic in mics) takes += Take(id(takes.size), source, mic, note, segments)
            }
        }
        return LabProtocol("selbsttest", "Selbsttest ohne Spieler", takes, prepSec = 0.5)
    }

    /**
     * Guided tuba measurement, about ten minutes: two low notes without drone with every source and microphone,
     * then Kontra-B against the drone, deliberately a little low, in tune and a little high.
     */
    fun tuba(mics: List<MicChoice>, sdk: Int): LabProtocol {
        val sources = available(tubaSources, sdk)
        val configs = sources.flatMap { source -> mics.map { mic -> source to mic } }
        val takes = ArrayList<Take>()
        val parts = listOf(KONTRA_F, GROSSES_F)
        parts.forEachIndexed { part, note ->
            val name = NoteNames.germanOctaveName(note)
            val segments = listOf(
                Segment("quiet", "Stille – nicht spielen", 2.0, playerPlays = false),
                Segment("play", "$name spielen und halten", 6.0, playerPlays = true)
            )
            configs.forEachIndexed { i, (source, mic) ->
                val pause = if (i == 0) partIntro(part + 1, name, configs.size) else null
                takes += Take(id(takes.size), source, mic, note, segments, pauseBefore = pause)
            }
        }
        // The self-test on the Nord 2 (#32) showed two paths: UNPROCESSED (raw, the microphone choice matters) and one
        // processed path shared by all other sources (microphone choice without effect). Hence: UNPROCESSED in unison
        // with every microphone, VOICE_RECOGNITION for the processed path, and UNPROCESSED once with the drone an
        // octave higher (the app's option against bleed).
        val droneTakes = mics.map { mic -> Triple(LabSource.UNPROCESSED, mic, 0) } +
            Triple(LabSource.VOICE_RECOGNITION, mics.first(), 0) +
            Triple(LabSource.UNPROCESSED, mics.first(), 1)
        droneTakes.forEachIndexed { i, (source, mic, octave) ->
            val pause = if (i == 0) droneIntro(droneTakes.size) else null
            takes += Take(id(takes.size), source, mic, KONTRA_B, droneSegments, octave, pause)
        }
        return LabProtocol("tuba", "Tuba-Messung", takes, prepSec = 4.0)
    }

    private val droneSegments = listOf(
        Segment("drone", "Drone hören – noch nicht spielen", 3.0, playerPlays = false, drone = true),
        Segment("low", "Etwas zu TIEF spielen (Schwebung hörbar)", 5.0, playerPlays = true, drone = true),
        Segment("clean", "Sauber einstimmen (keine Schwebung)", 6.0, playerPlays = true, drone = true),
        Segment("high", "Etwas zu HOCH spielen (Schwebung hörbar)", 5.0, playerPlays = true, drone = true)
    )

    private fun partIntro(part: Int, note: String, count: Int) =
        "Teil $part von 3: $note ohne Drone, $count Aufnahmen. Jede Aufnahme: 2 s Stille, dann 6 s $note " +
            "halten. Immer gleich laut (mf) und mit gleichem Abstand zum Handy spielen."

    private fun droneIntro(count: Int) =
        "Teil 3 von 3: Kontra-B mit Drone, $count Aufnahmen. Medienlautstärke so einstellen, wie du üben würdest. " +
            "Erst zuhören, dann etwas zu tief, sauber und etwas zu hoch spielen, jeweils ohne abzusetzen."

    private fun available(sources: List<LabSource>, sdk: Int) = sources.filter { sdk >= it.minSdk }

    private fun id(index: Int) = String.format(Locale.ROOT, "T%02d", index + 1)
}
