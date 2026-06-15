package dev.worxbend.airgradient.data.airgradient

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET

interface AirGradientApi {
    @GET("measures/current")
    suspend fun getCurrentMeasures(): Response<ResponseBody>
}
