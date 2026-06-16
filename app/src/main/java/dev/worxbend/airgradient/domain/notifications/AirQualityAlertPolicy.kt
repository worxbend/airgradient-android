package dev.worxbend.airgradient.domain.notifications

import dev.worxbend.airgradient.domain.error.AirGradientError
import dev.worxbend.airgradient.domain.model.AirMeasureSnapshot
import java.time.Duration
import java.time.Instant
import java.util.Locale
import kotlin.math.abs

class AirQualityAlertPolicy(
    private val cooldown: Duration = DEFAULT_COOLDOWN,
) {
    private val alertStates = mutableMapOf<AirQualityAlertKind, AlertState>()
    private var consecutiveFetchFailures: Int = 0

    fun evaluateSnapshot(
        snapshot: AirMeasureSnapshot,
        now: Instant,
    ): List<AirQualityAlert> {
        consecutiveFetchFailures = 0
        clearKind(AirQualityAlertKind.DEVICE_OFFLINE)

        return listOfNotNull(
            evaluateKind(
                kind = AirQualityAlertKind.CO2,
                severity = co2Severity(snapshot.co2),
                now = now,
                title = "High CO2 detected",
                body = snapshot.co2?.let { "CO2 is ${it.formatValue()} ppm. Ventilate the room." },
            ),
            evaluateKind(
                kind = AirQualityAlertKind.AQI,
                severity = aqiSeverity(snapshot.aqi),
                now = now,
                title = "Air quality needs attention",
                body = snapshot.aqi?.let { "AQI is $it. Reduce indoor pollutants if possible." },
            ),
            evaluateKind(
                kind = AirQualityAlertKind.PM25,
                severity = pm25Severity(snapshot.pm25),
                now = now,
                title = "Fine particles are elevated",
                body = snapshot.pm25?.let { "PM2.5 is ${it.formatValue()} ug/m3. Consider ventilation or filtration." },
            ),
            evaluateKind(
                kind = AirQualityAlertKind.TVOC,
                severity = tvocSeverity(snapshot.tvoc),
                now = now,
                title = "VOC levels are elevated",
                body = snapshot.tvoc?.let { "TVOC is ${it.formatValue()}. Check possible indoor sources." },
            ),
            evaluateKind(
                kind = AirQualityAlertKind.NOX,
                severity = noxSeverity(snapshot.nox),
                now = now,
                title = "NOx levels are elevated",
                body = snapshot.nox?.let { "NOx is ${it.formatValue()}. Check combustion or outdoor-air sources." },
            ),
            evaluateKind(
                kind = AirQualityAlertKind.HUMIDITY_LOW,
                severity = humidityLowSeverity(snapshot.humidityPercent),
                now = now,
                title = "Humidity is low",
                body = snapshot.humidityPercent?.let { "Humidity is ${it.formatValue()}%. Indoor air may feel dry." },
            ),
            evaluateKind(
                kind = AirQualityAlertKind.HUMIDITY_HIGH,
                severity = humidityHighSeverity(snapshot.humidityPercent),
                now = now,
                title = "Humidity is high",
                body =
                    snapshot.humidityPercent?.let {
                        "Humidity is ${it.formatValue()}%. Watch for condensation or mold risk."
                    },
            ),
        )
    }

    fun evaluateFetchFailure(
        error: AirGradientError,
        now: Instant,
    ): List<AirQualityAlert> {
        consecutiveFetchFailures += 1

        val alert =
            evaluateKind(
                kind = AirQualityAlertKind.DEVICE_OFFLINE,
                severity = AirQualityAlertSeverity.WARNING,
                now = now,
                title = "AirGradient device is offline",
                body =
                    "The device has failed $consecutiveFetchFailures refreshes. " +
                        "Last error: ${error.alertLabel()}.",
                requiredConsecutiveReadings = REQUIRED_CONSECUTIVE_FETCH_FAILURES,
            )

        return listOfNotNull(alert)
    }

    fun clear() {
        consecutiveFetchFailures = 0
        alertStates.clear()
    }

    private fun evaluateKind(
        kind: AirQualityAlertKind,
        severity: AirQualityAlertSeverity?,
        now: Instant,
        title: String,
        body: String?,
        requiredConsecutiveReadings: Int = REQUIRED_CONSECUTIVE_SENSOR_READINGS,
    ): AirQualityAlert? =
        if (severity == null || body == null) {
            clearKind(kind)
            null
        } else {
            val currentState = alertStates.getOrPut(kind) { AlertState() }
            val updatedState = currentState.copy(consecutiveReadings = currentState.consecutiveReadings + 1)
            alertStates[kind] = updatedState

            if (updatedState.shouldNotify(severity, now, requiredConsecutiveReadings)) {
                alertStates[kind] =
                    updatedState.copy(
                        activeSeverity = severity,
                        lastSentAt = now,
                    )
                AirQualityAlert(
                    kind = kind,
                    severity = severity,
                    title = title,
                    body = body,
                )
            } else {
                null
            }
        }

    private fun clearKind(kind: AirQualityAlertKind) {
        alertStates.remove(kind)
    }

    private fun AirQualityAlertSeverity.isEscalationFrom(previousSeverity: AirQualityAlertSeverity?): Boolean =
        previousSeverity != null && rank > previousSeverity.rank

    private fun AlertState.shouldNotify(
        severity: AirQualityAlertSeverity,
        now: Instant,
        requiredConsecutiveReadings: Int,
    ): Boolean =
        consecutiveReadings >= requiredConsecutiveReadings &&
            (
                lastSentAt == null ||
                    severity.isEscalationFrom(activeSeverity) ||
                    Duration.between(lastSentAt, now) >= cooldown
            )

    private data class AlertState(
        val consecutiveReadings: Int = 0,
        val activeSeverity: AirQualityAlertSeverity? = null,
        val lastSentAt: Instant? = null,
    )

    private companion object {
        val DEFAULT_COOLDOWN: Duration = Duration.ofMinutes(20)
        const val REQUIRED_CONSECUTIVE_SENSOR_READINGS = 2
        const val REQUIRED_CONSECUTIVE_FETCH_FAILURES = 3

        fun co2Severity(value: Double?): AirQualityAlertSeverity? =
            when {
                value == null -> null
                value > 2_000.0 -> AirQualityAlertSeverity.CRITICAL
                value > 1_200.0 -> AirQualityAlertSeverity.WARNING
                value > 800.0 -> AirQualityAlertSeverity.NOTICE
                else -> null
            }

        fun aqiSeverity(value: Int?): AirQualityAlertSeverity? =
            when {
                value == null -> null
                value > 200 -> AirQualityAlertSeverity.CRITICAL
                value > 150 -> AirQualityAlertSeverity.WARNING
                value > 100 -> AirQualityAlertSeverity.NOTICE
                else -> null
            }

        fun pm25Severity(value: Double?): AirQualityAlertSeverity? =
            when {
                value == null -> null
                value > 150.0 -> AirQualityAlertSeverity.CRITICAL
                value > 55.0 -> AirQualityAlertSeverity.WARNING
                value > 35.0 -> AirQualityAlertSeverity.NOTICE
                else -> null
            }

        fun tvocSeverity(value: Double?): AirQualityAlertSeverity? =
            when {
                value == null -> null
                value > 660.0 -> AirQualityAlertSeverity.CRITICAL
                value > 220.0 -> AirQualityAlertSeverity.WARNING
                else -> null
            }

        fun noxSeverity(value: Double?): AirQualityAlertSeverity? =
            when {
                value == null -> null
                value > 150.0 -> AirQualityAlertSeverity.CRITICAL
                value > 50.0 -> AirQualityAlertSeverity.WARNING
                else -> null
            }

        fun humidityLowSeverity(value: Double?): AirQualityAlertSeverity? =
            when {
                value == null -> null
                value < 30.0 -> AirQualityAlertSeverity.NOTICE
                else -> null
            }

        fun humidityHighSeverity(value: Double?): AirQualityAlertSeverity? =
            when {
                value == null -> null
                value > 75.0 -> AirQualityAlertSeverity.CRITICAL
                value > 65.0 -> AirQualityAlertSeverity.NOTICE
                else -> null
            }

        fun Double.formatValue(): String =
            if (abs(this % 1.0) < 0.05 || abs(this) >= 100.0) {
                "%.0f".format(Locale.US, this)
            } else {
                "%.1f".format(Locale.US, this)
            }

        fun AirGradientError.alertLabel(): String =
            when (this) {
                AirGradientError.DeviceUnreachable -> "device unreachable"
                AirGradientError.InvalidDeviceUrl -> "invalid URL"
                AirGradientError.MalformedPayload -> "malformed response"
                AirGradientError.MissingDeviceUrl -> "missing device URL"
                AirGradientError.Timeout -> "timeout"
                AirGradientError.Unknown -> "unknown error"
                is AirGradientError.HttpFailure -> "HTTP $statusCode"
            }
    }
}
