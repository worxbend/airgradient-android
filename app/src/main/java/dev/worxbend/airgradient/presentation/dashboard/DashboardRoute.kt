package dev.worxbend.airgradient.presentation.dashboard

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun DashboardRoute(
    viewModel: DashboardViewModel,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val state = viewModel.uiState.collectAsStateWithLifecycle()
    val monitoringPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                viewModel.startAlwaysOnMonitoring()
            } else {
                viewModel.onMonitoringPermissionDenied()
            }
        }

    DashboardScreen(
        state = state.value,
        onRefresh = viewModel::refresh,
        onOpenSettings = onOpenSettings,
        onConfigureDevice = onOpenSettings,
        onStartMonitoring = {
            if (hasNotificationPermission(context)) {
                viewModel.startAlwaysOnMonitoring()
            } else {
                monitoringPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        },
        onStopMonitoring = viewModel::stopMonitoring,
    )
}

private fun hasNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
