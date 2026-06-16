package dev.worxbend.airgradient.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.worxbend.airgradient.core.dispatchers.AppDispatchers
import dev.worxbend.airgradient.core.time.ClockProvider
import dev.worxbend.airgradient.core.time.SystemClockProvider
import dev.worxbend.airgradient.domain.error.AirGradientError
import dev.worxbend.airgradient.domain.model.AirMeasureSnapshot
import dev.worxbend.airgradient.domain.model.AppSettings
import dev.worxbend.airgradient.domain.monitoring.MonitoringMode
import dev.worxbend.airgradient.domain.monitoring.MonitoringPolicyValidationError
import dev.worxbend.airgradient.domain.monitoring.MonitoringRuntimeState
import dev.worxbend.airgradient.domain.monitoring.MonitoringSettings
import dev.worxbend.airgradient.domain.notifications.AirQualityConditionFactory
import dev.worxbend.airgradient.domain.notifications.NotificationDecision
import dev.worxbend.airgradient.domain.notifications.NotificationPolicy
import dev.worxbend.airgradient.domain.notifications.NotificationPolicyFactory
import dev.worxbend.airgradient.domain.notifications.NotificationState
import dev.worxbend.airgradient.domain.repository.AirGradientFetchResult
import dev.worxbend.airgradient.domain.sensors.SensorMetricFactory
import dev.worxbend.airgradient.domain.sensors.SensorThresholds
import dev.worxbend.airgradient.domain.usecase.ObserveSettingsUseCase
import dev.worxbend.airgradient.domain.usecase.RefreshDashboardUseCase
import dev.worxbend.airgradient.service.MonitoringServiceControllerResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

@Suppress("TooManyFunctions")
class DashboardViewModel(
    private val observeSettings: ObserveSettingsUseCase,
    private val refreshDashboard: RefreshDashboardUseCase,
    private val monitoringDependencies: DashboardMonitoringDependencies,
    private val notificationDependencies: DashboardNotificationDependencies,
    private val clockProvider: ClockProvider = SystemClockProvider,
    private val dispatchers: AppDispatchers = AppDispatchers.production,
) : ViewModel() {
    private val refreshMutex = Mutex()
    private val _uiState = MutableStateFlow<DashboardUiState>(DashboardUiState.Loading)
    private var currentSettings = AppSettings.default
    private var currentMonitoringSettings = MonitoringSettings.default
    private var currentMonitoringRuntimeState = MonitoringRuntimeState.default
    private var monitoringActionState: DashboardMonitoringActionState = DashboardMonitoringActionState.Idle
    private var latestSuccessfulSnapshot: AirMeasureSnapshot? = null
    private var previousSuccessfulSnapshot: AirMeasureSnapshot? = null
    private var autoRefreshJob: Job? = null

    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch(dispatchers.io) {
            observeSettings()
                .distinctUntilChanged()
                .collect { settings -> handleSettingsChanged(settings) }
        }
        viewModelScope.launch(dispatchers.io) {
            monitoringDependencies
                .observeMonitoringSettings()
                .distinctUntilChanged()
                .collect { settings -> handleMonitoringSettingsChanged(settings) }
        }
        viewModelScope.launch(dispatchers.io) {
            monitoringDependencies
                .observeMonitoringRuntimeState()
                .distinctUntilChanged()
                .collect { state -> handleMonitoringRuntimeStateChanged(state) }
        }
    }

    fun refresh() {
        launchRefresh(RefreshPresentation.ShowProgress)
    }

    private fun launchRefresh(presentation: RefreshPresentation) {
        viewModelScope.launch(dispatchers.io) {
            refreshCurrentSettings(presentation)
        }
    }

    fun startAlwaysOnMonitoring() {
        monitoringActionState = DashboardMonitoringActionState.Starting
        updateMonitoringSummary()
        viewModelScope.launch(dispatchers.io) {
            val actionState =
                when (val result = monitoringDependencies.monitoringServiceController.startAlwaysOnMonitoring()) {
                    MonitoringServiceControllerResult.Started -> {
                        DashboardMonitoringActionState.Started
                    }

                    MonitoringServiceControllerResult.Stopped -> {
                        DashboardMonitoringActionState.Stopped
                    }

                    is MonitoringServiceControllerResult.Rejected -> {
                        DashboardMonitoringActionState.Rejected(result.error)
                    }
                }
            monitoringActionState = actionState
            updateMonitoringSummary()
        }
    }

    fun stopMonitoring() {
        monitoringActionState = DashboardMonitoringActionState.Stopping
        updateMonitoringSummary()
        viewModelScope.launch(dispatchers.io) {
            monitoringDependencies.monitoringServiceController.stopMonitoring()
            monitoringActionState = DashboardMonitoringActionState.Stopped
            updateMonitoringSummary()
        }
    }

    fun onMonitoringPermissionDenied() {
        monitoringActionState =
            DashboardMonitoringActionState.Rejected(
                MonitoringPolicyValidationError.MissingNotificationPermission,
            )
        updateMonitoringSummary()
    }

    override fun onCleared() {
        autoRefreshJob?.cancel()
        super.onCleared()
    }

    private suspend fun handleSettingsChanged(settings: AppSettings) {
        currentSettings = settings

        if (settings.serverUrl.isNullOrBlank()) {
            autoRefreshJob?.cancel()
            autoRefreshJob = null
            notificationDependencies.notificationStateRepository.clearNotificationState()
            latestSuccessfulSnapshot = null
            previousSuccessfulSnapshot = null
            _uiState.value = DashboardUiState.Unconfigured
        } else {
            if (!settings.notificationsEnabled) {
                notificationDependencies.notificationStateRepository.clearNotificationState()
            }
            restartAutoRefresh(settings)
            launchRefresh(RefreshPresentation.ShowProgress)
        }
    }

    private fun handleMonitoringSettingsChanged(settings: MonitoringSettings) {
        currentMonitoringSettings = settings
        if (settings.mode == MonitoringMode.AlwaysOnForegroundService &&
            monitoringActionState == DashboardMonitoringActionState.Starting
        ) {
            monitoringActionState = DashboardMonitoringActionState.Started
        }
        updateMonitoringSummary()
    }

    private fun handleMonitoringRuntimeStateChanged(state: MonitoringRuntimeState) {
        currentMonitoringRuntimeState = state
        updateMonitoringSummary()
    }

    private fun restartAutoRefresh(settings: AppSettings) {
        autoRefreshJob?.cancel()
        autoRefreshJob =
            viewModelScope.launch(dispatchers.io) {
                while (isActive) {
                    delay(settings.refreshIntervalSeconds * MILLIS_PER_SECOND)
                    refreshCurrentSettings(RefreshPresentation.Silent)
                }
            }
    }

    private suspend fun refreshCurrentSettings(presentation: RefreshPresentation) {
        if (!refreshMutex.tryLock()) return

        try {
            val settings = currentSettings

            if (settings.serverUrl.isNullOrBlank()) {
                _uiState.value = DashboardUiState.Unconfigured
                return
            }

            if (presentation == RefreshPresentation.ShowProgress) {
                emitRefreshingState(settings)
            }

            when (val result = refreshDashboard(settings)) {
                is AirGradientFetchResult.Success -> emitContent(settings, result.snapshot)
                is AirGradientFetchResult.Failure -> emitFailure(settings, result.error)
            }
        } finally {
            refreshMutex.unlock()
        }
    }

    private fun emitRefreshingState(settings: AppSettings) {
        val latestSnapshot = latestSuccessfulSnapshot

        _uiState.value =
            if (latestSnapshot == null) {
                DashboardUiState.Loading
            } else {
                contentState(
                    settings = settings,
                    snapshot = latestSnapshot,
                    fetchStatusLabel = FETCHING_STATUS_LABEL,
                    isRefreshing = true,
                )
            }
    }

    private suspend fun emitContent(
        settings: AppSettings,
        snapshot: AirMeasureSnapshot,
    ) {
        previousSuccessfulSnapshot = latestSuccessfulSnapshot
        latestSuccessfulSnapshot = snapshot
        _uiState.value =
            contentState(
                settings = settings,
                snapshot = snapshot,
                fetchStatusLabel = LOADED_STATUS_LABEL,
                isRefreshing = false,
            )
        evaluateNotificationDecision(settings) { state, policy ->
            notificationDependencies.notificationDecisionEngine.evaluateCondition(
                condition = AirQualityConditionFactory.fromSnapshot(snapshot),
                state = state,
                policy = policy,
            )
        }
    }

    private suspend fun emitFailure(
        settings: AppSettings,
        error: AirGradientError,
    ) {
        val latestSnapshot = latestSuccessfulSnapshot
        _uiState.value =
            if (latestSnapshot == null) {
                DashboardUiState.Error(
                    reason = DashboardPresentationFormatter.dashboardError(error),
                    lastKnownSnapshot = null,
                    metrics = emptyList(),
                    lastUpdatedLabel = null,
                    monitoringSummary = monitoringSummary(),
                )
            } else {
                DashboardUiState.ContentWithWarning(
                    snapshot = latestSnapshot,
                    metrics = SensorMetricFactory.createMetrics(latestSnapshot, previousSuccessfulSnapshot),
                    overallStatus = SensorThresholds.overallStatus(latestSnapshot),
                    warning =
                        DashboardWarning(
                            cause = error,
                            message = DashboardPresentationFormatter.warningMessage(error),
                        ),
                    lastUpdatedLabel = DashboardPresentationFormatter.lastUpdatedLabel(latestSnapshot),
                    fetchStatusLabel = DashboardPresentationFormatter.fetchFailureStatusLabel(error),
                    refreshIntervalSeconds = settings.refreshIntervalSeconds,
                    monitoringSummary = monitoringSummary(),
                    isRefreshing = false,
                )
            }
        evaluateNotificationDecision(settings) { state, policy ->
            val now = clockProvider.now()
            val failureDecision =
                notificationDependencies.notificationDecisionEngine.evaluateFetchFailure(
                    error = error,
                    now = now,
                    state = state,
                    policy = policy,
                )

            if (failureDecision is NotificationDecision.Notify) {
                failureDecision
            } else {
                notificationDependencies.notificationDecisionEngine.evaluateStaleData(
                    now = now,
                    state = failureDecision.nextState,
                    policy = policy,
                )
            }
        }
    }

    private suspend fun evaluateNotificationDecision(
        settings: AppSettings,
        evaluate: (NotificationState, NotificationPolicy) -> NotificationDecision,
    ) {
        if (!settings.notificationsEnabled) {
            notificationDependencies.notificationStateRepository.clearNotificationState()
            return
        }

        val currentState = notificationDependencies.notificationStateRepository.getNotificationState()
        val decision =
            evaluate(
                currentState,
                NotificationPolicyFactory.fromSettings(settings),
            )

        notificationDependencies.notificationStateRepository.saveNotificationState(decision.nextState)

        if (decision is NotificationDecision.Notify) {
            notificationDependencies.notificationMessageDispatcher.show(decision.message)
        }
    }

    private fun contentState(
        settings: AppSettings,
        snapshot: AirMeasureSnapshot,
        fetchStatusLabel: String,
        isRefreshing: Boolean,
    ): DashboardUiState.Content =
        DashboardUiState.Content(
            snapshot = snapshot,
            metrics = SensorMetricFactory.createMetrics(snapshot, previousSuccessfulSnapshot),
            overallStatus = SensorThresholds.overallStatus(snapshot),
            lastUpdatedLabel = DashboardPresentationFormatter.lastUpdatedLabel(snapshot),
            fetchStatusLabel = fetchStatusLabel,
            refreshIntervalSeconds = settings.refreshIntervalSeconds,
            monitoringSummary = monitoringSummary(),
            isRefreshing = isRefreshing,
        )

    private fun updateMonitoringSummary() {
        _uiState.value =
            when (val state = _uiState.value) {
                is DashboardUiState.Content -> state.copy(monitoringSummary = monitoringSummary())

                is DashboardUiState.ContentWithWarning -> state.copy(monitoringSummary = monitoringSummary())

                is DashboardUiState.Error -> state.copy(monitoringSummary = monitoringSummary())

                DashboardUiState.Loading,
                DashboardUiState.Unconfigured,
                -> state
            }
    }

    private fun monitoringSummary(): DashboardMonitoringSummary =
        DashboardMonitoringSummary(
            mode = currentMonitoringSettings.mode,
            foregroundPollingIntervalSeconds = currentMonitoringSettings.foregroundPollingIntervalSeconds,
            periodicBackgroundIntervalMinutes = currentMonitoringSettings.periodicBackgroundIntervalMinutes,
            lastBackgroundCheckLabel =
                currentMonitoringRuntimeState.lastCheckedAt
                    ?.let(DashboardPresentationFormatter::lastBackgroundCheckLabel),
            lastSuccessfulBackgroundReadLabel =
                currentMonitoringRuntimeState.lastSuccessfulMeasurementAt
                    ?.let(DashboardPresentationFormatter::lastSuccessfulBackgroundReadLabel),
            actionState = monitoringActionState,
        )

    private companion object {
        const val MILLIS_PER_SECOND = 1_000L
        const val FETCHING_STATUS_LABEL = "Fetching measurements..."
        const val LOADED_STATUS_LABEL = "Latest measurements loaded."
    }
}

private enum class RefreshPresentation {
    ShowProgress,
    Silent,
}
