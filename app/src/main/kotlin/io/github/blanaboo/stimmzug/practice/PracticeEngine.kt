package io.github.blanaboo.stimmzug.practice

import io.github.blanaboo.stimmzug.audio.AudioInput
import io.github.blanaboo.stimmzug.audio.OutputRoute
import io.github.blanaboo.stimmzug.dsp.session.PracticeSession
import io.github.blanaboo.stimmzug.dsp.session.SessionPhase
import io.github.blanaboo.stimmzug.dsp.session.SessionSnapshot
import io.github.blanaboo.stimmzug.dsp.session.SessionSummary
import java.io.Closeable
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Everything the practice screens show while an exercise runs. */
data class EngineState(
    val snapshot: SessionSnapshot,
    val route: OutputRoute = OutputRoute.UNKNOWN,
    /** Set once the session is finished. */
    val summary: SessionSummary? = null,
    /** Why the input stopped, if it did. */
    val error: String? = null,
    /** Processing time of the detection per audio time, 0..1 (1 = a whole core). */
    val load: Double? = null
)

/**
 * Runs a [PracticeSession] on the input's audio thread (#67): every chunk from [input] goes through the session,
 * controls from the screen are queued and applied there, and the state is published as [state].
 */
class PracticeEngine(
    private val session: PracticeSession,
    private val input: AudioInput,
    private val route: () -> OutputRoute = { OutputRoute.UNKNOWN },
    private val onClose: () -> Unit = {},
    /** Called once with the final summary: when the session finishes, or on [close] if it did not (#37). */
    private val onEnd: (SessionSummary) -> Unit = {}
) : Closeable {
    private val commands = ConcurrentLinkedQueue<(PracticeSession) -> Unit>()
    private val ended = AtomicBoolean(false)
    private val _state = MutableStateFlow(EngineState(session.snapshot))
    private var processedNanos = 0L
    private var audioNanos = 0L

    val state: StateFlow<EngineState> = _state.asStateFlow()

    fun start() {
        session.start()
        input.start(::onChunk) { e -> _state.value = _state.value.copy(error = e.message ?: e.toString()) }
    }

    fun pause() = post { it.pause() }

    fun resume() = post { it.resume() }

    fun skip() = post { it.skip() }

    fun back() = post { it.back() }

    fun stop() = post { it.stop() }

    private fun post(command: (PracticeSession) -> Unit) {
        commands += command
    }

    private fun onChunk(samples: FloatArray, count: Int) {
        while (true) (commands.poll() ?: break)(session)
        val start = System.nanoTime()
        val snapshot = session.process(samples, count)
        processedNanos += System.nanoTime() - start
        audioNanos += count * NANOS_PER_SECOND / input.sampleRate
        val finished = snapshot.phase == SessionPhase.FINISHED
        val summary = if (finished) _state.value.summary ?: session.summary() else null
        _state.value = _state.value.copy(
            snapshot = snapshot,
            route = route(),
            summary = summary,
            load = if (audioNanos > 0) processedNanos.toDouble() / audioNanos else null
        )
        if (summary != null) end(summary)
    }

    override fun close() {
        input.close()
        // The input thread has stopped: the session can be finished here if the player left in the middle.
        if (!ended.get()) {
            session.stop()
            end(session.summary())
        }
        onClose()
    }

    private fun end(summary: SessionSummary) {
        if (ended.compareAndSet(false, true)) onEnd(summary)
    }

    private companion object {
        const val NANOS_PER_SECOND = 1_000_000_000L
    }
}
