package io.github.blanaboo.stimmzug.dsp.pitch

import io.github.blanaboo.stimmzug.dsp.music.NoteMath
import io.github.blanaboo.stimmzug.dsp.music.NoteNames
import io.github.blanaboo.stimmzug.dsp.pitch.PitchTestSupport.FS
import io.github.blanaboo.stimmzug.dsp.pitch.PitchTestSupport.NOTES
import io.github.blanaboo.stimmzug.dsp.pitch.PitchTestSupport.TONE_SCENARIOS
import java.io.File
import java.util.Locale
import kotlin.math.abs
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Compares MPM and YIN on synthetic tuba scenarios and writes a Markdown report to
 * `build/reports/benchmark/pitch-detectors.md`. MPM must meet the acceptance criteria of issue #11;
 * YIN results are informative (comparison for the detector choice, see docs/adr).
 */
class PitchDetectorBenchmarkTest {

    private data class Row(
        val scenario: String,
        val detector: String,
        val medianCents: Double,
        val p95Cents: Double,
        val maxCents: Double,
        val octaveErrors: Int,
        val unvoiced: Int,
        /** Windows reported as voiced when the scenario's disturbances play without the tuba. */
        val mutedVoiced: Int,
        /** Of those, windows within [HIT_RANGE_CENTS] of the target, i.e. they would pass as the target note. */
        val mutedHits: Int,
        val total: Int,
        val hasBleed: Boolean
    )

    private val windowEnds = listOf(0.45, 0.55, 0.65, 0.75, 0.85, 0.95)

    private fun evaluate(detector: PitchDetector, scenario: PitchTestSupport.Scenario): Row {
        val errors = ArrayList<Double>()
        var octaveErrors = 0
        var unvoiced = 0
        var mutedVoiced = 0
        var mutedHits = 0
        for (midi in NOTES) {
            val f = NoteMath.frequency(midi)
            val spec = scenario.spec(f)
            val samples = PitchTestSupport.render(spec)
            // Same noise, filter, clipping and drone bleed, but nobody plays.
            val muted = PitchTestSupport.render(spec.copy(tonePeakDbfs = null, seed = midi.toLong()))
            for (end in windowEnds) {
                val estimate = PitchTestSupport.detectAt(detector, samples, f, end)
                if (!estimate.isVoiced) {
                    unvoiced++
                } else {
                    val error = abs(NoteMath.cents(estimate.frequencyHz, f))
                    if (error > 600) octaveErrors++ else errors += error
                }
                val silent = PitchTestSupport.detectAt(detector, muted, f, end)
                if (silent.isVoiced) {
                    mutedVoiced++
                    if (abs(NoteMath.cents(silent.frequencyHz, f)) <= HIT_RANGE_CENTS) mutedHits++
                }
            }
        }
        errors.sort()
        fun percentile(p: Double) = if (errors.isEmpty()) Double.NaN else errors[((errors.size - 1) * p).toInt()]
        return Row(
            scenario = scenario.name,
            detector = detector.name,
            medianCents = percentile(0.5),
            p95Cents = percentile(0.95),
            maxCents = errors.lastOrNull() ?: Double.NaN,
            octaveErrors = octaveErrors,
            unvoiced = unvoiced,
            mutedVoiced = mutedVoiced,
            mutedHits = mutedHits,
            total = NOTES.size * windowEnds.size,
            hasBleed = scenario.spec(100.0).bleedFrequencyHz != null
        )
    }

    @Test
    fun `benchmark MPM and YIN`() {
        val detectors = listOf(MpmPitchDetector(), YinPitchDetector())
        val rows = TONE_SCENARIOS.flatMap { scenario -> detectors.map { evaluate(it, scenario) } }
        writeReport(rows)

        val mpm = rows.filter { it.detector == "MPM" }
        mpm.forEach {
            assertEquals(0, it.octaveErrors, "MPM octave errors in ${it.scenario}")
            assertEquals(0, it.unvoiced, "MPM unvoiced frames in ${it.scenario}")
            assertTrue(it.maxCents < 2.0, "MPM max error ${it.maxCents} ct in ${it.scenario}")
            assertEquals(0, it.mutedHits, "MPM reports the target without a tuba in ${it.scenario}")
            // Drone bleed is a real tone (an octave above the target); suppressing it is the level gate's job (#13).
            if (!it.hasBleed) assertEquals(0, it.mutedVoiced, "MPM reports pitch in pure noise in ${it.scenario}")
        }
        val clean = mpm.first { it.scenario == PitchTestSupport.CLEAN.name }
        assertTrue(clean.medianCents < 0.5 && clean.maxCents < 1.0, "MPM clean: $clean")
    }

    private fun writeReport(rows: List<Row>) {
        val notes = NOTES.joinToString(", ") { NoteNames.germanOctaveName(it) }
        val text = buildString {
            appendLine("# Benchmark Tonhöhenerkennung: MPM vs. YIN")
            appendLine()
            appendLine("Synthetische Tuba-Töne ($FS Hz), Ziel = Nominalton, je Ton ${windowEnds.size} Analysefenster.")
            appendLine("Töne: $notes.")
            appendLine()
            appendLine("Fehler in Cent über alle stimmhaften Fenster in der richtigen Oktave (Abweichung ≤ 600 ct).")
            appendLine()
            appendLine(
                "| Szenario | Verfahren | Median ct | 95 % ct | Max ct | Oktavfehler | stumm " +
                    "| ohne Tuba: Ton | ohne Tuba: Treffer |"
            )
            appendLine("|---|---|---:|---:|---:|---:|---:|---:|---:|")
            for (r in rows) {
                val cells = listOf(
                    r.scenario,
                    r.detector,
                    fmt(r.medianCents),
                    fmt(r.p95Cents),
                    fmt(r.maxCents),
                    r.octaveErrors.toString(),
                    "${r.unvoiced}/${r.total}",
                    "${r.mutedVoiced}/${r.total}",
                    "${r.mutedHits}/${r.total}"
                )
                appendLine(cells.joinToString(" | ", prefix = "| ", postfix = " |"))
            }
            appendLine()
            appendLine(
                "**Fehlauslösungen** („ohne Tuba“): dieselben Störungen des Szenarios (Rauschen, Hochpass, Clipping, " +
                    "Drone-Übersprechen) ohne Tuba-Ton. „Ton“ = als stimmhaft gemeldete Fenster, „Treffer“ = davon " +
                    "innerhalb ±${HIT_RANGE_CENTS.toInt()} ct des Ziels (würden als gespielter Zielton zählen). " +
                    "Der übersprechende Drone eine Oktave höher ist ein echter Ton und wird daher als Ton erkannt, " +
                    "aber nie als Zielton; ihn auszublenden ist Aufgabe des Pegel-Gates (#13)."
            )
        }
        val dir = File("build/reports/benchmark").apply { mkdirs() }
        File(dir, "pitch-detectors.md").writeText(text)
        println(text)
    }

    private fun fmt(v: Double) = if (v.isNaN()) "–" else String.format(Locale.ROOT, "%.3f", v)

    private companion object {
        const val HIT_RANGE_CENTS = 50.0
    }
}
