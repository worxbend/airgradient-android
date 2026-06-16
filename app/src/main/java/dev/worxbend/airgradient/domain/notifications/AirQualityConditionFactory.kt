package dev.worxbend.airgradient.domain.notifications

import dev.worxbend.airgradient.domain.model.AirMeasureSnapshot
import dev.worxbend.airgradient.domain.model.SensorMetric
import dev.worxbend.airgradient.domain.model.SensorStatus
import dev.worxbend.airgradient.domain.sensors.SensorMetricFactory
import dev.worxbend.airgradient.domain.sensors.SensorThresholds
import java.time.Instant
import java.util.Locale

object AirQualityConditionFactory {
    fun fromSnapshot(snapshot: AirMeasureSnapshot): AirQualityCondition {
        val metrics = SensorMetricFactory.createMetrics(current = snapshot, previous = null)
        val dominantMetric =
            metrics
                .filter { metric -> metric.status.severity >= SensorStatus.WARNING.severity }
                .maxWithOrNull(compareBy<SensorMetric> { it.status.severity })

        return AirQualityCondition(
            status = SensorThresholds.overallStatus(snapshot),
            dominantMetricKey = dominantMetric?.kind?.name?.lowercase(Locale.US),
            dominantMetricLabel = dominantMetric?.displayName,
            observedAt = snapshot.measuredAt,
        )
    }
}

data class AirQualityCondition(
    val status: SensorStatus,
    val dominantMetricKey: String?,
    val dominantMetricLabel: String?,
    val observedAt: Instant,
)
