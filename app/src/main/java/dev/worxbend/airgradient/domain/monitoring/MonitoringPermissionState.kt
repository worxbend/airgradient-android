package dev.worxbend.airgradient.domain.monitoring

data class MonitoringPermissionState(
    val hasConfiguredDeviceUrl: Boolean,
    val notificationPermissionRequired: Boolean,
    val notificationPermissionGranted: Boolean,
) {
    val canPostNotifications: Boolean
        get() = !notificationPermissionRequired || notificationPermissionGranted

    companion object {
        val Granted: MonitoringPermissionState =
            MonitoringPermissionState(
                hasConfiguredDeviceUrl = true,
                notificationPermissionRequired = false,
                notificationPermissionGranted = true,
            )
    }
}
