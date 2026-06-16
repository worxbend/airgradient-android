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
import dev.worxbend.airgradient.domain.monitoring.MonitoringMode
import dev.worxbend.airgradient.domain.monitoring.MonitoringSettings
import dev.worxbend.airgradient.domain.notifications.NotificationSeverity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

@Suppress("TooManyFunctions")
class SettingsDataSource(
    private val dataStore: DataStore<Preferences>,
) {
    private val preferences =
        dataStore.data
            .catch { error ->
                if (error is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw error
                }
            }

    val settings: Flow<AppSettings> =
        preferences.map(::mapPreferences)

    val monitoringSettings: Flow<MonitoringSettings> =
        preferences.map(::mapMonitoringPreferences)

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

    suspend fun saveMinimumNotificationSeverity(severity: NotificationSeverity) {
        dataStore.edit { preferences ->
            preferences[MINIMUM_NOTIFICATION_SEVERITY] = severity.name
        }
    }

    suspend fun saveNotifyOnRecovery(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[NOTIFY_ON_RECOVERY] = enabled
        }
    }

    suspend fun saveNotifyOnDeviceUnreachable(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[NOTIFY_ON_DEVICE_UNREACHABLE] = enabled
        }
    }

    suspend fun saveThemeMode(themeMode: AppThemeMode) {
        dataStore.edit { preferences ->
            preferences[THEME_MODE] = themeMode.name
        }
    }

    suspend fun saveMonitoringMode(mode: MonitoringMode) {
        dataStore.edit { preferences ->
            preferences[MONITORING_MODE] = mode.name
        }
    }

    suspend fun saveForegroundPollingIntervalSeconds(seconds: Int) {
        dataStore.edit { preferences ->
            preferences[MONITORING_FOREGROUND_INTERVAL_SECONDS] = seconds
        }
    }

    suspend fun savePeriodicBackgroundIntervalMinutes(minutes: Int) {
        dataStore.edit { preferences ->
            preferences[MONITORING_PERIODIC_INTERVAL_MINUTES] = minutes
        }
    }

    suspend fun saveAdaptivePollingEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[MONITORING_ADAPTIVE_POLLING_ENABLED] = enabled
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
            minimumNotificationSeverity = preferences[MINIMUM_NOTIFICATION_SEVERITY].toNotificationSeverity(),
            notifyOnRecovery = preferences[NOTIFY_ON_RECOVERY] ?: true,
            notifyOnDeviceUnreachable = preferences[NOTIFY_ON_DEVICE_UNREACHABLE] ?: true,
        )

    private fun mapMonitoringPreferences(preferences: Preferences): MonitoringSettings =
        MonitoringSettings(
            mode = preferences[MONITORING_MODE].toMonitoringMode(),
            foregroundPollingIntervalSeconds =
                preferences[MONITORING_FOREGROUND_INTERVAL_SECONDS]
                    ?.coerceAtLeast(MonitoringSettings.MIN_FOREGROUND_POLLING_INTERVAL_SECONDS)
                    ?: MonitoringSettings.DEFAULT_FOREGROUND_POLLING_INTERVAL_SECONDS,
            periodicBackgroundIntervalMinutes =
                preferences[MONITORING_PERIODIC_INTERVAL_MINUTES]
                    ?.coerceAtLeast(MonitoringSettings.MIN_PERIODIC_BACKGROUND_INTERVAL_MINUTES)
                    ?: MonitoringSettings.DEFAULT_PERIODIC_BACKGROUND_INTERVAL_MINUTES,
            persistentNotificationEnabled = preferences[MONITORING_PERSISTENT_NOTIFICATION_ENABLED] ?: true,
            adaptivePollingEnabled = preferences[MONITORING_ADAPTIVE_POLLING_ENABLED] ?: true,
        )

    private fun String?.toThemeMode(): AppThemeMode =
        this
            ?.let { storedValue -> AppThemeMode.entries.firstOrNull { it.name == storedValue } }
            ?: AppThemeMode.SYSTEM

    private fun String?.toMonitoringMode(): MonitoringMode =
        this
            ?.let { storedValue -> MonitoringMode.entries.firstOrNull { it.name == storedValue } }
            ?: MonitoringMode.Off

    private fun String?.toNotificationSeverity(): NotificationSeverity =
        this
            ?.let { storedValue -> NotificationSeverity.entries.firstOrNull { it.name == storedValue } }
            ?: NotificationSeverity.Warning

    private companion object {
        val SERVER_URL: Preferences.Key<String> = stringPreferencesKey("server_url")
        val REFRESH_INTERVAL_SECONDS: Preferences.Key<Int> = intPreferencesKey("refresh_interval_seconds")
        val NOTIFICATIONS_ENABLED: Preferences.Key<Boolean> = booleanPreferencesKey("notifications_enabled")
        val MINIMUM_NOTIFICATION_SEVERITY: Preferences.Key<String> =
            stringPreferencesKey("minimum_notification_severity")
        val NOTIFY_ON_RECOVERY: Preferences.Key<Boolean> = booleanPreferencesKey("notify_on_recovery")
        val NOTIFY_ON_DEVICE_UNREACHABLE: Preferences.Key<Boolean> =
            booleanPreferencesKey("notify_on_device_unreachable")
        val THEME_MODE: Preferences.Key<String> = stringPreferencesKey("theme_mode")
        val MONITORING_MODE: Preferences.Key<String> = stringPreferencesKey("monitoring_mode")
        val MONITORING_FOREGROUND_INTERVAL_SECONDS: Preferences.Key<Int> =
            intPreferencesKey("monitoring_foreground_interval_seconds")
        val MONITORING_PERIODIC_INTERVAL_MINUTES: Preferences.Key<Int> =
            intPreferencesKey("monitoring_periodic_interval_minutes")
        val MONITORING_PERSISTENT_NOTIFICATION_ENABLED: Preferences.Key<Boolean> =
            booleanPreferencesKey("monitoring_persistent_notification_enabled")
        val MONITORING_ADAPTIVE_POLLING_ENABLED: Preferences.Key<Boolean> =
            booleanPreferencesKey("monitoring_adaptive_polling_enabled")
    }
}
