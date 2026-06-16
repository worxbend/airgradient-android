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
import dev.worxbend.airgradient.domain.repository.AirGradientFetchResult
import dev.worxbend.airgradient.domain.repository.NoOpNotificationStateRepository
import dev.worxbend.airgradient.domain.repository.NotificationStateRepository
import dev.worxbend.airgradient.domain.usecase.GetCurrentMeasurementUseCase
import kotlinx.coroutines.sync.Mutex
import java.time.Instant

class MonitoringLoopRunner(
    private val getCurrentMeasurement: GetCurrentMeasurementUseCase,
    private val notificationStateRepository: NotificationStateRepository = NoOpNotificationStateRepository,
    private val notificationDecisionEngine: NotificationDecisionEngine = NotificationDecisionEngine(),
    private val notificationMessageDispatcher: NotificationMessageDispatcher = NoOpNotificationMessageDispatcher,
    private val clockProvider: ClockProvider = SystemClockProvider,
) {
    private val tickMutex = Mutex()

    suspend fun runOneTick(settings: AppSettings): MonitoringTickResult {
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
            when (val result = getCurrentMeasurement(settings.serverUrl)) {
                is AirGradientFetchResult.Success -> handleSuccess(settings, result, checkedAt)
                is AirGradientFetchResult.Failure -> handleFailure(settings, result, checkedAt)
            }
        } finally {
            tickMutex.unlock()
        }

    private suspend fun handleSuccess(
        settings: AppSettings,
        result: AirGradientFetchResult.Success,
        checkedAt: Instant,
    ): MonitoringTickResult.Success {
        if (settings.notificationsEnabled) {
            val state = notificationStateRepository.getNotificationState()
            val decision =
                notificationDecisionEngine.evaluateCondition(
                    condition = AirQualityConditionFactory.fromSnapshot(result.snapshot),
                    state = state,
                    policy = NotificationPolicy.default.copy(notificationsEnabled = true),
                )
            persistAndDispatch(decision)
        } else {
            notificationStateRepository.clearNotificationState()
        }

        return MonitoringTickResult.Success(snapshot = result.snapshot, checkedAt = checkedAt)
    }

    private suspend fun handleFailure(
        settings: AppSettings,
        result: AirGradientFetchResult.Failure,
        checkedAt: Instant,
    ): MonitoringTickResult.Failure {
        if (!settings.notificationsEnabled) {
            notificationStateRepository.clearNotificationState()
            return MonitoringTickResult.Failure(
                error = result.error,
                consecutiveFailureCount = 0,
                checkedAt = checkedAt,
            )
        }

        val state = notificationStateRepository.getNotificationState()
        val decision =
            notificationDecisionEngine.evaluateFetchFailure(
                error = result.error,
                now = checkedAt,
                state = state,
                policy = NotificationPolicy.default.copy(notificationsEnabled = true),
            )
        persistAndDispatch(decision)

        return MonitoringTickResult.Failure(
            error = result.error,
            consecutiveFailureCount = decision.nextState.consecutiveFailureCount,
            checkedAt = checkedAt,
        )
    }

    private suspend fun persistAndDispatch(decision: NotificationDecision) {
        notificationStateRepository.saveNotificationState(decision.nextState)

        if (decision is NotificationDecision.Notify) {
            notificationMessageDispatcher.show(decision.message)
        }
    }
}
