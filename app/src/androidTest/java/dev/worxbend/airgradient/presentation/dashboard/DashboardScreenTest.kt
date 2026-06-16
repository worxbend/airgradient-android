package dev.worxbend.airgradient.presentation.dashboard

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.worxbend.airgradient.domain.error.AirGradientError
import dev.worxbend.airgradient.domain.model.AirMeasureSnapshot
import dev.worxbend.airgradient.domain.model.SensorMeasurementUnit
import dev.worxbend.airgradient.domain.model.SensorStatus
import dev.worxbend.airgradient.domain.sensors.SensorMetricFactory
import dev.worxbend.airgradient.presentation.theme.AirGradientTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class DashboardScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun unconfiguredStateShowsConfigurationActionAndDisablesRefresh() {
        val configureClicks = AtomicInteger(0)

        composeRule.setContent {
            AirGradientTheme(dynamicColor = false) {
                DashboardScreen(
                    state = DashboardUiState.Unconfigured,
                    onRefresh = {},
                    onOpenSettings = {},
                    onConfigureDevice = { configureClicks.incrementAndGet() },
                    onStartMonitoring = {},
                    onStopMonitoring = {},
                )
            }
        }

        composeRule.onNodeWithText("Configure a local AirGradient device").assertIsDisplayed()
        composeRule.onNodeWithContentDescription(REFRESH_ACTION_DESCRIPTION).assertIsNotEnabled()
        composeRule.onNodeWithContentDescription(SETTINGS_ACTION_DESCRIPTION).assertIsDisplayed()

        composeRule.onNodeWithText("Configure device").performClick()
        composeRule.runOnIdle {
            check(configureClicks.get() == 1)
        }
    }

    @Test
    fun contentStateShowsHeadlineMetricsAndRefreshes() {
        val refreshClicks = AtomicInteger(0)
        val startMonitoringClicks = AtomicInteger(0)

        composeRule.setContent {
            AirGradientTheme(dynamicColor = false) {
                DashboardScreen(
                    state = contentState(),
                    onRefresh = { refreshClicks.incrementAndGet() },
                    onOpenSettings = {},
                    onConfigureDevice = {},
                    onStartMonitoring = { startMonitoringClicks.incrementAndGet() },
                    onStopMonitoring = {},
                )
            }
        }

        composeRule.onNodeWithText("Good air").assertIsDisplayed()
        composeRule.onNodeWithText("42").assertIsDisplayed()
        composeRule.onAllNodesWithText("Good")[0].assertIsDisplayed()
        composeRule.onNodeWithText("Latest measurements loaded.").assertIsDisplayed()
        composeRule.onNodeWithText("30s").assertIsDisplayed()
        composeRule.onNodeWithText("Background monitoring").assertIsDisplayed()
        composeRule.onNodeWithText("Monitoring off").assertIsDisplayed()
        composeRule.onNodeWithText("Last background check 2026-06-16T00:15:00Z").assertIsDisplayed()
        composeRule.onNodeWithText("Last successful reading 2026-06-16T00:12:00Z").assertIsDisplayed()
        composeRule.onNodeWithText("Start always-on").performClick()
        composeRule.onNodeWithContentDescription(REFRESH_ACTION_DESCRIPTION).assertIsEnabled().performClick()

        composeRule.runOnIdle {
            check(refreshClicks.get() == 1)
            check(startMonitoringClicks.get() == 1)
        }
    }

    @Test
    fun errorStateShowsRetryAndSettingsActions() {
        val retryClicks = AtomicInteger(0)
        val configureClicks = AtomicInteger(0)

        composeRule.setContent {
            AirGradientTheme(dynamicColor = false) {
                DashboardScreen(
                    state =
                        DashboardUiState.Error(
                            reason =
                                DashboardError(
                                    cause = AirGradientError.Timeout,
                                    title = "Connection timed out",
                                    message = "The device did not respond before the request timed out.",
                                ),
                            lastKnownSnapshot = null,
                            metrics = emptyList(),
                            lastUpdatedLabel = null,
                        ),
                    onRefresh = { retryClicks.incrementAndGet() },
                    onOpenSettings = {},
                    onConfigureDevice = { configureClicks.incrementAndGet() },
                    onStartMonitoring = {},
                    onStopMonitoring = {},
                )
            }
        }

        composeRule.onNodeWithText("Connection timed out").assertIsDisplayed()
        composeRule.onNodeWithText("Retry").performClick()
        composeRule.onNodeWithContentDescription(SETTINGS_ACTION_DESCRIPTION).assertIsDisplayed()
        composeRule.onAllNodesWithText("Settings")[0].performClick()

        composeRule.runOnIdle {
            check(retryClicks.get() == 1)
            check(configureClicks.get() == 1)
        }
    }

    private fun contentState(): DashboardUiState.Content {
        val current = snapshot()
        val previous = current.copy(aqi = 40, pm25 = 8.0, co2 = 460.0)

        return DashboardUiState.Content(
            snapshot = current,
            metrics = SensorMetricFactory.createMetrics(current = current, previous = previous),
            overallStatus = SensorStatus.GOOD,
            lastUpdatedLabel = "Last updated just now",
            fetchStatusLabel = "Latest measurements loaded.",
            refreshIntervalSeconds = 30,
            monitoringSummary =
                DashboardMonitoringSummary(
                    lastBackgroundCheckLabel = "Last background check 2026-06-16T00:15:00Z",
                    lastSuccessfulBackgroundReadLabel = "Last successful reading 2026-06-16T00:12:00Z",
                ),
            isRefreshing = false,
        )
    }

    private fun snapshot(): AirMeasureSnapshot =
        AirMeasureSnapshot(
            aqi = 42,
            pm003Count = 442.0,
            pm01 = 3.0,
            pm25 = 7.0,
            pm10 = 8.0,
            co2 = 447.0,
            tvoc = 100.0,
            tvocUnit = SensorMeasurementUnit.INDEX,
            nox = 1.0,
            noxUnit = SensorMeasurementUnit.INDEX,
            temperatureCelsius = 24.5,
            humidityPercent = 49.0,
            measuredAt = Instant.parse("2026-06-16T00:12:00Z"),
        )
}
