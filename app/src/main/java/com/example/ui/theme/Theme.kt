package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = GoldAccent,
    secondary = MintGreen,
    tertiary = GoldAmber,
    background = DarkSlate,
    surface = SurfaceDark,
    onPrimary = Charcoal,
    onSecondary = PureWhite,
    onTertiary = Charcoal,
    onBackground = PureWhite,
    onSurface = PureWhite,
    primaryContainer = EmeraldGreen,
    onPrimaryContainer = PureWhite,
    surfaceVariant = CardDark,
    onSurfaceVariant = OffWhite
)

private val LightColorScheme = lightColorScheme(
    primary = EmeraldGreen,
    secondary = GoldAmber,
    tertiary = ForestGreen,
    background = OffWhite,
    surface = PureWhite,
    onPrimary = PureWhite,
    onSecondary = Charcoal,
    onTertiary = PureWhite,
    onBackground = Charcoal,
    onSurface = Charcoal,
    primaryContainer = LightMint,
    onPrimaryContainer = ForestGreen,
    surfaceVariant = DividerGray,
    onSurfaceVariant = Charcoal
)

@Composable
fun SponsorRewardsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Keep dynamic color support for Android 12+ or use explicit branding theme
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
