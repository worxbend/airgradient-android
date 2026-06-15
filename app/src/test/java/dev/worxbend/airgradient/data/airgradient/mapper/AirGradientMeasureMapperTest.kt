package dev.worxbend.airgradient.data.airgradient.mapper

import dev.worxbend.airgradient.core.time.ClockProvider
import dev.worxbend.airgradient.data.airgradient.dto.AirGradientMeasureDto
import dev.worxbend.airgradient.domain.model.SensorMeasurementUnit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class AirGradientMeasureMapperTest {
    private val mapper =
        AirGradientMeasureMapper(
            clockProvider = ClockProvider { FIXED_INSTANT },
        )

    @Test
    fun `maps sample local server payload into normalized snapshot`() {
        val snapshot =
            mapper.map(
                dto(
                    """
                    {
                      "wifi": -46,
                      "serialno": "ecda3b1eaaaf",
                      "rco2": 447,
                      "pm01": 3,
                      "pm02": 7,
                      "pm10": 8,
                      "pm003Count": 442,
                      "atmp": 25.87,
                      "atmpCompensated": 24.47,
                      "rhum": 43,
                      "rhumCompensated": 49,
                      "tvocIndex": 100,
                      "tvocRaw": 33051,
                      "noxIndex": 1,
                      "noxRaw": 16307
                    }
                    """.trimIndent(),
                ),
            )

        assertEquals(29, snapshot.aqi)
        assertEquals(447.0, requireNotNull(snapshot.co2), 0.0)
        assertEquals(3.0, requireNotNull(snapshot.pm01), 0.0)
        assertEquals(7.0, requireNotNull(snapshot.pm25), 0.0)
        assertEquals(8.0, requireNotNull(snapshot.pm10), 0.0)
        assertEquals(442.0, requireNotNull(snapshot.pm003Count), 0.0)
        assertEquals(24.47, requireNotNull(snapshot.temperatureCelsius), 0.0)
        assertEquals(49.0, requireNotNull(snapshot.humidityPercent), 0.0)
        assertEquals(100.0, requireNotNull(snapshot.tvoc), 0.0)
        assertEquals(SensorMeasurementUnit.INDEX, snapshot.tvocUnit)
        assertEquals(1.0, requireNotNull(snapshot.nox), 0.0)
        assertEquals(SensorMeasurementUnit.INDEX, snapshot.noxUnit)
        assertEquals(FIXED_INSTANT, snapshot.measuredAt)
    }

    @Test
    fun `explicit AQI overrides PM25 fallback`() {
        val snapshot = mapper.map(dto("""{"aqi": 88, "pm02": 7}"""))

        assertEquals(88, snapshot.aqi)
    }

    @Test
    fun `parses nested payloads and numeric strings`() {
        val snapshot =
            mapper.map(
                dto(
                    """
                    {
                      "device": {
                        "measurements": [
                          {
                            "co2_ppm": "801",
                            "pm2_5": "12.5",
                            "temperature_c": "21.6",
                            "humidity_pct": "45"
                          }
                        ]
                      }
                    }
                    """.trimIndent(),
                ),
            )

        assertEquals(801.0, requireNotNull(snapshot.co2), 0.0)
        assertEquals(12.5, requireNotNull(snapshot.pm25), 0.0)
        assertEquals(21.6, requireNotNull(snapshot.temperatureCelsius), 0.0)
        assertEquals(45.0, requireNotNull(snapshot.humidityPercent), 0.0)
    }

    @Test
    fun `ignores malformed optional values without failing whole payload`() {
        val snapshot =
            mapper.map(
                dto(
                    """
                    {
                      "rco2": "not a number",
                      "pm02": false,
                      "pm10": { "value": 10 },
                      "atmp": [22]
                    }
                    """.trimIndent(),
                ),
            )

        assertNull(snapshot.co2)
        assertNull(snapshot.pm25)
        assertNull(snapshot.pm10)
        assertNull(snapshot.temperatureCelsius)
        assertNull(snapshot.aqi)
    }

    @Test
    fun `treats TVOC and NOx non index aliases as ppb`() {
        val snapshot = mapper.map(dto("""{"tvoc_ppb": 120, "nox_ppb": 5}"""))

        assertEquals(SensorMeasurementUnit.PPB, snapshot.tvocUnit)
        assertEquals(SensorMeasurementUnit.PPB, snapshot.noxUnit)
    }

    private fun dto(json: String): AirGradientMeasureDto =
        AirGradientMeasureDto(
            payload = Json.parseToJsonElement(json).jsonObject,
        )

    private companion object {
        val FIXED_INSTANT: Instant = Instant.parse("2026-06-16T00:00:00Z")
    }
}
