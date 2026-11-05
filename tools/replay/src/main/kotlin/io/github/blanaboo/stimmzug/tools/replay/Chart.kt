package io.github.blanaboo.stimmzug.tools.replay

import io.github.blanaboo.stimmzug.dsp.match.MatchPhase
import io.github.blanaboo.stimmzug.dsp.music.NoteMath
import io.github.blanaboo.stimmzug.dsp.music.NoteNames
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Line2D
import java.awt.geom.Path2D
import java.awt.image.BufferedImage
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min

/**
 * PNG chart of a replay: pitch (cents from the target with the tolerance band, or a note scale in free mode) above,
 * input level with the gate thresholds below. Labels are German (the tool is for the owner).
 */
object Chart {
    const val WIDTH = 1600
    const val HEIGHT = 900

    /** Pitch panel range around the target; deviations beyond (wrong octave/note) are marked at the edge. */
    const val RANGE_CENTS = 50.0

    val BAND = Color(198, 239, 206)
    val SMOOTHED = Color(31, 87, 196)
    val RAW = Color(120, 120, 120)
    val OUTSIDE = Color(204, 40, 40)
    val IN_RANGE = Color(226, 236, 252)
    val HIT = Color(20, 140, 60)
    val CALIBRATION = Color(236, 236, 236)
    val GATE = Color(230, 130, 0)

    private const val LEFT = 90
    private const val RIGHT = 30
    private const val TOP = 60
    private const val PITCH_HEIGHT = 560
    private const val LEVEL_TOP = TOP + PITCH_HEIGHT + 50
    private const val LEVEL_HEIGHT = 170
    private const val PLOT_WIDTH = WIDTH - LEFT - RIGHT
    private const val LEVEL_MIN_DB = -90.0

    fun render(result: ReplayResult, title: String, a4Hz: Double, file: File) {
        val image = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            g.color = Color.WHITE
            g.fillRect(0, 0, WIDTH, HEIGHT)
            g.font = Font(Font.SANS_SERIF, Font.BOLD, 18)
            g.color = Color.BLACK
            g.drawString(title, LEFT, 32)
            val axis = TimeAxis(max(result.durationSec, 0.1))
            if (result.options.targetHz !=
                null
            ) {
                drawTargetPitch(g, result, axis)
            } else {
                drawFreePitch(g, result, axis, a4Hz)
            }
            drawLevel(g, result, axis)
        } finally {
            g.dispose()
        }
        ImageIO.write(image, "png", file)
    }

    private class TimeAxis(val durationSec: Double) {
        val step = listOf(0.5, 1.0, 2.0, 5.0, 10.0, 15.0, 30.0, 60.0, 120.0, 300.0, 600.0)
            .first { durationSec / it <= 16 || it == 600.0 }

        fun x(t: Double) = LEFT + t / durationSec * PLOT_WIDTH
    }

    private fun drawTargetPitch(g: Graphics2D, result: ReplayResult, axis: TimeAxis) {
        val target = result.options.targetHz!!
        fun y(cents: Double) = TOP + (RANGE_CENTS - cents) / (2 * RANGE_CENTS) * PITCH_HEIGHT
        shadeCalibration(g, result, axis, TOP, PITCH_HEIGHT)
        // Phases "Im Bereich" as background, one rectangle per contiguous stretch (per-frame boxes leave pixel gaps).
        g.color = IN_RANGE
        var stretchStart: Double? = null
        for ((a, b) in result.rows.zipWithNext()) {
            val inRange = a.status?.phase == MatchPhase.IN_RANGE
            if (inRange && stretchStart == null) stretchStart = a.frame.timeSec
            if (!inRange && stretchStart != null) {
                fill(g, axis.x(stretchStart), TOP.toDouble(), axis.x(a.frame.timeSec), (TOP + PITCH_HEIGHT).toDouble())
                stretchStart = null
            }
            if (inRange && b === result.rows.last()) {
                fill(
                    g,
                    axis.x(stretchStart!!),
                    TOP.toDouble(),
                    axis.x(b.frame.timeSec),
                    (TOP + PITCH_HEIGHT).toDouble()
                )
            }
        }
        val tolerance = result.options.match.toleranceCents
        g.color = BAND
        fill(g, LEFT.toDouble(), y(tolerance), (LEFT + PLOT_WIDTH).toDouble(), y(-tolerance))
        drawTimeGrid(g, axis, TOP, PITCH_HEIGHT)
        g.font = Font(Font.SANS_SERIF, Font.PLAIN, 13)
        for (c in listOf(-50.0, -25.0, 0.0, 25.0, 50.0)) {
            g.color = if (c == 0.0) Color.GRAY else Color(225, 225, 225)
            g.draw(Line2D.Double(LEFT.toDouble(), y(c), (LEFT + PLOT_WIDTH).toDouble(), y(c)))
            g.color = Color.DARK_GRAY
            g.drawString(String.format(Locale.GERMANY, "%+.0f ct", c).replace("+0", "0"), 20, (y(c) + 5).toInt())
        }
        // Raw deviations of the frames the hit logic saw.
        for (row in result.rows) {
            val e = row.frame.estimate
            if (row.inCalibration || !row.passedGate || !e.isVoiced) continue
            val cents = NoteMath.cents(e.frequencyHz, target)
            val x = axis.x(row.frame.timeSec)
            if (abs(cents) <= RANGE_CENTS) {
                g.color = RAW
                g.fillOval((x - 2).toInt(), (y(cents) - 2).toInt(), 4, 4)
            } else {
                g.color = OUTSIDE
                val edge = if (cents > 0) TOP + 6.0 else TOP + PITCH_HEIGHT - 6.0
                g.fillRect((x - 2).toInt(), (edge - 4).toInt(), 4, 8)
            }
        }
        // Smoothed deviation as used for the decision.
        g.color = SMOOTHED
        g.stroke = BasicStroke(2f)
        var path: Path2D.Double? = null
        for (row in result.rows) {
            val cents = row.status?.centsDeviation?.takeIf { abs(it) <= RANGE_CENTS }
            if (cents == null) {
                path?.let { g.draw(it) }
                path = null
                continue
            }
            val p = path ?: Path2D.Double().also { it.moveTo(axis.x(row.frame.timeSec), y(cents)) }
            p.lineTo(axis.x(row.frame.timeSec), y(cents))
            path = p
        }
        path?.let { g.draw(it) }
        g.stroke = BasicStroke(1f)
        // Hit.
        val note = result.note
        val hitRow = result.rows.firstOrNull { it.status?.phase == MatchPhase.HIT }
        if (note != null && hitRow != null) {
            val x = axis.x(hitRow.frame.timeSec)
            g.color = HIT
            g.stroke = BasicStroke(3f)
            g.draw(Line2D.Double(x, TOP.toDouble(), x, (TOP + PITCH_HEIGHT).toDouble()))
            g.stroke = BasicStroke(1f)
            g.font = Font(Font.SANS_SERIF, Font.BOLD, 14)
            g.drawString(
                String.format(Locale.GERMANY, "Treffer nach %.2f s", note.timeToHitSec),
                (x + 6).toInt(),
                TOP + 20
            )
        }
        frame(g, TOP, PITCH_HEIGHT)
        legend(
            g,
            listOf(
                RAW to "Rohwert",
                SMOOTHED to "geglättet (Median)",
                BAND to String.format(Locale.GERMANY, "Toleranz ±%.1f ct", tolerance),
                IN_RANGE to "im Bereich",
                OUTSIDE to "außerhalb ±50 ct (falscher Ton/Oktave)"
            )
        )
    }

    private fun drawFreePitch(g: Graphics2D, result: ReplayResult, axis: TimeAxis, a4Hz: Double) {
        val points = result.rows.filter { !it.inCalibration && it.passedGate && it.frame.estimate.isVoiced }
            .map { it.frame.timeSec to NoteMath.MIDI_A4 + 12 * log2(it.frame.estimate.frequencyHz / a4Hz) }
        var low = points.minOfOrNull { it.second }?.let { floor(it) - 1 } ?: 33.0
        var high = points.maxOfOrNull { it.second }?.let { ceil(it) + 1 } ?: 57.0
        if (high - low < 6) {
            val mid = (low + high) / 2
            low = mid - 3
            high = mid + 3
        }
        fun y(midi: Double) = TOP + (high - midi) / (high - low) * PITCH_HEIGHT
        shadeCalibration(g, result, axis, TOP, PITCH_HEIGHT)
        drawTimeGrid(g, axis, TOP, PITCH_HEIGHT)
        g.font = Font(Font.SANS_SERIF, Font.PLAIN, 12)
        val everyNote = high - low <= 24
        for (m in ceil(low).toInt()..floor(high).toInt()) {
            if (!everyNote && Math.floorMod(m, 12) != 0) continue
            g.color = Color(225, 225, 225)
            g.draw(Line2D.Double(LEFT.toDouble(), y(m.toDouble()), (LEFT + PLOT_WIDTH).toDouble(), y(m.toDouble())))
            g.color = Color.DARK_GRAY
            g.drawString(NoteNames.germanOctaveName(m), 6, (y(m.toDouble()) + 4).toInt())
        }
        g.color = SMOOTHED
        for ((t, m) in points) g.fillOval((axis.x(t) - 2).toInt(), (y(m) - 2).toInt(), 4, 4)
        frame(g, TOP, PITCH_HEIGHT)
        legend(g, listOf(SMOOTHED to "erkannte Tonhöhe"))
    }

    private fun drawLevel(g: Graphics2D, result: ReplayResult, axis: TimeAxis) {
        fun y(db: Double) = LEVEL_TOP + (0.0 - max(db, LEVEL_MIN_DB)) / -LEVEL_MIN_DB * LEVEL_HEIGHT
        shadeCalibration(g, result, axis, LEVEL_TOP, LEVEL_HEIGHT)
        drawTimeGrid(g, axis, LEVEL_TOP, LEVEL_HEIGHT)
        g.font = Font(Font.SANS_SERIF, Font.PLAIN, 13)
        for (db in listOf(0.0, -30.0, -60.0, -90.0)) {
            g.color = Color(225, 225, 225)
            g.draw(Line2D.Double(LEFT.toDouble(), y(db), (LEFT + PLOT_WIDTH).toDouble(), y(db)))
            g.color = Color.DARK_GRAY
            g.drawString(String.format(Locale.GERMANY, "%.0f dB", db), 20, (y(db) + 5).toInt())
        }
        result.calibration?.let { c ->
            g.color = GATE
            g.stroke = BasicStroke(2f)
            g.draw(
                Line2D.Double(
                    LEFT.toDouble(),
                    y(c.openThresholdDb),
                    (LEFT + PLOT_WIDTH).toDouble(),
                    y(c.openThresholdDb)
                )
            )
            g.stroke = BasicStroke(1f)
        }
        g.color = Color.DARK_GRAY
        val path = Path2D.Double()
        result.rows.forEachIndexed { i, row ->
            val x = axis.x(row.frame.timeSec)
            if (i == 0) path.moveTo(x, y(row.frame.estimate.levelDb)) else path.lineTo(x, y(row.frame.estimate.levelDb))
        }
        g.draw(path)
        frame(g, LEVEL_TOP, LEVEL_HEIGHT)
        g.font = Font(Font.SANS_SERIF, Font.PLAIN, 13)
        g.color = Color.BLACK
        val label = if (result.calibration != null) "Pegel (dBFS), orange: Gate-Schwelle" else "Pegel (dBFS)"
        g.drawString(label, LEFT, LEVEL_TOP - 8)
        // Time labels below the level panel.
        var t = 0.0
        while (t <= axis.durationSec + 1e-9) {
            g.drawString(
                String.format(Locale.GERMANY, "%s s", fmtTime(t)),
                (axis.x(t) - 10).toInt(),
                LEVEL_TOP + LEVEL_HEIGHT + 20
            )
            t += axis.step
        }
    }

    private fun fmtTime(t: Double) = if (t ==
        floor(t)
    ) {
        "%.0f".format(Locale.GERMANY, t)
    } else {
        "%.1f".format(Locale.GERMANY, t)
    }

    private fun shadeCalibration(g: Graphics2D, result: ReplayResult, axis: TimeAxis, top: Int, height: Int) {
        val end = result.options.calibrationSec
        if (end <= 0.0) return
        g.color = CALIBRATION
        fill(g, axis.x(0.0), top.toDouble(), axis.x(min(end, axis.durationSec)), (top + height).toDouble())
        g.color = Color.GRAY
        g.font = Font(Font.SANS_SERIF, Font.ITALIC, 12)
        g.drawString("Kalibrierung", (axis.x(0.0) + 4).toInt(), top + height - 6)
    }

    private fun drawTimeGrid(g: Graphics2D, axis: TimeAxis, top: Int, height: Int) {
        g.color = Color(235, 235, 235)
        var t = axis.step
        while (t < axis.durationSec) {
            g.draw(Line2D.Double(axis.x(t), top.toDouble(), axis.x(t), (top + height).toDouble()))
            t += axis.step
        }
    }

    private fun frame(g: Graphics2D, top: Int, height: Int) {
        g.color = Color.GRAY
        g.drawRect(LEFT, top, PLOT_WIDTH, height)
    }

    private fun legend(g: Graphics2D, entries: List<Pair<Color, String>>) {
        g.font = Font(Font.SANS_SERIF, Font.PLAIN, 13)
        var x = LEFT + PLOT_WIDTH
        for ((color, text) in entries.reversed()) {
            x -= g.fontMetrics.stringWidth(text) + 34
            g.color = color
            g.fillRect(x, 40, 14, 12)
            g.color = Color.BLACK
            g.drawString(text, x + 18, 51)
        }
    }

    private fun fill(g: Graphics2D, x0: Double, y0: Double, x1: Double, y1: Double) {
        g.fillRect(x0.toInt(), min(y0, y1).toInt(), max(1, (x1 - x0).toInt()), abs(y1 - y0).toInt())
    }
}
