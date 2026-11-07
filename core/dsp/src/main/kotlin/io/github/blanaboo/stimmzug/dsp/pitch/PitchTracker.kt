package io.github.blanaboo.stimmzug.dsp.pitch

import io.github.blanaboo.stimmzug.dsp.signal.TargetLowPass
import kotlin.math.ceil
import kotlin.math.roundToInt

/** One pitch estimate on the audio time line. [timeSec] is the time of the newest sample in the analysed window. */
data class PitchFrame(val timeSec: Double, val estimate: PitchEstimate)

/**
 * Streams audio chunks through a [PitchDetector] and emits a [PitchFrame] every [hopSec].
 *
 * With a target, the input first passes a [TargetLowPass] just above the target (ADR 0005), so that a drone from the
 * phone's loudspeaker does not take over the detection. The levels of the frames are measured after that filter,
 * so a level gate calibrated on these frames sees the same signal. [useTargetLowPass] = `false` turns the filter off
 * (for comparisons in the replay tool).
 *
 * Time is counted in samples (not wall clock), so results are deterministic and independent of CPU speed.
 * Not thread-safe: feed it from one audio thread.
 */
class PitchTracker(
    val sampleRate: Double,
    val detector: PitchDetector = MpmPitchDetector(),
    hopSec: Double = 0.02,
    historySec: Double = 0.6,
    useTargetLowPass: Boolean = true
) {
    private val lowPass = if (useTargetLowPass) TargetLowPass(sampleRate) else null

    /** Note the player should play; `null` = free search over the detector's whole range, without low-pass. */
    var targetHz: Double? = null
        set(value) {
            field = value
            lowPass?.targetHz = value
        }

    /** Cutoff of the low-pass in use, `null` if the input is not filtered. */
    val lowPassCutoffHz: Double? get() = lowPass?.cutoffHz

    val hopSamples: Int = (hopSec * sampleRate).roundToInt()
    private val capacity = ceil(historySec * sampleRate).toInt()
    private val ring = DoubleArray(capacity)
    private val work = DoubleArray(capacity)
    private var written = 0L
    private var nextAnalysis = hopSamples.toLong()

    init {
        require(sampleRate > 0 && hopSamples > 0) { "invalid sample rate or hop" }
    }

    val samplesProcessed: Long get() = written

    fun process(chunk: FloatArray, length: Int = chunk.size, onFrame: (PitchFrame) -> Unit) {
        for (i in 0 until length) {
            val x = chunk[i].toDouble()
            ring[(written % capacity).toInt()] = if (lowPass != null) lowPass.process(x) else x
            written++
            if (written >= nextAnalysis) {
                nextAnalysis += hopSamples
                analyze(onFrame)
            }
        }
    }

    /** Convenience for tests and offline replay. */
    fun processAll(samples: FloatArray, chunkSize: Int = 480): List<PitchFrame> {
        val frames = ArrayList<PitchFrame>()
        var offset = 0
        while (offset < samples.size) {
            val n = minOf(chunkSize, samples.size - offset)
            process(samples.copyOfRange(offset, offset + n), n) { frames += it }
            offset += n
        }
        return frames
    }

    private fun analyze(onFrame: (PitchFrame) -> Unit) {
        val needed = detector.requiredSamples(sampleRate, targetHz)
        require(needed <= capacity) { "history of $capacity samples is too short, detector needs $needed" }
        if (written < needed) return
        val start = written - needed
        for (i in 0 until needed) work[i] = ring[((start + i) % capacity).toInt()]
        onFrame(PitchFrame(written / sampleRate, detector.detect(work, sampleRate, targetHz, end = needed)))
    }
}
