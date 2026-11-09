package io.github.blanaboo.stimmzug.ui.practice

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.blanaboo.stimmzug.R
import io.github.blanaboo.stimmzug.data.settings.DisplayMode
import io.github.blanaboo.stimmzug.data.settings.PracticeSettings
import io.github.blanaboo.stimmzug.dsp.music.NoteNames
import io.github.blanaboo.stimmzug.practice.ExerciseChoice
import io.github.blanaboo.stimmzug.practice.ExerciseKind
import io.github.blanaboo.stimmzug.ui.profile.ProfilesViewModel

/** The practice screen (#67): "Übung wählen", the running exercise and its summary, one after the other. */
@Composable
fun PracticeFlow(vm: PracticeViewModel, navigation: PracticeNavigation) {
    val context = LocalContext.current
    val settings = vm.settings.collectAsStateWithLifecycle().value ?: PracticeSettings()
    val engine by vm.engine.collectAsStateWithLifecycle()
    var micDenied by rememberSaveable { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        micDenied = !granted
        if (granted) vm.start()
    }
    val start = {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            micDenied = false
            vm.start()
        } else {
            permission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    val title = exerciseTitle(vm.choice)
    val state = engine
    when {
        vm.stage == PracticeStage.SUMMARY && state?.summary != null -> {
            BackHandler { vm.leave() }
            PracticeSummaryScreen(
                summary = state.summary,
                title = title,
                actions = SummaryActions(again = start, done = navigation.home),
                load = state.load
            )
        }

        vm.stage == PracticeStage.SETUP && vm.warmUp.timerStart != null -> {
            BackHandler { vm.warmUp.cancel() }
            WarmUpScreen(startedAt = vm.warmUp.timerStart ?: 0L, done = vm.warmUp::finish, cancel = vm.warmUp::cancel)
        }

        vm.stage == PracticeStage.RUNNING && state != null -> Running(
            vm,
            RunningState(
                state,
                vm.notes,
                title,
                settings.display,
                settings.holdSec,
                settings.toleranceCents
            )
        )

        else -> Setup(vm, settings, micDenied, navigation, start)
    }
    if (vm.warmUp.asking) WarmUpQuestion(vm.warmUp::ready, vm.warmUp::warmUpNow, vm.warmUp::dismiss)
}

@Composable
private fun Running(vm: PracticeViewModel, state: RunningState) {
    BackHandler { vm.stop() }
    PracticeRunningScreen(
        state = state,
        actions = RunningActions(
            pause = { vm.pause() },
            resume = { vm.resume() },
            skip = { vm.skip() },
            back = { vm.back() },
            stop = { vm.stop() },
            toggleDisplay = {
                vm.setDisplay(if (state.display == DisplayMode.EAR) DisplayMode.TUNER else DisplayMode.EAR)
            }
        )
    )
}

@Composable
private fun Setup(
    vm: PracticeViewModel,
    settings: PracticeSettings,
    micDenied: Boolean,
    navigation: PracticeNavigation,
    start: () -> Unit
) {
    val profile by vm.profile.collectAsStateWithLifecycle()
    val profiles: ProfilesViewModel = viewModel()
    val profileState by profiles.state.collectAsStateWithLifecycle()
    PracticeSetupScreen(
        state = SetupState(
            choice = vm.choice,
            noteCount = vm.noteCount(),
            settings = settings,
            profile = profile,
            profiles = profileState?.profiles.orEmpty(),
            range = ExerciseChoice.instrumentFor(profile?.key).range,
            micDenied = micDenied
        ),
        actions = SetupActions(
            back = navigation.home,
            choose = vm::choose,
            setDisplay = vm::setDisplay,
            openProfiles = navigation.openProfiles,
            chooseProfile = profiles::setActive,
            openSettings = navigation.openSettings,
            start = start
        )
    )
}

/** "B-Dur, Kontra-F bis kleines f" or "Chromatisch, …". */
@Composable
private fun exerciseTitle(choice: ExerciseChoice): String {
    val kind = when (choice.kind) {
        ExerciseKind.KEY_NOTES -> choice.keyName
        ExerciseKind.SCALE -> "${stringResource(R.string.kind_scale)} ${choice.keyName}"
        ExerciseKind.CHROMATIC -> stringResource(R.string.kind_chromatic)
    }
    val range = "${NoteNames.germanOctaveName(choice.lowMidi)} – ${NoteNames.germanOctaveName(choice.highMidi)}"
    return "$kind · $range"
}
