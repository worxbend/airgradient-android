package dev.worxbend.airgradient.data.airgradient

import dev.worxbend.airgradient.domain.error.AirGradientError
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.ServerSocket
import java.time.Duration

class AirGradientRemoteDataSourceTest {
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
    fun `successful request fetches measures current exactly once`() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .code(200)
                    .body("""{"pm02": 7}""")
                    .build(),
            )
            val dataSource = dataSource()

            val result = dataSource.fetchCurrentMeasure(server.url("/").toString().trimEnd('/'))

            assertTrue(result is RemoteMeasureResult.Success)
            assertEquals("/measures/current", server.takeRequest().target)
        }

    @Test
    fun `http error maps to typed failure`() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .code(503)
                    .body("Service unavailable")
                    .build(),
            )
            val dataSource = dataSource()

            val result = dataSource.fetchCurrentMeasure(server.url("/").toString().trimEnd('/'))

            assertEquals(
                RemoteMeasureResult.Failure(AirGradientError.HttpFailure(statusCode = 503)),
                result,
            )
        }

    @Test
    fun `malformed JSON maps to malformed payload failure`() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .code(200)
                    .body("{not json")
                    .build(),
            )
            val dataSource = dataSource()

            val result = dataSource.fetchCurrentMeasure(server.url("/").toString().trimEnd('/'))

            assertEquals(
                RemoteMeasureResult.Failure(AirGradientError.MalformedPayload),
                result,
            )
        }

    @Test
    fun `slow response maps to timeout failure`() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .code(200)
                    .headersDelay(250, java.util.concurrent.TimeUnit.MILLISECONDS)
                    .body("""{"pm02": 7}""")
                    .build(),
            )
            val dataSource =
                dataSource(
                    okHttpClient =
                        OkHttpClient
                            .Builder()
                            .callTimeout(Duration.ofMillis(100))
                            .connectTimeout(Duration.ofMillis(100))
                            .readTimeout(Duration.ofMillis(100))
                            .build(),
                )

            val result = dataSource.fetchCurrentMeasure(server.url("/").toString().trimEnd('/'))

            assertEquals(
                RemoteMeasureResult.Failure(AirGradientError.Timeout),
                result,
            )
        }

    @Test
    fun `closed local port maps to unreachable failure`() =
        runTest {
            val closedPort = reserveClosedPort()
            val dataSource = dataSource()

            val result = dataSource.fetchCurrentMeasure("http://127.0.0.1:$closedPort")

            assertEquals(
                RemoteMeasureResult.Failure(AirGradientError.DeviceUnreachable),
                result,
            )
        }

    private fun dataSource(okHttpClient: OkHttpClient = OkHttpClient()): AirGradientRemoteDataSource =
        AirGradientRemoteDataSource(
            apiFactory = AirGradientApiFactory(okHttpClient = okHttpClient),
        )

    private fun reserveClosedPort(): Int =
        ServerSocket(0).use { socket ->
            socket.localPort
        }
}
