package dev.worxbend.airgradient.domain.model

data class AppSettings(
    val serverUrl: String?,
    val refreshIntervalSeconds: Int,
    val notificationsEnabled: Boolean,
    val themeMode: AppThemeMode,
) {
    companion object {
        const val DEFAULT_REFRESH_INTERVAL_SECONDS: Int = 30
        const val MIN_REFRESH_INTERVAL_SECONDS: Int = 5
        const val MAX_REFRESH_INTERVAL_SECONDS: Int = 3_600

        val default: AppSettings =
            AppSettings(
                serverUrl = null,
                refreshIntervalSeconds = DEFAULT_REFRESH_INTERVAL_SECONDS,
                notificationsEnabled = false,
                themeMode = AppThemeMode.SYSTEM,
            )

        fun clampRefreshInterval(seconds: Int): Int =
            seconds.coerceIn(
                minimumValue = MIN_REFRESH_INTERVAL_SECONDS,
                maximumValue = MAX_REFRESH_INTERVAL_SECONDS,
            )
    }
}
