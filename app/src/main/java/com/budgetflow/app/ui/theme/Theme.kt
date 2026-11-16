package com.budgetflow.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.budgetflow.app.domain.model.ThemeMode

val EmeraldPrimary = Color(0xFF059669)
val EmeraldPrimaryDark = Color(0xFF34D399)
val EmeraldContainerLight = Color(0xFFD1FAE5)
val EmeraldContainerDark = Color(0xFF064E3B)

val StatusWarning = Color(0xFFF59E0B)
val StatusError = Color(0xFFEF4444)

private val LightColors = lightColorScheme(
    primary = EmeraldPrimary,
    onPrimary = Color.White,
    primaryContainer = EmeraldContainerLight,
    onPrimaryContainer = Color(0xFF064E3B),
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF64748B),
    outline = Color(0xFFE2E8F0),
    error = StatusError
)

private val DarkColors = darkColorScheme(
    primary = EmeraldPrimaryDark,
    onPrimary = Color(0xFF022C22),
    primaryContainer = EmeraldContainerDark,
    onPrimaryContainer = Color(0xFFA7F3D0),
    background = Color(0xFF0B1120),
    onBackground = Color(0xFFF8FAFC),
    surface = Color(0xFF131C31),
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = Color(0xFF1E293B),
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF26334D),
    error = Color(0xFFF87171)
)

@Composable
fun BudgetFlowTheme(
    themeMode: ThemeMode = ThemeMode.LIGHT,
    content: @Composable () -> Unit
) {
    val useDark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (useDark) DarkColors else LightColors,
        content = content
    )
}
