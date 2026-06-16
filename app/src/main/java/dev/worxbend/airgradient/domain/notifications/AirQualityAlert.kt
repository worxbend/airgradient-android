package dev.worxbend.airgradient.domain.notifications

data class AirQualityAlert(
    val kind: AirQualityAlertKind,
    val severity: AirQualityAlertSeverity,
    val title: String,
    val body: String,
)

enum class AirQualityAlertKind(
    val notificationId: Int,
) {
    CO2(notificationId = 1001),
    AQI(notificationId = 1002),
    PM25(notificationId = 1003),
    TVOC(notificationId = 1004),
    NOX(notificationId = 1005),
    HUMIDITY_LOW(notificationId = 1006),
    HUMIDITY_HIGH(notificationId = 1007),
    DEVICE_OFFLINE(notificationId = 1008),
}

enum class AirQualityAlertSeverity(
    val rank: Int,
) {
    NOTICE(rank = 1),
    WARNING(rank = 2),
    CRITICAL(rank = 3),
}
