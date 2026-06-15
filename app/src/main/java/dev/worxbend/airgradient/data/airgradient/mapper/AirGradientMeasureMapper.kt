package dev.worxbend.airgradient.data.airgradient.mapper

import dev.worxbend.airgradient.core.time.ClockProvider
import dev.worxbend.airgradient.core.time.SystemClockProvider
import dev.worxbend.airgradient.data.airgradient.dto.AirGradientMeasureDto
import dev.worxbend.airgradient.domain.model.AirMeasureSnapshot
import dev.worxbend.airgradient.domain.model.SensorMeasurementUnit
import dev.worxbend.airgradient.domain.sensors.AqiCalculator
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlin.math.roundToInt

class AirGradientMeasureMapper(
    private val clockProvider: ClockProvider = SystemClockProvider,
) {
    fun map(dto: AirGradientMeasureDto): AirMeasureSnapshot {
        val pm25 = dto.findNumber(PM25_ALIASES)
        val explicitAqi = dto.findNumber(AQI_ALIASES)?.roundToInt()

        return AirMeasureSnapshot(
            aqi = explicitAqi ?: AqiCalculator.calculateFromPm25(pm25),
            pm003Count = dto.findNumber(PM003_COUNT_ALIASES),
            pm01 = dto.findNumber(PM01_ALIASES),
            pm25 = pm25,
            pm10 = dto.findNumber(PM10_ALIASES),
            co2 = dto.findNumber(CO2_ALIASES),
            tvoc = dto.findNumber(TVOC_ALIASES),
            tvocUnit = dto.inferUnit(indexAliases = TVOC_INDEX_ALIASES),
            nox = dto.findNumber(NOX_ALIASES),
            noxUnit = dto.inferUnit(indexAliases = NOX_INDEX_ALIASES),
            temperatureCelsius = dto.findNumber(TEMPERATURE_ALIASES),
            humidityPercent = dto.findNumber(HUMIDITY_ALIASES),
            measuredAt = clockProvider.now(),
        )
    }

    private fun AirGradientMeasureDto.findNumber(aliases: List<String>): Double? =
        payload.findTopLevelNumber(aliases) ?: payload.findRecursiveNumber(aliases)

    private fun AirGradientMeasureDto.inferUnit(indexAliases: List<String>): SensorMeasurementUnit =
        if (payload.containsKeyRecursively(indexAliases)) {
            SensorMeasurementUnit.INDEX
        } else {
            SensorMeasurementUnit.PPB
        }

    private fun JsonObject.findTopLevelNumber(aliases: List<String>): Double? {
        for (alias in aliases.expandedAliases()) {
            val value = this[alias]?.asSupportedNumber()
            if (value != null) {
                return value
            }
        }

        return null
    }

    private fun JsonElement.findRecursiveNumber(aliases: List<String>): Double? =
        when (this) {
            is JsonObject -> {
                findTopLevelNumber(aliases)
                    ?: values.firstNotNullOfOrNull { child -> child.findRecursiveNumber(aliases) }
            }

            is JsonArray -> {
                firstNotNullOfOrNull { child -> child.findRecursiveNumber(aliases) }
            }

            else -> {
                null
            }
        }

    private fun JsonElement.containsKeyRecursively(aliases: List<String>): Boolean =
        when (this) {
            is JsonObject -> {
                aliases.expandedAliases().any(::containsKey) ||
                    values.any { child -> child.containsKeyRecursively(aliases) }
            }

            is JsonArray -> {
                any { child -> child.containsKeyRecursively(aliases) }
            }

            else -> {
                false
            }
        }

    private fun JsonElement.asSupportedNumber(): Double? =
        when (this) {
            JsonNull -> {
                null
            }

            is JsonPrimitive -> {
                if (booleanOrNull != null) {
                    null
                } else {
                    doubleOrNull ?: contentOrNull?.toDoubleOrNull()
                }
            }

            else -> {
                null
            }
        }

    private fun List<String>.expandedAliases(): List<String> = flatMap { alias -> listOf(alias, alias.lowercase()) }

    private companion object {
        val AQI_ALIASES = listOf("aqi", "air_quality_index")
        val CO2_ALIASES = listOf("rco2", "co2", "co2_ppm")
        val PM01_ALIASES = listOf("pm1", "pm1.0", "pm01", "pm_1_0")
        val PM25_ALIASES = listOf("pm02", "pm2_5", "pm25", "pm2.5")
        val PM10_ALIASES = listOf("pm10", "pm10_0")
        val PM003_COUNT_ALIASES = listOf("pm003Count", "pm003_count", "pm0_3_count")
        val TEMPERATURE_ALIASES =
            listOf(
                "atmpCompensated",
                "temperatureCompensated",
                "temperature_compensated",
                "atmp",
                "temperature",
                "temp",
                "temp_c",
                "temperature_c",
                "temperatureC",
            )
        val HUMIDITY_ALIASES =
            listOf(
                "rhumCompensated",
                "humidityCompensated",
                "humidity_compensated",
                "rhum",
                "humidity",
                "hum",
                "relative_humidity",
                "rh",
                "humidity_pct",
            )
        val TVOC_INDEX_ALIASES = listOf("tvocIndex", "tvoc_index")
        val TVOC_ALIASES = listOf("tvoc", "tvoc_ppb", "tvoc_ppm", "voc") + TVOC_INDEX_ALIASES
        val NOX_INDEX_ALIASES = listOf("noxIndex", "nox_index")
        val NOX_ALIASES = listOf("nox", "no2", "nox_ppb") + NOX_INDEX_ALIASES
    }
}
