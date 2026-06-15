package dev.worxbend.airgradient.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

val Context.airGradientSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "airgradient_settings",
)
