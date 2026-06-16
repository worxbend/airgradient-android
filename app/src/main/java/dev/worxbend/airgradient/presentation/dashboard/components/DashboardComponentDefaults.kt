package dev.worxbend.airgradient.presentation.dashboard.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.worxbend.airgradient.domain.model.SensorStatus

internal object DashboardComponentDefaults {
    val cardShape = RoundedCornerShape(8.dp)
    val compactWidth = 520.dp
    const val COMPACT_GRID_COLUMNS = 2
    const val EXPANDED_GRID_COLUMNS = 4

    val good = Color(0xFF1B8A5A)
    val moderate = Color(0xFFD0A018)
    val warning = Color(0xFFE16D25)
    val critical = Color(0xFFBA2D35)
}

@Composable
internal fun SensorStatus.statusColor(): Color =
    when (this) {
        SensorStatus.GOOD -> DashboardComponentDefaults.good
        SensorStatus.MODERATE -> DashboardComponentDefaults.moderate
        SensorStatus.WARNING -> DashboardComponentDefaults.warning
        SensorStatus.CRITICAL -> DashboardComponentDefaults.critical
        SensorStatus.UNKNOWN -> MaterialTheme.colorScheme.outline
    }
