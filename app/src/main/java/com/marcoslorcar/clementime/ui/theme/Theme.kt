package com.marcoslorcar.clementime.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
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

fun getThemeColorScheme(themeName: String, isDark: Boolean): ColorScheme {
    return when (themeName.lowercase()) {
        "blueberry" -> if (isDark) darkColorScheme(
            primary = BlueberryPrimary,
            onPrimary = Color(0xFF002966),
            secondary = BlueberrySecondary,
            onSecondary = Color(0xFF001D4A),
            tertiary = BlueberryTertiary,
            onTertiary = Color(0xFFFFFFFF),
            primaryContainer = BlueberryContainer,
            onPrimaryContainer = Color(0xFFD6E3FF)
        ) else lightColorScheme(
            primary = Color(0xFF1E56B7),
            onPrimary = Color(0xFFFFFFFF),
            secondary = BlueberryPrimary,
            onSecondary = Color(0xFFFFFFFF),
            tertiary = BlueberryTertiary,
            onTertiary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFD6E3FF),
            onPrimaryContainer = Color(0xFF001B44)
        )
        "matcha" -> if (isDark) darkColorScheme(
            primary = MatchaPrimary,
            onPrimary = Color(0xFF0D3812),
            secondary = MatchaSecondary,
            onSecondary = Color(0xFF142716),
            tertiary = MatchaTertiary,
            onTertiary = Color(0xFFFFFFFF),
            primaryContainer = MatchaContainer,
            onPrimaryContainer = Color(0xFFC8E6C9)
        ) else lightColorScheme(
            primary = Color(0xFF2E7D32),
            onPrimary = Color(0xFFFFFFFF),
            secondary = Color(0xFF4CAF50),
            onSecondary = Color(0xFFFFFFFF),
            tertiary = MatchaPrimary,
            onTertiary = Color(0xFF000000),
            primaryContainer = Color(0xFFC8E6C9),
            onPrimaryContainer = Color(0xFF002105)
        )
        "espresso" -> if (isDark) darkColorScheme(
            primary = EspressoPrimaryDark,
            onPrimary = EspressoOnPrimaryDark,
            secondary = EspressoSecondaryDark,
            onSecondary = EspressoOnSecondaryDark,
            tertiary = EspressoTertiaryDark,
            onTertiary = EspressoOnTertiaryDark,
            primaryContainer = EspressoContainerDark,
            onPrimaryContainer = Color(0xFFEFEBE9)
        ) else lightColorScheme(
            primary = EspressoPrimaryLight,
            onPrimary = EspressoOnPrimaryLight,
            primaryContainer = EspressoPrimaryContainerLight,
            onPrimaryContainer = EspressoOnPrimaryContainerLight,
            secondary = EspressoSecondaryLight,
            onSecondary = EspressoOnSecondaryLight,
            secondaryContainer = EspressoSecondaryContainerLight,
            onSecondaryContainer = EspressoOnSecondaryContainerLight,
            tertiary = EspressoTertiaryLight,
            onTertiary = EspressoOnTertiaryLight
        )
        "grape" -> if (isDark) darkColorScheme(
            primary = GrapePrimary,
            onPrimary = Color(0xFF3B1044),
            secondary = GrapeSecondary,
            onSecondary = Color(0xFF260033),
            tertiary = GrapeTertiary,
            onTertiary = Color(0xFFFFFFFF),
            primaryContainer = GrapeContainer,
            onPrimaryContainer = Color(0xFFF3E5F5)
        ) else lightColorScheme(
            primary = Color(0xFF7B1FA2),
            onPrimary = Color(0xFFFFFFFF),
            secondary = GrapePrimary,
            onSecondary = Color(0xFFFFFFFF),
            tertiary = GrapeTertiary,
            onTertiary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFF3E5F5),
            onPrimaryContainer = Color(0xFF2C003B)
        )
        else -> if (isDark) DarkColorScheme else LightColorScheme
    }
}

@Composable
fun ClemenTimeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    selectedTheme: String = "clementine",
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        selectedTheme != "clementine" -> getThemeColorScheme(selectedTheme, darkTheme)
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
