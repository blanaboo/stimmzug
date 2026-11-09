package io.github.blanaboo.stimmzug.ui.practice

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.blanaboo.stimmzug.R
import io.github.blanaboo.stimmzug.ui.theme.StimmzugTheme
import kotlinx.coroutines.delay

const val TAG_WARM_UP_READY = "warm_up_ready"
const val TAG_WARM_UP_NOW = "warm_up_now"
const val TAG_WARM_UP_TIMER = "warm_up_timer"
const val TAG_WARM_UP_DONE = "warm_up_done"

/** „Schon eingespielt?“ before the first exercise of the day (#44). */
@Composable
fun WarmUpQuestion(ready: () -> Unit, warmUpNow: () -> Unit, dismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(stringResource(R.string.warm_up_title)) },
        text = { Text(stringResource(R.string.warm_up_text)) },
        confirmButton = {
            TextButton(onClick = ready, modifier = Modifier.testTag(TAG_WARM_UP_READY)) {
                Text(stringResource(R.string.warm_up_ready))
            }
        },
        dismissButton = {
            TextButton(onClick = warmUpNow, modifier = Modifier.testTag(TAG_WARM_UP_NOW)) {
                Text(stringResource(R.string.warm_up_now))
            }
        }
    )
}

/** Warming up with a running clock (#44); [startedAt] is in [SystemClock.elapsedRealtime] time. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WarmUpScreen(startedAt: Long, done: () -> Unit, cancel: () -> Unit, modifier: Modifier = Modifier) {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(startedAt) {
        while (true) {
            now = SystemClock.elapsedRealtime()
            delay(TICK_MS)
        }
    }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.warm_up_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = cancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        WarmUpContent(elapsedText((now - startedAt) / MILLIS_PER_SECOND), done, Modifier.padding(padding))
    }
}

@Composable
private fun WarmUpContent(elapsed: String, done: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(24.dp))
        Text(
            elapsed,
            fontSize = 72.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.testTag(TAG_WARM_UP_TIMER)
        )
        Text(
            stringResource(R.string.warm_up_tips),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.weight(1f))
        Button(onClick = done, modifier = Modifier.fillMaxWidth().height(56.dp).testTag(TAG_WARM_UP_DONE)) {
            Text(stringResource(R.string.warm_up_done), style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** "4:07" for minutes and seconds. */
internal fun elapsedText(seconds: Long): String {
    val s = seconds.coerceAtLeast(0)
    return "${s / SECONDS_PER_MINUTE}:${(s % SECONDS_PER_MINUTE).toString().padStart(2, '0')}"
}

private const val TICK_MS = 250L
private const val MILLIS_PER_SECOND = 1000L
private const val SECONDS_PER_MINUTE = 60L

@Preview(showBackground = true)
@Composable
private fun WarmUpPreview() {
    StimmzugTheme { WarmUpContent("4:07", done = {}) }
}
