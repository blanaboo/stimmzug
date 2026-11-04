package io.github.blanaboo.stimmzug.dsp.pitch

import io.github.blanaboo.stimmzug.dsp.music.NoteMath
import io.github.blanaboo.stimmzug.dsp.synth.ToneSpec
import io.github.blanaboo.stimmzug.dsp.synth.TubaSpectrum
import io.github.blanaboo.stimmzug.dsp.synth.TubaToneGenerator

/** Shared scenarios and helpers for detector tests and the benchmark. */
object PitchTestSupport {
    const val FS = 48_000

    /** Tuba-relevant notes from A0 (27.5 Hz) to F5 (698 Hz) in steps of a major third. */
    val NOTES = (21..77 step 4).toList()

    /** A disturbance scenario applied to a tone at [frequencyHz]. */
    data class Scenario(val name: String, val spec: (Double) -> ToneSpec)

    val CLEAN = Scenario("sauber (mf)") { f -> ToneSpec(frequencyHz = f, toneSec = 1.0) }
    val SOFT = Scenario("leise (p, -24 dBFS)") { f ->
        ToneSpec(f, toneSec = 1.0, dynamic = TubaSpectrum.Dynamic.PIANO, tonePeakDbfs = -24.0, noiseRmsDbfs = -65.0)
    }
    val LOUD = Scenario("laut (f, -6 dBFS)") { f ->
        ToneSpec(f, toneSec = 1.0, dynamic = TubaSpectrum.Dynamic.FORTE, tonePeakDbfs = -6.0)
    }
    val PHONE = Scenario("Handy-Mikro (Hochpass 120 Hz, Rauschen)") { f ->
        ToneSpec(f, toneSec = 1.0, micHighPassHz = 120.0, noiseRmsDbfs = -50.0)
    }
    val HARSH = Scenario("Handy-Mikro hart (Hochpass 150 Hz, Rauschen, Clipping)") { f ->
        ToneSpec(f, toneSec = 1.0, micHighPassHz = 150.0, noiseRmsDbfs = -40.0, clipDbfs = -15.0)
    }
    val DRONE_OCTAVE_UP = Scenario("Drone eine Oktave höher über Lautsprecher (-24 dBFS)") { f ->
        ToneSpec(
            f,
            toneSec = 1.0,
            micHighPassHz = 120.0,
            noiseRmsDbfs = -55.0,
            bleedFrequencyHz = 2 * f,
            bleedPeakDbfs = -24.0
        )
    }

    val TONE_SCENARIOS = listOf(CLEAN, SOFT, LOUD, PHONE, HARSH, DRONE_OCTAVE_UP)

    fun frequency(midi: Int) = NoteMath.frequency(midi)

    /** Detects with the window ending at [endSec] of the rendered scenario. */
    fun detectAt(detector: PitchDetector, samples: DoubleArray, targetHz: Double?, endSec: Double): PitchEstimate =
        detector.detect(samples, FS.toDouble(), targetHz, end = (endSec * FS).toInt())

    fun render(spec: ToneSpec): DoubleArray = TubaToneGenerator.renderDouble(spec)
}
