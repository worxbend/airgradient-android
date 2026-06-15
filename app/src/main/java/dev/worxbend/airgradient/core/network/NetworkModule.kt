package dev.worxbend.airgradient.core.network

import okhttp3.OkHttpClient
import java.time.Duration

object NetworkModule {
    fun createOkHttpClient(): OkHttpClient =
        OkHttpClient
            .Builder()
            .callTimeout(REQUEST_TIMEOUT)
            .connectTimeout(REQUEST_TIMEOUT)
            .readTimeout(REQUEST_TIMEOUT)
            .build()

    private val REQUEST_TIMEOUT: Duration = Duration.ofSeconds(8)
}
