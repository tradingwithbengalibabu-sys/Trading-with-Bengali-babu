package com.example.data.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class ExchangeRateQuote(
    val usdToInr: Double,
    val source: String,
    val fetchedAtMillis: Long = System.currentTimeMillis()
)

object CurrencyRateService {
    private const val TAG = "CurrencyRateService"

    suspend fun fetchLiveUsdToInr(): Result<ExchangeRateQuote> = withContext(Dispatchers.IO) {
        val primary = runCatching { fetchFromOpenErApi() }
        if (primary.isSuccess) return@withContext primary

        val fallback = runCatching { fetchFromFrankfurter() }
        if (fallback.isSuccess) return@withContext fallback

        val err = fallback.exceptionOrNull() ?: primary.exceptionOrNull()
            ?: IllegalStateException("Unable to fetch live USD/INR rate.")
        Log.w(TAG, "Live exchange rate fetch failed: ${err.message}")
        Result.failure(err)
    }

    private fun fetchFromOpenErApi(): ExchangeRateQuote {
        val body = httpGetJson("https://open.er-api.com/v6/latest/USD")
        val json = JSONObject(body)
        val rates = json.getJSONObject("rates")
        val inr = rates.getDouble("INR")
        require(inr in 40.0..200.0) { "Out-of-range INR rate: $inr" }
        return ExchangeRateQuote(usdToInr = inr, source = "ExchangeRate-API")
    }

    private fun fetchFromFrankfurter(): ExchangeRateQuote {
        val body = httpGetJson("https://api.frankfurter.app/latest?from=USD&to=INR")
        val json = JSONObject(body)
        val rates = json.getJSONObject("rates")
        val inr = rates.getDouble("INR")
        require(inr in 40.0..200.0) { "Out-of-range INR rate: $inr" }
        return ExchangeRateQuote(usdToInr = inr, source = "Frankfurter ECB")
    }

    private fun httpGetJson(urlString: String): String {
        val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 6000
            readTimeout = 6000
            setRequestProperty("Accept", "application/json")
        }
        return try {
            val code = conn.responseCode
            require(code in 200..299) { "HTTP $code from $urlString" }
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
