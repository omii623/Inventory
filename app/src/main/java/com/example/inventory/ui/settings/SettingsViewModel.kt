package com.example.inventory.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.inventory.data.settings.AppLanguage
import com.example.inventory.data.settings.LanguageManager
import com.example.inventory.data.settings.SettingsRepository
import com.example.inventory.domain.model.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val settings: SettingsRepository) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> =
        settings.themeMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.SYSTEM)

    /**
     * A nyelvet az Android tárolja. Váltáskor az Activity újraindul, de ez a ViewModel túléli,
     * ezért nem lehet egyszer kiolvasott mező: a képernyő minden felépítéskor újra lekérdezi.
     */
    fun currentLanguage(): AppLanguage = LanguageManager.current()

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settings.setThemeMode(mode) }
    }

    fun setLanguage(language: AppLanguage) {
        if (language != currentLanguage()) LanguageManager.set(language)
    }
}
