package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.TradingCurrency
import com.example.domain.TradingAnalytics
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

data class CustomTradingSession(
    val id: String,
    val name: String,
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int
) {
    val windowLabel: String
        get() = "${formatMinute(startMinuteOfDay)}–${formatMinute(endMinuteOfDay)}"

    fun containsMinute(minuteOfDay: Int): Boolean {
        val m = minuteOfDay.coerceIn(0, 1439)
        return if (startMinuteOfDay <= endMinuteOfDay) {
            m in startMinuteOfDay..endMinuteOfDay
        } else {
            // Overnight window e.g. 22:00 to 04:00
            m >= startMinuteOfDay || m <= endMinuteOfDay
        }
    }

    companion object {
        fun formatMinute(minuteOfDay: Int): String {
            val safe = minuteOfDay.coerceIn(0, 1439)
            return String.format(Locale.US, "%02d:%02d", safe / 60, safe % 60)
        }

        val DEFAULT_SESSIONS = listOf(
            CustomTradingSession("india_open", "India Morning", 9 * 60 + 15, 11 * 60 + 29),
            CustomTradingSession("india_mid", "India Mid-Day", 11 * 60 + 30, 13 * 60 + 59),
            CustomTradingSession("india_close", "India Closing", 14 * 60, 15 * 60 + 30),
            CustomTradingSession("london", "London Session", 15 * 60 + 31, 17 * 60 + 59),
            CustomTradingSession("new_york", "New York Session", 18 * 60, 23 * 60 + 30),
            CustomTradingSession("asian", "Asian / Early", 5 * 60 + 30, 9 * 60 + 14)
        )

        val SUPPORTED_TIMEZONES = listOf(
            "Asia/Kolkata" to "IST (Asia/Kolkata)",
            "UTC" to "UTC (Coordinated)",
            "Europe/London" to "London (GMT/BST)",
            "America/New_York" to "New York (EST/EDT)",
            "Asia/Dubai" to "Dubai (GST)",
            "Asia/Singapore" to "Singapore (SGT)",
            "Asia/Tokyo" to "Tokyo (JST)"
        )
    }
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "twbb_session_prefs")

class SessionPreferences(private val context: Context) {
    private object Keys {
        val IS_DARK_MODE = booleanPreferencesKey("is_dark_mode")
        val DISPLAY_CURRENCY = stringPreferencesKey("display_currency")
        val CACHED_USD_TO_INR = doublePreferencesKey("cached_usd_to_inr")
        val CACHED_RATE_UPDATED_AT = longPreferencesKey("cached_rate_updated_at")
        val SELECTED_TIMEZONE_ID = stringPreferencesKey("selected_timezone_id")
        val CUSTOM_TRADING_SESSIONS = stringPreferencesKey("custom_trading_sessions")
    }

    val isDarkMode: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.IS_DARK_MODE] ?: true
    }

    val displayCurrency: Flow<TradingCurrency> = context.dataStore.data.map { prefs ->
        val raw = prefs[Keys.DISPLAY_CURRENCY]
        TradingCurrency.entries.firstOrNull { it.name == raw } ?: TradingCurrency.INR
    }

    val cachedUsdToInrRate: Flow<Double> = context.dataStore.data.map { prefs ->
        val rate = prefs[Keys.CACHED_USD_TO_INR] ?: TradingAnalytics.DEFAULT_USD_TO_INR_RATE
        if (rate in 40.0..200.0) rate else TradingAnalytics.DEFAULT_USD_TO_INR_RATE
    }

    val cachedRateUpdatedAtMillis: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[Keys.CACHED_RATE_UPDATED_AT] ?: 0L
    }

    val selectedTimezoneId: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.SELECTED_TIMEZONE_ID]?.takeIf { it.isNotBlank() } ?: "Asia/Kolkata"
    }

    val customTradingSessions: Flow<List<CustomTradingSession>> = context.dataStore.data.map { prefs ->
        val raw = prefs[Keys.CUSTOM_TRADING_SESSIONS]
        if (raw.isNullOrBlank()) {
            CustomTradingSession.DEFAULT_SESSIONS
        } else {
            decodeSessions(raw).ifEmpty { CustomTradingSession.DEFAULT_SESSIONS }
        }
    }

    suspend fun setDarkMode(isDark: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.IS_DARK_MODE] = isDark
        }
    }

    suspend fun setDisplayCurrency(currency: TradingCurrency) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DISPLAY_CURRENCY] = currency.name
        }
    }

    suspend fun saveCachedUsdToInrRate(rate: Double, updatedAtMillis: Long) {
        if (rate in 40.0..200.0) {
            context.dataStore.edit { prefs ->
                prefs[Keys.CACHED_USD_TO_INR] = rate
                prefs[Keys.CACHED_RATE_UPDATED_AT] = updatedAtMillis
            }
        }
    }

    suspend fun setTimezoneId(timezoneId: String) {
        val clean = timezoneId.trim().ifEmpty { "Asia/Kolkata" }
        context.dataStore.edit { prefs ->
            prefs[Keys.SELECTED_TIMEZONE_ID] = clean
        }
    }

    suspend fun saveCustomTradingSessions(sessions: List<CustomTradingSession>) {
        val sanitized = sessions.filter { it.name.isNotBlank() }
        context.dataStore.edit { prefs ->
            if (sanitized.isEmpty()) {
                prefs.remove(Keys.CUSTOM_TRADING_SESSIONS)
            } else {
                prefs[Keys.CUSTOM_TRADING_SESSIONS] = encodeSessions(sanitized)
            }
        }
    }

    suspend fun resetDefaultTradingSessions() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.CUSTOM_TRADING_SESSIONS)
        }
    }

    private fun encodeSessions(sessions: List<CustomTradingSession>): String {
        val arr = JSONArray()
        sessions.forEach { s ->
            arr.put(JSONObject().apply {
                put("id", s.id)
                put("name", s.name.trim().take(40))
                put("startMinute", s.startMinuteOfDay.coerceIn(0, 1439))
                put("endMinute", s.endMinuteOfDay.coerceIn(0, 1439))
            })
        }
        return arr.toString()
    }

    private fun decodeSessions(json: String): List<CustomTradingSession> {
        return runCatching {
            val arr = JSONArray(json)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val name = obj.optString("name").trim()
                    if (name.isNotEmpty()) {
                        add(
                            CustomTradingSession(
                                id = obj.optString("id").ifBlank { "session_$i" },
                                name = name,
                                startMinuteOfDay = obj.optInt("startMinute", 0).coerceIn(0, 1439),
                                endMinuteOfDay = obj.optInt("endMinute", 1439).coerceIn(0, 1439)
                            )
                        )
                    }
                }
            }
        }.getOrDefault(emptyList())
    }
}
