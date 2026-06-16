package dev.worxbend.airgradient.data.settings

import dev.worxbend.airgradient.domain.model.AppThemeMode
import dev.worxbend.airgradient.domain.monitoring.MonitoringMode
import dev.worxbend.airgradient.domain.monitoring.MonitoringSettings
import dev.worxbend.airgradient.domain.repository.MonitoringSettingsRepository
import dev.worxbend.airgradient.domain.repository.SaveDeviceUrlResult
import dev.worxbend.airgradient.domain.repository.SettingsRepository
import dev.worxbend.airgradient.domain.sensors.DeviceUrlNormalizationResult
import dev.worxbend.airgradient.domain.sensors.DeviceUrlNormalizer
import kotlinx.coroutines.flow.first
import java.time.Duration

class SettingsRepositoryImpl(
    private val settingsDataSource: SettingsDataSource,
) : SettingsRepository,
    MonitoringSettingsRepository {
    override val settings = settingsDataSource.settings

    override fun observeMonitoringSettings() = settingsDataSource.monitoringSettings

    override suspend fun getMonitoringSettings(): MonitoringSettings = settingsDataSource.monitoringSettings.first()

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

    override suspend fun updateMonitoringMode(mode: MonitoringMode) {
        settingsDataSource.saveMonitoringMode(mode)
    }

    override suspend fun updateForegroundPollingInterval(interval: Duration) {
        settingsDataSource.saveForegroundPollingIntervalSeconds(
            MonitoringSettings.requireSupportedForegroundInterval(interval),
        )
    }

    override suspend fun updatePeriodicBackgroundInterval(interval: Duration) {
        settingsDataSource.savePeriodicBackgroundIntervalMinutes(
            MonitoringSettings.requireSupportedPeriodicInterval(interval),
        )
    }

    private suspend fun saveServerUrl(serverUrl: String?): SaveDeviceUrlResult.Saved {
        settingsDataSource.saveServerUrl(serverUrl)
        return SaveDeviceUrlResult.Saved(serverUrl)
    }
}
