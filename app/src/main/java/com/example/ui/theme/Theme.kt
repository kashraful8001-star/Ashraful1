package com.example.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val HorrorColorScheme = darkColorScheme(
    primary = BloodRed,
    onPrimary = HorrorBg,
    primaryContainer = DeepCrimson,
    onPrimaryContainer = BloodRed,
    secondary = HorrorGold,
    onSecondary = HorrorBg,
    secondaryContainer = HorrorSurfaceVariant,
    onSecondaryContainer = HorrorGold,
    tertiary = GhostGreen,
    onTertiary = HorrorBg,
    background = HorrorBg,
    onBackground = OnHorrorSurface,
    surface = HorrorSurface,
    onSurface = OnHorrorSurface,
    surfaceVariant = HorrorSurfaceVariant,
    onSurfaceVariant = HorrorMuted
)

@Composable
fun BouBhutTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            window?.let {
                it.statusBarColor = HorrorBg.toArgb()
                it.navigationBarColor = HorrorBg.toArgb()
                val controller = WindowCompat.getInsetsController(it, view)
                controller.isAppearanceLightStatusBars = false
                controller.isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = HorrorColorScheme,
        typography = Typography,
        content = content
    )
}
