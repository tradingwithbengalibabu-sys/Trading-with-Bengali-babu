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
data class SmartDisciplineAlertEntity(
    @DocumentId val id: String = "",
    val userId: String = "",
    val userName: String = "",
    val alertType: String = SmartAlertType.STOP_LOSS_STREAK.name,
    val triggerKey: String = "",
    val strategyName: String = "",
    val stopLossCount: Int = 0,
    val lookbackCount: Int = 10,
    val recommendedBreakDays: Int = 0,
    val lossAmountUsd: Double = 0.0,
    val lossAmountInr: Double = 0.0,
    val exchangeRateUsed: Double = TradingAnalytics.DEFAULT_USD_TO_INR_RATE,
    val triggerDateIso: String = "",
    val resolved: Boolean = false,
    val title: String = "",
    val message: String = "",
    val acknowledged: Boolean = false,
    val acknowledgedAtMillis: Long = 0L,
    val triggeredAtMillis: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    @get:Exclude
    val alertTypeEnum: SmartAlertType
        get() = SmartAlertType.entries.firstOrNull { it.name == alertType } ?: SmartAlertType.STOP_LOSS_STREAK

    @get:Exclude
    val isActiveUnacknowledged: Boolean
        get() = !acknowledged && !resolved

    @get:Exclude
    val effectiveTimestampMillis: Long
        get() = if (triggeredAtMillis > 0L) triggeredAtMillis else (createdAt?.toDate()?.time ?: 0L)
}

@Keep
@IgnoreExtraProperties
data class WeeklyReportEntity(
    @DocumentId val id: String = "",
    val userId: String = "",
    val userName: String = "",
    val weekKey: String = "",
    val weekStartIso: String = "",
    val weekEndIso: String = "",
    val currencyCode: String = TradingCurrency.USD.name,
    val totalTrades: Int = 0,
    val winningTrades: Int = 0,
    val losingTrades: Int = 0,
    val breakEvenTrades: Int = 0,
    val stopLossHits: Int = 0,
    val netPnl: Double = 0.0,
    val winRatePercent: Double = 0.0,
    val disciplineScore: Int = 100,
    val overtradingDays: Int = 0,
    val revengeTradingCount: Int = 0,
    val noStopLossCount: Int = 0,
    val ruleViolationsCount: Int = 0,
    val repeatedMistakes: List<String> = emptyList(),
    val poorStrategies: List<String> = emptyList(),
    val ruleViolationDetails: List<String> = emptyList(),
    val improvementAreas: List<String> = emptyList(),
    val personalizedSuggestions: List<String> = emptyList(),
    val summaryText: String = "",
    val readByUser: Boolean = false,
    val generatedAtMillis: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    @get:Exclude
    val currencyEnum: TradingCurrency
        get() = TradingCurrency.entries.firstOrNull { it.name.equals(currencyCode, ignoreCase = true) }
            ?: TradingCurrency.USD

    @get:Exclude
    val effectiveGeneratedMillis: Long
        get() = if (generatedAtMillis > 0L) generatedAtMillis else (createdAt?.toDate()?.time ?: 0L)
}
