package com.example.inventory.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.inventory.data.backup.BackupSettings
import com.example.inventory.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Egyszerű felhasználói beállítások (nem relációs adat → DataStore). A nyelvet a [LanguageManager] kezeli. */
class SettingsRepository(context: Context) : BackupSettings {

    private val store = context.dataStore

    val themeMode: Flow<ThemeMode> =
        store.data.map { prefs ->
            prefs[KEY_THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM
        }

    suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[KEY_THEME] = mode.name }
    }

    /** Mentéshez: a beállítások szövegként (csak a ténylegesen beállítottak). A nyelvet az Android tárolja, az nem kerül bele. */
    override suspend fun snapshot(): Map<String, String> {
        val prefs = store.data.first()
        return buildMap { prefs[KEY_THEME]?.let { put(BACKUP_THEME, it) } }
    }

    /** Visszaállításkor: a mentett beállítások (a hiányzók alapértékre állnak). */
    override suspend fun restore(values: Map<String, String>) {
        store.edit { prefs ->
            prefs.clear()
            values[BACKUP_THEME]?.takeIf { v -> ThemeMode.entries.any { it.name == v } }?.let { prefs[KEY_THEME] = it }
        }
    }

    private companion object {
        const val BACKUP_THEME = "themeMode"
        val KEY_THEME = stringPreferencesKey("theme_mode")
    }
}
