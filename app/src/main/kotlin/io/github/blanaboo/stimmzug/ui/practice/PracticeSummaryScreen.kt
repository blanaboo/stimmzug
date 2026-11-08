package io.github.blanaboo.stimmzug.ui.practice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.blanaboo.stimmzug.BuildConfig
import io.github.blanaboo.stimmzug.R
import io.github.blanaboo.stimmzug.dsp.music.NoteNames
import io.github.blanaboo.stimmzug.dsp.session.NoteOutcome
import io.github.blanaboo.stimmzug.dsp.session.NoteStatus
import io.github.blanaboo.stimmzug.dsp.session.SessionSummary
import java.util.Locale
import kotlin.math.roundToInt

const val TAG_SUMMARY_TITLE = "summary_title"
const val TAG_SUMMARY_DONE = "summary_done"

class SummaryActions(val again: () -> Unit = {}, val done: () -> Unit = {})

/** The exercise's result (#67): hits, time, the slowest note and every note's measurements. */
@Composable
fun PracticeSummaryScreen(
    summary: SessionSummary,
    title: String,
    actions: SummaryActions,
    modifier: Modifier = Modifier,
    load: Double? = null
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.summary_hits, summary.hits, summary.outcomes.size),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.testTag(TAG_SUMMARY_TITLE)
                )
                val duration = minutes(summary.durationSec)
                Text(
                    summary.meanTimeToHitSec?.let { stringResource(R.string.summary_line, duration, seconds(it)) }
                        ?: stringResource(R.string.summary_line_no_hits, duration),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            summary.slowest?.takeIf { summary.hits >= 2 }?.let { Slowest(it) }
            Outcomes(summary.outcomes)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = actions.again, modifier = Modifier.weight(1f).height(56.dp)) {
                    Text(stringResource(R.string.summary_again))
                }
                Button(onClick = actions.done, modifier = Modifier.weight(1f).height(56.dp).testTag(TAG_SUMMARY_DONE)) {
                    Text(stringResource(R.string.summary_done))
                }
            }
            if (BuildConfig.DEBUG && load != null) {
                Text(
                    stringResource(R.string.summary_load, (load * PERCENT).roundToInt()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun Slowest(outcome: NoteOutcome) {
    val result = outcome.result ?: return
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.summary_slowest, NoteNames.germanOctaveName(outcome.midi)),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                stringResource(
                    R.string.summary_slowest_hint,
                    result.entryCents?.let(::centsText) ?: "–",
                    seconds(result.timeToHitSec ?: 0.0)
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun Outcomes(outcomes: List<NoteOutcome>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                Cell(stringResource(R.string.summary_column_note), weight = NAME_WEIGHT, header = true)
                Cell(stringResource(R.string.summary_column_time), header = true)
                Cell(stringResource(R.string.summary_column_entry), header = true)
                Cell(stringResource(R.string.summary_column_hold), header = true)
            }
            for (outcome in outcomes) {
                HorizontalDivider()
                Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    val result = outcome.result
                    Cell(NoteNames.germanOctaveName(outcome.midi), weight = NAME_WEIGHT)
                    when (outcome.status) {
                        NoteStatus.HIT -> {
                            Cell("${seconds(result?.timeToHitSec ?: 0.0)} s")
                            Cell(result?.entryCents?.let(::centsText) ?: "–")
                            Cell(result?.holdMeanCents?.let(::centsText) ?: "–")
                        }

                        NoteStatus.SKIPPED -> Cell(stringResource(R.string.summary_skipped), weight = REST_WEIGHT)

                        NoteStatus.STOPPED -> Cell(stringResource(R.string.summary_stopped), weight = REST_WEIGHT)

                        NoteStatus.NOT_REACHED -> Cell(
                            stringResource(R.string.summary_not_reached),
                            weight = REST_WEIGHT
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.Cell(text: String, weight: Float = 1f, header: Boolean = false) {
    Text(
        text,
        modifier = Modifier.weight(weight),
        style = if (header) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium,
        color = if (header) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        textAlign = if (weight == 1f) TextAlign.End else TextAlign.Start
    )
}

private fun minutes(seconds: Double): String {
    val total = seconds.roundToInt()
    return String.format(Locale.ROOT, "%d:%02d", total / SECONDS_PER_MINUTE, total % SECONDS_PER_MINUTE)
}

private const val NAME_WEIGHT = 1.4f
private const val REST_WEIGHT = 3f
private const val PERCENT = 100
private const val SECONDS_PER_MINUTE = 60
