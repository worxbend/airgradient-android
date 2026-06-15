package dev.worxbend.airgradient.domain.sensors

import dev.worxbend.airgradient.domain.model.AirMeasureSnapshot
import dev.worxbend.airgradient.domain.model.SensorStatus

object SensorThresholds {
    fun classifyCo2(ppm: Double?): SensorStatus =
        when {
            ppm == null -> SensorStatus.UNKNOWN
            ppm < CO2_MODERATE_PPM -> SensorStatus.GOOD
            ppm < CO2_WARNING_PPM -> SensorStatus.MODERATE
            ppm < CO2_CRITICAL_PPM -> SensorStatus.WARNING
            else -> SensorStatus.CRITICAL
        }

    fun classifyPm25(microgramsPerCubicMeter: Double?): SensorStatus =
        when {
            microgramsPerCubicMeter == null -> SensorStatus.UNKNOWN
            microgramsPerCubicMeter < PM25_MODERATE -> SensorStatus.GOOD
            microgramsPerCubicMeter < PM25_WARNING -> SensorStatus.MODERATE
            microgramsPerCubicMeter < PM25_CRITICAL -> SensorStatus.WARNING
            else -> SensorStatus.CRITICAL
        }

    fun classifyTvoc(value: Double?): SensorStatus =
        when {
            value == null -> SensorStatus.UNKNOWN
            value < TVOC_MODERATE -> SensorStatus.GOOD
            value < TVOC_WARNING -> SensorStatus.MODERATE
            value < TVOC_CRITICAL -> SensorStatus.WARNING
            else -> SensorStatus.CRITICAL
        }

    fun classifyNox(value: Double?): SensorStatus =
        when {
            value == null -> SensorStatus.UNKNOWN
            value < NOX_MODERATE -> SensorStatus.GOOD
            value < NOX_WARNING -> SensorStatus.MODERATE
            value < NOX_CRITICAL -> SensorStatus.WARNING
            else -> SensorStatus.CRITICAL
        }

    fun classifyAqi(aqi: Int?): SensorStatus =
        when {
            aqi == null -> SensorStatus.UNKNOWN
            aqi <= AQI_GOOD_MAX -> SensorStatus.GOOD
            aqi <= AQI_MODERATE_MAX -> SensorStatus.MODERATE
            aqi <= AQI_WARNING_MAX -> SensorStatus.WARNING
            else -> SensorStatus.CRITICAL
        }

    fun overallStatus(snapshot: AirMeasureSnapshot): SensorStatus =
        listOf(
            classifyAqi(snapshot.aqi),
            classifyPm25(snapshot.pm25),
            classifyCo2(snapshot.co2),
            classifyTvoc(snapshot.tvoc),
            classifyNox(snapshot.nox),
        ).maxByOrNull(SensorStatus::severity) ?: SensorStatus.UNKNOWN

    // Thresholds copied from the reference implementation findings in PLAN.md.
    private const val CO2_MODERATE_PPM = 800.0
    private const val CO2_WARNING_PPM = 1_200.0
    private const val CO2_CRITICAL_PPM = 2_000.0
    private const val PM25_MODERATE = 12.0
    private const val PM25_WARNING = 35.0
    private const val PM25_CRITICAL = 55.0
    private const val TVOC_MODERATE = 65.0
    private const val TVOC_WARNING = 220.0
    private const val TVOC_CRITICAL = 660.0
    private const val NOX_MODERATE = 20.0
    private const val NOX_WARNING = 50.0
    private const val NOX_CRITICAL = 150.0
    private const val AQI_GOOD_MAX = 50
    private const val AQI_MODERATE_MAX = 100
    private const val AQI_WARNING_MAX = 150
}
