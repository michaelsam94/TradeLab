package com.michael.tradelab.data.repo

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** Bump when the disclaimer text changes to re-gate users. */
    val currentDisclaimerVersion = 1

    val disclaimerAccepted: Flow<Boolean> = context.dataStore.data
        .map { (it[KEY_DISCLAIMER_VERSION] ?: 0) >= currentDisclaimerVersion }

    suspend fun acceptDisclaimer() {
        context.dataStore.edit { it[KEY_DISCLAIMER_VERSION] = currentDisclaimerVersion }
    }

    val notificationDenialCount: Flow<Int> = context.dataStore.data.map { it[KEY_NOTIF_DENIALS] ?: 0 }

    suspend fun recordNotificationDenial() {
        context.dataStore.edit { it[KEY_NOTIF_DENIALS] = (it[KEY_NOTIF_DENIALS] ?: 0) + 1 }
    }

    val notificationRationaleSeen: Flow<Boolean> = context.dataStore.data.map { it[KEY_NOTIF_RATIONALE] ?: false }

    suspend fun markNotificationRationaleSeen() {
        context.dataStore.edit { it[KEY_NOTIF_RATIONALE] = true }
    }

    suspend fun eraseAll() {
        context.dataStore.edit { it.clear() }
    }

    companion object {
        private val KEY_DISCLAIMER_VERSION = intPreferencesKey("disclaimerAcceptedVersion")
        private val KEY_NOTIF_DENIALS = intPreferencesKey("notificationDenialCount")
        private val KEY_NOTIF_RATIONALE = booleanPreferencesKey("notificationRationaleSeen")
    }
}
