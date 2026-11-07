package io.github.blanaboo.stimmzug.dsp.pitch

import io.github.blanaboo.stimmzug.dsp.signal.Autocorrelation
import io.github.blanaboo.stimmzug.dsp.signal.Biquad
import io.github.blanaboo.stimmzug.dsp.signal.Fft
import io.github.blanaboo.stimmzug.dsp.signal.SignalMath
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/** Settings shared by the lag-domain detectors (MPM, YIN). */
data class DetectorConfig(
    val minHz: Double = 25.0,
    val maxHz: Double = 1000.0,
    /** Analysis window length in periods of the target note. */
    val periodsInWindow: Double = 5.0,
    val minWindowSec: Double = 0.06,
    /** Target-guided search spans ±this many semitones around the target. */
    val searchSemitones: Double = 6.0,
    /** Estimates with a lower clarity are reported as unvoiced. */
    val minClarity: Double = 0.6,
    /** Octave up if the periodicity at half the candidate lag reaches this fraction of the candidate's. */
    val octaveUpRatio: Double = 0.9,
    /** Octave down if the periodicity at twice the candidate lag exceeds the candidate's by this margin. */
    val octaveDownMargin: Double = 0.1
) {
    init {
        require(minHz > 0 && maxHz > minHz) { "invalid frequency range $minHz..$maxHz" }
    }
}

/**
 * Shared machinery of time-domain period detectors: window planning, decimation for low notes, DC removal and the
 * autocorrelation r(τ) plus the energy term m(τ) = Σ (x[j]² + x[j+τ]²) of the window. Subclasses turn these into
 * their periodicity function and pick the period. Instances keep work buffers and are **not** thread-safe.
 */
abstract class LagDomainDetector(val config: DetectorConfig) : PitchDetector {

    /** How a window is analysed for a given sample rate and target. Lags are in decimated samples. */
    protected data class Plan(
        val decimation: Int,
        val decimatedRate: Double,
        val window: Int,
        /** Candidate search range (target-guided: ±searchSemitones; free: minHz..maxHz). */
        val minLag: Int,
        val maxLag: Int,
        /** Largest lag computed (covers the octave-down check). */
        val analysisLag: Int,
        val inputSamples: Int,
        val targeted: Boolean
    )

    private val autocorrelations = HashMap<Int, Autocorrelation>()
    protected var work = DoubleArray(0)
    protected var acf = DoubleArray(0)
    protected var energy = DoubleArray(0)

    override fun requiredSamples(sampleRate: Double, targetHz: Double?): Int = plan(sampleRate, targetHz).inputSamples

    override fun detect(samples: DoubleArray, sampleRate: Double, targetHz: Double?, end: Int): PitchEstimate {
        val plan = plan(sampleRate, targetHz)
        require(end <= samples.size && end >= plan.inputSamples) {
            "need ${plan.inputSamples} samples before index $end"
        }
        val levelDb = SignalMath.rmsDb(samples, end - plan.window * plan.decimation, end)
        prepare(samples, end, plan)
        if (energy[0] <= 0.0) return PitchEstimate.unvoiced(levelDb)
        return pick(plan, levelDb)
    }

    /** Chooses the period from [acf]/[energy] (valid for lags 0..plan.analysisLag). */
    protected abstract fun pick(plan: Plan, levelDb: Double): PitchEstimate

    protected fun plan(fs: Double, targetHz: Double?): Plan {
        require(fs > 0) { "sample rate must be positive" }
        return if (targetHz != null) targetedPlan(fs, targetHz) else freePlan(fs)
    }

    private fun targetedPlan(fs: Double, targetHz: Double): Plan {
        require(targetHz in config.minHz..config.maxHz) {
            "target $targetHz Hz outside ${config.minHz}..${config.maxHz}"
        }
        // Low notes have long periods: decimate (keeping >= ~11 kHz) to cut the work without losing precision.
        val preferred = when {
            targetHz < 90.0 -> 4
            targetHz < 180.0 -> 2
            else -> 1
        }
        val decimation = max(1, min(preferred, (fs / 11_000.0).toInt()))
        val rate = fs / decimation
        val period = rate / targetHz
        val ratio = 2.0.pow(config.searchSemitones / 12.0)
        val minLag = max(2, floor(period / ratio).toInt())
        val maxLag = ceil(period * ratio).toInt()
        val analysisLag = ceil(2.0 * period * ratio).toInt() + 3
        val window = maxOf(
            ceil(config.periodsInWindow * period).toInt(),
            ceil(config.minWindowSec * rate).toInt(),
            analysisLag + ceil(period).toInt()
        )
        return Plan(decimation, rate, window, minLag, maxLag, analysisLag, inputSamples(window, decimation), true)
    }

    private fun freePlan(fs: Double): Plan {
        val decimation = max(1, min(2, (fs / 20_000.0).toInt()))
        val rate = fs / decimation
        val minLag = max(2, floor(rate / config.maxHz).toInt())
        val maxLag = ceil(rate / config.minHz).toInt()
        val analysisLag = maxLag + 2
        val window = max(2 * maxLag + 2, ceil(config.minWindowSec * rate).toInt())
        return Plan(decimation, rate, window, minLag, maxLag, analysisLag, inputSamples(window, decimation), false)
    }

    private fun inputSamples(window: Int, decimation: Int) = window * decimation + warmup(decimation)

    private fun warmup(decimation: Int) = if (decimation > 1) WARMUP_PER_DECIMATION * decimation else 0

    private fun prepare(samples: DoubleArray, end: Int, plan: Plan) {
        val autocorrelation = ensureCapacity(plan)
        val window = plan.window
        val d = plan.decimation
        if (d == 1) {
            System.arraycopy(samples, end - window, work, 0, window)
        } else {
            // 4th-order Butterworth low-pass (two biquads) at 80 % of the new Nyquist, then keep every d-th sample.
            val cutoff = 0.4 * plan.decimatedRate
            val fs = plan.decimatedRate * d
            val stage1 = Biquad.lowPass(cutoff, fs, Biquad.BUTTERWORTH4_Q1)
            val stage2 = Biquad.lowPass(cutoff, fs, Biquad.BUTTERWORTH4_Q2)
            val start = end - plan.inputSamples
            val firstKept = start + warmup(d)
            var out = 0
            for (i in start until end) {
                val y = stage2.process(stage1.process(samples[i]))
                if (i >= firstKept && (i - firstKept) % d == d - 1) work[out++] = y
            }
            check(out == window) { "decimation produced $out samples, expected $window" }
        }
        var mean = 0.0
        for (i in 0 until window) mean += work[i]
        mean /= window
        for (i in 0 until window) work[i] -= mean

        autocorrelation.compute(work, window, plan.analysisLag, acf)
        var m = 0.0
        for (i in 0 until window) m += work[i] * work[i]
        energy[0] = 2.0 * m
        for (lag in 1..plan.analysisLag) {
            val a = work[lag - 1]
            val b = work[window - lag]
            energy[lag] = energy[lag - 1] - a * a - b * b
        }
    }

    /** Grows the work buffers for [plan] and returns the autocorrelation to use. */
    private fun ensureCapacity(plan: Plan): Autocorrelation {
        if (work.size < plan.window) work = DoubleArray(plan.window)
        if (acf.size <= plan.analysisLag) {
            acf = DoubleArray(plan.analysisLag + 1)
            energy = DoubleArray(plan.analysisLag + 1)
        }
        // One autocorrelation per FFT size, so high notes (short windows) do not pay for a large FFT that was
        // needed for a low note earlier. Window sizes only take a handful of power-of-two FFT sizes.
        val fftSize = Fft.nextPowerOfTwo(2 * plan.window)
        return autocorrelations.getOrPut(fftSize) { Autocorrelation(fftSize / 2) }
    }

    /**
     * Target-guided choice on a periodicity [curve] (1 = perfectly periodic): the highest local maximum within the
     * search range, then the octave checks. Returns the lag index or -1 if there is no positive peak.
     *
     * - Octave down: a tone at half the target frequency only has weak side maxima near the target period
     *   (fractions of its true period); its real period shows up as a clearly higher peak in the octave below.
     * - Octave up: a tone at twice the target frequency also repeats after the target period, so the candidate
     *   looks fine – but it is just as periodic at half the lag.
     *
     * The octave-down check must come first: the octave-up check compares a ratio, which a weak side maximum and an
     * equally weak value at half its lag also satisfy. A tone at the target or an octave above is about as periodic
     * at twice the period as at the period, so the octave-down check never fires for them.
     *
     * The octave below is only taken if it lies within [DetectorConfig.minHz]: very low tuba notes in ff can show
     * period doubling (a subharmonic from slightly alternating lip vibration) that is as periodic as the tone itself,
     * and a "tone" at e.g. 21 Hz cannot be played anyway (seen in the Iowa recordings of E1 and F1).
     */
    protected fun pickTargeted(curve: DoubleArray, plan: Plan): Int {
        val last = plan.analysisLag - 1
        val best = highestPeak(curve, plan.minLag, min(plan.maxLag, last))
        val lowestLag = plan.decimatedRate / config.minHz
        val lower = highestPeak(curve, 2 * plan.minLag, min(min(2 * plan.maxLag, last), lowestLag.toInt()))
        if (best < 0 || curve[best] <= 0.0) return if (lower > 0 && curve[lower] > 0.0) lower else -1
        if (lower > 0 && curve[lower] >= curve[best] + config.octaveDownMargin) return lower
        val half = localMaxNear(curve, best / 2.0, radius = 2, lo = 2, hi = last)
        if (half > 0 && curve[half] >= config.octaveUpRatio * curve[best]) return half
        return best
    }

    /** Highest strict local maximum of [curve] with lag in [from, to], or -1. */
    private fun highestPeak(curve: DoubleArray, from: Int, to: Int): Int {
        var best = -1
        for (lag in max(from, 1)..to) {
            val isPeak = curve[lag] > curve[lag - 1] && curve[lag] >= curve[lag + 1]
            if (isPeak && (best < 0 || curve[lag] > curve[best])) best = lag
        }
        return best
    }

    /** Turns a chosen lag on [curve] into an estimate (parabolic refinement, clarity threshold). */
    protected fun estimateAt(curve: DoubleArray, index: Int, plan: Plan, levelDb: Double): PitchEstimate {
        if (index < 1 || index >= plan.analysisLag) return PitchEstimate.unvoiced(levelDb)
        val (lag, value) = parabolicPeak(curve, index)
        val clarity = value.coerceIn(0.0, 1.0)
        if (clarity < config.minClarity || lag <= 0.0) return PitchEstimate.unvoiced(levelDb, clarity)
        return PitchEstimate(plan.decimatedRate / lag, clarity, levelDb)
    }

    /** Index of the largest value of [curve] in [center − radius, center + radius] ∩ [lo, hi], or -1. */
    protected fun localMaxNear(curve: DoubleArray, center: Double, radius: Int, lo: Int, hi: Int): Int {
        val c = center.roundToInt()
        val from = max(lo, c - radius)
        val to = min(hi, c + radius)
        if (from > to) return -1
        var best = from
        for (i in from..to) if (curve[i] > curve[best]) best = i
        return best
    }

    protected companion object {
        const val WARMUP_PER_DECIMATION = 64

        /** Vertex of the parabola through (i−1, i, i+1): refined position and value. */
        fun parabolicPeak(curve: DoubleArray, i: Int): Pair<Double, Double> {
            val a = curve[i - 1]
            val b = curve[i]
            val c = curve[i + 1]
            val denominator = a - 2.0 * b + c
            if (denominator == 0.0) return i.toDouble() to b
            val delta = (0.5 * (a - c) / denominator).coerceIn(-1.0, 1.0)
            return (i + delta) to (b - 0.25 * (a - c) * delta)
        }
    }
}
