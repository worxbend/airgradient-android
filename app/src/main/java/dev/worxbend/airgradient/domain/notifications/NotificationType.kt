package dev.worxbend.airgradient.domain.notifications

enum class NotificationType {
    AirQualityDegraded,
    AirQualityCritical,
    AirQualityPersistent,
    AirQualityRecovered,
    DeviceUnreachable,
    StaleData,
}
