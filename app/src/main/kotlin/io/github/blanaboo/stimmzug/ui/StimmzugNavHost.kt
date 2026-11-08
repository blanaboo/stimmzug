package io.github.blanaboo.stimmzug.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.blanaboo.stimmzug.R
import io.github.blanaboo.stimmzug.data.settings.PracticeSettings
import io.github.blanaboo.stimmzug.ui.home.HomeActions
import io.github.blanaboo.stimmzug.ui.home.HomeScreen
import io.github.blanaboo.stimmzug.ui.profile.ProfileListActions
import io.github.blanaboo.stimmzug.ui.profile.ProfileListScreen
import io.github.blanaboo.stimmzug.ui.profile.ProfileWizardScreen
import io.github.blanaboo.stimmzug.ui.profile.ProfileWizardViewModel
import io.github.blanaboo.stimmzug.ui.profile.ProfilesViewModel
import io.github.blanaboo.stimmzug.ui.settings.SettingsActions
import io.github.blanaboo.stimmzug.ui.settings.SettingsScreen
import io.github.blanaboo.stimmzug.ui.settings.SettingsViewModel
import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

@Serializable
data object ProfilesRoute

@Serializable
data object SettingsRoute

/** The profile setup; without [profileId] for a new profile. */
@Serializable
data class ProfileWizardRoute(val profileId: String? = null)

@Composable
fun StimmzugNavHost(versionName: String, modifier: Modifier = Modifier) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = HomeRoute, modifier = modifier) {
        composable<HomeRoute> {
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
                    createProfile = { nav.navigate(ProfileWizardRoute()) },
                    useDefault = { profiles.useDefault(defaultName) }
                )
            )
        }
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
        composable<SettingsRoute> {
            val practice: SettingsViewModel = viewModel()
            val settings by practice.settings.collectAsStateWithLifecycle()
            SettingsScreen(
                settings = settings,
                actions = SettingsActions(back = {
                    nav.popBackStack()
                }, update = practice::update, reset = practice::reset)
            )
        }
    }
}
