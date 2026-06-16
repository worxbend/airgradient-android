package dev.worxbend.airgradient.domain.monitoring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Duration

class MonitoringPolicyTest {
    @Test
    fun `default policy matches product defaults`() {
        val policy = MonitoringPolicy.default

        assertEquals(MonitoringMode.Off, policy.mode)
        assertEquals(Duration.ofSeconds(30), policy.foregroundPollingInterval)
        assertEquals(Duration.ofMinutes(15), policy.periodicBackgroundInterval)
        assertEquals(true, policy.stopOnInvalidConfiguration)
        assertEquals(3, policy.maxConsecutiveFailuresBeforeDeviceAlert)
    }

    @Test
    fun `thirty second foreground interval is accepted`() {
        val policy = monitoringPolicy(foregroundPollingInterval = Duration.ofSeconds(30))

        assertEquals(Duration.ofSeconds(30), policy.foregroundPollingInterval)
    }

    @Test
    fun `foreground interval below thirty seconds is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            monitoringPolicy(foregroundPollingInterval = Duration.ofSeconds(10))
        }
    }

    @Test
    fun `fifteen minute periodic interval is accepted`() {
        val policy = monitoringPolicy(periodicBackgroundInterval = Duration.ofMinutes(15))

        assertEquals(Duration.ofMinutes(15), policy.periodicBackgroundInterval)
    }

    @Test
    fun `periodic interval below fifteen minutes is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            monitoringPolicy(periodicBackgroundInterval = Duration.ofMinutes(14))
        }
    }

    @Test
    fun `always on monitoring requires configured device URL`() {
        val result =
            monitoringPolicy(mode = MonitoringMode.AlwaysOnForegroundService)
                .validate(permissionState(hasConfiguredDeviceUrl = false))

        assertEquals(
            MonitoringPolicyValidationResult.Invalid(MonitoringPolicyValidationError.MissingDeviceUrl),
            result,
        )
    }

    @Test
    fun `always on monitoring requires notification permission when platform requires it`() {
        val result =
            monitoringPolicy(mode = MonitoringMode.AlwaysOnForegroundService)
                .validate(
                    permissionState(
                        notificationPermissionRequired = true,
                        notificationPermissionGranted = false,
                    ),
                )

        assertEquals(
            MonitoringPolicyValidationResult.Invalid(MonitoringPolicyValidationError.MissingNotificationPermission),
            result,
        )
    }

    @Test
    fun `battery friendly monitoring requires configured device URL`() {
        val result =
            monitoringPolicy(mode = MonitoringMode.BatteryFriendlyPeriodic)
                .validate(permissionState(hasConfiguredDeviceUrl = false))

        assertEquals(
            MonitoringPolicyValidationResult.Invalid(MonitoringPolicyValidationError.MissingDeviceUrl),
            result,
        )
    }

    @Test
    fun `battery friendly monitoring does not require foreground notification permission`() {
        val result =
            monitoringPolicy(mode = MonitoringMode.BatteryFriendlyPeriodic)
                .validate(
                    permissionState(
                        notificationPermissionRequired = true,
                        notificationPermissionGranted = false,
                    ),
                )

        assertEquals(MonitoringPolicyValidationResult.Valid, result)
    }

    @Test
    fun `off mode is valid without configured device URL or notification permission`() {
        val result =
            monitoringPolicy(mode = MonitoringMode.Off)
                .validate(
                    permissionState(
                        hasConfiguredDeviceUrl = false,
                        notificationPermissionRequired = true,
                        notificationPermissionGranted = false,
                    ),
                )

        assertEquals(MonitoringPolicyValidationResult.Valid, result)
    }

    @Test
    fun `device alert failure threshold must be positive`() {
        assertThrows(IllegalArgumentException::class.java) {
            monitoringPolicy(maxConsecutiveFailuresBeforeDeviceAlert = 0)
        }
    }

    private fun monitoringPolicy(
        mode: MonitoringMode = MonitoringMode.Off,
        foregroundPollingInterval: Duration = Duration.ofSeconds(30),
        periodicBackgroundInterval: Duration = Duration.ofMinutes(15),
        stopOnInvalidConfiguration: Boolean = true,
        maxConsecutiveFailuresBeforeDeviceAlert: Int = 3,
    ): MonitoringPolicy =
        MonitoringPolicy(
            mode = mode,
            foregroundPollingInterval = foregroundPollingInterval,
            periodicBackgroundInterval = periodicBackgroundInterval,
            stopOnInvalidConfiguration = stopOnInvalidConfiguration,
            maxConsecutiveFailuresBeforeDeviceAlert = maxConsecutiveFailuresBeforeDeviceAlert,
        )

    private fun permissionState(
        hasConfiguredDeviceUrl: Boolean = true,
        notificationPermissionRequired: Boolean = false,
        notificationPermissionGranted: Boolean = true,
    ): MonitoringPermissionState =
        MonitoringPermissionState(
            hasConfiguredDeviceUrl = hasConfiguredDeviceUrl,
            notificationPermissionRequired = notificationPermissionRequired,
            notificationPermissionGranted = notificationPermissionGranted,
        )
}
