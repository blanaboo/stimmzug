package io.github.blanaboo.stimmzug.practice

import io.github.blanaboo.stimmzug.audio.AudioInput
import io.github.blanaboo.stimmzug.dsp.session.SessionSnapshot

/**
 * Chooses the practice input. Instrumented tests of debug builds swap in the "virtual tubist", a synthetic player
 * that listens to the session's state and plays along (#67); the app itself always uses the microphone.
 */
object PracticeInputs {
    /** Builds the input from a function returning the session's latest state; `null` = the microphone. */
    @Volatile
    var override: ((() -> SessionSnapshot) -> AudioInput)? = null
}
