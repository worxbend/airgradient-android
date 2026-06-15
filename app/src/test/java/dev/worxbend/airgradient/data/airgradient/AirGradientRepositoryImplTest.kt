package dev.worxbend.airgradient.data.airgradient

import dev.worxbend.airgradient.core.time.ClockProvider
import dev.worxbend.airgradient.data.airgradient.mapper.AirGradientMeasureMapper
import dev.worxbend.airgradient.domain.error.AirGradientError
import dev.worxbend.airgradient.domain.repository.AirGradientFetchResult
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

class AirGradientRepositoryImplTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `returns missing URL failure when server URL is blank`() =
        runTest {
            val repository = repository()

            val result = repository.fetchCurrentMeasurement(" ")

            assertEquals(
                AirGradientFetchResult.Failure(AirGradientError.MissingDeviceUrl),
                result,
            )
        }

    @Test
    fun `normalizes URL fetches remote payload and maps snapshot`() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .code(200)
                    .body("""{"pm02": 7, "rco2": 447}""")
                    .build(),
            )
            val repository = repository()

            val result = repository.fetchCurrentMeasurement(server.url("/device/path?ignored=true").toString())

            assertTrue(result is AirGradientFetchResult.Success)
            val snapshot = (result as AirGradientFetchResult.Success).snapshot
            assertEquals(29, snapshot.aqi)
            assertEquals(7.0, requireNotNull(snapshot.pm25), 0.0)
            assertEquals(447.0, requireNotNull(snapshot.co2), 0.0)
            assertEquals(FIXED_INSTANT, snapshot.measuredAt)
            assertEquals("/measures/current", server.takeRequest().target)
        }

    private fun repository(): AirGradientRepositoryImpl =
        AirGradientRepositoryImpl(
            remoteDataSource = AirGradientRemoteDataSource(),
            mapper = AirGradientMeasureMapper(clockProvider = ClockProvider { FIXED_INSTANT }),
        )

    private companion object {
        val FIXED_INSTANT: Instant = Instant.parse("2026-06-16T00:00:00Z")
    }
}
