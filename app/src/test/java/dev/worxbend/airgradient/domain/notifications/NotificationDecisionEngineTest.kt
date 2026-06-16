package dev.worxbend.airgradient.domain.notifications

import dev.worxbend.airgradient.domain.error.AirGradientError
import dev.worxbend.airgradient.domain.model.SensorStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class NotificationDecisionEngineTest {
    private val engine = NotificationDecisionEngine()

    @Test
    fun `notifications disabled suppresses decisions`() {
        val decision =
            engine.evaluateCondition(
                condition = warningCondition(),
                state = NotificationState.default,
                policy = NotificationPolicy.default.copy(notificationsEnabled = false),
            )

        assertSuppresses(NotificationSuppressionReason.NotificationsDisabled, decision)
    }

    @Test
    fun `first bad reading is suppressed until consecutive threshold is met`() {
        val decision =
            engine.evaluateCondition(
                condition = warningCondition(),
                state = NotificationState.default,
                policy = NotificationPolicy.default,
            )

        assertSuppresses(NotificationSuppressionReason.ConsecutiveBadReadingThresholdNotMet, decision)
        assertEquals(1, decision.nextState.consecutiveBadReadingCount)
    }

    @Test
    fun `second consecutive warning sends degraded alert`() {
        val firstSuppressed =
            engine.evaluateCondition(
                condition = warningCondition(),
                state = NotificationState.default,
                policy = NotificationPolicy.default,
            )

        val secondDecision =
            engine.evaluateCondition(
                condition = warningCondition(at = now.plusSeconds(30)),
                state = firstSuppressed.nextState,
                policy = NotificationPolicy.default,
            )

        val notify = secondDecision as NotificationDecision.Notify
        assertEquals(NotificationType.AirQualityDegraded, notify.message.type)
        assertEquals(NotificationSeverity.Warning, notify.message.severity)
        assertEquals("co2", notify.nextState.lastDominantMetricKey)
        assertEquals(2, notify.nextState.consecutiveBadReadingCount)
    }

    @Test
    fun `critical condition sends critical alert after consecutive threshold`() {
        val policy = NotificationPolicy.default.copy(consecutiveBadReadingsBeforeAlert = 1)
        val decision =
            engine.evaluateCondition(
                condition = criticalCondition(),
                state = NotificationState.default,
                policy = policy,
            )

        val notify = decision as NotificationDecision.Notify
        assertEquals(NotificationType.AirQualityCritical, notify.message.type)
        assertEquals(NotificationSeverity.Critical, notify.message.severity)
    }

    @Test
    fun `minimum critical severity suppresses warning alert`() {
        val decision =
            engine.evaluateCondition(
                condition = warningCondition(),
                state = NotificationState.default,
                policy = NotificationPolicy.default.copy(minimumSeverity = NotificationSeverity.Critical),
            )

        assertSuppresses(NotificationSuppressionReason.BelowMinimumSeverity, decision)
    }

    @Test
    fun `warning to critical escalation sends alert during cooldown`() {
        val policy = NotificationPolicy.default.copy(consecutiveBadReadingsBeforeAlert = 1)
        val warningDecision =
            engine.evaluateCondition(
                condition = warningCondition(),
                state = NotificationState.default,
                policy = policy,
            ) as NotificationDecision.Notify

        val criticalDecision =
            engine.evaluateCondition(
                condition = criticalCondition(at = now.plusSeconds(30)),
                state = warningDecision.nextState,
                policy = policy,
            )

        val notify = criticalDecision as NotificationDecision.Notify
        assertEquals(NotificationType.AirQualityCritical, notify.message.type)
    }

    @Test
    fun `dominant bad metric change sends alert`() {
        val policy = NotificationPolicy.default.copy(consecutiveBadReadingsBeforeAlert = 1)
        val firstDecision =
            engine.evaluateCondition(
                condition = warningCondition(metricKey = "co2", metricLabel = "CO2"),
                state = NotificationState.default,
                policy = policy,
            ) as NotificationDecision.Notify

        val changedDecision =
            engine.evaluateCondition(
                condition = warningCondition(metricKey = "pm25", metricLabel = "PM2.5", at = now.plusSeconds(30)),
                state = firstDecision.nextState,
                policy = policy,
            )

        val notify = changedDecision as NotificationDecision.Notify
        assertEquals(NotificationType.AirQualityDegraded, notify.message.type)
        assertTrue(notify.message.body.contains("PM2.5"))
    }

    @Test
    fun `persistent bad condition sends persistent alert after configured window`() {
        val policy = NotificationPolicy.default.copy(consecutiveBadReadingsBeforeAlert = 1)
        val firstDecision =
            engine.evaluateCondition(
                condition = warningCondition(),
                state = NotificationState.default,
                policy = policy,
            ) as NotificationDecision.Notify

        val persistentDecision =
            engine.evaluateCondition(
                condition = warningCondition(at = now.plus(Duration.ofHours(2))),
                state = firstDecision.nextState,
                policy = policy,
            )

        val notify = persistentDecision as NotificationDecision.Notify
        assertEquals(NotificationType.AirQualityPersistent, notify.message.type)
    }

    @Test
    fun `device unreachable alert waits for repeated failures`() {
        val first =
            engine.evaluateFetchFailure(
                error = AirGradientError.Timeout,
                now = now,
                state = NotificationState.default,
                policy = NotificationPolicy.default,
            )
        assertSuppresses(NotificationSuppressionReason.ConsecutiveFailureThresholdNotMet, first)

        val second =
            engine.evaluateFetchFailure(
                error = AirGradientError.Timeout,
                now = now.plusSeconds(30),
                state = first.nextState,
                policy = NotificationPolicy.default,
            )
        assertSuppresses(NotificationSuppressionReason.ConsecutiveFailureThresholdNotMet, second)

        val third =
            engine.evaluateFetchFailure(
                error = AirGradientError.Timeout,
                now = now.plusSeconds(60),
                state = second.nextState,
                policy = NotificationPolicy.default,
            )

        val notify = third as NotificationDecision.Notify
        assertEquals(NotificationType.DeviceUnreachable, notify.message.type)
    }

    @Test
    fun `recovery sends one alert after confirmation window`() {
        val policy = NotificationPolicy.default.copy(consecutiveBadReadingsBeforeAlert = 1)
        val warningDecision =
            engine.evaluateCondition(
                condition = warningCondition(),
                state = NotificationState.default,
                policy = policy,
            ) as NotificationDecision.Notify

        val firstGood =
            engine.evaluateCondition(
                condition = goodCondition(at = now.plusSeconds(30)),
                state = warningDecision.nextState,
                policy = policy,
            )
        assertSuppresses(NotificationSuppressionReason.RecoveryPending, firstGood)

        val confirmedGood =
            engine.evaluateCondition(
                condition = goodCondition(at = now.plusSeconds(90)),
                state = firstGood.nextState,
                policy = policy,
            )

        val notify = confirmedGood as NotificationDecision.Notify
        assertEquals(NotificationType.AirQualityRecovered, notify.message.type)

        val repeatedGood =
            engine.evaluateCondition(
                condition = goodCondition(at = now.plusSeconds(120)),
                state = notify.nextState,
                policy = policy,
            )
        assertSuppresses(NotificationSuppressionReason.NoActionableCondition, repeatedGood)
    }

    @Test
    fun `recovery resets consecutive count requiring two bad readings again`() {
        // Use a short cooldown so re-alert after recovery is not blocked by the initial-alert cooldown.
        val policy =
            NotificationPolicy.default.copy(
                consecutiveBadReadingsBeforeAlert = 2,
                cooldown = Duration.ofSeconds(10),
            )

        // Two bad readings to send the initial alert
        val suppressed = engine.evaluateCondition(warningCondition(), NotificationState.default, policy)
        val warningDecision =
            engine.evaluateCondition(
                warningCondition(at = now.plusSeconds(5)),
                suppressed.nextState,
                policy,
            ) as NotificationDecision.Notify

        // Recovery after confirmation window (cooldown expired)
        val recovery =
            engine.evaluateCondition(
                goodCondition(at = now.plusSeconds(75)),
                warningDecision.nextState,
                policy,
            )

        // Count is reset to 0 on recovery
        assertEquals(0, recovery.nextState.consecutiveBadReadingCount)

        // First bad reading again is suppressed
        val newBad1 =
            engine.evaluateCondition(
                warningCondition(at = now.plusSeconds(150)),
                recovery.nextState,
                policy,
            )
        assertSuppresses(NotificationSuppressionReason.ConsecutiveBadReadingThresholdNotMet, newBad1)

        // Second bad reading alerts again (cooldown from initial alert has long expired)
        val newBad2 =
            engine.evaluateCondition(
                warningCondition(at = now.plusSeconds(155)),
                newBad1.nextState,
                policy,
            )
        assertTrue(newBad2 is NotificationDecision.Notify)
    }

    @Test
    fun `recovery preference suppresses recovery alert and clears problem state`() {
        val policy = NotificationPolicy.default.copy(consecutiveBadReadingsBeforeAlert = 1)
        val warningDecision =
            engine.evaluateCondition(
                condition = warningCondition(),
                state = NotificationState.default,
                policy = policy,
            ) as NotificationDecision.Notify

        val goodDecision =
            engine.evaluateCondition(
                condition = goodCondition(at = now.plusSeconds(90)),
                state = warningDecision.nextState,
                policy = policy.copy(notifyOnRecovery = false),
            )

        assertSuppresses(NotificationSuppressionReason.NoActionableCondition, goodDecision)
        assertEquals(SensorStatus.GOOD, goodDecision.nextState.lastConditionStatus)
        assertEquals(null, goodDecision.nextState.activeProblemStartedAt)
    }

    @Test
    fun `stale data sends alert after configured window`() {
        val decision =
            engine.evaluateStaleData(
                now = now.plus(Duration.ofMinutes(11)),
                state = NotificationState.default.copy(lastSuccessfulReadAt = now),
                policy = NotificationPolicy.default,
            )

        val notify = decision as NotificationDecision.Notify
        assertEquals(NotificationType.StaleData, notify.message.type)
    }

    private fun assertSuppresses(
        reason: NotificationSuppressionReason,
        decision: NotificationDecision,
    ) {
        val suppress = decision as NotificationDecision.Suppress
        assertEquals(reason, suppress.reason)
    }

    private fun warningCondition(
        metricKey: String = "co2",
        metricLabel: String = "CO2",
        at: Instant = now,
    ): AirQualityCondition =
        AirQualityCondition(
            status = SensorStatus.WARNING,
            dominantMetricKey = metricKey,
            dominantMetricLabel = metricLabel,
            observedAt = at,
        )

    private fun criticalCondition(at: Instant = now): AirQualityCondition =
        AirQualityCondition(
            status = SensorStatus.CRITICAL,
            dominantMetricKey = "pm25",
            dominantMetricLabel = "PM2.5",
            observedAt = at,
        )

    private fun goodCondition(at: Instant): AirQualityCondition =
        AirQualityCondition(
            status = SensorStatus.GOOD,
            dominantMetricKey = null,
            dominantMetricLabel = null,
            observedAt = at,
        )

    private companion object {
        val now: Instant = Instant.parse("2026-06-16T00:00:00Z")
    }
}
