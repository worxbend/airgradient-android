package dev.worxbend.airgradient.app

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dev.worxbend.airgradient.core.dispatchers.AppDispatchers
import dev.worxbend.airgradient.data.airgradient.AirGradientRepositoryImpl
import dev.worxbend.airgradient.data.notifications.AndroidNotificationMessageDispatcher
import dev.worxbend.airgradient.data.notifications.NotificationStateRepositoryImpl
import dev.worxbend.airgradient.data.notifications.airGradientNotificationStateDataStore
import dev.worxbend.airgradient.data.settings.SettingsDataSource
import dev.worxbend.airgradient.data.settings.SettingsRepositoryImpl
import dev.worxbend.airgradient.data.settings.airGradientSettingsDataStore
import dev.worxbend.airgradient.domain.notifications.NotificationDecisionEngine
import dev.worxbend.airgradient.domain.repository.AirGradientRepository
import dev.worxbend.airgradient.domain.repository.MonitoringSettingsRepository
import dev.worxbend.airgradient.domain.repository.NotificationStateRepository
import dev.worxbend.airgradient.domain.repository.SettingsRepository
import dev.worxbend.airgradient.domain.usecase.GetCurrentMeasurementUseCase
import dev.worxbend.airgradient.domain.usecase.ObserveMonitoringSettingsUseCase
import dev.worxbend.airgradient.domain.usecase.ObserveSettingsUseCase
import dev.worxbend.airgradient.domain.usecase.RefreshDashboardUseCase
import dev.worxbend.airgradient.domain.usecase.SaveDeviceUrlUseCase
import dev.worxbend.airgradient.domain.usecase.SaveForegroundPollingIntervalUseCase
import dev.worxbend.airgradient.domain.usecase.SaveNotificationsEnabledUseCase
import dev.worxbend.airgradient.domain.usecase.SavePeriodicBackgroundIntervalUseCase
import dev.worxbend.airgradient.domain.usecase.SaveRefreshIntervalUseCase
import dev.worxbend.airgradient.domain.usecase.SaveThemeModeUseCase
import dev.worxbend.airgradient.domain.usecase.TestDeviceConnectionUseCase
import dev.worxbend.airgradient.presentation.dashboard.DashboardMonitoringDependencies
import dev.worxbend.airgradient.presentation.dashboard.DashboardNotificationDependencies
import dev.worxbend.airgradient.presentation.dashboard.DashboardViewModel
import dev.worxbend.airgradient.presentation.settings.SettingsUseCases
import dev.worxbend.airgradient.presentation.settings.SettingsViewModel
import dev.worxbend.airgradient.service.AirQualityMonitoringServiceController
import dev.worxbend.airgradient.service.AndroidMonitoringNotificationPermissionChecker
import dev.worxbend.airgradient.service.AndroidMonitoringServiceGateway
import dev.worxbend.airgradient.service.MonitoringLoopRunner
import dev.worxbend.airgradient.service.PersistentStatusNotificationUpdater
import dev.worxbend.airgradient.worker.AirQualityWorkerScheduler

class AppGraph(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val dispatchers = AppDispatchers.production
    private val settingsRepositoryImpl =
        SettingsRepositoryImpl(
            settingsDataSource = SettingsDataSource(appContext.airGradientSettingsDataStore),
        )

    val settingsRepository: SettingsRepository = settingsRepositoryImpl
    val monitoringSettingsRepository: MonitoringSettingsRepository = settingsRepositoryImpl

    private val airGradientRepository: AirGradientRepository = AirGradientRepositoryImpl()
    private val getCurrentMeasurement = GetCurrentMeasurementUseCase(airGradientRepository)
    private val notificationStateRepository: NotificationStateRepository =
        NotificationStateRepositoryImpl(appContext.airGradientNotificationStateDataStore)
    private val notificationDecisionEngine = NotificationDecisionEngine()
    private val notificationMessageDispatcher = AndroidNotificationMessageDispatcher(appContext)
    val periodicMonitoringScheduler = AirQualityWorkerScheduler(appContext)
    val monitoringServiceController =
        AirQualityMonitoringServiceController(
            settingsRepository = settingsRepository,
            monitoringSettingsRepository = monitoringSettingsRepository,
            permissionChecker = AndroidMonitoringNotificationPermissionChecker(appContext),
            serviceGateway = AndroidMonitoringServiceGateway(appContext),
            periodicScheduler = periodicMonitoringScheduler,
        )

    fun monitoringLoopRunner(): MonitoringLoopRunner =
        MonitoringLoopRunner(
            getCurrentMeasurement = getCurrentMeasurement,
            notificationStateRepository = notificationStateRepository,
            notificationDecisionEngine = notificationDecisionEngine,
            notificationMessageDispatcher = notificationMessageDispatcher,
        )

    fun persistentStatusNotificationUpdater(): PersistentStatusNotificationUpdater =
        PersistentStatusNotificationUpdater(
            appContext,
        )

    fun dashboardViewModelFactory(): ViewModelProvider.Factory =
        viewModelFactory {
            DashboardViewModel(
                observeSettings = ObserveSettingsUseCase(settingsRepository),
                refreshDashboard = RefreshDashboardUseCase(getCurrentMeasurement),
                monitoringDependencies =
                    DashboardMonitoringDependencies(
                        observeMonitoringSettings = ObserveMonitoringSettingsUseCase(monitoringSettingsRepository),
                        monitoringServiceController = monitoringServiceController,
                    ),
                notificationDependencies =
                    DashboardNotificationDependencies(
                        notificationStateRepository = notificationStateRepository,
                        notificationDecisionEngine = notificationDecisionEngine,
                        notificationMessageDispatcher = notificationMessageDispatcher,
                    ),
                dispatchers = dispatchers,
            )
        }

    fun settingsViewModelFactory(): ViewModelProvider.Factory =
        viewModelFactory {
            SettingsViewModel(
                useCases =
                    SettingsUseCases(
                        observeSettings = ObserveSettingsUseCase(settingsRepository),
                        observeMonitoringSettings =
                            ObserveMonitoringSettingsUseCase(monitoringSettingsRepository),
                        saveDeviceUrl = SaveDeviceUrlUseCase(settingsRepository),
                        saveRefreshInterval = SaveRefreshIntervalUseCase(settingsRepository),
                        saveForegroundPollingInterval =
                            SaveForegroundPollingIntervalUseCase(monitoringSettingsRepository),
                        savePeriodicBackgroundInterval =
                            SavePeriodicBackgroundIntervalUseCase(monitoringSettingsRepository),
                        saveNotificationsEnabled = SaveNotificationsEnabledUseCase(settingsRepository),
                        saveThemeMode = SaveThemeModeUseCase(settingsRepository),
                        testDeviceConnection = TestDeviceConnectionUseCase(getCurrentMeasurement),
                    ),
                monitoringServiceController = monitoringServiceController,
                dispatchers = dispatchers,
            )
        }

    private fun <T : ViewModel> viewModelFactory(create: () -> T): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <VM : ViewModel> create(modelClass: Class<VM>): VM = create() as VM
        }
}
