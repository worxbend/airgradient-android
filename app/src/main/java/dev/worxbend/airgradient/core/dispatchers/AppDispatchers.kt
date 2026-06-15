package dev.worxbend.airgradient.core.dispatchers

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

data class AppDispatchers(
    val io: CoroutineDispatcher,
    val default: CoroutineDispatcher,
    val main: CoroutineDispatcher,
) {
    companion object {
        val production: AppDispatchers =
            AppDispatchers(
                io = Dispatchers.IO,
                default = Dispatchers.Default,
                main = Dispatchers.Main.immediate,
            )
    }
}
