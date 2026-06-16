package dev.worxbend.airgradient.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import dev.worxbend.airgradient.app.AppGraph
import dev.worxbend.airgradient.presentation.dashboard.DashboardRoute
import dev.worxbend.airgradient.presentation.dashboard.DashboardViewModel
import dev.worxbend.airgradient.presentation.settings.SettingsRoute
import dev.worxbend.airgradient.presentation.settings.SettingsViewModel

@Composable
fun AppNavGraph(
    navController: NavHostController,
    appGraph: AppGraph,
) {
    NavHost(
        navController = navController,
        startDestination = AppDestination.Dashboard.route,
    ) {
        composable(AppDestination.Dashboard.route) {
            val dashboardViewModel =
                viewModel<DashboardViewModel>(
                    factory = appGraph.dashboardViewModelFactory(),
                )

            DashboardRoute(
                viewModel = dashboardViewModel,
                onOpenSettings = { navController.navigate(AppDestination.Settings.route) },
            )
        }
        composable(AppDestination.Settings.route) {
            val settingsViewModel =
                viewModel<SettingsViewModel>(
                    factory = appGraph.settingsViewModelFactory(),
                )

            SettingsRoute(
                viewModel = settingsViewModel,
                onNavigateBack = { navController.popBackStack() },
            )
        }
    }
}
