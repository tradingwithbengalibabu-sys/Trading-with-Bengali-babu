package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "twbb_session_prefs")

class SessionPreferences(private val context: Context) {
    private object Keys {
        val LOGGED_IN_USER_ID = longPreferencesKey("logged_in_user_id")
        val IS_DARK_MODE = booleanPreferencesKey("is_dark_mode")
    }

    val loggedInUserId: Flow<Long?> = context.dataStore.data.map { prefs ->
        val id = prefs[Keys.LOGGED_IN_USER_ID]
        if (id != null && id > 0L) id else null
    }

    val isDarkMode: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.IS_DARK_MODE] ?: true
    }

    suspend fun setLoggedInUserId(userId: Long?) {
        context.dataStore.edit { prefs ->
            if (userId != null && userId > 0L) {
                prefs[Keys.LOGGED_IN_USER_ID] = userId
            } else {
                prefs.remove(Keys.LOGGED_IN_USER_ID)
            }
        }
    }

    suspend fun setDarkMode(isDark: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.IS_DARK_MODE] = isDark
        }
    }
}
