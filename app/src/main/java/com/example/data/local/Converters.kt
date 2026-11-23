package com.example.data.local

import androidx.room.TypeConverter
import com.example.data.model.AccountStatus
import com.example.data.model.EmotionState
import com.example.data.model.ReviewPeriod
import com.example.data.model.Timeframe
import com.example.data.model.TradeDirection
import com.example.data.model.TradeResult
import com.example.data.model.UserRole

class Converters {
    @TypeConverter fun fromUserRole(v: UserRole): String = v.name
    @TypeConverter fun toUserRole(v: String): UserRole =
        UserRole.entries.firstOrNull { it.name == v } ?: UserRole.STUDENT

    @TypeConverter fun fromAccountStatus(v: AccountStatus): String = v.name
    @TypeConverter fun toAccountStatus(v: String): AccountStatus =
        AccountStatus.entries.firstOrNull { it.name == v } ?: AccountStatus.ACTIVE

    @TypeConverter fun fromTradeDirection(v: TradeDirection): String = v.name
    @TypeConverter fun toTradeDirection(v: String): TradeDirection =
        TradeDirection.entries.firstOrNull { it.name == v } ?: TradeDirection.BUY

    @TypeConverter fun fromTimeframe(v: Timeframe): String = v.name
    @TypeConverter fun toTimeframe(v: String): Timeframe =
        Timeframe.entries.firstOrNull { it.name == v } ?: Timeframe.M15

    @TypeConverter fun fromTradeResult(v: TradeResult): String = v.name
    @TypeConverter fun toTradeResult(v: String): TradeResult =
        TradeResult.entries.firstOrNull { it.name == v } ?: TradeResult.BREAK_EVEN

    @TypeConverter fun fromReviewPeriod(v: ReviewPeriod): String = v.name
    @TypeConverter fun toReviewPeriod(v: String): ReviewPeriod =
        ReviewPeriod.entries.firstOrNull { it.name == v } ?: ReviewPeriod.WEEKLY

    @TypeConverter fun fromEmotionState(v: EmotionState): String = v.name
    @TypeConverter fun toEmotionState(v: String): EmotionState =
        EmotionState.entries.firstOrNull { it.name == v } ?: EmotionState.CALM
}
