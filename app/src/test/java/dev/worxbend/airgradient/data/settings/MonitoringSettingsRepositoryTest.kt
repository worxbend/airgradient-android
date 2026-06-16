package dev.worxbend.airgradient.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import app.cash.turbine.test
import dev.worxbend.airgradient.domain.monitoring.MonitoringMode
import dev.worxbend.airgradient.domain.monitoring.MonitoringSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.time.Duration
import kotlin.io.path.createTempDirectory

@OptIn(ExperimentalCoroutinesApi::class)
class MonitoringSettingsRepositoryTest {
    private lateinit var temporaryDirectory: File
    private lateinit var testScope: TestScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repository: SettingsRepositoryImpl

    @Before
    fun setUp() {
        temporaryDirectory = createTempDirectory(prefix = "airgradient-monitoring-settings-test").toFile()
        testScope = TestScope(UnconfinedTestDispatcher())
        dataStore =
            PreferenceDataStoreFactory.create(
                scope = testScope,
                produceFile = { temporaryDirectory.resolve("settings.preferences_pb") },
            )
        repository = SettingsRepositoryImpl(SettingsDataSource(dataStore))
    }

    @After
    fun tearDown() {
        testScope.cancel()
        temporaryDirectory.deleteRecursively()
    }

    @Test
    fun `monitoring settings emit defaults when preferences are empty`() =
        runTest {
            repository.observeMonitoringSettings().test {
                assertEquals(MonitoringSettings.default, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `monitoring mode persists across repository recreation`() =
        runTest {
            repository.updateMonitoringMode(MonitoringMode.AlwaysOnForegroundService)

            assertEquals(
                MonitoringSettings.default.copy(mode = MonitoringMode.AlwaysOnForegroundService),
                awaitItemAfterRecreatingRepository(),
            )
        }

    @Test
    fun `foreground polling interval persists across repository recreation`() =
        runTest {
            repository.updateForegroundPollingInterval(Duration.ofMinutes(2))

            assertEquals(
                MonitoringSettings.default.copy(foregroundPollingIntervalSeconds = 120),
                awaitItemAfterRecreatingRepository(),
            )
        }

    @Test
    fun `foreground polling interval below thirty seconds is rejected`() =
        runTest {
            val result = runCatching { repository.updateForegroundPollingInterval(Duration.ofSeconds(10)) }

            assertTrue(result.exceptionOrNull() is IllegalArgumentException)
            assertEquals(MonitoringSettings.default, repository.getMonitoringSettings())
        }

    @Test
    fun `periodic background interval persists across repository recreation`() =
        runTest {
            repository.updatePeriodicBackgroundInterval(Duration.ofMinutes(30))

            assertEquals(
                MonitoringSettings.default.copy(periodicBackgroundIntervalMinutes = 30),
                awaitItemAfterRecreatingRepository(),
            )
        }

    @Test
    fun `periodic background interval below fifteen minutes is rejected`() =
        runTest {
            val result = runCatching { repository.updatePeriodicBackgroundInterval(Duration.ofMinutes(10)) }

            assertTrue(result.exceptionOrNull() is IllegalArgumentException)
            assertEquals(MonitoringSettings.default, repository.getMonitoringSettings())
        }

    @Test
    fun `all monitoring settings persist together`() =
        runTest {
            repository.updateMonitoringMode(MonitoringMode.BatteryFriendlyPeriodic)
            repository.updateForegroundPollingInterval(Duration.ofSeconds(60))
            repository.updatePeriodicBackgroundInterval(Duration.ofHours(1))

            assertEquals(
                MonitoringSettings(
                    mode = MonitoringMode.BatteryFriendlyPeriodic,
                    foregroundPollingIntervalSeconds = 60,
                    periodicBackgroundIntervalMinutes = 60,
                    persistentNotificationEnabled = true,
                ),
                awaitItemAfterRecreatingRepository(),
            )
        }

    private suspend fun awaitItemAfterRecreatingRepository(): MonitoringSettings {
        val recreatedRepository = SettingsRepositoryImpl(SettingsDataSource(dataStore))
        var item = MonitoringSettings.default

        recreatedRepository.observeMonitoringSettings().test {
            item = awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        return item
    }
}
