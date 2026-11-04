package io.github.blanaboo.stimmzug.dsp.synth

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sin

/**
 * One period of a periodic waveform built from harmonic [amplitudes] and [phases] (radians), peak-normalized to 1.
 * Read with a phase in cycles; linear interpolation over [size] points keeps the error far below audibility, and
 * the period – and therefore the pitch – stays exact because it is defined by the caller's phase accumulator.
 */
class Wavetable(amplitudes: DoubleArray, phases: DoubleArray = DoubleArray(amplitudes.size), val size: Int = 8192) {
    private val table = DoubleArray(size + 1)

    init {
        require(amplitudes.isNotEmpty()) { "need at least one harmonic" }
        require(phases.size == amplitudes.size) { "phases and amplitudes must have the same length" }
        for (i in 0 until size) {
            var v = 0.0
            val x = 2 * PI * i / size
            for (h in amplitudes.indices) v += amplitudes[h] * sin((h + 1) * x + phases[h])
            table[i] = v
        }
        val peak = table.maxOf { abs(it) }
        if (peak > 0) for (i in 0 until size) table[i] /= peak
        table[size] = table[0]
    }

    /** Waveform value at [phaseCycles] (any real number; only the fractional part matters). */
    fun valueAt(phaseCycles: Double): Double {
        val p = (phaseCycles - floor(phaseCycles)) * size
        val i = p.toInt().coerceIn(0, size - 1)
        val frac = p - i
        return table[i] + (table[i + 1] - table[i]) * frac
    }
}
