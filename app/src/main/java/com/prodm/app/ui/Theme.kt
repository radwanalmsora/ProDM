package com.prodm.app.ui

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val Light = lightColorScheme(
    primary = Color(0xFF4F46E5), onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E7FF), onPrimaryContainer = Color(0xFF1E1B4B),
    secondary = Color(0xFF6366F1), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0E1FF), onSecondaryContainer = Color(0xFF1E1B4B),
    tertiary = Color(0xFF9333EA), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF3E8FF), onTertiaryContainer = Color(0xFF3B0764),
    background = Color(0xFFF8F9FE), onBackground = Color(0xFF111827),
    surface = Color(0xFFF8F9FE), onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFE5E7F5), onSurfaceVariant = Color(0xFF4B5563),
    outline = Color(0xFF9CA3AF),
    surfaceContainerLow = Color(0xFFF1F3FC),
    surfaceContainer = Color(0xFFEBEEFA),
    surfaceContainerHigh = Color(0xFFE5E8F7)
)

private val Dark = darkColorScheme(
    primary = Color(0xFF818CF8), onPrimary = Color(0xFF1E1B4B),
    primaryContainer = Color(0xFF3730A3), onPrimaryContainer = Color(0xFFE0E7FF),
    secondary = Color(0xFFA5B4FC), onSecondary = Color(0xFF1E1B4B),
    secondaryContainer = Color(0xFF312E81), onSecondaryContainer = Color(0xFFE0E7FF),
    tertiary = Color(0xFFC084FC), onTertiary = Color(0xFF3B0764),
    tertiaryContainer = Color(0xFF6B21A8), onTertiaryContainer = Color(0xFFF3E8FF),
    background = Color(0xFF0B0D17), onBackground = Color(0xFFE5E7EB),
    surface = Color(0xFF0B0D17), onSurface = Color(0xFFE5E7EB),
    surfaceVariant = Color(0xFF272B3F), onSurfaceVariant = Color(0xFF9CA3AF),
    outline = Color(0xFF4B5563),
    surfaceContainerLow = Color(0xFF121524),
    surfaceContainer = Color(0xFF171A2B),
    surfaceContainerHigh = Color(0xFF1E2236)
)

@Composable
fun ProDMTheme(theme: Int, amoled: Boolean, dynamic: Boolean, content: @Composable () -> Unit) {
    val dark = when (theme) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    val ctx = LocalContext.current
    var scheme = when {
        dynamic && Build.VERSION.SDK_INT >= 31 ->
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> Dark
        else -> Light
    }
    if (dark && amoled) {
        scheme = scheme.copy(
            background = Color.Black, surface = Color.Black,
            surfaceContainerLow = Color(0xFF0A0A0C),
            surfaceContainer = Color(0xFF111114),
            surfaceContainerHigh = Color(0xFF18181C)
        )
    }
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window
        if (window != null) {
            val c = WindowCompat.getInsetsController(window, view)
            c.isAppearanceLightStatusBars = !dark
            c.isAppearanceLightNavigationBars = !dark
        }
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
