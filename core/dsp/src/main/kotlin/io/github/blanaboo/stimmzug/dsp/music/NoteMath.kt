package io.github.blanaboo.stimmzug.dsp.music

import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Twelve-tone equal temperament relative to a configurable reference pitch (A4).
 *
 * Notes are identified by their MIDI number (A4 = 69, C4 = 60). The tuba range used by this app spans roughly
 * MIDI 21 (A0, 27.5 Hz) to MIDI 79 (G5).
 */
object NoteMath {
    const val MIDI_A4 = 69
    const val DEFAULT_A4_HZ = 440.0
    private const val CENTS_PER_OCTAVE = 1200.0
    private const val SEMITONES_PER_OCTAVE = 12.0

    /** Frequency in Hz of the equal-tempered note [midi] for the reference [a4Hz]. */
    fun frequency(midi: Int, a4Hz: Double = DEFAULT_A4_HZ): Double {
        require(a4Hz > 0.0) { "a4Hz must be positive, was $a4Hz" }
        return a4Hz * 2.0.pow((midi - MIDI_A4) / SEMITONES_PER_OCTAVE)
    }

    /** Interval from [referenceHz] to [frequencyHz] in cents (positive = sharp/higher). */
    fun cents(frequencyHz: Double, referenceHz: Double): Double {
        require(frequencyHz > 0.0 && referenceHz > 0.0) {
            "Frequencies must be positive, were $frequencyHz and $referenceHz"
        }
        return CENTS_PER_OCTAVE * log2(frequencyHz / referenceHz)
    }

    /** Deviation of [frequencyHz] from the equal-tempered note [midi] in cents. */
    fun centsFromNote(frequencyHz: Double, midi: Int, a4Hz: Double = DEFAULT_A4_HZ): Double =
        cents(frequencyHz, frequency(midi, a4Hz))

    /** MIDI number of the equal-tempered note closest to [frequencyHz]. */
    fun nearestMidi(frequencyHz: Double, a4Hz: Double = DEFAULT_A4_HZ): Int {
        require(frequencyHz > 0.0) { "frequencyHz must be positive, was $frequencyHz" }
        return (MIDI_A4 + SEMITONES_PER_OCTAVE * log2(frequencyHz / a4Hz)).roundToInt()
    }
}
