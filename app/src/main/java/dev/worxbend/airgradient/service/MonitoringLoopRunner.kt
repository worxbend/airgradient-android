package dev.worxbend.airgradient.service

import dev.worxbend.airgradient.core.time.ClockProvider
import dev.worxbend.airgradient.core.time.SystemClockProvider
import dev.worxbend.airgradient.domain.model.AppSettings
import dev.worxbend.airgradient.domain.monitoring.MonitoringTickResult
import dev.worxbend.airgradient.domain.monitoring.MonitoringTickSkipReason
import dev.worxbend.airgradient.domain.notifications.AirQualityConditionFactory
import dev.worxbend.airgradient.domain.notifications.NoOpNotificationMessageDispatcher
import dev.worxbend.airgradient.domain.notifications.NotificationDecision
import dev.worxbend.airgradient.domain.notifications.NotificationDecisionEngine
import dev.worxbend.airgradient.domain.notifications.NotificationMessageDispatcher
import dev.worxbend.airgradient.domain.notifications.NotificationPolicy
import dev.worxbend.airgradient.domain.notifications.NotificationPolicyFactory
import dev.worxbend.airgradient.domain.repository.AirGradientFetchResult
import dev.worxbend.airgradient.domain.repository.MonitoringRuntimeStateRepository
import dev.worxbend.airgradient.domain.repository.NoOpMonitoringRuntimeStateRepository
import dev.worxbend.airgradient.domain.repository.NoOpNotificationStateRepository
import dev.worxbend.airgradient.domain.repository.NotificationStateRepository
import dev.worxbend.airgradient.domain.usecase.GetCurrentMeasurementUseCase
import kotlinx.coroutines.sync.Mutex
import java.time.Instant

interface MonitoringTickRunner {
    suspend fun runOneTick(settings: AppSettings): MonitoringTickResult
}

class MonitoringLoopRunner(
    private val getCurrentMeasurement: GetCurrentMeasurementUseCase,
    private val notificationStateRepository: NotificationStateRepository = NoOpNotificationStateRepository,
    private val monitoringRuntimeStateRepository: MonitoringRuntimeStateRepository =
        NoOpMonitoringRuntimeStateRepository,
    private val notificationDecisionEngine: NotificationDecisionEngine = NotificationDecisionEngine(),
    private val notificationMessageDispatcher: NotificationMessageDispatcher = NoOpNotificationMessageDispatcher,
    private val clockProvider: ClockProvider = SystemClockProvider,
) : MonitoringTickRunner {
    private val tickMutex = Mutex()

    // Tracks whether notification state has been written since the last clear.
    // Avoids a DataStore write on every tick when notifications are disabled and state is already empty.
    private var notificationStateMayBePopulated = true

    override suspend fun runOneTick(settings: AppSettings): MonitoringTickResult {
        val checkedAt = clockProvider.now()

        return when {
            settings.serverUrl.isNullOrBlank() -> {
                MonitoringTickResult.Skipped(MonitoringTickSkipReason.MissingDeviceUrl, checkedAt)
            }

            !tickMutex.tryLock() -> {
                MonitoringTickResult.Skipped(MonitoringTickSkipReason.RequestAlreadyRunning, checkedAt)
            }

            else -> {
                runLockedTick(settings, checkedAt)
            }
        }
    }

    private suspend fun runLockedTick(
        settings: AppSettings,
        checkedAt: Instant,
    ): MonitoringTickResult =
        try {
            val tickResult =
                when (val result = getCurrentMeasurement(settings.serverUrl)) {
                    is AirGradientFetchResult.Success -> handleSuccess(settings, result, checkedAt)
                    is AirGradientFetchResult.Failure -> handleFailure(settings, result, checkedAt)
                }
            monitoringRuntimeStateRepository.recordTickResult(tickResult)
            tickResult
        } finally {
            tickMutex.unlock()
        }

    private suspend fun handleSuccess(
        settings: AppSettings,
        result: AirGradientFetchResult.Success,
        checkedAt: Instant,
    ): MonitoringTickResult.Success {
        if (settings.notificationsEnabled) {
            notificationStateMayBePopulated = true
            val state = notificationStateRepository.getNotificationState()
            val decision =
                notificationDecisionEngine.evaluateCondition(
                    condition = AirQualityConditionFactory.fromSnapshot(result.snapshot),
                    state = state,
                    policy = NotificationPolicyFactory.fromSettings(settings),
                )
            persistAndDispatch(decision)
        } else {
            clearNotificationStateIfNeeded()
        }

        return MonitoringTickResult.Success(snapshot = result.snapshot, checkedAt = checkedAt)
    }

    private suspend fun handleFailure(
        settings: AppSettings,
        result: AirGradientFetchResult.Failure,
        checkedAt: Instant,
    ): MonitoringTickResult.Failure {
        val consecutiveFailureCount = nextRuntimeFailureCount()

        if (!settings.notificationsEnabled) {
            clearNotificationStateIfNeeded()
            return MonitoringTickResult.Failure(
                error = result.error,
                consecutiveFailureCount = consecutiveFailureCount,
                checkedAt = checkedAt,
            )
        }

        notificationStateMayBePopulated = true
        val state = notificationStateRepository.getNotificationState()
        val failureDecision =
            notificationDecisionEngine.evaluateFetchFailure(
                error = result.error,
                now = checkedAt,
                state = state,
                policy = NotificationPolicyFactory.fromSettings(settings),
            )
        persistAndDispatchFailureDecision(failureDecision, checkedAt, settings)

        return MonitoringTickResult.Failure(
            error = result.error,
            consecutiveFailureCount = consecutiveFailureCount,
            checkedAt = checkedAt,
        )
    }

    private suspend fun nextRuntimeFailureCount(): Int =
        monitoringRuntimeStateRepository.getMonitoringRuntimeState().consecutiveFailureCount + 1

    private suspend fun clearNotificationStateIfNeeded() {
        if (notificationStateMayBePopulated) {
            notificationStateRepository.clearNotificationState()
            notificationStateMayBePopulated = false
        }
    }

    private suspend fun persistAndDispatch(decision: NotificationDecision) {
        notificationStateRepository.saveNotificationState(decision.nextState)

        if (decision is NotificationDecision.Notify) {
            notificationMessageDispatcher.show(decision.message)
        }
    }

    private suspend fun persistAndDispatchFailureDecision(
        failureDecision: NotificationDecision,
        checkedAt: Instant,
        settings: AppSettings,
    ) {
        if (failureDecision is NotificationDecision.Notify) {
            persistAndDispatch(failureDecision)
            return
        }

        val staleDecision =
            notificationDecisionEngine.evaluateStaleData(
                now = checkedAt,
                state = failureDecision.nextState,
                policy = NotificationPolicyFactory.fromSettings(settings),
            )
        persistAndDispatch(staleDecision)
    }
}
