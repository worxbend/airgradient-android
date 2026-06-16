package dev.worxbend.airgradient.domain.monitoring

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitoringModeTest {
    @Test
    fun `off mode is disabled`() {
        assertFalse(MonitoringMode.Off.isEnabled)
        assertFalse(MonitoringMode.Off.usesForegroundService)
        assertFalse(MonitoringMode.Off.usesPeriodicWork)
    }

    @Test
    fun `always on mode uses foreground service`() {
        assertTrue(MonitoringMode.AlwaysOnForegroundService.isEnabled)
        assertTrue(MonitoringMode.AlwaysOnForegroundService.usesForegroundService)
        assertFalse(MonitoringMode.AlwaysOnForegroundService.usesPeriodicWork)
    }

    @Test
    fun `battery friendly mode uses periodic work`() {
        assertTrue(MonitoringMode.BatteryFriendlyPeriodic.isEnabled)
        assertFalse(MonitoringMode.BatteryFriendlyPeriodic.usesForegroundService)
        assertTrue(MonitoringMode.BatteryFriendlyPeriodic.usesPeriodicWork)
    }
}
