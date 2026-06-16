package dev.worxbend.airgradient.data.monitoring

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

val Context.airGradientMonitoringRuntimeStateDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "airgradient_monitoring_runtime_state",
)
