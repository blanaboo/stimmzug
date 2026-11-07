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
       replay <labor-sitzung> [--detector mpm|yin] [--no-lowpass] [--out <ordner>]

Spielt eine Aufnahme durch Tonhöhenerkennung, Pegel-Gate und Treffer-Logik und schreibt
<name>.csv (ein Frame pro Zeile), <name>.png (Diagramm) und <name>.txt (Zusammenfassung).

Ein Ordner einer Sitzung des Audio-Labors (takes.csv, segments.csv, WAV-Dateien) wird komplett
ausgewertet: report.md, report.csv und je Aufnahme Diagramm und CSV in <out>/charts/.
Standard für <out> ist <labor-sitzung>/auswertung. Zielton und Kalibrierung kommen aus der Sitzung.

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
  --no-lowpass          Ohne Tiefpass vor der Erkennung. Standard wie in der App: mit Zielton
                        Tiefpass bei max(250 Hz, 1,4 × Zielton) gegen den Drone aus dem Lautsprecher.
  --out <ordner>       Ausgabeordner (Standard: neben der Aufnahme)
"""

/** Thrown for invalid command lines; the message is shown with the usage. */
class UsageException(message: String) : Exception(message)

private fun usageError(message: String): Nothing = throw UsageException(message)

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
    var lowPass = true
    var i = 0
    fun value(): String = args.getOrNull(++i) ?: usageError("Wert fehlt für ${args[i - 1]}")
    fun number(): Double = value().replace(',', '.').toDoubleOrNull() ?: usageError("Keine Zahl: ${args[i]}")
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

            "--no-lowpass" -> lowPass = false

            "--detector" -> detector = when (value().lowercase()) {
                "mpm" -> DetectorChoice.MPM
                "yin" -> DetectorChoice.YIN
                else -> usageError("Unbekanntes Verfahren: ${args[i]} (mpm oder yin)")
            }

            else -> {
                if (arg.startsWith("--")) usageError("Unbekannte Option: $arg")
                if (input != null) usageError("Nur eine Aufnahme angeben (zweite: $arg)")
                input = File(arg)
            }
        }
        i++
    }
    if (input == null) usageError("Keine Aufnahme angegeben")
    if (a4 <= 0) usageError("Kammerton muss positiv sein")
    val targetHz = target?.let { parseTargetHz(it, a4) ?: usageError("Unbekannter Zielton: $it") }
    return Arguments(
        input,
        target,
        a4,
        out,
        ReplayOptions(targetHz, detector, calibrationSec = calibrate, match = match, targetLowPass = lowPass)
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
    if (parsed.input.isDirectory) return runLab(parsed, out, err)
    return try {
        val clip = AudioFileReader.read(parsed.input)
        val result = Replay.run(clip.samples, clip.sampleRate, parsed.options)
        val dir = (parsed.outDir ?: parsed.input.absoluteFile.parentFile).apply { mkdirs() }
        val base = parsed.input.nameWithoutExtension
        val csv = File(dir, "$base.csv")
        val png = File(dir, "$base.png")
        val txt = File(dir, "$base.txt")
        val targetHz = parsed.options.targetHz
        val targetName = parsed.targetText?.let { text -> targetHz?.let { describeTarget(text, it, parsed.a4Hz) } }
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

/** Evaluates a session folder of the audio lab, see [LabReport]. */
private fun runLab(parsed: Arguments, out: PrintStream, err: PrintStream): Int {
    val dir = parsed.input
    if (!LabReport.isSession(dir)) {
        err.println("Fehler: $dir ist kein Ordner einer Labor-Sitzung (takes.csv oder segments.csv fehlt)")
        return 2
    }
    if (parsed.targetText != null || parsed.options.calibrationSec > 0.0) {
        err.println("Fehler: Bei einer Labor-Sitzung kommen Zielton und Kalibrierung aus der Sitzung")
        return 2
    }
    return try {
        val outDir = (parsed.outDir ?: File(dir, "auswertung")).apply { mkdirs() }
        val outcome = LabReport.run(dir, outDir, parsed.options)
        out.println("Labor-Sitzung ${dir.name}: ${outcome.analyzed} Aufnahmen ausgewertet")
        outcome.skipped.forEach { out.println("Nicht ausgewertet: $it") }
        out.println("Bericht: ${outcome.markdown.path}")
        out.println("Tabelle: ${outcome.csv.path}")
        0
    } catch (e: UnsupportedAudioFileException) {
        err.println("Fehler: Audioformat nicht lesbar (${e.message})")
        1
    } catch (e: NoSuchElementException) {
        err.println("Fehler: Sitzungsdateien unvollständig (${e.message})")
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
