package com.github.marcoslorcar.clementime.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = ClementinePrimaryDark,
    onPrimary = ClementineOnPrimaryDark,
    primaryContainer = ClementinePrimaryContainerDark,
    onPrimaryContainer = ClementineOnPrimaryContainerDark,
    secondary = ClementineSecondaryDark,
    onSecondary = ClementineOnSecondaryDark,
    secondaryContainer = ClementineSecondaryContainerDark,
    onSecondaryContainer = ClementineOnSecondaryContainerDark,
    tertiary = ClementineTertiaryDark,
    onTertiary = ClementineOnTertiaryDark,
    tertiaryContainer = ClementineTertiaryContainerDark,
    onTertiaryContainer = ClementineOnTertiaryContainerDark,
    background = ClementineBackgroundDark,
    onBackground = ClementineOnBackgroundDark,
    surface = ClementineSurfaceDark,
    onSurface = ClementineOnSurfaceDark,
    surfaceVariant = ClementineSurfaceVariantDark,
    onSurfaceVariant = ClementineOnSurfaceVariantDark,
    outline = ClementineOutlineDark
)

private val LightColorScheme = lightColorScheme(
    primary = ClementinePrimaryLight,
    onPrimary = ClementineOnPrimaryLight,
    primaryContainer = ClementinePrimaryContainerLight,
    onPrimaryContainer = ClementineOnPrimaryContainerLight,
    secondary = ClementineSecondaryLight,
    onSecondary = ClementineOnSecondaryLight,
    secondaryContainer = ClementineSecondaryContainerLight,
    onSecondaryContainer = ClementineOnSecondaryContainerLight,
    tertiary = ClementineTertiaryLight,
    onTertiary = ClementineOnTertiaryLight,
    tertiaryContainer = ClementineTertiaryContainerLight,
    onTertiaryContainer = ClementineOnTertiaryContainerLight,
    background = ClementineBackgroundLight,
    onBackground = ClementineOnBackgroundLight,
    surface = ClementineSurfaceLight,
    onSurface = ClementineOnSurfaceLight,
    surfaceVariant = ClementineSurfaceVariantLight,
    onSurfaceVariant = ClementineOnSurfaceVariantLight,
    outline = ClementineOutlineLight
)

@Composable
fun ClemenTimeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
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

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
        motionScheme = MotionScheme.expressive()
    )
}
