package com.suncanvas.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suncanvas.app.R

/* ----------------------------------------------------------------------------
 * SunCanvas design system
 *  Palette: deep navy night, warm white day, golden-amber primary, sunset-orange
 *  secondary, soft-blue tertiary. Display type: Space Grotesk. Text: Inter.
 *  Spacing on a 4-dp grid; corner radii 12 / 20 / 28.
 * --------------------------------------------------------------------------*/

object SunPalette {
    val Navy950 = Color(0xFF070B1C)
    val Navy900 = Color(0xFF0B1026)
    val Navy850 = Color(0xFF10173A)
    val Navy800 = Color(0xFF151E46)
    val Navy700 = Color(0xFF1F2A5C)
    val Navy600 = Color(0xFF2C3A75)
    val Ink = Color(0xFFEEF1FA)
    val InkMuted = Color(0xFFA9B1CF)

    val WarmWhite = Color(0xFFFFF9F2)
    val Sand50 = Color(0xFFFBF1E4)
    val Sand100 = Color(0xFFF4E6D3)
    val Cocoa = Color(0xFF2A2018)
    val CocoaMuted = Color(0xFF6E6052)

    val Amber = Color(0xFFFFB547)
    val AmberDeep = Color(0xFFB86A00)
    val Sunset = Color(0xFFFF7A3D)
    val SunsetDeep = Color(0xFFC94F1C)
    val SoftBlue = Color(0xFF7FB2FF)
    val SoftBlueDeep = Color(0xFF3D6FD1)
    val Twilight = Color(0xFF8E6CFF)
    val Danger = Color(0xFFFF6B6B)
}

/** Semantic colours beyond Material roles. */
@Immutable
data class SunColors(
    val sunrise: Color,
    val sunset: Color,
    val golden: Color,
    val blue: Color,
    val twilight: Color,
    val night: Color,
    val dayTrack: Color,
    val cardStroke: Color,
    val muted: Color,
)

val LocalSunColors = staticCompositionLocalOf {
    SunColors(SunPalette.Amber, SunPalette.Sunset, SunPalette.Amber, SunPalette.SoftBlue, SunPalette.Twilight,
        SunPalette.Navy900, Color(0x33FFFFFF), Color(0x1AFFFFFF), SunPalette.InkMuted)
}

private val DarkScheme: ColorScheme = darkColorScheme(
    primary = SunPalette.Amber,
    onPrimary = Color(0xFF2A1700),
    primaryContainer = Color(0xFF4A3008),
    onPrimaryContainer = Color(0xFFFFDDB0),
    secondary = SunPalette.Sunset,
    onSecondary = Color(0xFF2E0E00),
    secondaryContainer = Color(0xFF55260F),
    onSecondaryContainer = Color(0xFFFFDBCB),
    tertiary = SunPalette.SoftBlue,
    onTertiary = Color(0xFF00224D),
    tertiaryContainer = Color(0xFF1C3566),
    onTertiaryContainer = Color(0xFFD7E3FF),
    background = SunPalette.Navy900,
    onBackground = SunPalette.Ink,
    surface = SunPalette.Navy900,
    onSurface = SunPalette.Ink,
    surfaceVariant = SunPalette.Navy800,
    onSurfaceVariant = SunPalette.InkMuted,
    surfaceContainerLowest = SunPalette.Navy950,
    surfaceContainerLow = Color(0xFF0E1431),
    surfaceContainer = SunPalette.Navy850,
    surfaceContainerHigh = SunPalette.Navy800,
    surfaceContainerHighest = SunPalette.Navy700,
    outline = Color(0xFF4A5480),
    outlineVariant = Color(0xFF2A3360),
    error = SunPalette.Danger,
)

private val LightScheme: ColorScheme = lightColorScheme(
    primary = SunPalette.AmberDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE2B8),
    onPrimaryContainer = Color(0xFF3A2200),
    secondary = SunPalette.SunsetDeep,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDACB),
    onSecondaryContainer = Color(0xFF3B0F00),
    tertiary = SunPalette.SoftBlueDeep,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD9E4FF),
    onTertiaryContainer = Color(0xFF00194A),
    background = SunPalette.WarmWhite,
    onBackground = SunPalette.Cocoa,
    surface = SunPalette.WarmWhite,
    onSurface = SunPalette.Cocoa,
    surfaceVariant = SunPalette.Sand100,
    onSurfaceVariant = SunPalette.CocoaMuted,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFFCF8),
    surfaceContainer = SunPalette.Sand50,
    surfaceContainerHigh = SunPalette.Sand100,
    surfaceContainerHighest = Color(0xFFEDDCC5),
    outline = Color(0xFFB8A894),
    outlineVariant = Color(0xFFE5D6C2),
)

val DisplayFamily = FontFamily(
    Font(R.font.space_grotesk_300, FontWeight.Light),
    Font(R.font.space_grotesk_400, FontWeight.Normal),
    Font(R.font.space_grotesk_500, FontWeight.Medium),
    Font(R.font.space_grotesk_700, FontWeight.Bold),
)

val TextFamily = FontFamily(
    Font(R.font.inter_300, FontWeight.Light),
    Font(R.font.inter_400, FontWeight.Normal),
    Font(R.font.inter_500, FontWeight.Medium),
    Font(R.font.inter_600, FontWeight.SemiBold),
    Font(R.font.inter_700, FontWeight.Bold),
)

private val base = Typography()

val SunTypography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = DisplayFamily, fontWeight = FontWeight.Light),
    displayMedium = base.displayMedium.copy(fontFamily = DisplayFamily, fontWeight = FontWeight.Light),
    displaySmall = base.displaySmall.copy(fontFamily = DisplayFamily, fontWeight = FontWeight.Normal),
    headlineLarge = base.headlineLarge.copy(fontFamily = DisplayFamily, fontWeight = FontWeight.Medium),
    headlineMedium = base.headlineMedium.copy(fontFamily = DisplayFamily, fontWeight = FontWeight.Medium),
    headlineSmall = base.headlineSmall.copy(fontFamily = DisplayFamily, fontWeight = FontWeight.Medium),
    titleLarge = base.titleLarge.copy(fontFamily = DisplayFamily, fontWeight = FontWeight.Medium),
    titleMedium = base.titleMedium.copy(fontFamily = TextFamily, fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontFamily = TextFamily, fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.copy(fontFamily = TextFamily),
    bodyMedium = base.bodyMedium.copy(fontFamily = TextFamily),
    bodySmall = base.bodySmall.copy(fontFamily = TextFamily),
    labelLarge = base.labelLarge.copy(fontFamily = TextFamily, fontWeight = FontWeight.SemiBold),
    labelMedium = base.labelMedium.copy(fontFamily = TextFamily, fontWeight = FontWeight.Medium),
    labelSmall = base.labelSmall.copy(fontFamily = TextFamily, fontWeight = FontWeight.Medium, letterSpacing = 0.6.sp),
)

/** Large digital clock style. */
val ClockTextStyle = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.Light, fontSize = 64.sp, letterSpacing = (-1).sp)

val SunShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val screen = 20.dp
}

@Composable
fun SunCanvasTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    val sun = if (darkTheme) {
        SunColors(SunPalette.Amber, SunPalette.Sunset, Color(0xFFFFC870), SunPalette.SoftBlue, SunPalette.Twilight,
            SunPalette.Navy950, Color(0x26FFFFFF), Color(0x14FFFFFF), SunPalette.InkMuted)
    } else {
        SunColors(Color(0xFFE08A00), SunPalette.SunsetDeep, Color(0xFFD99A2B), SunPalette.SoftBlueDeep, Color(0xFF6B4FD8),
            SunPalette.Navy800, Color(0x1F2A2018), Color(0x142A2018), SunPalette.CocoaMuted)
    }
    CompositionLocalProvider(LocalSunColors provides sun) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = SunTypography,
            shapes = SunShapes,
            content = content,
        )
    }
}
