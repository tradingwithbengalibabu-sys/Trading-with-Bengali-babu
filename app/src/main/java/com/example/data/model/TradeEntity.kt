package com.example.data.model

import androidx.annotation.Keep
import com.example.domain.TradingAnalytics
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class TradeEntity(
    @DocumentId val id: String = "",
    val tradeCode: String = "",
    val userId: String = "",
    val userName: String = "",
    val tradeDateMillis: Long = 0L,
    val tradeDateIso: String = "",
    val instrument: String = "",
    val direction: String = TradeDirection.BUY.name,
    val timeframe: String = Timeframe.M15.name,
    val currency: String = TradingCurrency.USD.name,
    val usdToInrRate: Double = TradingAnalytics.DEFAULT_USD_TO_INR_RATE,
    val entryPrice: Double = 0.0,
    val exitPrice: Double = 0.0,
    val stopLoss: Double? = null,
    val takeProfit: Double? = null,
    val lotSize: Double = 1.0,
    val riskAmount: Double = 0.0,
    val actualPnl: Double = 0.0,
    val fees: Double = 0.0,
    val tradeStatus: String = TradeStatus.CLOSED.name,
    val result: String = TradeResult.BREAK_EVEN.name,
    val strategy: String = "",
    val entryReason: String = "",
    val exitReason: String = "",
    val mistakes: String = "",
    val lessonsLearned: String = "",
    val screenshotData: String? = null,
    val notes: String = "",
    val entryTimeMillis: Long? = null,
    val exitDateIso: String = "",
    val exitTimeMillis: Long? = null,
    val tradingSession: String = "",
    val exitClosureType: String = "",
    val timezoneId: String = "Asia/Kolkata",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    @get:Exclude
    val tradeStatusEnum: TradeStatus
        get() = TradeStatus.entries.firstOrNull { it.name.equals(tradeStatus, ignoreCase = true) }
            ?: TradeStatus.CLOSED

    @get:Exclude
    val isClosedTrade: Boolean
        get() = tradeStatusEnum == TradeStatus.CLOSED

    @get:Exclude
    val netRealizedPnl: Double
        get() = if (isClosedTrade) actualPnl - fees.coerceAtLeast(0.0) else 0.0

    @get:Exclude
    val directionEnum: TradeDirection
        get() = TradeDirection.entries.firstOrNull { it.name == direction } ?: TradeDirection.BUY

    @get:Exclude
    val timeframeEnum: Timeframe
        get() = Timeframe.entries.firstOrNull { it.name == timeframe } ?: Timeframe.M15

    @get:Exclude
    val resultEnum: TradeResult
        get() = TradeResult.entries.firstOrNull { it.name == result } ?: TradeResult.BREAK_EVEN

    @get:Exclude
    val currencyEnum: TradingCurrency
        get() = TradingCurrency.entries.firstOrNull { it.name.equals(currency, ignoreCase = true) }
            ?: TradingCurrency.USD

    @get:Exclude
    val effectiveUsdToInrRate: Double
        get() = if (usdToInrRate > 0.0) usdToInrRate else TradingAnalytics.DEFAULT_USD_TO_INR_RATE

    @get:Exclude
    val hasExactEntryTime: Boolean
        get() = entryTimeMillis != null && entryTimeMillis > 0L

    @get:Exclude
    val hasExactExitTime: Boolean
        get() = exitTimeMillis != null && exitTimeMillis > 0L

    @get:Exclude
    val effectiveEntryMillis: Long
        get() = entryTimeMillis?.takeIf { it > 0L } ?: tradeDateMillis

    @get:Exclude
    val holdingDurationMillis: Long?
        get() {
            val entry = entryTimeMillis
            val exit = exitTimeMillis
            return if (entry != null && entry > 0L && exit != null && exit >= entry) {
                exit - entry
            } else {
                null
            }
        }

    @get:Exclude
    val effectiveTimezoneId: String
        get() = timezoneId.trim().ifEmpty { "Asia/Kolkata" }

    @get:Exclude
    val resolvedExitClosureType: ExitClosureType
        get() {
            val explicit = ExitClosureType.entries.firstOrNull { it.name == exitClosureType }
            if (explicit != null && explicit != ExitClosureType.UNKNOWN) return explicit
            val reason = exitReason.trim().lowercase(java.util.Locale.US)
            return when {
                reason.contains("trailing") -> ExitClosureType.TRAILING_SL
                reason.contains("sl hit") || reason.contains("stop loss") || reason == "sl" -> ExitClosureType.AUTO_SL
                reason.contains("tp hit") || reason.contains("take profit") || reason.contains("target") || reason == "tp" -> ExitClosureType.AUTO_TP
                reason.contains("break even") || reason.contains("breakeven") -> ExitClosureType.BREAKEVEN
                reason.contains("manual") || reason.contains("rule exit") || reason.contains("early exit") -> ExitClosureType.MANUAL
                resultEnum == TradeResult.BREAK_EVEN && reason.isNotEmpty() -> ExitClosureType.BREAKEVEN
                else -> ExitClosureType.UNKNOWN
            }
        }
}
