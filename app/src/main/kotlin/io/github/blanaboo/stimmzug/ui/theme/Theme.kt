package io.github.blanaboo.stimmzug.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** "Stage" palette: anthracite background with a brass accent; green is reserved for a successful hit. */
object StimmzugColors {
    val Brass = Color(0xFFD9A93F)
    val BrassDeep = Color(0xFF8A6514)
    val Anthracite = Color(0xFF15171A)
    val AnthraciteRaised = Color(0xFF1E2125)
    val AnthraciteHigh = Color(0xFF2A2E33)
    val WarmWhite = Color(0xFFECE7DE)
    val Hit = Color(0xFF5BC27A)
    val HitDeep = Color(0xFF2E7D4A)
}

private val DarkColors = darkColorScheme(
    primary = StimmzugColors.Brass,
    onPrimary = Color(0xFF2B1F00),
    secondary = Color(0xFFC9B48A),
    onSecondary = Color(0xFF2B2416),
    tertiary = StimmzugColors.Hit,
    onTertiary = Color(0xFF003919),
    background = StimmzugColors.Anthracite,
    onBackground = StimmzugColors.WarmWhite,
    surface = StimmzugColors.AnthraciteRaised,
    onSurface = StimmzugColors.WarmWhite,
    surfaceVariant = StimmzugColors.AnthraciteHigh,
    onSurfaceVariant = Color(0xFFB9B3A8),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF3B0907)
)

private val LightColors = lightColorScheme(
    primary = StimmzugColors.BrassDeep,
    onPrimary = Color.White,
    secondary = Color(0xFF6B5B3A),
    onSecondary = Color.White,
    tertiary = StimmzugColors.HitDeep,
    onTertiary = Color.White,
    background = Color(0xFFFAF7F0),
    onBackground = Color(0xFF1C1B18),
    surface = Color(0xFFFFFCF6),
    onSurface = Color(0xFF1C1B18),
    surfaceVariant = Color(0xFFEDE6D8),
    onSurfaceVariant = Color(0xFF4D473C)
)

@Composable
fun StimmzugTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = StimmzugTypography,
        content = content
    )
}
