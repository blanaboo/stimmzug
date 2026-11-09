package io.github.blanaboo.stimmzug.ui.practice

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.blanaboo.stimmzug.audio.DroneTrack
import io.github.blanaboo.stimmzug.audio.MicInput
import io.github.blanaboo.stimmzug.data.log.DisplayTimeline
import io.github.blanaboo.stimmzug.data.log.SessionContext
import io.github.blanaboo.stimmzug.data.log.SessionLogBuilder
import io.github.blanaboo.stimmzug.data.log.SessionLogRepository
import io.github.blanaboo.stimmzug.data.profile.ProfileRepository
import io.github.blanaboo.stimmzug.data.settings.DisplayMode
import io.github.blanaboo.stimmzug.data.settings.DroneOutput
import io.github.blanaboo.stimmzug.data.settings.PracticeSettings
import io.github.blanaboo.stimmzug.data.settings.SettingsRepository
import io.github.blanaboo.stimmzug.dsp.music.InstrumentProfile
import io.github.blanaboo.stimmzug.dsp.session.PracticeSession
import io.github.blanaboo.stimmzug.dsp.session.SessionConfig
import io.github.blanaboo.stimmzug.practice.EngineState
import io.github.blanaboo.stimmzug.practice.ExerciseChoice
import io.github.blanaboo.stimmzug.practice.PracticeEngine
import io.github.blanaboo.stimmzug.practice.PracticeInputs
import io.github.blanaboo.stimmzug.practice.TapInfo
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Which of the practice screens is shown. */
enum class PracticeStage { SETUP, RUNNING, SUMMARY }

/**
 * The practice screen (#67): the exercise choice, the running session with microphone and drone, and its summary.
 * The engine lives here, so a rotation does not stop the exercise.
 */
class PracticeViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsRepository = SettingsRepository(application)

    val settings: StateFlow<PracticeSettings?> =
        settingsRepository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val profile: StateFlow<InstrumentProfile?> = ProfileRepository(application).state
        .map { it.active }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    var choice by mutableStateOf(ExerciseChoice())
        private set

    var stage by mutableStateOf(PracticeStage.SETUP)
        private set

    /** The notes of the running (or last) exercise. */
    var notes by mutableStateOf(emptyList<Int>())
        private set

    private val _engine = MutableStateFlow<EngineState?>(null)
    val engine: StateFlow<EngineState?> = _engine.asStateFlow()

    private var running: PracticeEngine? = null
    private var forwarding: Job? = null
    private var displayWatch: Job? = null
    private val log = SessionLogRepository(application)

    fun choose(change: (ExerciseChoice) -> ExerciseChoice) {
        runCatching { change(choice) }.onSuccess { choice = it }
    }

    /** How many notes the current choice gives on the active tuba (0 = the choice does not work). */
    fun noteCount(): Int = choice.notes(ExerciseChoice.instrumentFor(profile.value?.key), seed = 0).size

    fun setDisplay(mode: DisplayMode) {
        viewModelScope.launch { settingsRepository.update { it.copy(display = mode) } }
    }

    /** Starts the exercise; the microphone permission must be granted. */
    fun start() {
        val settings = settings.value ?: PracticeSettings()
        val chosen = choice.notes(ExerciseChoice.instrumentFor(profile.value?.key), seed = System.currentTimeMillis())
        if (chosen.isEmpty()) return
        closeEngine()
        val app = getApplication<Application>()
        val drone = DroneTrack(app, settings.droneOutput == DroneOutput.SPEAKER, settings.droneTimbre)
        val config = settings.toSessionConfig()
        lateinit var engine: PracticeEngine
        val source = PracticeInputs.override?.invoke { engine.state.value.snapshot } ?: MicInput(app)
        val input = PracticeInputs.tap?.wrap(source, TapInfo(chosen, config, choice.toString())) { engine.state.value }
            ?: source
        val session = PracticeSession(chosen, input.sampleRate.toDouble(), drone, config)
        val context = logContext(settings, config)
        engine = PracticeEngine(session, input, route = { drone.route }, onClose = drone::close) { summary ->
            if (summary.attempts.isNotEmpty()) {
                val calibration = session.calibrationResult
                log.record(SessionLogBuilder.build(context, summary, calibration, input.sourceName, drone.route))
            }
        }
        running = engine
        notes = chosen
        stage = PracticeStage.RUNNING
        forwarding = viewModelScope.launch {
            engine.state.collect { state ->
                _engine.value = state
                if (state.summary != null && stage == PracticeStage.RUNNING) {
                    stage = PracticeStage.SUMMARY
                    closeEngine()
                }
            }
        }
        displayWatch = viewModelScope.launch {
            this@PracticeViewModel.settings.collect { s ->
                s?.let { context.display.change(engine.state.value.snapshot.timeSec, it.display) }
            }
        }
        engine.start()
    }

    /** What the session log keeps besides the results (#37). */
    private fun logContext(settings: PracticeSettings, config: SessionConfig) = SessionContext(
        startedAt = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS).toString(),
        profile = profile.value,
        config = config,
        droneTimbre = settings.droneTimbre,
        exercise = choice.logText,
        display = DisplayTimeline(settings.display)
    )

    fun pause() = running?.pause()

    fun resume() = running?.resume()

    fun skip() = running?.skip()

    fun back() = running?.back()

    /** Ends the exercise; the summary follows. Without a running engine (input failed) back to the choice. */
    fun stop() {
        val engine = running
        if (engine == null || _engine.value?.error != null) leave() else engine.stop()
    }

    /** Back to "Übung wählen". */
    fun leave() {
        closeEngine()
        _engine.value = null
        stage = PracticeStage.SETUP
    }

    private fun closeEngine() {
        forwarding?.cancel()
        forwarding = null
        displayWatch?.cancel()
        displayWatch = null
        running?.close()
        running = null
    }

    override fun onCleared() = closeEngine()
}
