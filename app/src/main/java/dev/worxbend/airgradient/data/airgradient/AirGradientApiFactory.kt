package dev.worxbend.airgradient.data.airgradient

import dev.worxbend.airgradient.core.network.NetworkModule
import okhttp3.OkHttpClient
import retrofit2.Retrofit

class AirGradientApiFactory(
    private val okHttpClient: OkHttpClient = NetworkModule.createOkHttpClient(),
) {
    fun create(baseUrl: String): AirGradientApi =
        Retrofit
            .Builder()
            .baseUrl(baseUrl.withTrailingSlash())
            .client(okHttpClient)
            .build()
            .create(AirGradientApi::class.java)

    private fun String.withTrailingSlash(): String = if (endsWith("/")) this else "$this/"
}
