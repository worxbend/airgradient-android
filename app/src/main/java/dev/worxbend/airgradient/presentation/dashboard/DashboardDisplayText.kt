package dev.worxbend.airgradient.presentation.dashboard

import dev.worxbend.airgradient.domain.model.SensorStatus

internal val SensorStatus.label: String
    get() =
        when (this) {
            SensorStatus.GOOD -> "Good air"
            SensorStatus.MODERATE -> "Moderate"
            SensorStatus.WARNING -> "Elevated"
            SensorStatus.CRITICAL -> "Needs attention"
            SensorStatus.UNKNOWN -> "Unknown"
        }
