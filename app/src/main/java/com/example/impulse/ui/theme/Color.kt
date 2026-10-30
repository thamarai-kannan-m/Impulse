package com.example.impulse.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Brand
val Violet = Color(0xFF5B4FE0)
val VioletLight = Color(0xFFA79FFF)
val VioletDeep = Color(0xFF3A2FB8)
val Teal = Color(0xFF0EA5A0)
val TealLight = Color(0xFF5EE0D6)
val Coral = Color(0xFFFF7A59)
val CoralLight = Color(0xFFFFB199)
val Pink = Color(0xFFF062A6)

// Semantic
val Success = Color(0xFF22A06B)
val Warning = Color(0xFFF5A524)
val Danger = Color(0xFFE5484D)
val Info = Color(0xFF3E8EF7)

// Light neutrals
val LightBackground = Color(0xFFF6F5FB)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFEEEDF6)
val LightOutline = Color(0xFFCAC7D9)
val LightOnSurface = Color(0xFF1A1830)
val LightOnSurfaceVariant = Color(0xFF615E78)

// Dark neutrals
val DarkBackground = Color(0xFF100F17)
val DarkSurface = Color(0xFF1A1924)
val DarkSurfaceVariant = Color(0xFF282636)
val DarkOutline = Color(0xFF4A4760)
val DarkOnSurface = Color(0xFFECEAF7)
val DarkOnSurfaceVariant = Color(0xFFA9A6C0)

/** Accent colours for habits and categories (good contrast on both themes). */
val Palette = listOf(
    Color(0xFF6D5BFF), Color(0xFF0EA5E9), Color(0xFF10B981), Color(0xFFF59E0B),
    Color(0xFFEC4899), Color(0xFFEF4444), Color(0xFF8B5CF6), Color(0xFF14B8A6),
)

fun paletteColor(index: Int): Color = Palette[Math.floorMod(index, Palette.size)]

object Gradients {
    val Hero = Brush.linearGradient(listOf(Color(0xFF5B4FE0), Color(0xFF8B5CF6), Color(0xFFF062A6)))
    val HeroDark = Brush.linearGradient(listOf(Color(0xFF3A2FB8), Color(0xFF6D3FD0), Color(0xFFB23F7E)))
    val Sunrise = Brush.linearGradient(listOf(Color(0xFFFF9A62), Color(0xFFFF6A88)))
    val Ocean = Brush.linearGradient(listOf(Color(0xFF0EA5A0), Color(0xFF3E8EF7)))
    val Forest = Brush.linearGradient(listOf(Color(0xFF22A06B), Color(0xFF0EA5A0)))
    val Berry = Brush.linearGradient(listOf(Color(0xFF8B5CF6), Color(0xFFEC4899)))
}
