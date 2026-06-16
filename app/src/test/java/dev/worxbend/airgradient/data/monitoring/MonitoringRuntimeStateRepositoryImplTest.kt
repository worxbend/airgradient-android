package dev.worxbend.airgradient.data.monitoring

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import app.cash.turbine.test
import dev.worxbend.airgradient.domain.error.AirGradientError
import dev.worxbend.airgradient.domain.model.AirMeasureSnapshot
import dev.worxbend.airgradient.domain.monitoring.MonitoringRuntimeState
import dev.worxbend.airgradient.domain.monitoring.MonitoringTickResult
import dev.worxbend.airgradient.domain.monitoring.MonitoringTickSkipReason
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
class MonitoringRuntimeStateRepositoryImplTest {
    private lateinit var temporaryDirectory: File
    private lateinit var testScope: TestScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repository: MonitoringRuntimeStateRepositoryImpl

    @Before
    fun setUp() {
        temporaryDirectory = createTempDirectory(prefix = "airgradient-monitoring-runtime-test").toFile()
        testScope = TestScope(UnconfinedTestDispatcher())
        dataStore =
            PreferenceDataStoreFactory.create(
                scope = testScope,
                produceFile = { temporaryDirectory.resolve("monitoring_runtime.preferences_pb") },
            )
        repository = MonitoringRuntimeStateRepositoryImpl(dataStore)
    }

    @After
    fun tearDown() {
        testScope.cancel()
        temporaryDirectory.deleteRecursively()
    }

    @Test
    fun `runtime state emits default when preferences are empty`() =
        runTest {
            repository.observeMonitoringRuntimeState().test {
                assertEquals(MonitoringRuntimeState.default, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `successful tick persists check and measurement timestamps`() =
        runTest {
            repository.recordTickResult(
                MonitoringTickResult.Success(
                    snapshot = snapshot,
                    checkedAt = checkedAt,
                ),
            )

            assertEquals(
                MonitoringRuntimeState.default.copy(
                    lastCheckedAt = checkedAt,
                    lastSuccessfulCheckAt = checkedAt,
                    lastSuccessfulMeasurementAt = measuredAt,
                ),
                awaitItemAfterRecreatingRepository(),
            )
        }

    @Test
    fun `failure tick preserves last success and records failure count`() =
        runTest {
            repository.recordTickResult(
                MonitoringTickResult.Success(
                    snapshot = snapshot,
                    checkedAt = checkedAt,
                ),
            )
            repository.recordTickResult(
                MonitoringTickResult.Failure(
                    error = AirGradientError.Timeout,
                    consecutiveFailureCount = 3,
                    checkedAt = failedAt,
                ),
            )

            assertEquals(
                MonitoringRuntimeState(
                    lastCheckedAt = failedAt,
                    lastSuccessfulCheckAt = checkedAt,
                    lastSuccessfulMeasurementAt = measuredAt,
                    lastFailureAt = failedAt,
                    consecutiveFailureCount = 3,
                ),
                repository.getMonitoringRuntimeState(),
            )
        }

    @Test
    fun `skipped tick does not update runtime state`() =
        runTest {
            repository.recordTickResult(
                MonitoringTickResult.Skipped(
                    reason = MonitoringTickSkipReason.RequestAlreadyRunning,
                    checkedAt = checkedAt,
                ),
            )

            assertEquals(MonitoringRuntimeState.default, repository.getMonitoringRuntimeState())
        }

    @Test
    fun `clearing runtime state restores default`() =
        runTest {
            repository.recordTickResult(
                MonitoringTickResult.Success(
                    snapshot = snapshot,
                    checkedAt = checkedAt,
                ),
            )

            repository.clearMonitoringRuntimeState()

            assertEquals(MonitoringRuntimeState.default, repository.getMonitoringRuntimeState())
        }

    private suspend fun awaitItemAfterRecreatingRepository(): MonitoringRuntimeState {
        val recreatedRepository = MonitoringRuntimeStateRepositoryImpl(dataStore)
        var item = MonitoringRuntimeState.default

        recreatedRepository.observeMonitoringRuntimeState().test {
            item = awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        return item
    }

    private companion object {
        val checkedAt: Instant = Instant.parse("2026-06-16T00:10:00Z")
        val failedAt: Instant = Instant.parse("2026-06-16T00:11:00Z")
        val measuredAt: Instant = Instant.parse("2026-06-16T00:09:58Z")
        val snapshot =
            AirMeasureSnapshot(
                aqi = 42,
                pm003Count = 442.0,
                pm01 = 3.0,
                pm25 = 7.0,
                pm10 = 8.0,
                co2 = 447.0,
                tvoc = 100.0,
                nox = 1.0,
                temperatureCelsius = 24.5,
                humidityPercent = 49.0,
                measuredAt = measuredAt,
            )
    }
}
