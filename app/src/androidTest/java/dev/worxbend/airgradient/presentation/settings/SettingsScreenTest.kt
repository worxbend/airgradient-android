package dev.worxbend.airgradient.presentation.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.worxbend.airgradient.domain.error.AirGradientError
import dev.worxbend.airgradient.domain.model.AppThemeMode
import dev.worxbend.airgradient.domain.monitoring.MonitoringMode
import dev.worxbend.airgradient.domain.monitoring.MonitoringPolicyValidationError
import dev.worxbend.airgradient.domain.notifications.NotificationSeverity
import dev.worxbend.airgradient.presentation.theme.AirGradientTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun invalidDeviceUrlStateShowsValidationCopyAndActions() {
        val saveClicks = AtomicInteger(0)
        val testClicks = AtomicInteger(0)

        composeRule.setContent {
            AirGradientTheme(dynamicColor = false) {
                SettingsScreen(
                    state =
                        SettingsUiState(
                            deviceUrlInput = "ftp://airgradient.local",
                            deviceUrlPreview = DeviceUrlPreview.Invalid,
                            connectionTestState = ConnectionTestState.InvalidInput,
                        ),
                    onNavigateBack = {},
                    actions =
                        actions(
                            onSaveDeviceUrl = { saveClicks.incrementAndGet() },
                            onTestConnection = { testClicks.incrementAndGet() },
                        ),
                )
            }
        }

        composeRule.onNodeWithText("Use http or https with a valid host.").assertIsDisplayed()
        composeRule.onNodeWithText("Enter a valid device URL before testing.").assertIsDisplayed()
        composeRule.onNodeWithText("Save").performClick()
        composeRule.onNodeWithText("Test connection").performClick()

        composeRule.runOnIdle {
            check(saveClicks.get() == 1)
            check(testClicks.get() == 1)
        }
    }

    @Test
    fun validDeviceUrlStateShowsEndpointPreviewAndConnectionSuccess() {
        composeRule.setContent {
            AirGradientTheme(dynamicColor = false) {
                SettingsScreen(
                    state =
                        SettingsUiState(
                            deviceUrlInput = "192.168.1.201",
                            deviceUrlPreview = DeviceUrlPreview.Valid("http://192.168.1.201"),
                            saveState = DeviceUrlSaveState.Saved("http://192.168.1.201"),
                            connectionTestState = ConnectionTestState.Success("http://192.168.1.201"),
                        ),
                    onNavigateBack = {},
                    actions = actions(),
                )
            }
        }

        composeRule
            .onNodeWithText("Saved http://192.168.1.201.")
            .assertIsDisplayed()
        composeRule
            .onNodeWithText("Connected to http://192.168.1.201.")
            .assertIsDisplayed()
    }

    @Test
    fun settingsControlsDispatchSelectionCallbacks() {
        val selectedInterval = AtomicReference<Int>()
        val selectedTheme = AtomicReference<AppThemeMode>()

        composeRule.setContent {
            AirGradientTheme(dynamicColor = false) {
                SettingsScreen(
                    state = SettingsUiState(refreshIntervalSeconds = 30),
                    onNavigateBack = {},
                    actions =
                        actions(
                            onRefreshIntervalSelected = selectedInterval::set,
                            onThemeModeSelected = selectedTheme::set,
                        ),
                )
            }
        }

        composeRule.onNodeWithText("Refresh").performScrollTo()
        composeRule.onNodeWithText("1m").performClick()
        composeRule.onNodeWithText("Appearance").performScrollTo()
        composeRule.onNodeWithText("Dark").performClick()

        composeRule.runOnIdle {
            check(selectedInterval.get() == 60)
            check(selectedTheme.get() == AppThemeMode.DARK)
        }
    }

    @Test
    fun notificationPermissionDeniedStateShowsDenialCopy() {
        composeRule.setContent {
            AirGradientTheme(dynamicColor = false) {
                SettingsScreen(
                    state = SettingsUiState(notificationPermissionDenied = true),
                    onNavigateBack = {},
                    actions = actions(),
                )
            }
        }

        composeRule.onNodeWithText("Notifications").performScrollTo()
        composeRule
            .onNodeWithText("Android notification permission was denied. Alerts remain off.")
            .assertIsDisplayed()
    }

    @Test
    fun notificationPolicyControlsDispatchCallbacks() {
        val selectedSeverity = AtomicReference<NotificationSeverity>()
        val recoveryEnabled = AtomicReference<Boolean>()
        val unreachableEnabled = AtomicReference<Boolean>()

        composeRule.setContent {
            AirGradientTheme(dynamicColor = false) {
                SettingsScreen(
                    state =
                        SettingsUiState(
                            minimumNotificationSeverity = NotificationSeverity.Warning,
                            notifyOnRecovery = true,
                            notifyOnDeviceUnreachable = true,
                        ),
                    onNavigateBack = {},
                    actions =
                        actions(
                            onMinimumNotificationSeveritySelected = selectedSeverity::set,
                            onNotifyOnRecoveryChanged = recoveryEnabled::set,
                            onNotifyOnDeviceUnreachableChanged = unreachableEnabled::set,
                        ),
                )
            }
        }

        composeRule.onNodeWithText("Notifications").performScrollTo()
        composeRule.onNodeWithText("Critical").performClick()
        composeRule.onNodeWithText("Recovery alerts").assertIsDisplayed()
        composeRule.onNodeWithText("Device unreachable alerts").assertIsDisplayed()
        composeRule.onNodeWithText("Notify after air quality returns below alert thresholds.").performClick()
        composeRule.onNodeWithText("Notify after repeated failed local-network checks.").performClick()

        composeRule.runOnIdle {
            check(selectedSeverity.get() == NotificationSeverity.Critical)
            check(recoveryEnabled.get() == false)
            check(unreachableEnabled.get() == false)
        }
    }

    @Test
    fun monitoringControlsDispatchStartStopAndIntervalCallbacks() {
        val selectedForegroundInterval = AtomicReference<Int>()
        val selectedPeriodicInterval = AtomicReference<Int>()
        val startClicks = AtomicInteger(0)
        val periodicStartClicks = AtomicInteger(0)
        val stopClicks = AtomicInteger(0)

        composeRule.setContent {
            AirGradientTheme(dynamicColor = false) {
                SettingsScreen(
                    state = SettingsUiState(foregroundPollingIntervalSeconds = 30),
                    onNavigateBack = {},
                    actions =
                        actions(
                            onForegroundPollingIntervalSelected = selectedForegroundInterval::set,
                            onPeriodicBackgroundIntervalSelected = selectedPeriodicInterval::set,
                            onStartAlwaysOnMonitoring = { startClicks.incrementAndGet() },
                            onStartBatteryFriendlyMonitoring = { periodicStartClicks.incrementAndGet() },
                            onStopMonitoring = { stopClicks.incrementAndGet() },
                        ),
                )
            }
        }

        composeRule.onNodeWithText("Monitoring").performScrollTo()
        composeRule.onNodeWithText("1m").performClick()
        composeRule.onNodeWithText("30m").performClick()
        composeRule.onNodeWithText("Start always-on").performClick()
        composeRule.onNodeWithText("Start battery-friendly").performClick()

        composeRule.runOnIdle {
            check(selectedForegroundInterval.get() == 60)
            check(selectedPeriodicInterval.get() == 30)
            check(startClicks.get() == 1)
            check(periodicStartClicks.get() == 1)
            check(stopClicks.get() == 0)
        }

        composeRule.setContent {
            AirGradientTheme(dynamicColor = false) {
                SettingsScreen(
                    state =
                        SettingsUiState(
                            monitoringMode = MonitoringMode.AlwaysOnForegroundService,
                            monitoringActionState = MonitoringActionState.Started,
                        ),
                    onNavigateBack = {},
                    actions =
                        actions(
                            onForegroundPollingIntervalSelected = selectedForegroundInterval::set,
                            onPeriodicBackgroundIntervalSelected = selectedPeriodicInterval::set,
                            onStartAlwaysOnMonitoring = { startClicks.incrementAndGet() },
                            onStartBatteryFriendlyMonitoring = { periodicStartClicks.incrementAndGet() },
                            onStopMonitoring = { stopClicks.incrementAndGet() },
                        ),
                )
            }
        }

        composeRule.onNodeWithText("Monitoring").performScrollTo()
        composeRule.onNodeWithText("Stop monitoring").performClick()

        composeRule.runOnIdle {
            check(startClicks.get() == 1)
            check(periodicStartClicks.get() == 1)
            check(stopClicks.get() == 1)
        }
    }

    @Test
    fun monitoringValidationErrorShowsUserFacingCopy() {
        composeRule.setContent {
            AirGradientTheme(dynamicColor = false) {
                SettingsScreen(
                    state =
                        SettingsUiState(
                            monitoringActionState =
                                MonitoringActionState.Rejected(
                                    MonitoringPolicyValidationError.MissingDeviceUrl,
                                ),
                        ),
                    onNavigateBack = {},
                    actions = actions(),
                )
            }
        }

        composeRule.onNodeWithText("Monitoring").performScrollTo()
        composeRule
            .onNodeWithText("Configure a device URL before starting monitoring.")
            .assertIsDisplayed()
    }

    @Test
    fun monitoringDiagnosticsShowRuntimeLabels() {
        composeRule.setContent {
            AirGradientTheme(dynamicColor = false) {
                SettingsScreen(
                    state =
                        SettingsUiState(
                            monitoringDiagnostics =
                                SettingsMonitoringDiagnostics(
                                    lastBackgroundCheckLabel =
                                        "Last background check 2026-06-16T12:00:00Z",
                                    lastSuccessfulReadLabel =
                                        "Last successful reading 2026-06-16T11:44:58Z",
                                    lastFailureLabel = "Last failed check 2026-06-16T12:00:00Z",
                                    consecutiveFailureCount = 2,
                                ),
                        ),
                    onNavigateBack = {},
                    actions = actions(),
                )
            }
        }

        composeRule.onNodeWithText("Monitoring").performScrollTo()
        composeRule.onNodeWithText("Diagnostics").assertIsDisplayed()
        composeRule
            .onNodeWithText("Last background check 2026-06-16T12:00:00Z")
            .assertIsDisplayed()
        composeRule
            .onNodeWithText("Last successful reading 2026-06-16T11:44:58Z")
            .assertIsDisplayed()
        composeRule
            .onNodeWithText("Last failed check 2026-06-16T12:00:00Z")
            .assertIsDisplayed()
        composeRule
            .onNodeWithText("Consecutive failed checks: 2")
            .assertIsDisplayed()
    }

    @Test
    fun connectionFailureStateShowsMappedErrorMessage() {
        composeRule.setContent {
            AirGradientTheme(dynamicColor = false) {
                SettingsScreen(
                    state =
                        SettingsUiState(
                            connectionTestState =
                                ConnectionTestState.Failure(
                                    error = AirGradientError.DeviceUnreachable,
                                    message = "The device could not be reached on the local network.",
                                ),
                        ),
                    onNavigateBack = {},
                    actions = actions(),
                )
            }
        }

        composeRule
            .onNodeWithText("The device could not be reached on the local network.")
            .assertIsDisplayed()
    }

    private fun actions(
        onDeviceUrlChanged: (String) -> Unit = {},
        onSaveDeviceUrl: () -> Unit = {},
        onTestConnection: () -> Unit = {},
        onRefreshIntervalSelected: (Int) -> Unit = {},
        onNotificationsEnabledChanged: (Boolean) -> Unit = {},
        onMinimumNotificationSeveritySelected: (NotificationSeverity) -> Unit = {},
        onNotifyOnRecoveryChanged: (Boolean) -> Unit = {},
        onNotifyOnDeviceUnreachableChanged: (Boolean) -> Unit = {},
        onThemeModeSelected: (AppThemeMode) -> Unit = {},
        onForegroundPollingIntervalSelected: (Int) -> Unit = {},
        onPeriodicBackgroundIntervalSelected: (Int) -> Unit = {},
        onAdaptivePollingEnabledChanged: (Boolean) -> Unit = {},
        onStartAlwaysOnMonitoring: () -> Unit = {},
        onStartBatteryFriendlyMonitoring: () -> Unit = {},
        onStopMonitoring: () -> Unit = {},
    ): SettingsScreenActions =
        SettingsScreenActions(
            onDeviceUrlChanged = onDeviceUrlChanged,
            onSaveDeviceUrl = onSaveDeviceUrl,
            onTestConnection = onTestConnection,
            onRefreshIntervalSelected = onRefreshIntervalSelected,
            onNotificationsEnabledChanged = onNotificationsEnabledChanged,
            onMinimumNotificationSeveritySelected = onMinimumNotificationSeveritySelected,
            onNotifyOnRecoveryChanged = onNotifyOnRecoveryChanged,
            onNotifyOnDeviceUnreachableChanged = onNotifyOnDeviceUnreachableChanged,
            onThemeModeSelected = onThemeModeSelected,
            onForegroundPollingIntervalSelected = onForegroundPollingIntervalSelected,
            onPeriodicBackgroundIntervalSelected = onPeriodicBackgroundIntervalSelected,
            onAdaptivePollingEnabledChanged = onAdaptivePollingEnabledChanged,
            onStartAlwaysOnMonitoring = onStartAlwaysOnMonitoring,
            onStartBatteryFriendlyMonitoring = onStartBatteryFriendlyMonitoring,
            onStopMonitoring = onStopMonitoring,
        )
}
