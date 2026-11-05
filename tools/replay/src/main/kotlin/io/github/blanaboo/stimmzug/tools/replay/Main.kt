package io.github.blanaboo.stimmzug.tools.replay

import io.github.blanaboo.stimmzug.dsp.match.MatchConfig
import io.github.blanaboo.stimmzug.dsp.music.NoteMath
import io.github.blanaboo.stimmzug.dsp.music.NoteNames
import java.io.File
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.PrintStream
import java.util.Locale
import javax.sound.sampled.UnsupportedAudioFileException
import kotlin.system.exitProcess

const val USAGE = """Verwendung: replay <aufnahme.wav|aiff> [Optionen]

Spielt eine Aufnahme durch Tonhöhenerkennung, Pegel-Gate und Treffer-Logik und schreibt
<name>.csv (ein Frame pro Zeile), <name>.png (Diagramm) und <name>.txt (Zusammenfassung).

  --target <ton>        Zielton: MIDI-Nummer (34), "Bb1", "Kontra-B", "großes F", "f'" oder "58.27Hz".
                        Ohne Zielton: freie Erkennung ohne Treffer-Logik.
  --a4 <Hz>             Kammerton a' (Standard 440)
  --calibrate <s>       Die ersten s Sekunden (niemand spielt) kalibrieren das Pegel-Gate.
                        Standard 0: kein Gate, jeder stimmhafte Frame zählt.
  --tolerance <ct>      Toleranz (Standard 5)
  --hold <s>            Haltedauer (Standard 3)
  --grace <s>           Gnadenzeit (Standard 0.3)
  --min-clarity <0..1>  Mindest-Klarheit (Standard 0.8)
  --median <n>          Median-Glättung über n Frames (Standard 5)
  --detector mpm|yin    Verfahren (Standard mpm)
  --out <ordner>        Ausgabeordner (Standard: neben der Aufnahme)
"""

/** Thrown for invalid command lines; the message is shown with the usage. */
class UsageException(message: String) : Exception(message)

data class Arguments(
    val input: File,
    val targetText: String?,
    val a4Hz: Double,
    val outDir: File?,
    val options: ReplayOptions
)

fun parseArguments(args: List<String>): Arguments {
    var input: File? = null
    var target: String? = null
    var a4 = NoteMath.DEFAULT_A4_HZ
    var out: File? = null
    var calibrate = 0.0
    var detector = DetectorChoice.MPM
    var match = MatchConfig(preListenSec = 0.0)
    var i = 0
    fun value(): String = args.getOrNull(++i) ?: throw UsageException("Wert fehlt für ${args[i - 1]}")
    fun number(): Double = value().replace(',', '.').toDoubleOrNull() ?: throw UsageException("Keine Zahl: ${args[i]}")
    while (i < args.size) {
        when (val arg = args[i]) {
            "--target" -> target = value()

            "--a4" -> a4 = number()

            "--out" -> out = File(value())

            "--calibrate" -> calibrate = number()

            "--tolerance" -> match = match.copy(toleranceCents = number())

            "--hold" -> match = match.copy(holdSec = number())

            "--grace" -> match = match.copy(graceSec = number())

            "--min-clarity" -> match = match.copy(minClarity = number())

            "--median" -> match = match.copy(medianFrames = number().toInt())

            "--detector" -> detector = when (value().lowercase()) {
                "mpm" -> DetectorChoice.MPM
                "yin" -> DetectorChoice.YIN
                else -> throw UsageException("Unbekanntes Verfahren: ${args[i]} (mpm oder yin)")
            }

            else -> {
                if (arg.startsWith("--")) throw UsageException("Unbekannte Option: $arg")
                if (input != null) throw UsageException("Nur eine Aufnahme angeben (zweite: $arg)")
                input = File(arg)
            }
        }
        i++
    }
    if (input == null) throw UsageException("Keine Aufnahme angegeben")
    if (a4 <= 0) throw UsageException("Kammerton muss positiv sein")
    val targetHz = target?.let { parseTargetHz(it, a4) ?: throw UsageException("Unbekannter Zielton: $it") }
    return Arguments(
        input,
        target,
        a4,
        out,
        ReplayOptions(targetHz, detector, calibrationSec = calibrate, match = match)
    )
}

private val hzPattern = Regex("(\\d+(?:[.,]\\d+)?)\\s*Hz", RegexOption.IGNORE_CASE)

/** Target in Hz from a note ("Kontra-B", "Bb1", 34) or a frequency ("58.27Hz"). */
fun parseTargetHz(text: String, a4Hz: Double): Double? {
    hzPattern.matchEntire(text.trim())?.let { return it.groupValues[1].replace(',', '.').toDouble() }
    return NoteNames.parseMidi(text)?.let { NoteMath.frequency(it, a4Hz) }
}

/** Runs the CLI and returns the exit code: 0 ok, 1 processing error, 2 invalid command line. */
fun run(args: List<String>, out: PrintStream = System.out, err: PrintStream = System.err): Int {
    if (args.isEmpty() || "--help" in args || "-h" in args) {
        out.print(USAGE)
        return if (args.isEmpty()) 2 else 0
    }
    val parsed = try {
        parseArguments(args)
    } catch (e: UsageException) {
        err.println("Fehler: ${e.message}\n")
        err.print(USAGE)
        return 2
    } catch (e: IllegalArgumentException) {
        err.println("Fehler: ${e.message}")
        return 2
    }
    return try {
        val clip = AudioFileReader.read(parsed.input)
        val result = Replay.run(clip.samples, clip.sampleRate, parsed.options)
        val dir = (parsed.outDir ?: parsed.input.absoluteFile.parentFile).apply { mkdirs() }
        val base = parsed.input.nameWithoutExtension
        val csv = File(dir, "$base.csv")
        val png = File(dir, "$base.png")
        val txt = File(dir, "$base.txt")
        val targetName = parsed.targetText?.let { describeTarget(it, parsed.options.targetHz!!, parsed.a4Hz) }
        Reports.writeCsv(result, csv, parsed.a4Hz)
        Chart.render(result, chartTitle(parsed.input.name, targetName, result), parsed.a4Hz, png)
        val summary = Reports.summary(result, parsed.input.name, targetName)
        txt.writeText(summary + "\n", Charsets.UTF_8)
        out.println(summary)
        out.println("CSV: ${csv.path}")
        out.println("PNG: ${png.path}")
        out.println("Zusammenfassung: ${txt.path}")
        0
    } catch (e: UnsupportedAudioFileException) {
        err.println("Fehler: Audioformat nicht lesbar (${e.message}). Unterstützt: WAV, AIFF.")
        1
    } catch (e: IllegalArgumentException) {
        err.println("Fehler: ${e.message}")
        1
    }
}

private fun describeTarget(text: String, hz: Double, a4Hz: Double): String {
    val midi = NoteMath.nearestMidi(hz, a4Hz)
    val exact = kotlin.math.abs(NoteMath.centsFromNote(hz, midi, a4Hz)) < 0.01
    return if (exact) NoteNames.germanOctaveName(midi) else text
}

private fun chartTitle(file: String, targetName: String?, result: ReplayResult): String {
    val note = result.note ?: return "$file – freie Erkennung"
    val outcome = if (note.hit) {
        String.format(Locale.GERMANY, "Treffer nach %.2f s", note.timeToHitSec)
    } else {
        "kein Treffer"
    }
    return String.format(Locale.GERMANY, "%s – Ziel %s (%.2f Hz) – %s", file, targetName, note.targetHz, outcome)
}

fun main(args: Array<String>) {
    // Without a console (e.g. under `gradlew run`) the JVM would print umlauts in the Windows code page.
    val charset = System.console()?.charset() ?: Charsets.UTF_8
    val out = PrintStream(FileOutputStream(FileDescriptor.out), true, charset)
    val err = PrintStream(FileOutputStream(FileDescriptor.err), true, charset)
    exitProcess(run(args.toList(), out, err))
}
