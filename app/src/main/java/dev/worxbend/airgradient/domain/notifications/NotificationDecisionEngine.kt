package dev.worxbend.airgradient.domain.notifications

import dev.worxbend.airgradient.domain.error.AirGradientError
import dev.worxbend.airgradient.domain.model.SensorStatus
import java.time.Duration
import java.time.Instant

@Suppress("ReturnCount")
class NotificationDecisionEngine {
    fun evaluateCondition(
        condition: AirQualityCondition,
        state: NotificationState,
        policy: NotificationPolicy,
    ): NotificationDecision {
        val severity = condition.status.toNotificationSeverity()

        if (!policy.notificationsEnabled) {
            return NotificationDecision.Suppress(NotificationSuppressionReason.NotificationsDisabled, state)
        }

        return if (severity == null) {
            evaluateRecovery(condition, state, policy)
        } else {
            evaluateBadCondition(condition, severity, state, policy)
        }
    }

    fun evaluateFetchFailure(
        error: AirGradientError,
        now: Instant,
        state: NotificationState,
        policy: NotificationPolicy,
    ): NotificationDecision {
        val nextState =
            state.copy(
                consecutiveFailureCount = state.consecutiveFailureCount + 1,
                recoveryCandidateStartedAt = null,
            )

        if (!policy.notificationsEnabled) {
            return NotificationDecision.Suppress(NotificationSuppressionReason.NotificationsDisabled, nextState)
        }

        if (!policy.notifyOnDeviceUnreachable) {
            return NotificationDecision.Suppress(NotificationSuppressionReason.NoActionableCondition, nextState)
        }

        if (nextState.consecutiveFailureCount < policy.maxConsecutiveFailuresBeforeDeviceAlert) {
            return NotificationDecision.Suppress(
                NotificationSuppressionReason.ConsecutiveFailureThresholdNotMet,
                nextState,
            )
        }

        val message =
            NotificationMessage(
                type = NotificationType.DeviceUnreachable,
                key = DEVICE_UNREACHABLE_KEY,
                severity = NotificationSeverity.Warning,
                title = "AirGradient device is unreachable",
                body =
                    "The device has failed ${nextState.consecutiveFailureCount} checks. " +
                        "Last error: ${error.label()}.",
            )

        return notifyUnlessCoolingDown(message = message, now = now, state = nextState, policy = policy)
    }

    fun evaluateStaleData(
        now: Instant,
        state: NotificationState,
        policy: NotificationPolicy,
    ): NotificationDecision {
        if (!policy.notificationsEnabled) {
            return NotificationDecision.Suppress(NotificationSuppressionReason.NotificationsDisabled, state)
        }

        val lastSuccessfulReadAt =
            state.lastSuccessfulReadAt
                ?: return NotificationDecision.Suppress(NotificationSuppressionReason.NoActionableCondition, state)

        if (Duration.between(lastSuccessfulReadAt, now) < policy.staleDataAfter) {
            return NotificationDecision.Suppress(NotificationSuppressionReason.NoActionableCondition, state)
        }

        val message =
            NotificationMessage(
                type = NotificationType.StaleData,
                key = STALE_DATA_KEY,
                severity = NotificationSeverity.Warning,
                title = "AirGradient data is stale",
                body = "No successful reading has been received since $lastSuccessfulReadAt.",
            )

        return notifyUnlessCoolingDown(message = message, now = now, state = state, policy = policy)
    }

    private fun evaluateBadCondition(
        condition: AirQualityCondition,
        severity: NotificationSeverity,
        state: NotificationState,
        policy: NotificationPolicy,
    ): NotificationDecision {
        val now = condition.observedAt
        val activeProblemStartedAt = state.activeProblemStartedAt ?: now
        val nextState =
            state.copy(
                lastConditionStatus = condition.status,
                lastDominantMetricKey = condition.dominantMetricKey,
                activeProblemStartedAt = activeProblemStartedAt,
                lastSuccessfulReadAt = now,
                consecutiveFailureCount = 0,
                recoveryCandidateStartedAt = null,
            )

        if (severity.rank < policy.minimumSeverity.rank) {
            return NotificationDecision.Suppress(NotificationSuppressionReason.BelowMinimumSeverity, nextState)
        }

        val previousSeverity = state.lastConditionStatus.toNotificationSeverity()
        val dominantMetricChanged =
            state.lastDominantMetricKey != null &&
                condition.dominantMetricKey != null &&
                state.lastDominantMetricKey != condition.dominantMetricKey
        val shouldNotifyImmediate =
            previousSeverity == null ||
                severity.rank > previousSeverity.rank ||
                dominantMetricChanged

        if (shouldNotifyImmediate) {
            val type =
                if (severity == NotificationSeverity.Critical) {
                    NotificationType.AirQualityCritical
                } else {
                    NotificationType.AirQualityDegraded
                }

            return notifyUnlessCoolingDown(
                message = condition.toMessage(type = type, severity = severity),
                now = now,
                state = nextState,
                policy = policy,
                bypassCooldown = severity.rank > (previousSeverity?.rank ?: 0),
            )
        }

        if (Duration.between(activeProblemStartedAt, now) >= policy.persistentBadAirQualityAfter) {
            return notifyUnlessCoolingDown(
                message =
                    condition.toMessage(
                        type = NotificationType.AirQualityPersistent,
                        severity = severity,
                        title = "Air quality remains degraded",
                    ),
                now = now,
                state = nextState,
                policy = policy,
            )
        }

        return NotificationDecision.Suppress(NotificationSuppressionReason.CooldownActive, nextState)
    }

    private fun evaluateRecovery(
        condition: AirQualityCondition,
        state: NotificationState,
        policy: NotificationPolicy,
    ): NotificationDecision {
        val now = condition.observedAt
        val hadActiveProblem = state.lastConditionStatus.toNotificationSeverity() != null
        val baseState =
            state.copy(
                lastSuccessfulReadAt = now,
                consecutiveFailureCount = 0,
            )

        if (!hadActiveProblem) {
            return NotificationDecision.Suppress(
                NotificationSuppressionReason.NoActionableCondition,
                baseState.copy(
                    lastConditionStatus = condition.status,
                    lastDominantMetricKey = null,
                    activeProblemStartedAt = null,
                    recoveryCandidateStartedAt = null,
                ),
            )
        }

        if (!policy.notifyOnRecovery) {
            return NotificationDecision.Suppress(
                NotificationSuppressionReason.NoActionableCondition,
                baseState.copy(
                    lastConditionStatus = condition.status,
                    lastDominantMetricKey = null,
                    activeProblemStartedAt = null,
                    recoveryCandidateStartedAt = null,
                ),
            )
        }

        val recoveryCandidateStartedAt = state.recoveryCandidateStartedAt ?: now
        val pendingState = baseState.copy(recoveryCandidateStartedAt = recoveryCandidateStartedAt)

        if (Duration.between(recoveryCandidateStartedAt, now) < policy.recoveryConfirmationWindow) {
            return NotificationDecision.Suppress(NotificationSuppressionReason.RecoveryPending, pendingState)
        }

        val recoveredState =
            pendingState.copy(
                lastConditionStatus = condition.status,
                lastDominantMetricKey = null,
                activeProblemStartedAt = null,
                recoveryCandidateStartedAt = null,
            )
        val message =
            NotificationMessage(
                type = NotificationType.AirQualityRecovered,
                key = RECOVERY_KEY,
                severity = NotificationSeverity.Warning,
                title = "Air quality recovered",
                body = "Current readings are back below alert thresholds.",
            )

        return notifyUnlessCoolingDown(message = message, now = now, state = recoveredState, policy = policy)
    }

    private fun notifyUnlessCoolingDown(
        message: NotificationMessage,
        now: Instant,
        state: NotificationState,
        policy: NotificationPolicy,
        bypassCooldown: Boolean = false,
    ): NotificationDecision {
        val lastSentAt = state.lastNotificationByKey[message.key]

        if (!bypassCooldown && lastSentAt != null && Duration.between(lastSentAt, now) < policy.cooldown) {
            return NotificationDecision.Suppress(NotificationSuppressionReason.CooldownActive, state)
        }

        return NotificationDecision.Notify(
            message = message,
            nextState =
                state.copy(
                    lastNotificationAt = now,
                    lastNotificationByKey = state.lastNotificationByKey + (message.key to now),
                ),
        )
    }

    private fun AirQualityCondition.toMessage(
        type: NotificationType,
        severity: NotificationSeverity,
        title: String =
            if (severity == NotificationSeverity.Critical) {
                "Air quality is critical"
            } else {
                "Air quality degraded"
            },
    ): NotificationMessage {
        val metricLabel = dominantMetricLabel ?: "Overall air quality"
        return NotificationMessage(
            type = type,
            key = "${type.name}:${dominantMetricKey ?: "overall"}",
            severity = severity,
            title = title,
            body = "$metricLabel is ${status.name.lowercase()}.",
        )
    }

    private fun SensorStatus?.toNotificationSeverity(): NotificationSeverity? =
        when (this) {
            SensorStatus.CRITICAL -> NotificationSeverity.Critical

            SensorStatus.WARNING -> NotificationSeverity.Warning

            SensorStatus.GOOD,
            SensorStatus.MODERATE,
            SensorStatus.UNKNOWN,
            null,
            -> null
        }

    private fun AirGradientError.label(): String =
        when (this) {
            AirGradientError.DeviceUnreachable -> "device unreachable"
            AirGradientError.InvalidDeviceUrl -> "invalid URL"
            AirGradientError.MalformedPayload -> "malformed response"
            AirGradientError.MissingDeviceUrl -> "missing device URL"
            AirGradientError.Timeout -> "timeout"
            AirGradientError.Unknown -> "unknown error"
            is AirGradientError.HttpFailure -> "HTTP $statusCode"
        }

    private companion object {
        const val DEVICE_UNREACHABLE_KEY = "DeviceUnreachable"
        const val STALE_DATA_KEY = "StaleData"
        const val RECOVERY_KEY = "AirQualityRecovered"
    }
}
