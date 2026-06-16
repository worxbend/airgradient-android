package dev.worxbend.airgradient.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.worxbend.airgradient.core.dispatchers.AppDispatchers
import dev.worxbend.airgradient.core.time.ClockProvider
import dev.worxbend.airgradient.core.time.SystemClockProvider
import dev.worxbend.airgradient.domain.error.AirGradientError
import dev.worxbend.airgradient.domain.model.AirMeasureSnapshot
import dev.worxbend.airgradient.domain.model.AppSettings
import dev.worxbend.airgradient.domain.notifications.AirQualityConditionFactory
import dev.worxbend.airgradient.domain.notifications.NoOpNotificationMessageDispatcher
import dev.worxbend.airgradient.domain.notifications.NotificationDecision
import dev.worxbend.airgradient.domain.notifications.NotificationDecisionEngine
import dev.worxbend.airgradient.domain.notifications.NotificationMessageDispatcher
import dev.worxbend.airgradient.domain.notifications.NotificationPolicy
import dev.worxbend.airgradient.domain.notifications.NotificationState
import dev.worxbend.airgradient.domain.repository.AirGradientFetchResult
import dev.worxbend.airgradient.domain.repository.NoOpNotificationStateRepository
import dev.worxbend.airgradient.domain.repository.NotificationStateRepository
import dev.worxbend.airgradient.domain.sensors.SensorMetricFactory
import dev.worxbend.airgradient.domain.sensors.SensorThresholds
import dev.worxbend.airgradient.domain.usecase.ObserveSettingsUseCase
import dev.worxbend.airgradient.domain.usecase.RefreshDashboardUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

class DashboardViewModel(
    private val observeSettings: ObserveSettingsUseCase,
    private val refreshDashboard: RefreshDashboardUseCase,
    private val notificationStateRepository: NotificationStateRepository = NoOpNotificationStateRepository,
    private val notificationDecisionEngine: NotificationDecisionEngine = NotificationDecisionEngine(),
    private val notificationMessageDispatcher: NotificationMessageDispatcher = NoOpNotificationMessageDispatcher,
    private val clockProvider: ClockProvider = SystemClockProvider,
    private val dispatchers: AppDispatchers = AppDispatchers.production,
) : ViewModel() {
    private val refreshMutex = Mutex()
    private val _uiState = MutableStateFlow<DashboardUiState>(DashboardUiState.Loading)
    private var currentSettings = AppSettings.default
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
    }

    fun refresh() {
        viewModelScope.launch(dispatchers.io) {
            refreshCurrentSettings()
        }
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
            notificationStateRepository.clearNotificationState()
            latestSuccessfulSnapshot = null
            previousSuccessfulSnapshot = null
            _uiState.value = DashboardUiState.Unconfigured
        } else {
            if (!settings.notificationsEnabled) {
                notificationStateRepository.clearNotificationState()
            }
            restartAutoRefresh(settings)
            refresh()
        }
    }

    private fun restartAutoRefresh(settings: AppSettings) {
        autoRefreshJob?.cancel()
        autoRefreshJob =
            viewModelScope.launch(dispatchers.io) {
                while (isActive) {
                    delay(settings.refreshIntervalSeconds * MILLIS_PER_SECOND)
                    refreshCurrentSettings()
                }
            }
    }

    private suspend fun refreshCurrentSettings() {
        if (!refreshMutex.tryLock()) return

        try {
            val settings = currentSettings

            if (settings.serverUrl.isNullOrBlank()) {
                _uiState.value = DashboardUiState.Unconfigured
                return
            }

            emitRefreshingState(settings)

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
            notificationDecisionEngine.evaluateCondition(
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
                    isRefreshing = false,
                )
            }
        evaluateNotificationDecision(settings) { state, policy ->
            notificationDecisionEngine.evaluateFetchFailure(
                error = error,
                now = clockProvider.now(),
                state = state,
                policy = policy,
            )
        }
    }

    private suspend fun evaluateNotificationDecision(
        settings: AppSettings,
        evaluate: (NotificationState, NotificationPolicy) -> NotificationDecision,
    ) {
        if (!settings.notificationsEnabled) {
            notificationStateRepository.clearNotificationState()
            return
        }

        val currentState = notificationStateRepository.getNotificationState()
        val decision =
            evaluate(
                currentState,
                NotificationPolicy.default.copy(notificationsEnabled = true),
            )

        notificationStateRepository.saveNotificationState(decision.nextState)

        if (decision is NotificationDecision.Notify) {
            notificationMessageDispatcher.show(decision.message)
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
            isRefreshing = isRefreshing,
        )

    private companion object {
        const val MILLIS_PER_SECOND = 1_000L
        const val FETCHING_STATUS_LABEL = "Fetching measurements..."
        const val LOADED_STATUS_LABEL = "Latest measurements loaded."
    }
}
