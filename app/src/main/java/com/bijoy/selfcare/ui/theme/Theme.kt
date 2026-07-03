package com.bijoy.selfcare.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF00E5FF),
    onPrimary = Color(0xFF000000),
    secondary = Color(0xFFE0E0E0),
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFFFF4081),
    background = Color(0xFF121212),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF212121),
    onSurface = Color(0xFFF5F5F5),
    surfaceVariant = Color(0xFF303030),
    onSurfaceVariant = Color(0xFFE0E0E0),
    error = Color(0xFFFF5252)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFFFF5722),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF424242),
    onSecondary = Color(0xFF000000),
    tertiary = Color(0xFF2979FF),
    background = Color(0xFFF5F5F5),
    onBackground = Color(0xFF121212),
    surface = Color(0xFFE0E0E0),
    onSurface = Color(0xFF212121),
    surfaceVariant = Color(0xFFEEEEEE),
    onSurfaceVariant = Color(0xFF424242),
    error = Color(0xFFD32F2F)
)

@Composable
fun BijoySelfCareTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = androidx.compose.material3.Typography(),
        content = content
    )
}
