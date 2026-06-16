package dev.worxbend.airgradient.core.network

import okhttp3.OkHttpClient
import java.time.Duration

object NetworkModule {
    fun createOkHttpClient(): OkHttpClient =
        OkHttpClient
            .Builder()
            .callTimeout(CALL_TIMEOUT)
            .connectTimeout(CONNECT_TIMEOUT)
            .readTimeout(READ_TIMEOUT)
            .retryOnConnectionFailure(false)
            .build()

    private val CALL_TIMEOUT: Duration = Duration.ofSeconds(5)
    private val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(3)
    private val READ_TIMEOUT: Duration = Duration.ofSeconds(3)
}
