package dev.worxbend.airgradient.app

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dev.worxbend.airgradient.core.dispatchers.AppDispatchers
import dev.worxbend.airgradient.data.airgradient.AirGradientRepositoryImpl
import dev.worxbend.airgradient.data.notifications.AndroidAirQualityAlertNotifier
import dev.worxbend.airgradient.data.settings.SettingsDataSource
import dev.worxbend.airgradient.data.settings.SettingsRepositoryImpl
import dev.worxbend.airgradient.data.settings.airGradientSettingsDataStore
import dev.worxbend.airgradient.domain.notifications.AirQualityAlertPolicy
import dev.worxbend.airgradient.domain.repository.AirGradientRepository
import dev.worxbend.airgradient.domain.repository.SettingsRepository
import dev.worxbend.airgradient.domain.usecase.GetCurrentMeasurementUseCase
import dev.worxbend.airgradient.domain.usecase.ObserveSettingsUseCase
import dev.worxbend.airgradient.domain.usecase.RefreshDashboardUseCase
import dev.worxbend.airgradient.domain.usecase.SaveDeviceUrlUseCase
import dev.worxbend.airgradient.domain.usecase.SaveNotificationsEnabledUseCase
import dev.worxbend.airgradient.domain.usecase.SaveRefreshIntervalUseCase
import dev.worxbend.airgradient.domain.usecase.SaveThemeModeUseCase
import dev.worxbend.airgradient.domain.usecase.TestDeviceConnectionUseCase
import dev.worxbend.airgradient.presentation.dashboard.DashboardViewModel
import dev.worxbend.airgradient.presentation.settings.SettingsViewModel

class AppGraph(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val dispatchers = AppDispatchers.production

    val settingsRepository: SettingsRepository =
        SettingsRepositoryImpl(
            settingsDataSource = SettingsDataSource(appContext.airGradientSettingsDataStore),
        )

    private val airGradientRepository: AirGradientRepository = AirGradientRepositoryImpl()
    private val getCurrentMeasurement = GetCurrentMeasurementUseCase(airGradientRepository)
    private val airQualityAlertNotifier = AndroidAirQualityAlertNotifier(appContext)

    fun dashboardViewModelFactory(): ViewModelProvider.Factory =
        viewModelFactory {
            DashboardViewModel(
                observeSettings = ObserveSettingsUseCase(settingsRepository),
                refreshDashboard = RefreshDashboardUseCase(getCurrentMeasurement),
                alertPolicy = AirQualityAlertPolicy(),
                alertNotifier = airQualityAlertNotifier,
                dispatchers = dispatchers,
            )
        }

    fun settingsViewModelFactory(): ViewModelProvider.Factory =
        viewModelFactory {
            SettingsViewModel(
                observeSettings = ObserveSettingsUseCase(settingsRepository),
                saveDeviceUrlUseCase = SaveDeviceUrlUseCase(settingsRepository),
                saveRefreshInterval = SaveRefreshIntervalUseCase(settingsRepository),
                saveNotificationsEnabled = SaveNotificationsEnabledUseCase(settingsRepository),
                saveThemeMode = SaveThemeModeUseCase(settingsRepository),
                testDeviceConnection = TestDeviceConnectionUseCase(getCurrentMeasurement),
                dispatchers = dispatchers,
            )
        }

    private fun <T : ViewModel> viewModelFactory(create: () -> T): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <VM : ViewModel> create(modelClass: Class<VM>): VM = create() as VM
        }
}
