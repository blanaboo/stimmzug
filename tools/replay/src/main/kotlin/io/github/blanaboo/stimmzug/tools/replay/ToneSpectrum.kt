package io.github.blanaboo.stimmzug.tools.replay

import io.github.blanaboo.stimmzug.dsp.music.NoteMath
import io.github.blanaboo.stimmzug.dsp.pitch.PitchTracker
import io.github.blanaboo.stimmzug.dsp.signal.Fft
import io.github.blanaboo.stimmzug.dsp.signal.SignalMath
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/** Settings of the harmonic analysis of held notes. */
data class SpectrumOptions(
    val a4Hz: Double = 442.0,
    /** Harmonics are measured up to this frequency (the drone uses up to 6 kHz). */
    val maxHz: Double = 6000.0,
    /** Shorter sounds (attacks, noises) are not notes. */
    val minNoteSec: Double = 2.0,
    /** Measured from this long after the start of a note … */
    val skipStartSec: Double = 1.0,
    /** … to this long before its end: the steady part. */
    val skipEndSec: Double = 0.5,
    /** Takes whose pitch wanders more than this (standard deviation) are not used. */
    val maxSpreadCents: Double = 20.0,
    /** A note's table ends with its last harmonic at least this strong (relative to the strongest). */
    val floorDb: Double = -50.0,
    /** Harmonics closer to the room noise than this end a note's table. */
    val minSnrDb: Double = 10.0
) {
    init {
        require(a4Hz > 0.0 && maxHz > 0.0) { "a4 and maxHz must be positive" }
        require(minNoteSec > skipStartSec + skipEndSec) { "a note must be longer than the parts skipped" }
    }
}

/** Pitch of a held note in its steady part. */
data class NotePitch(
    /** Nearest tempered note at the analysis' a′. */
    val midi: Int,
    /** Median fundamental. */
    val f0Hz: Double,
    /** Deviation of [f0Hz] from the tempered note. */
    val cents: Double,
    /** Standard deviation of the pitch. */
    val spreadCents: Double
)

/** One held note found in the recording. Harmonic powers in dB with the room noise subtracted. */
class HeldNote(
    val startSec: Double,
    val endSec: Double,
    val pitch: NotePitch,
    val levelDbfs: Double,
    /** Power of harmonics 1..n in dB (index 0 = fundamental), up to [SpectrumOptions.maxHz]. */
    val powerDb: DoubleArray,
    /** Distance of each harmonic from the room noise in its band. */
    val snrDb: DoubleArray
) {
    val midi: Int get() = pitch.midi
    val f0Hz: Double get() = pitch.f0Hz
    val cents: Double get() = pitch.cents
    val spreadCents: Double get() = pitch.spreadCents
}

/** The levels of one note for the drone: averaged over its usable takes, or filled in from its neighbours. */
class NoteLevels(
    val midi: Int,
    /** Usable takes; 0 = filled in from the neighbouring notes. */
    val takes: Int,
    /** Mean fundamental of the takes, `null` if filled in. */
    val f0Hz: Double?,
    /** Harmonics 1..n in dB relative to the strongest, ending with the last reliable one. */
    val levelsDb: DoubleArray
)

/**
 * Measures the harmonic levels of a recording of held notes (#72): finds the notes by level, measures each one's
 * pitch with the app's detector and the power of every harmonic k·f0 in its band (k ± ½)·f0 from averaged
 * Hann-windowed spectra, minus the room noise of the longest quiet stretch in the same band.
 */
object ToneSpectrum {
    private const val HOP_SEC = 0.05
    private const val MERGE_GAP_SEC = 0.3
    private const val NOISE_PERCENTILE = 0.05
    private const val LOUD_PERCENTILE = 0.98
    private const val ABOVE_NOISE_DB = 20.0
    private const val BELOW_LOUD_DB = 40.0
    private const val QUIET_MARGIN_DB = 6.0
    private const val FFT_SEC = 0.35
    private const val MIN_CLARITY = 0.8
    private const val MIN_PITCH_FRAMES = 10
    private const val SAME_NOTE_CENTS = 50.0
    private const val TINY_POWER = 1e-30

    /** Weak harmonics inside a table (between stronger ones) are kept down to this far below the floor. */
    private const val BELOW_FLOOR_DB = 20.0

    fun findNotes(
        samples: FloatArray,
        sampleRate: Double,
        options: SpectrumOptions = SpectrumOptions()
    ): List<HeldNote> {
        val x = SignalMath.toDoubleArray(samples)
        val hop = (HOP_SEC * sampleRate).toInt()
        val levels = DoubleArray(x.size / hop) { SignalMath.rmsDb(x, it * hop, (it + 1) * hop) }
        require(levels.size > 1) { "recording too short" }
        val sorted = levels.sorted()
        val noise = sorted[(NOISE_PERCENTILE * sorted.size).toInt()]
        val loud = sorted[(LOUD_PERCENTILE * (sorted.size - 1)).toInt()]
        val threshold = max(noise + ABOVE_NOISE_DB, loud - BELOW_LOUD_DB)

        val fftSize = Fft.nextPowerOfTwo((FFT_SEC * sampleRate).toInt())
        val fft = Fft(fftSize)
        val quiet = longestRun(levels) { it < noise + QUIET_MARGIN_DB }
        val noisePower = meanPower(x, quiet.first * hop, quiet.last * hop + hop, fft)

        val analysis = Analysis(x, sampleRate, fft, noisePower, options)
        return runs(levels, hop, sampleRate, options) { it > threshold }.mapNotNull { (from, to) ->
            analysis.measure(from * hop, to * hop)
        }
    }

    /** Groups the takes by note, keeps the steady ones and fills gaps between the lowest and highest note. */
    fun combine(notes: List<HeldNote>, options: SpectrumOptions = SpectrumOptions()): List<NoteLevels> {
        val usable = notes.filter { it.spreadCents <= options.maxSpreadCents && abs(it.cents) < SAME_NOTE_CENTS }
        val measured = usable.groupBy { it.midi }.mapValues { (midi, takes) -> average(midi, takes, options) }
        if (measured.isEmpty()) return emptyList()
        return (measured.keys.min()..measured.keys.max()).map { midi ->
            measured[midi] ?: fillIn(midi, measured)
        }
    }

    private fun average(midi: Int, takes: List<HeldNote>, options: SpectrumOptions): NoteLevels {
        val count = takes.minOf { it.powerDb.size }
        val power = DoubleArray(count) { k -> takes.sumOf { 10.0.pow(it.powerDb[k] / 10) } / takes.size }
        val snr = DoubleArray(count) { k -> takes.minOf { it.snrDb[k] } }
        val peak = power.max()
        val relative = DoubleArray(count) { 10 * log10(max(power[it], TINY_POWER) / peak) }
        val last = (count - 1 downTo 0).firstOrNull { relative[it] >= options.floorDb && snr[it] >= options.minSnrDb }
            ?: relative.indices.maxBy { relative[it] }
        val levels = DoubleArray(last + 1) { max(relative[it], options.floorDb - BELOW_FLOOR_DB) }
        return NoteLevels(midi, takes.size, takes.map { it.f0Hz }.average(), levels)
    }

    /** A missing note gets the mean (in dB) of the nearest measured notes below and above. */
    private fun fillIn(midi: Int, measured: Map<Int, NoteLevels>): NoteLevels {
        val below = measured.getValue(measured.keys.filter { it < midi }.max())
        val above = measured.getValue(measured.keys.filter { it > midi }.min())
        val count = min(below.levelsDb.size, above.levelsDb.size)
        val mean = DoubleArray(count) { (below.levelsDb[it] + above.levelsDb[it]) / 2 }
        val peak = mean.max()
        return NoteLevels(midi, takes = 0, f0Hz = null, levelsDb = DoubleArray(count) { mean[it] - peak })
    }

    /** Measures the held notes of one recording against its room noise. */
    private class Analysis(
        val x: DoubleArray,
        val sampleRate: Double,
        val fft: Fft,
        val noisePower: DoubleArray,
        val options: SpectrumOptions
    ) {
        fun measure(start: Int, end: Int): HeldNote? {
            val from = start + (options.skipStartSec * sampleRate).toInt()
            val to = end - (options.skipEndSec * sampleRate).toInt()
            val f0s = pitches(from, to)
            if (f0s.size < MIN_PITCH_FRAMES) return null
            val f0 = f0s.sorted()[f0s.size / 2]
            val cents = f0s.map { NoteMath.cents(it, f0) }
            val spread = sqrt(cents.sumOf { it * it } / cents.size - cents.average().let { it * it })
            val midi = NoteMath.nearestMidi(f0, options.a4Hz)

            val power = meanPower(x, from, to, fft)
            val bin = sampleRate / fft.size
            val count = max(1, (min(options.maxHz, sampleRate / 2 - f0) / f0).toInt())
            val powerDb = DoubleArray(count)
            val snrDb = DoubleArray(count)
            for (k in 1..count) {
                val lo = max(1, ceil(((k - 0.5) * f0) / bin).toInt())
                val hi = min(power.size - 1, floor(((k + 0.5) * f0) / bin).toInt())
                var p = 0.0
                var q = 0.0
                for (i in lo..hi) {
                    p += power[i]
                    q += noisePower[i]
                }
                powerDb[k - 1] = 10 * log10(max(p - q, TINY_POWER))
                snrDb[k - 1] = 10 * log10(max(p, TINY_POWER) / max(q, TINY_POWER))
            }
            return HeldNote(
                startSec = start / sampleRate,
                endSec = end / sampleRate,
                pitch = NotePitch(midi, f0, NoteMath.centsFromNote(f0, midi, options.a4Hz), spread),
                levelDbfs = SignalMath.rmsDb(x, from, to),
                powerDb = powerDb,
                snrDb = snrDb
            )
        }

        /** Voiced pitches of the free search (as the app's detector without a target) in [from, to). */
        private fun pitches(from: Int, to: Int): List<Double> {
            val tracker = PitchTracker(sampleRate)
            val part = FloatArray(max(0, to - from)) { x[from + it].toFloat() }
            return tracker.processAll(part)
                .filter { it.estimate.isVoiced && it.estimate.clarity >= MIN_CLARITY }
                .map { it.estimate.frequencyHz }
        }
    }

    /**
     * Mean power spectrum of Hann windows with 50 % overlap over [from, to), one shorter window if the stretch is
     * short. Each window's spectrum is divided by its energy Σw², so band powers do not depend on its length.
     */
    private fun meanPower(x: DoubleArray, from: Int, to: Int, fft: Fft): DoubleArray {
        val n = fft.size
        val sum = DoubleArray(n / 2)
        val re = DoubleArray(n)
        val im = DoubleArray(n)
        var windows = 0
        var offset = from
        while (offset + n <= to || windows == 0) {
            val length = min(n, to - offset)
            if (length < 2) break
            re.fill(0.0)
            im.fill(0.0)
            var energy = 0.0
            for (i in 0 until length) {
                val w = 0.5 - 0.5 * cos(2 * PI * i / (length - 1))
                re[i] = x[offset + i] * w
                energy += w * w
            }
            fft.forward(re, im)
            for (i in sum.indices) sum[i] += (re[i] * re[i] + im[i] * im[i]) / energy
            windows++
            offset += n / 2
        }
        if (windows > 1) for (i in sum.indices) sum[i] /= windows
        return sum
    }

    /** Runs of frames where [loud] holds, gaps up to [MERGE_GAP_SEC] bridged, at least [SpectrumOptions.minNoteSec]. */
    private fun runs(
        levels: DoubleArray,
        hop: Int,
        sampleRate: Double,
        options: SpectrumOptions,
        loud: (Double) -> Boolean
    ): List<Pair<Int, Int>> {
        val gap = (MERGE_GAP_SEC * sampleRate / hop).toInt()
        val minFrames = (options.minNoteSec * sampleRate / hop).toInt()
        val result = ArrayList<Pair<Int, Int>>()
        var start = -1
        var lastLoud = -1
        for (f in levels.indices) {
            if (!loud(levels[f])) continue
            if (start >= 0 && f - lastLoud > gap + 1) {
                if (lastLoud + 1 - start >= minFrames) result += start to lastLoud + 1
                start = -1
            }
            if (start < 0) start = f
            lastLoud = f
        }
        if (start >= 0 && lastLoud + 1 - start >= minFrames) result += start to lastLoud + 1
        return result
    }

    /** Longest run of frames where [quiet] holds (at least one frame). */
    private fun longestRun(levels: DoubleArray, quiet: (Double) -> Boolean): IntRange {
        var best = 0..0
        var start = -1
        for (f in 0..levels.size) {
            val q = f < levels.size && quiet(levels[f])
            if (q && start < 0) start = f
            if (!q && start >= 0) {
                if (f - start > best.last - best.first + 1) best = start until f
                start = -1
            }
        }
        return best
    }
}
