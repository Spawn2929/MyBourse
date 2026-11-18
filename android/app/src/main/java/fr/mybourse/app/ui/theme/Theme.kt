package fr.mybourse.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import fr.mybourse.app.data.Theme

// Couleurs sobres : vert pétrole, gris bleuté.
private val Clair = lightColorScheme(
    primary = Color(0xFF0F5C4D),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC8EBDF),
    onPrimaryContainer = Color(0xFF002019),
    secondary = Color(0xFF4A635C),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCE8DF),
    onSecondaryContainer = Color(0xFF06201A),
    tertiary = Color(0xFF3F6375),
    background = Color(0xFFF8FAFB),
    onBackground = Color(0xFF191C1D),
    surface = Color(0xFFF8FAFB),
    onSurface = Color(0xFF191C1D),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1F4F5),
    surfaceContainer = Color(0xFFEBEFF0),
    surfaceContainerHigh = Color(0xFFE5E9EA),
    surfaceContainerHighest = Color(0xFFDFE3E4),
    onSurfaceVariant = Color(0xFF404947),
    outlineVariant = Color(0xFFBFC9C5),
    error = Color(0xFFBA1A1A),
)

private val Sombre = darkColorScheme(
    primary = Color(0xFF7FD8C0),
    onPrimary = Color(0xFF00382D),
    primaryContainer = Color(0xFF005142),
    onPrimaryContainer = Color(0xFF9CF4DB),
    secondary = Color(0xFFB1CCC3),
    onSecondary = Color(0xFF1C352F),
    secondaryContainer = Color(0xFF334B45),
    onSecondaryContainer = Color(0xFFCCE8DF),
    tertiary = Color(0xFFA7CCE0),
    background = Color(0xFF0F1415),
    onBackground = Color(0xFFDEE3E3),
    surface = Color(0xFF0F1415),
    onSurface = Color(0xFFDEE3E3),
    surfaceContainerLowest = Color(0xFF0A0F10),
    surfaceContainerLow = Color(0xFF171D1D),
    surfaceContainer = Color(0xFF1B2121),
    surfaceContainerHigh = Color(0xFF252B2C),
    surfaceContainerHighest = Color(0xFF303637),
    onSurfaceVariant = Color(0xFFBFC9C5),
    outlineVariant = Color(0xFF404947),
    error = Color(0xFFFFB4AB),
)

private val Typo = Typography().let { t ->
    t.copy(
        bodyLarge = t.bodyLarge.copy(fontSize = 17.sp, lineHeight = 23.sp),
        bodyMedium = t.bodyMedium.copy(fontSize = 15.sp, lineHeight = 21.sp),
        titleMedium = t.titleMedium.copy(fontSize = 17.sp),
        labelLarge = t.labelLarge.copy(fontSize = 15.sp),
    )
}

@Composable
fun estSombre(theme: Theme): Boolean = when (theme) {
    Theme.AUTO -> isSystemInDarkTheme()
    Theme.CLAIR -> false
    Theme.SOMBRE -> true
}

@Composable
fun MyBourseTheme(theme: Theme, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (estSombre(theme)) Sombre else Clair,
        typography = Typo,
        content = content,
    )
}

/** Vert = hausse / gain, rouge = baisse / perte, lisibles dans les deux thèmes. */
object Couleurs {
    val gainClair = Color(0xFF1B7F3B)
    val perteClair = Color(0xFFC62828)
    val gainSombre = Color(0xFF6FD98A)
    val perteSombre = Color(0xFFFF8A80)

    @Composable
    @ReadOnlyComposable
    fun signe(v: Double?): Color {
        val sombre = MaterialTheme.colorScheme.background.luminance() < 0.5f
        return when {
            v == null || kotlin.math.abs(v) < 0.005 -> MaterialTheme.colorScheme.onSurfaceVariant
            v > 0 -> if (sombre) gainSombre else gainClair
            else -> if (sombre) perteSombre else perteClair
        }
    }
}

private fun Color.luminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue
