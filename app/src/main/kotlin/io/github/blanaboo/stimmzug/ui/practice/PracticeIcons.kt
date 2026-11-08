package io.github.blanaboo.stimmzug.ui.practice

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Transport icons the core icon set lacks (24 × 24, filled). */
internal object PracticeIcons {
    val Previous: ImageVector = icon("Previous") {
        moveTo(6f, 6f)
        lineTo(8f, 6f)
        lineTo(8f, 18f)
        lineTo(6f, 18f)
        close()
        moveTo(9.5f, 12f)
        lineTo(18f, 6f)
        lineTo(18f, 18f)
        close()
    }

    val Pause: ImageVector = icon("Pause") {
        moveTo(6f, 5f)
        lineTo(10f, 5f)
        lineTo(10f, 19f)
        lineTo(6f, 19f)
        close()
        moveTo(14f, 5f)
        lineTo(18f, 5f)
        lineTo(18f, 19f)
        lineTo(14f, 19f)
        close()
    }

    val Next: ImageVector = icon("Next") {
        moveTo(6f, 6f)
        lineTo(14.5f, 12f)
        lineTo(6f, 18f)
        close()
        moveTo(16f, 6f)
        lineTo(18f, 6f)
        lineTo(18f, 18f)
        lineTo(16f, 18f)
        close()
    }

    val Stop: ImageVector = icon("Stop") {
        moveTo(6f, 6f)
        lineTo(18f, 6f)
        lineTo(18f, 18f)
        lineTo(6f, 18f)
        close()
    }

    private fun icon(name: String, path: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
            .apply { path(fill = SolidColor(Color.Black), pathBuilder = path) }
            .build()
}
