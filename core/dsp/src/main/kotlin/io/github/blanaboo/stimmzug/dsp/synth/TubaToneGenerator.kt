package io.github.blanaboo.stimmzug.dsp.synth

import io.github.blanaboo.stimmzug.dsp.signal.Biquad
import io.github.blanaboo.stimmzug.dsp.signal.SignalMath
import java.util.Random
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Description of a synthetic "recording": an optional tuba tone plus the disturbances a phone microphone sees.
 *
 * Time layout: [leadInSec] silence, then the tone ([toneSec] including attack, followed by the release), then
 * [tailSec]. Bleed and noise run for the whole length.
 */
data class ToneSpec(
    val frequencyHz: Double,
    val toneSec: Double = 2.0,
    val sampleRate: Int = 48_000,
    val dynamic: TubaSpectrum.Dynamic = TubaSpectrum.Dynamic.MEZZO,
    /** Peak level of the tuba tone in dBFS before noise/filtering; `null` = no tuba at all (e.g. only drone bleed). */
    val tonePeakDbfs: Double? = -12.0,
    val leadInSec: Double = 0.0,
    val tailSec: Double = 0.0,
    val attackSec: Double = 0.08,
    val releaseSec: Double = 0.15,
    /** Deterministic pitch contour in cents relative to [frequencyHz], as a function of time since tone start. */
    val centsContour: (Double) -> Double = { 0.0 },
    /** Standard deviation of a stationary random pitch wander (Ornstein–Uhlenbeck process) in cents. */
    val jitterCents: Double = 0.0,
    val jitterTimeConstantSec: Double = 0.25,
    val vibratoRateHz: Double = 0.0,
    val vibratoDepthCents: Double = 0.0,
    /** White noise RMS level in dBFS, `null` = no noise. */
    val noiseRmsDbfs: Double? = null,
    /** Second-order high-pass modelling the phone microphone's low-frequency roll-off, `null` = flat. */
    val micHighPassHz: Double? = null,
    /** Hard clipping level in dBFS, `null` = no clipping. */
    val clipDbfs: Double? = null,
    /** Drone leaking from the loudspeaker into the microphone: frequency and peak level (dBFS). */
    val bleedFrequencyHz: Double? = null,
    val bleedPeakDbfs: Double = -30.0,
    val seed: Long = 1L
) {
    val totalSec: Double get() = leadInSec + toneSec + releaseSec + tailSec
    val totalSamples: Int get() = (totalSec * sampleRate).toInt()
}

/** Renders [ToneSpec]s. Deterministic for a given seed, so test failures are reproducible. */
object TubaToneGenerator {

    fun render(spec: ToneSpec): FloatArray {
        val out = renderDouble(spec)
        return FloatArray(out.size) { out[it].toFloat() }
    }

    fun renderDouble(spec: ToneSpec): DoubleArray {
        val fs = spec.sampleRate.toDouble()
        val out = DoubleArray(spec.totalSamples)
        val random = Random(spec.seed)

        spec.tonePeakDbfs?.let { renderTone(spec, out, random, SignalMath.dbToAmplitude(it)) }
        spec.bleedFrequencyHz?.let {
            renderSteadyTone(it, fs, out, SignalMath.dbToAmplitude(spec.bleedPeakDbfs), random)
        }
        spec.noiseRmsDbfs?.let {
            val sigma = SignalMath.dbToAmplitude(it)
            for (i in out.indices) out[i] += sigma * random.nextGaussian()
        }
        spec.micHighPassHz?.let { Biquad.highPass(it, fs).processInPlace(out) }
        spec.clipDbfs?.let {
            val limit = SignalMath.dbToAmplitude(it)
            for (i in out.indices) out[i] = out[i].coerceIn(-limit, limit)
        }
        return out
    }

    private fun renderTone(spec: ToneSpec, out: DoubleArray, random: Random, peak: Double) {
        val fs = spec.sampleRate.toDouble()
        val table = tubaWavetable(spec.frequencyHz, fs, spec.dynamic, random)
        val start = (spec.leadInSec * fs).toInt()
        val sustainEnd = start + (spec.toneSec * fs).toInt()
        val end = min(out.size, sustainEnd + (spec.releaseSec * fs).toInt())
        val dt = 1.0 / fs
        val tau = spec.jitterTimeConstantSec
        val jitterStep = spec.jitterCents * sqrt(2.0 * dt / tau)
        var jitter = if (spec.jitterCents > 0) spec.jitterCents * random.nextGaussian() else 0.0
        var phase = 0.0
        for (i in start until end) {
            val t = (i - start) * dt
            if (spec.jitterCents > 0) jitter += -jitter * dt / tau + jitterStep * random.nextGaussian()
            val vibrato = if (spec.vibratoDepthCents >
                0
            ) {
                spec.vibratoDepthCents * sin(2 * PI * spec.vibratoRateHz * t)
            } else {
                0.0
            }
            val cents = spec.centsContour(t) + jitter + vibrato
            val f = spec.frequencyHz * 2.0.pow(cents / 1200.0)
            out[i] += peak * envelope(i, start, sustainEnd, spec, fs) * table.valueAt(phase)
            phase += f * dt
            if (phase >= 1.0) phase -= 1.0
        }
    }

    private fun envelope(i: Int, start: Int, sustainEnd: Int, spec: ToneSpec, fs: Double): Double {
        val sinceStart = (i - start) / fs
        val attack = if (spec.attackSec > 0) min(1.0, sinceStart / spec.attackSec) else 1.0
        if (i < sustainEnd) return attack
        val sinceRelease = (i - sustainEnd) / fs
        return attack * (1.0 - sinceRelease / spec.releaseSec).coerceAtLeast(0.0)
    }

    private fun renderSteadyTone(frequencyHz: Double, fs: Double, out: DoubleArray, peak: Double, random: Random) {
        val table = tubaWavetable(frequencyHz, fs, TubaSpectrum.Dynamic.MEZZO, random)
        val increment = frequencyHz / fs
        var phase = 0.0
        for (i in out.indices) {
            out[i] += peak * table.valueAt(phase)
            phase += increment
            if (phase >= 1.0) phase -= 1.0
        }
    }

    /** Tuba-like single-cycle waveform with random (seeded) harmonic phases. */
    fun tubaWavetable(
        frequencyHz: Double,
        sampleRate: Double,
        dynamic: TubaSpectrum.Dynamic,
        random: Random
    ): Wavetable {
        val amplitudes = TubaSpectrum.harmonicAmplitudes(frequencyHz, sampleRate, dynamic)
        val phases = DoubleArray(amplitudes.size) { 2 * PI * random.nextDouble() }
        return Wavetable(amplitudes, phases)
    }
}
