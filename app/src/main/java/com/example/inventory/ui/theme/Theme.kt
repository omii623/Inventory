package com.example.inventory.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.example.inventory.domain.model.ThemeMode

/**
 * Az app saját, jelentéssel bíró színei (TERV.md 6. pont), amik a Material sémában nincsenek:
 * a státuszok színei (szöveg/pötty + halvány háttér) és a kezdőlap összesítő kártyája.
 */
@Immutable
data class InventoryColors(
    val inStock: Color,
    val inStockContainer: Color,
    val reserved: Color,
    val reservedContainer: Color,
    val sold: Color,
    val soldContainer: Color,
    val hero: Color,
    val onHero: Color,
    val onHeroMuted: Color,
    val border: Color,
)

private val LightInventoryColors = InventoryColors(
    inStock = Color(0xFF10B981),
    inStockContainer = Color(0xFFECFDF5),
    reserved = Color(0xFFF59E0B),
    reservedContainer = Color(0xFFFFFBEB),
    sold = Color(0xFF6B7280),
    soldContainer = Color(0xFFF3F4F6),
    hero = Color(0xFF4F46E5),
    onHero = Color.White,
    onHeroMuted = Color(0xFFC7D2FE),
    border = Color(0xFFE5E7EB),
)

private val DarkInventoryColors = InventoryColors(
    inStock = Color(0xFF34D399),
    inStockContainer = Color(0xFF0F2A22),
    reserved = Color(0xFFFBBF24),
    reservedContainer = Color(0xFF2E2410),
    sold = Color(0xFF9CA3AF),
    soldContainer = Color(0xFF23262D),
    hero = Color(0xFF3730A3),
    onHero = Color.White,
    onHeroMuted = Color(0xFFC7D2FE),
    border = Color(0xFF2A2F3A),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF4F46E5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEEF2FF),
    onPrimaryContainer = Color(0xFF4F46E5),
    secondaryContainer = Color(0xFFEEF2FF),
    onSecondaryContainer = Color(0xFF4F46E5),
    background = Color(0xFFF6F7FB),
    onBackground = Color(0xFF111827),
    surface = Color(0xFFF6F7FB),
    onSurface = Color(0xFF111827),
    surfaceContainer = Color.White,
    surfaceContainerLow = Color.White,
    onSurfaceVariant = Color(0xFF6B7280),
    outline = Color(0xFFD1D5DB),
    outlineVariant = Color(0xFFE5E7EB),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF818CF8),
    onPrimary = Color(0xFF111827),
    primaryContainer = Color(0xFF262A4D),
    onPrimaryContainer = Color(0xFFC7D2FE),
    secondaryContainer = Color(0xFF262A4D),
    onSecondaryContainer = Color(0xFFC7D2FE),
    background = Color(0xFF0F1115),
    onBackground = Color(0xFFF3F4F6),
    surface = Color(0xFF0F1115),
    onSurface = Color(0xFFF3F4F6),
    surfaceContainer = Color(0xFF1A1D24),
    surfaceContainerLow = Color(0xFF1A1D24),
    onSurfaceVariant = Color(0xFF9CA3AF),
    outline = Color(0xFF3A404C),
    outlineVariant = Color(0xFF2A2F3A),
)

val LocalInventoryColors = staticCompositionLocalOf { LightInventoryColors }

/** Rövid elérés: `InventoryTheme.colors.inStock` */
object InventoryTheme {
    val colors: InventoryColors
        @Composable get() = LocalInventoryColors.current
}

/** Sötét-e a téma (Rendszer módban a telefon beállítása dönt). */
@Composable
fun ThemeMode.isDark(): Boolean = when (this) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun InventoryAppTheme(themeMode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = themeMode.isDark()
    CompositionLocalProvider(LocalInventoryColors provides if (dark) DarkInventoryColors else LightInventoryColors) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            content = content,
        )
    }
}
