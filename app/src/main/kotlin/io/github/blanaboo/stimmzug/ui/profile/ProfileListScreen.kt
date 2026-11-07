package io.github.blanaboo.stimmzug.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.blanaboo.stimmzug.R
import io.github.blanaboo.stimmzug.data.profile.ProfileState
import io.github.blanaboo.stimmzug.dsp.music.InstrumentProfile
import io.github.blanaboo.stimmzug.dsp.music.TubaKey
import io.github.blanaboo.stimmzug.dsp.music.ValveInterval
import io.github.blanaboo.stimmzug.ui.theme.StimmzugTheme

const val TAG_PROFILE_ADD = "profiles_add"
const val TAG_PROFILE_ACTIVE = "profiles_active"

/** What the profile list can do; the screen itself holds no logic. */
class ProfileListActions(
    val back: () -> Unit = {},
    val add: () -> Unit = {},
    val edit: (id: String) -> Unit = {},
    val select: (id: String) -> Unit = {},
    val delete: (id: String) -> Unit = {}
)

/** All tubas of the player; one is active and used for practice. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileListScreen(state: ProfileState?, actions: ProfileListActions, modifier: Modifier = Modifier) {
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.profiles_title)) },
                navigationIcon = {
                    IconButton(onClick = actions.back) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = actions.add,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.profiles_add)) },
                modifier = Modifier.testTag(TAG_PROFILE_ADD)
            )
        }
    ) { padding ->
        val profiles = state?.profiles.orEmpty()
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state != null && profiles.isEmpty()) {
                item { Text(stringResource(R.string.home_no_profile), style = MaterialTheme.typography.bodyLarge) }
            }
            items(profiles, key = { it.id }) { profile ->
                ProfileCard(profile, active = profile.id == state?.active?.id, actions, onDelete = { deleting = it })
            }
        }
    }
    val doomed = profiles(state).firstOrNull { it.id == deleting }
    if (doomed != null) {
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.profiles_delete_title)) },
            text = { Text(stringResource(R.string.profiles_delete_text, doomed.name)) },
            confirmButton = {
                TextButton(onClick = {
                    actions.delete(doomed.id)
                    deleting = null
                }) { Text(stringResource(R.string.profiles_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.profiles_cancel)) }
            }
        )
    }
}

private fun profiles(state: ProfileState?) = state?.profiles.orEmpty()

@Composable
private fun ProfileCard(
    profile: InstrumentProfile,
    active: Boolean,
    actions: ProfileListActions,
    onDelete: (String) -> Unit
) {
    val colors = if (active) {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    } else {
        CardDefaults.cardColors()
    }
    Card(
        colors = colors,
        modifier = Modifier.fillMaxWidth().then(if (active) Modifier.testTag(TAG_PROFILE_ACTIVE) else Modifier)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .selectable(selected = active, role = Role.RadioButton, onClick = { actions.select(profile.id) })
                .padding(start = 4.dp, top = 8.dp, end = 16.dp)
        ) {
            RadioButton(selected = active, onClick = null, modifier = Modifier.padding(12.dp))
            Column(Modifier.weight(1f)) {
                Text(profile.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    profileSummary(profile),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (active) {
                    Text(
                        stringResource(R.string.profiles_active),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(end = 8.dp, bottom = 4.dp), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { actions.edit(profile.id) }) { Text(stringResource(R.string.profiles_edit)) }
            TextButton(onClick = { onDelete(profile.id) }) { Text(stringResource(R.string.profiles_delete)) }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ProfileListPreview() {
    val profiles = listOf(
        InstrumentProfile(id = "b", name = "B-Tuba B&S GR55", key = TubaKey.B_FLAT, maker = "B&S", model = "GR55"),
        InstrumentProfile(
            id = "f",
            name = "F-Tuba Melton Tradition 4250",
            key = TubaKey.F,
            extraValves = listOf(ValveInterval.FOURTH, ValveInterval.LARGE_WHOLE_TONE),
            triggerValves = setOf(2)
        )
    )
    StimmzugTheme(darkTheme = true) {
        ProfileListScreen(ProfileState(profiles, activeId = "f"), ProfileListActions())
    }
}
