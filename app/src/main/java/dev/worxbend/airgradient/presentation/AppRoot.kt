package dev.worxbend.airgradient.presentation

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import dev.worxbend.airgradient.app.AppGraph
import dev.worxbend.airgradient.domain.model.AppSettings
import dev.worxbend.airgradient.domain.model.AppThemeMode
import dev.worxbend.airgradient.presentation.navigation.AppNavGraph
import dev.worxbend.airgradient.presentation.theme.AirGradientTheme

@Composable
fun AppRoot(appGraph: AppGraph) {
    val settings = appGraph.settingsRepository.settings.collectAsStateWithLifecycle(AppSettings.default)

    AirGradientTheme(
        darkTheme = settings.value.themeMode.useDarkTheme(),
    ) {
        AppNavGraph(
            navController = rememberNavController(),
            appGraph = appGraph,
        )
    }
}

@Composable
private fun AppThemeMode.useDarkTheme(): Boolean =
    when (this) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }
