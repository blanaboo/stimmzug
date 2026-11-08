package io.github.blanaboo.stimmzug.data.settings

import io.github.blanaboo.stimmzug.dsp.match.MatchConfig
import io.github.blanaboo.stimmzug.dsp.session.SessionConfig
import io.github.blanaboo.stimmzug.dsp.synth.DroneTimbre

/** What the practice screen shows while the player plays. */
enum class DisplayMode {
    /** Only "heard" and "in range" with a filling ring, no direction: the ear has to find the pitch. */
    EAR,

    /** Deviation in cents with a needle. */
    TUNER
}

/** Where the drone sounds (#61, ADR 0005). */
enum class DroneOutput {
    /** The system's choice: headphones when connected, else the loudspeaker. */
    AUTO,

    /** Always the phone's loudspeaker, even with headphones connected. */
    SPEAKER
}

/** The player's practice settings (#68). Whole numbers, so sliders snap to clear values. */
data class PracticeSettings(
    val a4Hz: Int = DEFAULT_A4_HZ,
    val toleranceCents: Int = DEFAULT_TOLERANCE_CENTS,
    val holdSec: Int = DEFAULT_HOLD_SEC,
    val display: DisplayMode = DisplayMode.EAR,
    val droneOutput: DroneOutput = DroneOutput.AUTO,
    /** The owner's recorded B♭ tuba by default (#72); the synthetic model stays for comparison. */
    val droneTimbre: DroneTimbre = DroneTimbre.RECORDED
) {
    init {
        require(a4Hz in A4_RANGE) { "a4 must be in $A4_RANGE, was $a4Hz" }
        require(toleranceCents in TOLERANCE_RANGE) { "tolerance must be in $TOLERANCE_RANGE, was $toleranceCents" }
        require(holdSec in HOLD_RANGE) { "hold time must be in $HOLD_RANGE, was $holdSec" }
    }

    /** The core's view of these settings, for a practice session. */
    fun toSessionConfig(): SessionConfig = SessionConfig(
        a4Hz = a4Hz.toDouble(),
        match = MatchConfig(toleranceCents = toleranceCents.toDouble(), holdSec = holdSec.toDouble())
    )

    companion object {
        /** Brass players usually tune to 442 Hz. */
        const val DEFAULT_A4_HZ = 442
        const val DEFAULT_TOLERANCE_CENTS = 5

        /** The owner prefers 5 s to 3 s (#68). */
        const val DEFAULT_HOLD_SEC = 5

        val A4_RANGE = 430..450
        val TOLERANCE_RANGE = 2..15
        val HOLD_RANGE = 2..10
    }
}
