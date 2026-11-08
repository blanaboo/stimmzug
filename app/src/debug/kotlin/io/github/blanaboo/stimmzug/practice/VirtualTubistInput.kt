package io.github.blanaboo.stimmzug.practice

import io.github.blanaboo.stimmzug.audio.AudioInput
import io.github.blanaboo.stimmzug.dsp.match.MatchPhase
import io.github.blanaboo.stimmzug.dsp.session.SessionPhase
import io.github.blanaboo.stimmzug.dsp.session.SessionSnapshot
import io.github.blanaboo.stimmzug.dsp.synth.TubaSpectrum
import io.github.blanaboo.stimmzug.dsp.synth.TubaToneGenerator
import io.github.blanaboo.stimmzug.dsp.synth.Wavetable
import java.util.Random
import kotlin.math.max
import kotlin.math.pow

/**
 * The "virtual tubist" (debug builds, E2E tests): replaces the microphone with a synthetic tuba that listens to the
 * session like a player. It waits through calibration and pre-listen, enters [entryCents] off, glides to the target
 * within [glideSec] and stops after the hit to breathe. Audio is produced in real time, 10 ms per chunk.
 */
class VirtualTubistInput(
    private val snapshot: () -> SessionSnapshot,
    override val sampleRate: Int = 48_000,
    private val entryCents: Double = -15.0,
    private val glideSec: Double = 1.0
) : AudioInput {
    @Volatile private var running = false
    private var thread: Thread? = null
    private var table: Wavetable? = null
    private var tableHz = 0.0
    private var phase = 0.0
    private var gain = 0.0
    private var startedAt: Double? = null
    private val noise = Random(1)

    override val sourceName: String = "VIRTUAL_TUBIST"

    override fun start(onChunk: (FloatArray, Int) -> Unit, onError: (Exception) -> Unit) {
        check(thread == null) { "already started" }
        running = true
        thread = Thread({ run(onChunk) }, "virtual-tubist").also { it.start() }
    }

    private fun run(onChunk: (FloatArray, Int) -> Unit) {
        val chunk = FloatArray(sampleRate / CHUNKS_PER_SECOND)
        val chunkNanos = NANOS_PER_SECOND / CHUNKS_PER_SECOND
        var next = System.nanoTime()
        while (running) {
            render(chunk, snapshot())
            onChunk(chunk, chunk.size)
            next += chunkNanos
            val wait = next - System.nanoTime()
            if (wait > 0) Thread.sleep(wait / NANOS_PER_MILLI, (wait % NANOS_PER_MILLI).toInt())
        }
    }

    private fun render(out: FloatArray, state: SessionSnapshot) {
        val notePhase = state.note?.phase
        val target = state.targetHz
        val playing = state.phase == SessionPhase.PLAYING && target != null &&
            (notePhase == MatchPhase.LISTENING || notePhase == MatchPhase.IN_RANGE)
        target?.let(::tuneTo)
        startedAt = if (playing) startedAt ?: state.timeSec else null
        val since = startedAt?.let { state.timeSec - it } ?: 0.0
        val cents = entryCents * max(0.0, 1.0 - since / glideSec)
        val increment = tableHz * 2.0.pow(cents / CENTS_PER_OCTAVE) / sampleRate
        val goal = if (playing) 1.0 else 0.0
        val step = 1.0 / (ATTACK_SEC * sampleRate)
        for (i in out.indices) {
            gain += (goal - gain).coerceIn(-step, step)
            out[i] = (PEAK * gain * (table?.valueAt(phase) ?: 0.0) + NOISE * noise.nextGaussian()).toFloat()
            phase += increment
            if (phase >= 1.0) phase -= 1.0
        }
    }

    private fun tuneTo(hz: Double) {
        if (hz == tableHz) return
        tableHz = hz
        table =
            TubaToneGenerator.tubaWavetable(hz, sampleRate.toDouble(), TubaSpectrum.Dynamic.MEZZO, Random(hz.toLong()))
    }

    override fun close() {
        running = false
        thread?.join(JOIN_TIMEOUT_MS)
    }

    private companion object {
        const val CHUNKS_PER_SECOND = 100
        const val NANOS_PER_SECOND = 1_000_000_000L
        const val NANOS_PER_MILLI = 1_000_000L
        const val CENTS_PER_OCTAVE = 1200.0
        const val ATTACK_SEC = 0.05
        const val PEAK = 0.25
        const val NOISE = 0.001
        const val JOIN_TIMEOUT_MS = 1_000L
    }
}
