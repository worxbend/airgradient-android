package dev.worxbend.airgradient.domain.repository

import dev.worxbend.airgradient.domain.monitoring.MonitoringMode
import dev.worxbend.airgradient.domain.monitoring.MonitoringSettings
import kotlinx.coroutines.flow.Flow
import java.time.Duration

interface MonitoringSettingsRepository {
    fun observeMonitoringSettings(): Flow<MonitoringSettings>

    suspend fun getMonitoringSettings(): MonitoringSettings

    suspend fun updateMonitoringMode(mode: MonitoringMode)

    suspend fun updateForegroundPollingInterval(interval: Duration)

    suspend fun updatePeriodicBackgroundInterval(interval: Duration)

    suspend fun updateAdaptivePollingEnabled(enabled: Boolean)
}
