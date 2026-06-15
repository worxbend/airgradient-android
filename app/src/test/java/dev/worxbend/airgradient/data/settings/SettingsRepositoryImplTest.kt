package dev.worxbend.airgradient.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import app.cash.turbine.test
import dev.worxbend.airgradient.domain.model.AppSettings
import dev.worxbend.airgradient.domain.model.AppThemeMode
import dev.worxbend.airgradient.domain.repository.SaveDeviceUrlResult
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
import kotlin.io.path.createTempDirectory

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsRepositoryImplTest {
    private lateinit var temporaryDirectory: File
    private lateinit var testScope: TestScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repository: SettingsRepositoryImpl

    @Before
    fun setUp() {
        temporaryDirectory = createTempDirectory(prefix = "airgradient-settings-test").toFile()
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
    fun `settings emit Android defaults when preferences are empty`() =
        runTest {
            repository.settings.test {
                assertEquals(AppSettings.default, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `saving device URL normalizes and persists base URL`() =
        runTest {
            repository.settings.test {
                assertEquals(AppSettings.default, awaitItem())

                val result = repository.saveDeviceUrl("192.168.1.201")

                assertEquals(
                    SaveDeviceUrlResult.Saved("http://192.168.1.201"),
                    result,
                )
                assertEquals(
                    AppSettings.default.copy(serverUrl = "http://192.168.1.201"),
                    awaitItem(),
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `blank device URL clears persisted URL`() =
        runTest {
            repository.saveDeviceUrl("http://192.168.1.201")

            repository.settings.test {
                assertEquals(
                    AppSettings.default.copy(serverUrl = "http://192.168.1.201"),
                    awaitItem(),
                )

                val result = repository.saveDeviceUrl(" ")

                assertEquals(SaveDeviceUrlResult.Saved(null), result)
                assertEquals(AppSettings.default, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `invalid device URL is rejected without changing stored URL`() =
        runTest {
            repository.saveDeviceUrl("http://192.168.1.201")

            repository.settings.test {
                val previousSettings = AppSettings.default.copy(serverUrl = "http://192.168.1.201")
                assertEquals(previousSettings, awaitItem())

                val result = repository.saveDeviceUrl("ftp://airgradient.local")

                assertEquals(SaveDeviceUrlResult.Invalid, result)
                expectNoEvents()
                assertEquals(previousSettings, awaitItemAfterRecreatingRepository())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `refresh interval persists inside supported range`() =
        runTest {
            repository.saveRefreshIntervalSeconds(120)

            repository.settings.test {
                assertEquals(
                    AppSettings.default.copy(refreshIntervalSeconds = 120),
                    awaitItem(),
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `refresh interval clamps values outside supported range`() =
        runTest {
            repository.saveRefreshIntervalSeconds(1)
            assertEquals(
                AppSettings.default.copy(refreshIntervalSeconds = AppSettings.MIN_REFRESH_INTERVAL_SECONDS),
                awaitItemAfterRecreatingRepository(),
            )

            repository.saveRefreshIntervalSeconds(7_200)
            assertEquals(
                AppSettings.default.copy(refreshIntervalSeconds = AppSettings.MAX_REFRESH_INTERVAL_SECONDS),
                awaitItemAfterRecreatingRepository(),
            )
        }

    @Test
    fun `notification and theme settings persist`() =
        runTest {
            repository.saveNotificationsEnabled(true)
            repository.saveThemeMode(AppThemeMode.DARK)

            assertEquals(
                AppSettings.default.copy(
                    notificationsEnabled = true,
                    themeMode = AppThemeMode.DARK,
                ),
                awaitItemAfterRecreatingRepository(),
            )
        }

    private suspend fun awaitItemAfterRecreatingRepository(): AppSettings {
        val recreatedRepository = SettingsRepositoryImpl(SettingsDataSource(dataStore))
        var item = AppSettings.default

        recreatedRepository.settings.test {
            item = awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        return item
    }
}
