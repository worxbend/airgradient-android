package dev.worxbend.airgradient.data.airgradient

import dev.worxbend.airgradient.data.airgradient.dto.AirGradientMeasureDto
import dev.worxbend.airgradient.domain.error.AirGradientError
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class AirGradientRemoteDataSource(
    private val apiFactory: AirGradientApiFactory = AirGradientApiFactory(),
    private val json: Json = Json,
) {
    suspend fun fetchCurrentMeasure(baseUrl: String): RemoteMeasureResult =
        try {
            val response = apiFactory.create(baseUrl).getCurrentMeasures()

            if (!response.isSuccessful) {
                RemoteMeasureResult.Failure(AirGradientError.HttpFailure(response.code()))
            } else {
                val responseBody = response.body()?.string()
                parseBody(responseBody)
            }
        } catch (_: SocketTimeoutException) {
            RemoteMeasureResult.Failure(AirGradientError.Timeout)
        } catch (_: InterruptedIOException) {
            RemoteMeasureResult.Failure(AirGradientError.Timeout)
        } catch (_: UnknownHostException) {
            RemoteMeasureResult.Failure(AirGradientError.DeviceUnreachable)
        } catch (_: ConnectException) {
            RemoteMeasureResult.Failure(AirGradientError.DeviceUnreachable)
        } catch (_: NoRouteToHostException) {
            RemoteMeasureResult.Failure(AirGradientError.DeviceUnreachable)
        } catch (_: IOException) {
            RemoteMeasureResult.Failure(AirGradientError.DeviceUnreachable)
        } catch (_: IllegalArgumentException) {
            RemoteMeasureResult.Failure(AirGradientError.InvalidDeviceUrl)
        }

    private fun parseBody(responseBody: String?): RemoteMeasureResult {
        val payload =
            responseBody
                ?.takeIf(String::isNotBlank)
                ?.let { body -> runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() }

        return payload
            ?.let { AirGradientMeasureDto(payload = it) }
            ?.let(RemoteMeasureResult::Success)
            ?: RemoteMeasureResult.Failure(AirGradientError.MalformedPayload)
    }
}

sealed interface RemoteMeasureResult {
    data class Success(
        val dto: AirGradientMeasureDto,
    ) : RemoteMeasureResult

    data class Failure(
        val error: AirGradientError,
    ) : RemoteMeasureResult
}
