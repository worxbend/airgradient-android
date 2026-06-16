package dev.worxbend.airgradient.data.notifications

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import app.cash.turbine.test
import dev.worxbend.airgradient.domain.model.SensorStatus
import dev.worxbend.airgradient.domain.notifications.NotificationState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File
import java.time.Instant
import kotlin.io.path.createTempDirectory

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationStateRepositoryImplTest {
    private lateinit var temporaryDirectory: File
    private lateinit var testScope: TestScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repository: NotificationStateRepositoryImpl

    @Before
    fun setUp() {
        temporaryDirectory = createTempDirectory(prefix = "airgradient-notification-state-test").toFile()
        testScope = TestScope(UnconfinedTestDispatcher())
        dataStore =
            PreferenceDataStoreFactory.create(
                scope = testScope,
                produceFile = { temporaryDirectory.resolve("notification_state.preferences_pb") },
            )
        repository = NotificationStateRepositoryImpl(dataStore)
    }

    @After
    fun tearDown() {
        testScope.cancel()
        temporaryDirectory.deleteRecursively()
    }

    @Test
    fun `notification state emits default when preferences are empty`() =
        runTest {
            repository.observeNotificationState().test {
                assertEquals(NotificationState.default, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `notification state persists cooldown recovery and failure fields across repository recreation`() =
        runTest {
            val state =
                NotificationState(
                    lastConditionStatus = SensorStatus.WARNING,
                    lastDominantMetricKey = "co2",
                    activeProblemStartedAt = now.minusSeconds(180),
                    lastNotificationAt = now.minusSeconds(60),
                    lastNotificationByKey =
                        mapOf(
                            "co2" to now.minusSeconds(60),
                            "device" to now.minusSeconds(30),
                        ),
                    lastSuccessfulReadAt = now.minusSeconds(20),
                    consecutiveFailureCount = 2,
                    recoveryCandidateStartedAt = now.minusSeconds(10),
                    consecutiveBadReadingCount = 3,
                )

            repository.saveNotificationState(state)

            assertEquals(state, awaitItemAfterRecreatingRepository())
        }

    @Test
    fun `notification state update transforms persisted state`() =
        runTest {
            repository.updateNotificationState { state ->
                state.copy(
                    lastConditionStatus = SensorStatus.CRITICAL,
                    lastNotificationByKey = mapOf("pm25" to now),
                    consecutiveFailureCount = state.consecutiveFailureCount + 1,
                )
            }

            assertEquals(
                NotificationState.default.copy(
                    lastConditionStatus = SensorStatus.CRITICAL,
                    lastNotificationByKey = mapOf("pm25" to now),
                    consecutiveFailureCount = 1,
                ),
                repository.getNotificationState(),
            )
        }

    @Test
    fun `clearing notification state restores default`() =
        runTest {
            repository.saveNotificationState(NotificationState.default.copy(consecutiveFailureCount = 3))

            repository.clearNotificationState()

            assertEquals(NotificationState.default, repository.getNotificationState())
        }

    private suspend fun awaitItemAfterRecreatingRepository(): NotificationState {
        val recreatedRepository = NotificationStateRepositoryImpl(dataStore)
        var item = NotificationState.default

        recreatedRepository.observeNotificationState().test {
            item = awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        return item
    }

    private companion object {
        val now: Instant = Instant.parse("2026-06-16T00:00:00Z")
    }
}
