package com.sharetonostr.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Purple = Color(0xFF7B1FA2)
private val PurpleLight = Color(0xFFCE93D8)
private val PurpleDark = Color(0xFF4A148C)
private val Teal = Color(0xFF00BCD4)

private val DarkColorScheme = darkColorScheme(
    primary = PurpleLight,
    secondary = Teal,
    tertiary = PurpleLight,
    surface = Color(0xFF1C1B1F),
    background = Color(0xFF1C1B1F),
)

private val LightColorScheme = lightColorScheme(
    primary = Purple,
    secondary = Teal,
    tertiary = PurpleDark,
    surface = Color(0xFFFFFBFE),
    background = Color(0xFFFFFBFE),
)

@Composable
fun ShareToNostrTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
