package com.masselis.tpmsadvanced.interfaces.composable

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import com.masselis.tpmsadvanced.core.ui.DarkColors
import com.masselis.tpmsadvanced.core.ui.LightColors
import com.masselis.tpmsadvanced.core.ui.LocalReadability
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.darkColorScheme

@Composable
internal fun TpmsAdvancedTheme(
    colorScheme: ColorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
    shapes: Shapes = MaterialTheme.shapes,
    typography: Typography = AppTypography,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (LocalReadability.current.highVisibility) darkColorScheme(
            primary = Color.Yellow, onPrimary = Color.Black, primaryContainer = Color(0xFF292900),
            onPrimaryContainer = Color.White, secondary = Color.Yellow, onSecondary = Color.Black,
            background = Color.Black, onBackground = Color.White, surface = Color.Black,
            onSurface = Color.White, surfaceVariant = Color(0xFF202020), onSurfaceVariant = Color.White,
            surfaceContainer = Color(0xFF151515), surfaceContainerHigh = Color(0xFF202020),
            error = Color(0xFFFFB4AB), onError = Color.Black, outline = Color.White,
        ) else colorScheme,
        shapes = shapes,
        typography = typography,
        content = content
    )
}
