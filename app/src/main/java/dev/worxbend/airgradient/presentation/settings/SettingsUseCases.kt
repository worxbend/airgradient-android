package dev.worxbend.airgradient.presentation.settings

import dev.worxbend.airgradient.domain.usecase.ClearMonitoringRuntimeStateUseCase
import dev.worxbend.airgradient.domain.usecase.ObserveMonitoringRuntimeStateUseCase
import dev.worxbend.airgradient.domain.usecase.ObserveMonitoringSettingsUseCase
import dev.worxbend.airgradient.domain.usecase.ObserveSettingsUseCase
import dev.worxbend.airgradient.domain.usecase.SaveDeviceUrlUseCase
import dev.worxbend.airgradient.domain.usecase.SaveForegroundPollingIntervalUseCase
import dev.worxbend.airgradient.domain.usecase.SaveMinimumNotificationSeverityUseCase
import dev.worxbend.airgradient.domain.usecase.SaveNotificationsEnabledUseCase
import dev.worxbend.airgradient.domain.usecase.SaveNotifyOnDeviceUnreachableUseCase
import dev.worxbend.airgradient.domain.usecase.SaveNotifyOnRecoveryUseCase
import dev.worxbend.airgradient.domain.usecase.SavePeriodicBackgroundIntervalUseCase
import dev.worxbend.airgradient.domain.usecase.SaveRefreshIntervalUseCase
import dev.worxbend.airgradient.domain.usecase.SaveThemeModeUseCase
import dev.worxbend.airgradient.domain.usecase.TestDeviceConnectionUseCase

data class SettingsUseCases(
    val observeSettings: ObserveSettingsUseCase,
    val observeMonitoringSettings: ObserveMonitoringSettingsUseCase,
    val observeMonitoringRuntimeState: ObserveMonitoringRuntimeStateUseCase,
    val clearMonitoringRuntimeState: ClearMonitoringRuntimeStateUseCase,
    val saveDeviceUrl: SaveDeviceUrlUseCase,
    val saveRefreshInterval: SaveRefreshIntervalUseCase,
    val saveForegroundPollingInterval: SaveForegroundPollingIntervalUseCase,
    val savePeriodicBackgroundInterval: SavePeriodicBackgroundIntervalUseCase,
    val saveNotificationsEnabled: SaveNotificationsEnabledUseCase,
    val saveMinimumNotificationSeverity: SaveMinimumNotificationSeverityUseCase,
    val saveNotifyOnRecovery: SaveNotifyOnRecoveryUseCase,
    val saveNotifyOnDeviceUnreachable: SaveNotifyOnDeviceUnreachableUseCase,
    val saveThemeMode: SaveThemeModeUseCase,
    val testDeviceConnection: TestDeviceConnectionUseCase,
)
