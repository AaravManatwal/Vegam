package com.vegam.core.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Vegam brand: Electric Indigo + Cyan. */
val VegamIndigo = Color(0xFF6C5CE7)
val VegamCyan = Color(0xFF00B8D4)
val VegamGradient = Brush.linearGradient(listOf(VegamIndigo, VegamCyan))

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA9A0FF),
    onPrimary = Color(0xFF1B0F6B),
    primaryContainer = Color(0xFF3D2FB0),
    onPrimaryContainer = Color(0xFFE5DFFF),
    secondary = VegamCyan,
    onSecondary = Color(0xFF00363E),
    background = Color(0xFF121212),
    onBackground = Color(0xFFF2F2FB),
    surface = Color(0xFF1B1B25),
    onSurface = Color(0xFFF2F2FB),
    surfaceVariant = Color(0xFF262636),
    onSurfaceVariant = Color(0xFFA6A6C2),
    outline = Color(0xFF2F2F45),
    outlineVariant = Color(0xFF2F2F45),
    error = Color(0xFFFB923C),
)

private val LightColors = lightColorScheme(
    primary = VegamIndigo,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE5DFFF),
    onPrimaryContainer = Color(0xFF1B0F6B),
    secondary = Color(0xFF00838F),
    onSecondary = Color.White,
    background = Color(0xFFFAFAFA),
    onBackground = Color(0xFF16162B),
    surface = Color.White,
    onSurface = Color(0xFF16162B),
    surfaceVariant = Color(0xFFEFEFFA),
    onSurfaceVariant = Color(0xFF5B5B79),
    outline = Color(0xFFE0E0F1),
    outlineVariant = Color(0xFFE0E0F1),
    error = Color(0xFFC2410C),
)

/** Corners: card 24dp, button 16dp, chip 12dp (spec ke hisaab se). */
private val VegamShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
)

/**
 * dynamicColor = Material You (Android 12+). Default false: Vegam ka apna indigo-cyan brand dikhe.
 * Settings mein user ise ON kar sakega (Phase 3C).
 */
@Composable
fun VegamTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, shapes = VegamShapes, content = content)
}

/** Screen ke peeche halka indigo (upar-right) aur cyan (upar-left) glow: "gradient mesh" ka simple roop. */
@Composable
fun Modifier.vegamBackground(): Modifier {
    val bg = MaterialTheme.colorScheme.background
    val dark = bg.luminance() < 0.5f
    val a = if (dark) 0.28f else 0.14f
    return this.drawBehind {
        drawRect(bg)
        drawRect(
            Brush.radialGradient(
                listOf(VegamIndigo.copy(alpha = a), Color.Transparent),
                center = Offset(size.width, 0f), radius = size.width * 0.95f,
            ),
        )
        drawRect(
            Brush.radialGradient(
                listOf(VegamCyan.copy(alpha = a * 0.65f), Color.Transparent),
                center = Offset(0f, size.height * 0.04f), radius = size.width * 0.8f,
            ),
        )
    }
}
