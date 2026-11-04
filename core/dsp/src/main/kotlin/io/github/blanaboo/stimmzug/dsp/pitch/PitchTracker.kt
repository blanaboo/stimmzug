package io.github.blanaboo.stimmzug.dsp.pitch

import kotlin.math.ceil
import kotlin.math.roundToInt

/** One pitch estimate on the audio time line. [timeSec] is the time of the newest sample in the analysed window. */
data class PitchFrame(val timeSec: Double, val estimate: PitchEstimate)

/**
 * Streams audio chunks through a [PitchDetector] and emits a [PitchFrame] every [hopSec].
 *
 * Time is counted in samples (not wall clock), so results are deterministic and independent of CPU speed.
 * Not thread-safe: feed it from one audio thread.
 */
class PitchTracker(
    val sampleRate: Double,
    val detector: PitchDetector = MpmPitchDetector(),
    hopSec: Double = 0.02,
    historySec: Double = 0.6
) {
    /** Note the player should play; `null` = free search over the detector's whole range. */
    var targetHz: Double? = null

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
            ring[(written % capacity).toInt()] = chunk[i].toDouble()
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
