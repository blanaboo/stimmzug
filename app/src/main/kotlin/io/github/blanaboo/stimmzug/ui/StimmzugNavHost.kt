package io.github.blanaboo.stimmzug.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.blanaboo.stimmzug.R
import io.github.blanaboo.stimmzug.data.settings.PracticeSettings
import io.github.blanaboo.stimmzug.ui.home.HomeActions
import io.github.blanaboo.stimmzug.ui.home.HomeScreen
import io.github.blanaboo.stimmzug.ui.practice.PracticeFlow
import io.github.blanaboo.stimmzug.ui.practice.PracticeNavigation
import io.github.blanaboo.stimmzug.ui.practice.PracticeViewModel
import io.github.blanaboo.stimmzug.ui.profile.ProfileListActions
import io.github.blanaboo.stimmzug.ui.profile.ProfileListScreen
import io.github.blanaboo.stimmzug.ui.profile.ProfileWizardScreen
import io.github.blanaboo.stimmzug.ui.profile.ProfileWizardViewModel
import io.github.blanaboo.stimmzug.ui.profile.ProfilesViewModel
import io.github.blanaboo.stimmzug.ui.settings.SettingsActions
import io.github.blanaboo.stimmzug.ui.settings.SettingsScreen
import io.github.blanaboo.stimmzug.ui.settings.SettingsViewModel
import io.github.blanaboo.stimmzug.ui.settings.shareLog
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

@Serializable
data object ProfilesRoute

@Serializable
data object SettingsRoute

@Serializable
data object PracticeRoute

/** The profile setup; without [profileId] for a new profile. */
@Serializable
data class ProfileWizardRoute(val profileId: String? = null)

@Composable
fun StimmzugNavHost(versionName: String, modifier: Modifier = Modifier) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = HomeRoute, modifier = modifier) {
        composable<HomeRoute> { HomeEntry(nav, versionName) }
        composable<ProfilesRoute> {
            val profiles: ProfilesViewModel = viewModel()
            val state by profiles.state.collectAsStateWithLifecycle()
            ProfileListScreen(
                state = state,
                actions = ProfileListActions(
                    back = { nav.popBackStack() },
                    add = { nav.navigate(ProfileWizardRoute()) },
                    edit = { id -> nav.navigate(ProfileWizardRoute(id)) },
                    select = profiles::setActive,
                    delete = profiles::delete
                )
            )
        }
        composable<ProfileWizardRoute> {
            val wizard: ProfileWizardViewModel = viewModel()
            ProfileWizardScreen(wizard, onDone = { nav.popBackStack() })
        }
        composable<PracticeRoute> {
            val practice: PracticeViewModel = viewModel()
            PracticeFlow(
                vm = practice,
                navigation = PracticeNavigation(
                    home = { nav.popBackStack() },
                    openProfiles = { nav.navigate(ProfilesRoute) },
                    openSettings = { nav.navigate(SettingsRoute) }
                )
            )
        }
        composable<SettingsRoute> { SettingsEntry(nav) }
    }
}

/** The settings, with sharing the session log as CSV (#37). */
@Composable
private fun SettingsEntry(nav: NavHostController) {
    val practice: SettingsViewModel = viewModel()
    val settings by practice.settings.collectAsStateWithLifecycle()
    val logCount by practice.logCount.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    SettingsScreen(
        settings = settings,
        logCount = logCount,
        actions = SettingsActions(
            back = { nav.popBackStack() },
            update = practice::update,
            reset = practice::reset,
            exportLog = { scope.launch { practice.exportLog()?.let { shareLog(context, it) } } }
        )
    )
}

/** The start screen with the active tuba and the reference note at the chosen a'. */
@Composable
private fun HomeEntry(nav: NavHostController, versionName: String) {
    val profiles: ProfilesViewModel = viewModel()
    val state by profiles.state.collectAsStateWithLifecycle()
    val defaultName = stringResource(R.string.key_b_flat)
    val practice: SettingsViewModel = viewModel()
    val settings by practice.settings.collectAsStateWithLifecycle()
    HomeScreen(
        versionName = versionName,
        profiles = state,
        a4Hz = settings?.a4Hz ?: PracticeSettings.DEFAULT_A4_HZ,
        actions = HomeActions(
            openProfiles = { nav.navigate(ProfilesRoute) },
            openSettings = { nav.navigate(SettingsRoute) },
            practice = { nav.navigate(PracticeRoute) },
            createProfile = { nav.navigate(ProfileWizardRoute()) },
            useDefault = { profiles.useDefault(defaultName) }
        )
    )
}
