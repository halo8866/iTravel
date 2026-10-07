package com.itravel.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val BrandColors = lightColorScheme(
    primary = Color(0xFF2E7D6B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB7E4D8),
    onPrimaryContainer = Color(0xFF0B3D33),
    secondary = Color(0xFF8D6E63),
    onSecondary = Color.White,
    background = Color(0xFFF6F4EF),
    onBackground = Color(0xFF222222),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF222222),
    surfaceVariant = Color(0xFFEDE7E0),
    onSurfaceVariant = Color(0xFF555555),
    error = Color(0xFFC0392B),
    outline = Color(0xFFBDB5AA)
)

@Composable
fun ITravelTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // Light travel-journal palette; dark variant intentionally shares same palette.
    MaterialTheme(
        colorScheme = BrandColors,
        typography = MaterialTheme.typography,
        content = content
    )
}
