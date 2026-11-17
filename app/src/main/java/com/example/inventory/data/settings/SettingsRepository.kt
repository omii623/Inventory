package com.example.inventory.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.inventory.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Egyszerű felhasználói beállítások (nem relációs adat → DataStore). A nyelvet a [LanguageManager] kezeli. */
class SettingsRepository(context: Context) {

    private val store = context.dataStore

    val themeMode: Flow<ThemeMode> =
        store.data.map { prefs ->
            prefs[KEY_THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM
        }

    suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[KEY_THEME] = mode.name }
    }

    private companion object {
        val KEY_THEME = stringPreferencesKey("theme_mode")
    }
}
