package io.github.blanaboo.stimmzug.ui.practice

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.blanaboo.stimmzug.R
import io.github.blanaboo.stimmzug.audio.OutputKind
import io.github.blanaboo.stimmzug.audio.OutputRoute
import io.github.blanaboo.stimmzug.data.settings.DisplayMode
import io.github.blanaboo.stimmzug.dsp.match.MatchPhase
import io.github.blanaboo.stimmzug.dsp.match.MatchStatus
import io.github.blanaboo.stimmzug.dsp.match.PitchVerdict
import io.github.blanaboo.stimmzug.dsp.music.NoteNames
import io.github.blanaboo.stimmzug.dsp.music.NoteNaming
import io.github.blanaboo.stimmzug.dsp.session.NoteOutcome
import io.github.blanaboo.stimmzug.dsp.session.SessionPhase
import io.github.blanaboo.stimmzug.practice.EngineState
import kotlin.math.abs
import kotlin.math.roundToInt

const val TAG_PRACTICE_STATUS = "practice_status"
const val TAG_PRACTICE_STOP = "practice_stop"

/** What the running exercise shows. */
class RunningState(
    val engine: EngineState,
    val notes: List<Int>,
    val title: String,
    val display: DisplayMode,
    val holdSec: Int,
    val toleranceCents: Int
)

class RunningActions(
    val pause: () -> Unit = {},
    val resume: () -> Unit = {},
    val skip: () -> Unit = {},
    val back: () -> Unit = {},
    val stop: () -> Unit = {},
    val toggleDisplay: () -> Unit = {}
)

/** The exercise while it runs (#67), readable from about 1 m on the music stand. The display stays on. */
@Composable
fun PracticeRunningScreen(state: RunningState, actions: RunningActions, modifier: Modifier = Modifier) {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    val snapshot = state.engine.snapshot
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Header(state)
            Spacer(Modifier.weight(1f))
            Center(state)
            Spacer(Modifier.weight(1f))
            RouteLine(state.engine.route, showHint = snapshot.phase == SessionPhase.CALIBRATING)
            if (snapshot.phase == SessionPhase.PLAYING) {
                TextButton(onClick = actions.toggleDisplay) {
                    Text(
                        stringResource(
                            if (state.display ==
                                DisplayMode.EAR
                            ) {
                                R.string.practice_show_tuner
                            } else {
                                R.string.practice_show_ear
                            }
                        )
                    )
                }
            }
            Controls(paused = snapshot.phase == SessionPhase.PAUSED, actions = actions)
        }
    }
}

@Composable
private fun Header(state: RunningState) {
    val snapshot = state.engine.snapshot
    val shown = (snapshot.index + 1).coerceAtMost(snapshot.noteCount)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(
                state.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(end = 12.dp)
            )
            Text(
                stringResource(R.string.practice_note_of, shown, snapshot.noteCount),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        LinearProgressIndicator(
            progress = { snapshot.index.toFloat() / snapshot.noteCount.coerceAtLeast(1) },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun Center(state: RunningState) {
    val engine = state.engine
    val snapshot = engine.snapshot
    val midi = snapshot.midi
    val status = snapshot.note
    val hit = snapshot.hit
    when {
        engine.error != null -> Message(stringResource(R.string.practice_error, engine.error), "")

        snapshot.phase == SessionPhase.CALIBRATING -> Calibration(snapshot.calibrationProgress)

        midi == null -> Unit

        snapshot.phase == SessionPhase.PAUSED ->
            NoteRing(midi, 0f) {
                Message(stringResource(R.string.practice_paused), stringResource(R.string.practice_paused_hint))
            }

        hit != null -> Hit(hit, state.notes.getOrNull(snapshot.index + 1))

        status == null || status.phase == MatchPhase.PRE_LISTEN -> NoteRing(midi, 0f) {
            val hz = snapshot.targetHz?.let { String.format(LocalLocale.current.platformLocale, "%.2f", it) }.orEmpty()
            Message(stringResource(R.string.practice_listen), stringResource(R.string.practice_listen_hint, hz))
        }

        state.display == DisplayMode.EAR -> EarView(midi, status, state.holdSec)

        else -> TunerView(midi, status, state)
    }
}

@Composable
private fun Calibration(progress: Double) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Ring(progress.toFloat(), MaterialTheme.colorScheme.primary, size = 220.dp) {
            Text(
                "${(3 * (1 - progress)).let { kotlin.math.ceil(it).toInt() }.coerceAtLeast(0)}",
                fontSize = 64.sp,
                fontWeight = FontWeight.Medium
            )
        }
        Message(stringResource(R.string.practice_calibrating), stringResource(R.string.practice_calibrating_hint))
    }
}

/** The big note name in a ring that fills with [progress]; [below] goes under the ring. */
@Composable
private fun NoteRing(
    midi: Int,
    progress: Float,
    color: Color = MaterialTheme.colorScheme.tertiary,
    below: @Composable () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Ring(progress, color, size = 280.dp) { NoteName(midi, big = true) }
        below()
    }
}

@Composable
private fun NoteName(midi: Int, big: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            NoteNames.pitchClass(midi, NoteNaming.GERMAN),
            fontSize = if (big) 120.sp else 96.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = if (big) 120.sp else 96.sp
        )
        Text(
            NoteNames.germanOctaveName(midi),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EarView(midi: Int, status: MatchStatus, holdSec: Int) {
    val inRange = status.phase == MatchPhase.IN_RANGE
    val (title, detail) = when (status.verdict) {
        PitchVerdict.SILENT -> stringResource(R.string.practice_play) to stringResource(R.string.practice_waiting)

        PitchVerdict.IN_TOLERANCE -> stringResource(R.string.practice_hold) to
            stringResource(R.string.practice_seconds_left, seconds(holdSec * (1 - status.holdProgress)))

        PitchVerdict.WRONG_OCTAVE -> stringResource(R.string.practice_wrong_octave) to ""

        PitchVerdict.WRONG_NOTE -> stringResource(R.string.practice_wrong_note) to ""

        PitchVerdict.FLAT, PitchVerdict.SHARP ->
            stringResource(R.string.practice_heard) to stringResource(R.string.practice_heard_hint)
    }
    NoteRing(midi, if (inRange) status.holdProgress.toFloat() else 0f) {
        Message(title, detail, color = if (inRange) MaterialTheme.colorScheme.tertiary else Color.Unspecified)
    }
}

@Composable
private fun TunerView(midi: Int, status: MatchStatus, state: RunningState) {
    val cents = status.centsDeviation
    val inRange = status.verdict == PitchVerdict.IN_TOLERANCE
    val color = when {
        cents == null -> MaterialTheme.colorScheme.onSurfaceVariant
        inRange -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    val verdict = when (status.verdict) {
        PitchVerdict.SILENT -> stringResource(R.string.practice_play)
        PitchVerdict.IN_TOLERANCE -> stringResource(R.string.practice_in_range)
        PitchVerdict.FLAT -> stringResource(R.string.practice_flat)
        PitchVerdict.SHARP -> stringResource(R.string.practice_sharp)
        PitchVerdict.WRONG_OCTAVE -> stringResource(R.string.practice_wrong_octave)
        PitchVerdict.WRONG_NOTE -> stringResource(R.string.practice_wrong_note)
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        NoteName(midi, big = false)
        Text(
            cents?.let { centsText(it) } ?: "–",
            fontSize = 56.sp,
            fontWeight = FontWeight.Medium,
            color = color,
            modifier = Modifier.testTag(TAG_PRACTICE_STATUS)
        )
        Text(verdict, style = MaterialTheme.typography.headlineSmall, color = color)
        CentMeter(cents, state.toleranceCents, color)
        LinearProgressIndicator(
            progress = { if (status.phase == MatchPhase.IN_RANGE) status.holdProgress.toFloat() else 0f },
            color = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.width(300.dp).height(8.dp)
        )
    }
}

/** −50 … +50 ct with the tolerance band and a needle. */
@Composable
private fun CentMeter(cents: Double?, toleranceCents: Int, needle: Color) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val band = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.45f)
    Canvas(Modifier.width(300.dp).height(48.dp)) {
        val mid = size.width / 2
        val perCent = size.width / (2 * RANGE_CENTS.toFloat())
        drawRoundRect(track, topLeft = Offset(0f, size.height / 2 - 6.dp.toPx()), size = Size(size.width, 12.dp.toPx()))
        val bandWidth = 2 * toleranceCents * perCent
        drawRect(
            band,
            topLeft = Offset(mid - bandWidth / 2, size.height / 2 - 10.dp.toPx()),
            size = Size(bandWidth, 20.dp.toPx())
        )
        if (cents != null) {
            val x = mid + cents.coerceIn(-RANGE_CENTS, RANGE_CENTS).toFloat() * perCent
            drawLine(needle, Offset(x, 0f), Offset(x, size.height), strokeWidth = 6.dp.toPx(), cap = StrokeCap.Round)
        }
    }
}

@Composable
private fun Hit(hit: NoteOutcome, next: Int?) {
    val result = hit.result
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Ring(1f, MaterialTheme.colorScheme.tertiary, size = 240.dp) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(56.dp)
                )
                Text(NoteNames.pitchClass(hit.midi, NoteNaming.GERMAN), fontSize = 64.sp, fontWeight = FontWeight.Bold)
            }
        }
        Text(
            stringResource(R.string.practice_hit),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.testTag(TAG_PRACTICE_STATUS)
        )
        if (result != null) {
            Text(
                stringResource(
                    R.string.practice_hit_stats,
                    seconds(result.timeToHitSec ?: 0.0),
                    result.entryCents?.let(::centsText) ?: "–",
                    result.holdMeanCents?.let(::centsText) ?: "–"
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        if (next != null) {
            Text(
                stringResource(R.string.practice_next_note, NoteNames.germanOctaveName(next)),
                style = MaterialTheme.typography.titleLarge
            )
        }
    }
}

@Composable
private fun Message(title: String, detail: String, color: Color = Color.Unspecified) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium,
            color = color,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag(TAG_PRACTICE_STATUS)
        )
        if (detail.isNotEmpty()) {
            Text(
                detail,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun Ring(progress: Float, color: Color, size: Dp, content: @Composable () -> Unit) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 16.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(track, 0f, FULL_CIRCLE, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            if (progress > 0f) {
                drawArc(
                    color,
                    -QUARTER_CIRCLE,
                    FULL_CIRCLE * progress.coerceIn(0f, 1f),
                    false,
                    Offset(inset, inset),
                    arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round)
                )
            }
        }
        content()
    }
}

@Composable
private fun RouteLine(route: OutputRoute, showHint: Boolean) {
    val text = when (route.kind) {
        OutputKind.HEADPHONES -> route.name?.let { stringResource(R.string.practice_drone_headphones_named, it) }
            ?: stringResource(R.string.practice_drone_headphones)

        OutputKind.SPEAKER -> stringResource(R.string.practice_drone_speaker)

        OutputKind.OTHER -> stringResource(R.string.practice_drone_other, route.name ?: "?")

        OutputKind.UNKNOWN -> return
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (showHint && route.kind == OutputKind.SPEAKER) {
            Text(
                stringResource(R.string.practice_speaker_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun Controls(paused: Boolean, actions: RunningActions) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.large),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        Control(PracticeIcons.Previous, stringResource(R.string.control_back), actions.back)
        if (paused) {
            Control(Icons.Filled.PlayArrow, stringResource(R.string.control_resume), actions.resume)
        } else {
            Control(PracticeIcons.Pause, stringResource(R.string.control_pause), actions.pause)
        }
        Control(PracticeIcons.Next, stringResource(R.string.control_skip), actions.skip)
        Control(
            PracticeIcons.Stop,
            stringResource(R.string.control_stop),
            actions.stop,
            Modifier.testTag(TAG_PRACTICE_STOP)
        )
    }
}

@Composable
private fun Control(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = onClick, modifier = modifier.height(64.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(26.dp))
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** "+3 ct", "−12 ct", "±0 ct". */
internal fun centsText(cents: Double): String {
    val rounded = cents.roundToInt()
    val sign = when {
        rounded > 0 -> "+"
        rounded < 0 -> "−"
        else -> "±"
    }
    return "$sign${abs(rounded)} ct"
}

@Composable
internal fun seconds(value: Double): String = String.format(LocalLocale.current.platformLocale, "%.1f", value)

private const val RANGE_CENTS = 50.0
private const val FULL_CIRCLE = 360f
private const val QUARTER_CIRCLE = 90f
