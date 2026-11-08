package io.github.blanaboo.stimmzug.practice

import io.github.blanaboo.stimmzug.audio.AudioInput
import io.github.blanaboo.stimmzug.dsp.session.SessionConfig
import io.github.blanaboo.stimmzug.dsp.session.SessionSnapshot

/**
 * Chooses the practice input. Instrumented tests of debug builds swap in the "virtual tubist", a synthetic player
 * that listens to the session's state and plays along (#67); the app itself always uses the microphone.
 * Debug builds also record every session through [tap] (#75).
 */
object PracticeInputs {
    /** Builds the input from a function returning the session's latest state; `null` = the microphone. */
    @Volatile
    var override: ((() -> SessionSnapshot) -> AudioInput)? = null

    /** Wraps the input of every session, e.g. to record it; `null` = the input is used as it is. */
    @Volatile
    var tap: PracticeTap? = null
}

/** What a session is about, for a [PracticeTap]. */
data class TapInfo(
    /** MIDI numbers in the order of the exercise. */
    val notes: List<Int>,
    val config: SessionConfig,
    /** The exercise as chosen, readable for a person. */
    val exercise: String
)

/** Sees a session's audio and state without changing them (#75). */
fun interface PracticeTap {
    /** Returns an input that delivers what [input] delivers; [state] returns the engine's latest state. */
    fun wrap(input: AudioInput, info: TapInfo, state: () -> EngineState): AudioInput
}
