package com.example.data.model

import androidx.annotation.Keep
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
    val entryPrice: Double = 0.0,
    val exitPrice: Double = 0.0,
    val stopLoss: Double? = null,
    val takeProfit: Double? = null,
    val lotSize: Double = 1.0,
    val riskAmount: Double = 0.0,
    val actualPnl: Double = 0.0,
    val result: String = TradeResult.BREAK_EVEN.name,
    val strategy: String = "",
    val entryReason: String = "",
    val exitReason: String = "",
    val mistakes: String = "",
    val lessonsLearned: String = "",
    val screenshotData: String? = null,
    val notes: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    @get:Exclude
    val directionEnum: TradeDirection
        get() = TradeDirection.entries.firstOrNull { it.name == direction } ?: TradeDirection.BUY

    @get:Exclude
    val timeframeEnum: Timeframe
        get() = Timeframe.entries.firstOrNull { it.name == timeframe } ?: Timeframe.M15

    @get:Exclude
    val resultEnum: TradeResult
        get() = TradeResult.entries.firstOrNull { it.name == result } ?: TradeResult.BREAK_EVEN
}
