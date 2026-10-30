package com.example.impulse.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Violet, onPrimary = Color.White,
    primaryContainer = Color(0xFFE6E3FF), onPrimaryContainer = Color(0xFF1D1660),
    secondary = Teal, onSecondary = Color.White,
    secondaryContainer = Color(0xFFCDF4EF), onSecondaryContainer = Color(0xFF00332F),
    tertiary = Coral, onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE2D9), onTertiaryContainer = Color(0xFF4A1405),
    error = Danger, onError = Color.White, errorContainer = Color(0xFFFFE1E1), onErrorContainer = Color(0xFF5C0A0D),
    background = LightBackground, onBackground = LightOnSurface,
    surface = LightSurface, onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant, onSurfaceVariant = LightOnSurfaceVariant,
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFBFAFF),
    surfaceContainer = Color(0xFFF3F2FA), surfaceContainerHigh = Color(0xFFECEBF5), surfaceContainerHighest = Color(0xFFE5E4F0),
    outline = LightOutline, outlineVariant = Color(0xFFE3E1EE),
    inverseSurface = Color(0xFF2E2C40), inverseOnSurface = Color(0xFFF2F0FA), inversePrimary = VioletLight,
)

private val DarkColors = darkColorScheme(
    primary = VioletLight, onPrimary = Color(0xFF1D1660),
    primaryContainer = Color(0xFF3A2FB8), onPrimaryContainer = Color(0xFFE6E3FF),
    secondary = TealLight, onSecondary = Color(0xFF00332F),
    secondaryContainer = Color(0xFF0C4F4B), onSecondaryContainer = Color(0xFFCDF4EF),
    tertiary = CoralLight, onTertiary = Color(0xFF4A1405),
    tertiaryContainer = Color(0xFF7A2E19), onTertiaryContainer = Color(0xFFFFE2D9),
    error = Color(0xFFFF8A8D), onError = Color(0xFF5C0A0D), errorContainer = Color(0xFF7D1B1F), onErrorContainer = Color(0xFFFFE1E1),
    background = DarkBackground, onBackground = DarkOnSurface,
    surface = DarkSurface, onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant, onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceContainerLowest = Color(0xFF0C0B12), surfaceContainerLow = Color(0xFF16151F),
    surfaceContainer = Color(0xFF1E1D29), surfaceContainerHigh = Color(0xFF262534), surfaceContainerHighest = Color(0xFF2F2D3F),
    outline = DarkOutline, outlineVariant = Color(0xFF34324A),
    inverseSurface = Color(0xFFECEAF7), inverseOnSurface = Color(0xFF1A1924), inversePrimary = Violet,
)

/** Extra semantic colours Material 3 doesn't model. */
@Immutable
data class LoopColors(
    val success: Color, val successContainer: Color,
    val warning: Color, val warningContainer: Color,
    val danger: Color, val dangerContainer: Color,
    val info: Color, val infoContainer: Color,
    val hero: Brush,
    val isDark: Boolean,
)

private val LightLoop = LoopColors(
    success = Success, successContainer = Color(0xFFDDF5E9),
    warning = Color(0xFFD48806), warningContainer = Color(0xFFFFF0D4),
    danger = Danger, dangerContainer = Color(0xFFFFE1E1),
    info = Info, infoContainer = Color(0xFFDCEBFF),
    hero = Gradients.Hero, isDark = false,
)
private val DarkLoop = LoopColors(
    success = Color(0xFF4ADE97), successContainer = Color(0xFF123D2A),
    warning = Color(0xFFFFC555), warningContainer = Color(0xFF4A3510),
    danger = Color(0xFFFF8A8D), dangerContainer = Color(0xFF4F1518),
    info = Color(0xFF7DB6FF), infoContainer = Color(0xFF15304F),
    hero = Gradients.HeroDark, isDark = true,
)

val LocalLoopColors = staticCompositionLocalOf { LightLoop }

val LoopShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun ImpulseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    CompositionLocalProvider(LocalLoopColors provides if (darkTheme) DarkLoop else LightLoop) {
        MaterialTheme(colorScheme = colorScheme, typography = Typography, shapes = LoopShapes, content = content)
    }
}

object LoopTheme {
    val colors: LoopColors @Composable get() = LocalLoopColors.current
}
