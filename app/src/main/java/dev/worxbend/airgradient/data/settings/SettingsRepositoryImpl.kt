package dev.worxbend.airgradient.data.settings

import dev.worxbend.airgradient.domain.model.AppThemeMode
import dev.worxbend.airgradient.domain.repository.SaveDeviceUrlResult
import dev.worxbend.airgradient.domain.repository.SettingsRepository
import dev.worxbend.airgradient.domain.sensors.DeviceUrlNormalizationResult
import dev.worxbend.airgradient.domain.sensors.DeviceUrlNormalizer

class SettingsRepositoryImpl(
    private val settingsDataSource: SettingsDataSource,
) : SettingsRepository {
    override val settings = settingsDataSource.settings

    override suspend fun saveDeviceUrl(input: String): SaveDeviceUrlResult =
        when (val result = DeviceUrlNormalizer.normalize(input)) {
            DeviceUrlNormalizationResult.Unconfigured -> saveServerUrl(null)
            DeviceUrlNormalizationResult.Invalid -> SaveDeviceUrlResult.Invalid
            is DeviceUrlNormalizationResult.Normalized -> saveServerUrl(result.value)
        }

    override suspend fun saveRefreshIntervalSeconds(seconds: Int) {
        settingsDataSource.saveRefreshIntervalSeconds(seconds)
    }

    override suspend fun saveNotificationsEnabled(enabled: Boolean) {
        settingsDataSource.saveNotificationsEnabled(enabled)
    }

    override suspend fun saveThemeMode(themeMode: AppThemeMode) {
        settingsDataSource.saveThemeMode(themeMode)
    }

    private suspend fun saveServerUrl(serverUrl: String?): SaveDeviceUrlResult.Saved {
        settingsDataSource.saveServerUrl(serverUrl)
        return SaveDeviceUrlResult.Saved(serverUrl)
    }
}
