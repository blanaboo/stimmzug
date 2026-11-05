package io.github.blanaboo.stimmzug.dsp.synth

import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Timing and timbre of the drone. All times in seconds. */
data class DroneConfig(
    /** Fade in when the drone starts, fade out when it stops. */
    val fadeSec: Double = 0.3,
    /** Equal-power crossfade when the note or the octave offset changes. */
    val crossfadeSec: Double = 0.2,
    /** Volume changes ramp over this time (from 0 to 1), so they do not crackle. */
    val volumeRampSec: Double = 0.05,
    val dynamic: TubaSpectrum.Dynamic = TubaSpectrum.Dynamic.MEZZO
) {
    init {
        require(fadeSec >= 0.0 && crossfadeSec >= 0.0 && volumeRampSec >= 0.0) { "times must not be negative" }
    }
}

/**
 * The reference tone: additive wavetable synthesis with a tuba-like spectrum and a double-precision phase
 * accumulator per voice, so the pitch is exact (the wavetable only shapes the timbre).
 *
 * Control methods ([play], [stop], [setVolume], [setOctaveOffset], [playConfirmation]) may be called from any
 * thread; they queue commands (wavetables are built on the calling thread) that [render] applies at the start of
 * the next block. [render] must be called from a single audio thread. Output is mono, peak ≤ 1:
 * drone ≤ [DRONE_PEAK] (≤ √2·[DRONE_PEAK] during an equal-power crossfade) plus chime ≤ [ConfirmationChime.PEAK].
 */
class DroneSynth(val sampleRate: Double, val config: DroneConfig = DroneConfig()) {

    private sealed interface Command {
        class Play(val frequencyHz: Double, val table: Wavetable) : Command
        object Stop : Command
        class Volume(val volume: Double) : Command
        class Chime(val baseHz: Double) : Command
    }

    init {
        require(sampleRate > 0.0) { "sample rate must be positive, was $sampleRate" }
    }

    // Control side (any thread).
    private val commands = ConcurrentLinkedQueue<Command>()
    private var noteHz: Double? = null
    private var octaveOffset = 0

    /** Kept after stop(), so the chime's pitch does not depend on whether it is triggered before or after stop(). */
    private var lastNoteHz: Double? = null

    // Audio side (render thread).
    private val voices = ArrayList<Voice>(MAX_VOICES + 1)
    private val chime = ConfirmationChime(sampleRate)
    private var gain = 1.0
    private var gainTarget = 1.0
    private val gainStep = rate(config.volumeRampSec)

    /** True when the last rendered block ended in silence (no voice, no chime) and nothing is queued. */
    val isSilent: Boolean get() = silent && commands.isEmpty()

    @Volatile private var silent = true

    /** Starts the drone on [frequencyHz] (fade in) or changes to it (crossfade). The octave offset is added. */
    @Synchronized
    fun play(frequencyHz: Double) {
        require(frequencyHz > 0.0) { "frequency must be positive, was $frequencyHz" }
        noteHz = frequencyHz
        lastNoteHz = frequencyHz
        queueNote()
    }

    /** Fades the drone out; a confirmation chime keeps sounding. */
    @Synchronized
    fun stop() {
        noteHz = null
        commands += Command.Stop
    }

    /** Sounds the drone [octaves] (0..[MAX_OCTAVE_OFFSET]) higher, e.g. to keep it apart from the player's tone. */
    @Synchronized
    fun setOctaveOffset(octaves: Int) {
        require(octaves in 0..MAX_OCTAVE_OFFSET) { "octave offset must be in 0..$MAX_OCTAVE_OFFSET, was $octaves" }
        if (octaves == octaveOffset) return
        octaveOffset = octaves
        if (noteHz != null) queueNote()
    }

    /** Volume 0..1, ramped. */
    fun setVolume(volume: Double) {
        require(volume in 0.0..1.0) { "volume must be in 0..1, was $volume" }
        commands += Command.Volume(volume)
    }

    /** Mixes the confirmation chime into the output, on the pitch class of the last played note. */
    @Synchronized
    fun playConfirmation() {
        commands += Command.Chime(ConfirmationChime.baseFrequencyFor(lastNoteHz))
    }

    private fun queueNote() {
        val sounding = noteHz!! * 2.0.pow(octaveOffset)
        require(sounding < MAX_FREQUENCY_FRACTION * sampleRate) { "drone frequency $sounding Hz too high" }
        commands += Command.Play(sounding, droneWavetable(sounding, sampleRate, config.dynamic))
    }

    /** Renders [length] samples into out[offset, offset + length). */
    fun render(out: FloatArray, offset: Int = 0, length: Int = out.size - offset) {
        require(offset >= 0 && length >= 0 && offset + length <= out.size) { "invalid range" }
        applyCommands()
        for (i in offset until offset + length) {
            if (gain != gainTarget) gain = approach(gain, gainTarget, gainStep)
            var drone = 0.0
            for (v in voices) drone += v.next()
            val s = drone * gain * DRONE_PEAK + chime.next()
            // Safety net only: by construction |s| ≤ 1 unless more than two voices overlap (very fast changes).
            out[i] = s.coerceIn(-1.0, 1.0).toFloat()
        }
        voices.removeAll { it.finished }
        silent = voices.isEmpty() && !chime.isActive
    }

    private fun applyCommands() {
        while (true) {
            when (val c = commands.poll() ?: return) {
                is Command.Play -> startVoice(c)
                Command.Stop -> voices.forEach { it.fadeTo(0.0, rate(config.fadeSec)) }
                is Command.Volume -> gainTarget = c.volume
                is Command.Chime -> chime.trigger(c.baseHz)
            }
        }
    }

    private fun startVoice(c: Command.Play) {
        val current = voices.lastOrNull { it.target > 0.0 }
        if (current != null && current.frequencyHz == c.frequencyHz) return
        // A note change crossfades; a start (also during a fade-out) fades in.
        val step = rate(if (current != null) config.crossfadeSec else config.fadeSec)
        // Coming back to a note that is still fading out continues that voice seamlessly.
        val voice = voices.firstOrNull { it.frequencyHz == c.frequencyHz }
            ?: Voice(c.table, c.frequencyHz, sampleRate).also { voices += it }
        for (v in voices) if (v !== voice) v.fadeTo(0.0, step)
        voice.fadeTo(1.0, step)
        voices.remove(voice)
        voices += voice
        while (voices.size > MAX_VOICES) voices.removeAt(0)
    }

    private fun rate(seconds: Double) = if (seconds > 0.0) 1.0 / (seconds * sampleRate) else 1.0

    private class Voice(private val table: Wavetable, val frequencyHz: Double, sampleRate: Double) {
        private val increment = frequencyHz / sampleRate
        private var phase = 0.0
        private var level = 0.0
        private var step = 0.0
        var target = 0.0
            private set

        val finished: Boolean get() = level <= 0.0 && target <= 0.0

        fun fadeTo(target: Double, step: Double) {
            this.target = target
            this.step = step
        }

        fun next(): Double {
            if (level != target) level = approach(level, target, step)
            if (level <= 0.0) return 0.0
            // sin(level·π/2) for the fading voice and cos for the other keep the power constant in a crossfade.
            val gain = if (level >= 1.0) 1.0 else sin(level * PI / 2)
            val v = gain * table.valueAt(phase)
            phase += increment
            if (phase >= 1.0) phase -= 1.0
            return v
        }
    }

    companion object {
        /** Peak of the drone alone at volume 1. */
        const val DRONE_PEAK = 0.6
        const val MAX_OCTAVE_OFFSET = 2
        private const val MAX_VOICES = 4
        private const val MAX_FREQUENCY_FRACTION = 0.45

        private fun approach(value: Double, target: Double, step: Double) =
            if (value < target) min(value + step, target) else max(value - step, target)

        /**
         * Tuba-spectrum wavetable with the lower crest factor of two phase choices, so the drone is as loud as
         * possible at the same peak level (which matters on small phone speakers):
         * - Schroeder phases φ_k = −2π Σ_{l<k} (k−l)·p_l (p = relative power of each harmonic) win for low notes
         *   with many strong harmonics,
         * - zero phases win when the fundamental dominates (higher notes).
         */
        fun droneWavetable(frequencyHz: Double, sampleRate: Double, dynamic: TubaSpectrum.Dynamic): Wavetable {
            val amplitudes = TubaSpectrum.harmonicAmplitudes(frequencyHz, sampleRate, dynamic)
            val totalPower = amplitudes.sumOf { it * it }
            val schroeder = DoubleArray(amplitudes.size) { k ->
                var sum = 0.0
                for (l in 0 until k) sum += (k - l) * amplitudes[l] * amplitudes[l] / totalPower
                -2 * PI * sum
            }
            // Tables are peak-normalized, so the higher RMS means the lower crest factor.
            return listOf(Wavetable(amplitudes, schroeder), Wavetable(amplitudes)).maxBy { rms(it) }
        }

        private fun rms(table: Wavetable): Double {
            var sum = 0.0
            for (i in 0 until table.size) {
                val v = table.valueAt(i.toDouble() / table.size)
                sum += v * v
            }
            return sqrt(sum / table.size)
        }
    }
}
