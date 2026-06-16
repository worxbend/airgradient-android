package dev.worxbend.airgradient.domain.monitoring

import java.time.Duration

data class MonitoringSettings(
    val mode: MonitoringMode,
    val foregroundPollingIntervalSeconds: Int,
    val periodicBackgroundIntervalMinutes: Int,
    val persistentNotificationEnabled: Boolean,
) {
    val foregroundPollingInterval: Duration
        get() = Duration.ofSeconds(foregroundPollingIntervalSeconds.toLong())

    val periodicBackgroundInterval: Duration
        get() = Duration.ofMinutes(periodicBackgroundIntervalMinutes.toLong())

    companion object {
        const val DEFAULT_FOREGROUND_POLLING_INTERVAL_SECONDS: Int = 30
        const val DEFAULT_PERIODIC_BACKGROUND_INTERVAL_MINUTES: Int = 15
        const val MIN_FOREGROUND_POLLING_INTERVAL_SECONDS: Int = 30
        const val MIN_PERIODIC_BACKGROUND_INTERVAL_MINUTES: Int = 15

        val default: MonitoringSettings =
            MonitoringSettings(
                mode = MonitoringMode.Off,
                foregroundPollingIntervalSeconds = DEFAULT_FOREGROUND_POLLING_INTERVAL_SECONDS,
                periodicBackgroundIntervalMinutes = DEFAULT_PERIODIC_BACKGROUND_INTERVAL_MINUTES,
                persistentNotificationEnabled = true,
            )

        fun requireSupportedForegroundInterval(interval: Duration): Int {
            require(!interval.isNegative && interval >= MonitoringPolicy.MIN_FOREGROUND_POLLING_INTERVAL) {
                "Foreground polling interval must be at least $MIN_FOREGROUND_POLLING_INTERVAL_SECONDS seconds."
            }
            return interval.seconds.toInt()
        }

        fun requireSupportedPeriodicInterval(interval: Duration): Int {
            require(!interval.isNegative && interval >= MonitoringPolicy.MIN_PERIODIC_BACKGROUND_INTERVAL) {
                "Periodic background interval must be at least $MIN_PERIODIC_BACKGROUND_INTERVAL_MINUTES minutes."
            }
            return interval.toMinutes().toInt()
        }
    }
}
