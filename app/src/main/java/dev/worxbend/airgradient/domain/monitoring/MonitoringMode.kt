package dev.worxbend.airgradient.domain.monitoring

enum class MonitoringMode {
    Off,
    AlwaysOnForegroundService,
    BatteryFriendlyPeriodic,
    ;

    val isEnabled: Boolean
        get() = this != Off

    val usesForegroundService: Boolean
        get() = this == AlwaysOnForegroundService

    val usesPeriodicWork: Boolean
        get() = this == BatteryFriendlyPeriodic
}
