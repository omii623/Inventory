package com.example.inventory

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.inventory.domain.model.ThemeMode
import com.example.inventory.ui.navigation.InventoryApp
import com.example.inventory.ui.theme.InventoryAppTheme
import com.example.inventory.ui.theme.isDark

/**
 * AppCompatActivity, mert az alkalmazásonkénti nyelvváltás (AppCompatDelegate.setApplicationLocales)
 * Android 13 alatt csak így működik. A UI teljesen Compose.
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settings = (application as InventoryApplication).container.settingsRepository
        setContent {
            val themeMode by settings.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
            val dark = themeMode.isDark()
            // Az állapotsor és a navigációs sáv ikonjai az app témáját kövessék, ne a rendszerét.
            LaunchedEffect(dark) {
                val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            InventoryAppTheme(themeMode) {
                InventoryApp()
            }
        }
    }
}
