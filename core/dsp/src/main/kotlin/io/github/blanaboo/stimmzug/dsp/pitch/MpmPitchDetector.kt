package io.github.blanaboo.stimmzug.dsp.pitch

/**
 * McLeod Pitch Method (McLeod & Wyvill, "A smarter way to find pitch", 2005).
 *
 * The normalized square difference function n(τ) = 2·r(τ) / m(τ) lies in −1..1 and peaks at multiples of the
 * period; its peak height is a natural clarity measure. Free search uses MPM's key-maximum rule (first maximum
 * reaching [keyMaximumThreshold] of the highest one); target-guided search uses the octave-checked choice of
 * [LagDomainDetector].
 */
class MpmPitchDetector(config: DetectorConfig = DetectorConfig(), val keyMaximumThreshold: Double = 0.9) :
    LagDomainDetector(config) {
    override val name = "MPM"
    private var nsdf = DoubleArray(0)

    override fun pick(plan: Plan, levelDb: Double): PitchEstimate {
        if (nsdf.size < acf.size) nsdf = DoubleArray(acf.size)
        for (lag in 0..plan.analysisLag) {
            nsdf[lag] = if (energy[lag] > 0.0) 2.0 * acf[lag] / energy[lag] else 0.0
        }
        val index = if (plan.targeted) pickTargeted(nsdf, plan) else pickKeyMaximum(plan)
        return estimateAt(nsdf, index, plan, levelDb)
    }

    private fun pickKeyMaximum(plan: Plan): Int {
        val last = plan.analysisLag - 1
        // Skip the positive lobe around lag 0, then collect the maximum of every positive lobe.
        var lag = 1
        while (lag <= last && nsdf[lag] > 0.0) lag++
        val keys = ArrayList<Int>()
        var lobeMax = -1
        while (lag <= last) {
            if (nsdf[lag] > 0.0) {
                if (lobeMax < 0 || nsdf[lag] > nsdf[lobeMax]) lobeMax = lag
            } else if (lobeMax >= 0) {
                keys += lobeMax
                lobeMax = -1
            }
            lag++
        }
        if (lobeMax >= 0) keys += lobeMax
        val candidates = keys.filter { it in plan.minLag..plan.maxLag }
        if (candidates.isEmpty()) return -1
        val highest = candidates.maxOf { nsdf[it] }
        return candidates.first { nsdf[it] >= keyMaximumThreshold * highest }
    }
}
