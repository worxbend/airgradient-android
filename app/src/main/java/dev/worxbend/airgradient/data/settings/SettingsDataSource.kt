package dev.worxbend.airgradient.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.worxbend.airgradient.domain.model.AppSettings
import dev.worxbend.airgradient.domain.model.AppThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

class SettingsDataSource(
    private val dataStore: DataStore<Preferences>,
) {
    val settings: Flow<AppSettings> =
        dataStore.data
            .catch { error ->
                if (error is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw error
                }
            }.map(::mapPreferences)

    suspend fun saveServerUrl(serverUrl: String?) {
        dataStore.edit { preferences ->
            if (serverUrl == null) {
                preferences.remove(SERVER_URL)
            } else {
                preferences[SERVER_URL] = serverUrl
            }
        }
    }

    suspend fun saveRefreshIntervalSeconds(seconds: Int) {
        dataStore.edit { preferences ->
            preferences[REFRESH_INTERVAL_SECONDS] = AppSettings.clampRefreshInterval(seconds)
        }
    }

    suspend fun saveNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[NOTIFICATIONS_ENABLED] = enabled
        }
    }

    suspend fun saveThemeMode(themeMode: AppThemeMode) {
        dataStore.edit { preferences ->
            preferences[THEME_MODE] = themeMode.name
        }
    }

    private fun mapPreferences(preferences: Preferences): AppSettings =
        AppSettings(
            serverUrl = preferences[SERVER_URL],
            refreshIntervalSeconds =
                AppSettings.clampRefreshInterval(
                    preferences[REFRESH_INTERVAL_SECONDS] ?: AppSettings.DEFAULT_REFRESH_INTERVAL_SECONDS,
                ),
            notificationsEnabled = preferences[NOTIFICATIONS_ENABLED] ?: false,
            themeMode = preferences[THEME_MODE].toThemeMode(),
        )

    private fun String?.toThemeMode(): AppThemeMode =
        this
            ?.let { storedValue -> AppThemeMode.entries.firstOrNull { it.name == storedValue } }
            ?: AppThemeMode.SYSTEM

    private companion object {
        val SERVER_URL: Preferences.Key<String> = stringPreferencesKey("server_url")
        val REFRESH_INTERVAL_SECONDS: Preferences.Key<Int> = intPreferencesKey("refresh_interval_seconds")
        val NOTIFICATIONS_ENABLED: Preferences.Key<Boolean> = booleanPreferencesKey("notifications_enabled")
        val THEME_MODE: Preferences.Key<String> = stringPreferencesKey("theme_mode")
    }
}
