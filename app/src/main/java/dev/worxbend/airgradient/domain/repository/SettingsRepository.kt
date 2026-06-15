package dev.worxbend.airgradient.domain.repository

import dev.worxbend.airgradient.domain.model.AppSettings
import dev.worxbend.airgradient.domain.model.AppThemeMode
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun saveDeviceUrl(input: String): SaveDeviceUrlResult

    suspend fun saveRefreshIntervalSeconds(seconds: Int)

    suspend fun saveNotificationsEnabled(enabled: Boolean)

    suspend fun saveThemeMode(themeMode: AppThemeMode)
}

sealed interface SaveDeviceUrlResult {
    data class Saved(
        val serverUrl: String?,
    ) : SaveDeviceUrlResult

    data object Invalid : SaveDeviceUrlResult
}
