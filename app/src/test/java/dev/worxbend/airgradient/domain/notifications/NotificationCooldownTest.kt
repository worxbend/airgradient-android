package dev.worxbend.airgradient.domain.notifications

import dev.worxbend.airgradient.domain.error.AirGradientError
import dev.worxbend.airgradient.domain.model.SensorStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant

class NotificationCooldownTest {
    private val engine = NotificationDecisionEngine()
    private val policy = NotificationPolicy.default.copy(cooldown = Duration.ofMinutes(20))

    @Test
    fun `repeated warning is suppressed during cooldown`() {
        val firstDecision =
            engine.evaluateCondition(
                condition = warningCondition(at = now),
                state = NotificationState.default,
                policy = policy,
            ) as NotificationDecision.Notify

        val repeatedDecision =
            engine.evaluateCondition(
                condition = warningCondition(at = now.plusSeconds(30)),
                state = firstDecision.nextState,
                policy = policy,
            )

        val suppress = repeatedDecision as NotificationDecision.Suppress
        assertEquals(NotificationSuppressionReason.CooldownActive, suppress.reason)
    }

    @Test
    fun `device unreachable alert is suppressed during cooldown`() {
        val alertState =
            NotificationState.default.copy(
                consecutiveFailureCount = 2,
                lastNotificationByKey = mapOf("DeviceUnreachable" to now),
            )

        val decision =
            engine.evaluateFetchFailure(
                error = AirGradientError.Timeout,
                now = now.plusSeconds(30),
                state = alertState,
                policy = policy,
            )

        val suppress = decision as NotificationDecision.Suppress
        assertEquals(NotificationSuppressionReason.CooldownActive, suppress.reason)
    }

    @Test
    fun `stale data alert is suppressed during cooldown`() {
        val state =
            NotificationState.default.copy(
                lastSuccessfulReadAt = now.minus(Duration.ofMinutes(30)),
                lastNotificationByKey = mapOf("StaleData" to now),
            )

        val decision =
            engine.evaluateStaleData(
                now = now.plusSeconds(30),
                state = state,
                policy = policy,
            )

        val suppress = decision as NotificationDecision.Suppress
        assertEquals(NotificationSuppressionReason.CooldownActive, suppress.reason)
    }

    private fun warningCondition(at: Instant): AirQualityCondition =
        AirQualityCondition(
            status = SensorStatus.WARNING,
            dominantMetricKey = "co2",
            dominantMetricLabel = "CO2",
            observedAt = at,
        )

    private companion object {
        val now: Instant = Instant.parse("2026-06-16T00:00:00Z")
    }
}
