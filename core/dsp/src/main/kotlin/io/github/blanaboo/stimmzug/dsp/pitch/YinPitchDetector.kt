package io.github.blanaboo.stimmzug.dsp.pitch

import kotlin.math.max

/**
 * YIN (de Cheveigné & Kawahara, 2002), used as a comparison detector in the benchmark.
 *
 * The difference function d(τ) = m(τ) − 2·r(τ) is normalized cumulatively (CMNDF d′); its dips mark the period.
 * Internally the curve 1 − d′ is used as periodicity so that targeted search and clarity work like for MPM.
 */
class YinPitchDetector(
    config: DetectorConfig = DetectorConfig(),
    /** Absolute threshold on d′ for the free search. */
    val threshold: Double = 0.15
) : LagDomainDetector(config) {
    override val name = "YIN"
    private var periodicity = DoubleArray(0)

    override fun pick(plan: Plan, levelDb: Double): PitchEstimate {
        if (periodicity.size < acf.size) periodicity = DoubleArray(acf.size)
        periodicity[0] = 0.0
        var running = 0.0
        for (lag in 1..plan.analysisLag) {
            val d = max(0.0, energy[lag] - 2.0 * acf[lag])
            running += d
            val cmnd = if (running > 0.0) d * lag / running else 1.0
            periodicity[lag] = 1.0 - cmnd
        }
        val index = if (plan.targeted) pickTargeted(periodicity, plan) else pickAbsoluteThreshold(plan)
        return estimateAt(periodicity, index, plan, levelDb)
    }

    private fun pickAbsoluteThreshold(plan: Plan): Int {
        val last = plan.analysisLag - 1
        val to = minOf(plan.maxLag, last)
        var lag = max(plan.minLag, 1)
        while (lag <= to) {
            if (1.0 - periodicity[lag] < threshold) {
                // follow the dip down to its bottom
                while (lag < last && periodicity[lag + 1] > periodicity[lag]) lag++
                return lag
            }
            lag++
        }
        // No dip below the threshold: fall back to the deepest dip (the clarity check decides about voicing).
        var best = -1
        for (i in max(plan.minLag, 1)..to) if (best < 0 || periodicity[i] > periodicity[best]) best = i
        return best
    }
}
