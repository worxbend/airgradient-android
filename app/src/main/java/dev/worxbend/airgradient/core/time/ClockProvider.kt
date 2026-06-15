package dev.worxbend.airgradient.core.time

import java.time.Instant

fun interface ClockProvider {
    fun now(): Instant
}

object SystemClockProvider : ClockProvider {
    override fun now(): Instant = Instant.now()
}
