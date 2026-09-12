package com.zentra.z3d.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Identidade visual Zentra: escura, violeta + ciano, cantos arredondados. */
object ZentraColors {
    val bg = Color(0xFF0B0E17)
    val surface = Color(0xFF151A2B)
    val surface2 = Color(0xFF1D2338)
    val surface3 = Color(0xFF262E49)
    val accent = Color(0xFF7C5CFF)
    val accentSoft = Color(0xFF9D86FF)
    val accent2 = Color(0xFF38E1FF)
    val textMain = Color(0xFFF2F4FA)
    val textDim = Color(0xFF9AA3B8)
    val danger = Color(0xFFFF5C7A)
    val success = Color(0xFF3ED598)
    val warn = Color(0xFFFFB020)
    val axisX = Color(0xFFFF6B6B)
    val axisY = Color(0xFF51D88A)
    val axisZ = Color(0xFF5C9CFF)
    val select = Color(0xFFFFC14D)

    val gradient: Brush = Brush.horizontalGradient(listOf(Color(0xFF7C5CFF), Color(0xFF38B6FF)))
    val cardGradient: Brush = Brush.verticalGradient(listOf(Color(0xFF1A2036), Color(0xFF141A2E)))
}

private val DarkScheme = darkColorScheme(
    primary = ZentraColors.accent,
    onPrimary = Color.White,
    secondary = ZentraColors.accent2,
    onSecondary = Color(0xFF06121A),
    background = ZentraColors.bg,
    onBackground = ZentraColors.textMain,
    surface = ZentraColors.surface,
    onSurface = ZentraColors.textMain,
    surfaceVariant = ZentraColors.surface2,
    onSurfaceVariant = ZentraColors.textDim,
    error = ZentraColors.danger,
    onError = Color.White
)

private val ZentraShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun ZentraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkScheme,
        shapes = ZentraShapes,
        typography = Typography(),
        content = content
    )
}
