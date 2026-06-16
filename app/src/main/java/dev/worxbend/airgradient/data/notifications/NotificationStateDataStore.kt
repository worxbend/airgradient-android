package dev.worxbend.airgradient.data.notifications

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

val Context.airGradientNotificationStateDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "airgradient_notification_state",
)
