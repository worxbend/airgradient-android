package dev.worxbend.airgradient.service

import dev.worxbend.airgradient.core.time.ClockProvider
import dev.worxbend.airgradient.domain.error.AirGradientError
import dev.worxbend.airgradient.domain.model.AirMeasureSnapshot
import dev.worxbend.airgradient.domain.model.AppSettings
import dev.worxbend.airgradient.domain.model.AppThemeMode
import dev.worxbend.airgradient.domain.monitoring.MonitoringTickResult
import dev.worxbend.airgradient.domain.monitoring.MonitoringTickSkipReason
import dev.worxbend.airgradient.domain.notifications.NotificationMessage
import dev.worxbend.airgradient.domain.notifications.NotificationMessageDispatcher
import dev.worxbend.airgradient.domain.notifications.NotificationState
import dev.worxbend.airgradient.domain.notifications.NotificationType
import dev.worxbend.airgradient.domain.repository.AirGradientFetchResult
import dev.worxbend.airgradient.domain.repository.AirGradientRepository
import dev.worxbend.airgradient.domain.repository.NotificationStateRepository
import dev.worxbend.airgradient.domain.usecase.GetCurrentMeasurementUseCase
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class MonitoringLoopRunnerTest {
    @Test
    fun `missing device URL skips without fetching`() =
        runTest {
            val repository = FakeAirGradientRepository()
            val runner = runner(repository)

            val result = runner.runOneTick(settings(serverUrl = null))

            assertEquals(
                MonitoringTickResult.Skipped(MonitoringTickSkipReason.MissingDeviceUrl, now),
                result,
            )
            assertEquals(0, repository.calls)
        }

    @Test
    fun `successful degraded reading persists state and dispatches notification`() =
        runTest {
            val repository =
                FakeAirGradientRepository(
                    result = AirGradientFetchResult.Success(criticalSnapshot),
                )
            val stateRepository = InMemoryNotificationStateRepository()
            val dispatcher = RecordingNotificationMessageDispatcher()
            val runner =
                runner(
                    repository = repository,
                    stateRepository = stateRepository,
                    dispatcher = dispatcher,
                )

            val result = runner.runOneTick(settings(notificationsEnabled = true))

            assertTrue(result is MonitoringTickResult.Success)
            assertEquals(1, repository.calls)
            assertEquals("pm25", stateRepository.state.lastDominantMetricKey)
            assertEquals(NotificationType.AirQualityCritical, dispatcher.messages.single().type)
        }

    @Test
    fun `notifications disabled clears persisted decision state`() =
        runTest {
            val stateRepository =
                InMemoryNotificationStateRepository(
                    initialState = NotificationState.default.copy(consecutiveFailureCount = 2),
                )
            val runner =
                runner(
                    repository = FakeAirGradientRepository(AirGradientFetchResult.Success(criticalSnapshot)),
                    stateRepository = stateRepository,
                )

            runner.runOneTick(settings(notificationsEnabled = false))

            assertEquals(NotificationState.default, stateRepository.state)
        }

    @Test
    fun `third fetch failure dispatches unreachable notification`() =
        runTest {
            val repository =
                FakeAirGradientRepository(
                    result = AirGradientFetchResult.Failure(AirGradientError.Timeout),
                )
            val stateRepository = InMemoryNotificationStateRepository()
            val dispatcher = RecordingNotificationMessageDispatcher()
            val runner =
                runner(
                    repository = repository,
                    stateRepository = stateRepository,
                    dispatcher = dispatcher,
                )

            runner.runOneTick(settings(notificationsEnabled = true))
            runner.runOneTick(settings(notificationsEnabled = true))
            val thirdResult = runner.runOneTick(settings(notificationsEnabled = true))

            val failure = thirdResult as MonitoringTickResult.Failure
            assertEquals(3, failure.consecutiveFailureCount)
            assertEquals(NotificationType.DeviceUnreachable, dispatcher.messages.single().type)
        }

    @Test
    fun `overlapping check is skipped`() =
        runTest {
            val repository =
                FakeAirGradientRepository(
                    result = AirGradientFetchResult.Success(healthySnapshot),
                    delayMillis = 100,
                )
            val runner = runner(repository)

            val first = async(start = CoroutineStart.UNDISPATCHED) { runner.runOneTick(settings()) }
            val second = runner.runOneTick(settings())

            assertEquals(
                MonitoringTickResult.Skipped(MonitoringTickSkipReason.RequestAlreadyRunning, now),
                second,
            )
            assertTrue(first.await() is MonitoringTickResult.Success)
        }

    private fun runner(
        repository: FakeAirGradientRepository,
        stateRepository: NotificationStateRepository = InMemoryNotificationStateRepository(),
        dispatcher: NotificationMessageDispatcher = RecordingNotificationMessageDispatcher(),
    ): MonitoringLoopRunner =
        MonitoringLoopRunner(
            getCurrentMeasurement = GetCurrentMeasurementUseCase(repository),
            notificationStateRepository = stateRepository,
            notificationMessageDispatcher = dispatcher,
            clockProvider = ClockProvider { now },
        )

    private fun settings(
        serverUrl: String? = "http://192.168.1.201",
        notificationsEnabled: Boolean = true,
    ): AppSettings =
        AppSettings(
            serverUrl = serverUrl,
            refreshIntervalSeconds = 30,
            notificationsEnabled = notificationsEnabled,
            themeMode = AppThemeMode.SYSTEM,
        )

    private class FakeAirGradientRepository(
        private val result: AirGradientFetchResult = AirGradientFetchResult.Success(healthySnapshot),
        private val delayMillis: Long = 0,
    ) : AirGradientRepository {
        var calls = 0
            private set

        override suspend fun fetchCurrentMeasurement(serverUrl: String?): AirGradientFetchResult {
            calls += 1
            if (delayMillis > 0) {
                delay(delayMillis)
            }
            return result
        }
    }

    private class InMemoryNotificationStateRepository(
        initialState: NotificationState = NotificationState.default,
    ) : NotificationStateRepository {
        var state = initialState
            private set

        override fun observeNotificationState(): Flow<NotificationState> = flowOf(state)

        override suspend fun getNotificationState(): NotificationState = state

        override suspend fun saveNotificationState(state: NotificationState) {
            this.state = state
        }

        override suspend fun updateNotificationState(transform: (NotificationState) -> NotificationState) {
            state = transform(state)
        }

        override suspend fun clearNotificationState() {
            state = NotificationState.default
        }
    }

    private class RecordingNotificationMessageDispatcher : NotificationMessageDispatcher {
        val messages = mutableListOf<NotificationMessage>()

        override fun show(message: NotificationMessage) {
            messages += message
        }
    }

    private companion object {
        val now: Instant = Instant.parse("2026-06-16T00:00:00Z")

        val healthySnapshot =
            AirMeasureSnapshot(
                aqi = 29,
                pm003Count = 442.0,
                pm01 = 3.0,
                pm25 = 7.0,
                pm10 = 8.0,
                co2 = 447.0,
                tvoc = 100.0,
                nox = 1.0,
                temperatureCelsius = 24.47,
                humidityPercent = 49.0,
                measuredAt = now,
            )

        val criticalSnapshot =
            healthySnapshot.copy(
                aqi = 50,
                pm25 = 80.0,
            )
    }
}
