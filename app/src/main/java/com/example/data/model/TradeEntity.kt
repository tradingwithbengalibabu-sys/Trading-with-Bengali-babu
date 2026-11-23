package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "trades",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["userId"]),
        Index(value = ["tradeDateIso"]),
        Index(value = ["tradeCode"], unique = true)
    ]
)
data class TradeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val tradeCode: String,
    val userId: Long,
    val userName: String,
    val tradeDateMillis: Long,
    val tradeDateIso: String,
    val instrument: String,
    val direction: TradeDirection,
    val timeframe: Timeframe,
    val entryPrice: Double,
    val exitPrice: Double,
    val stopLoss: Double?,
    val takeProfit: Double?,
    val lotSize: Double,
    val riskAmount: Double,
    val actualPnl: Double,
    val result: TradeResult,
    val strategy: String,
    val entryReason: String,
    val exitReason: String,
    val mistakes: String,
    val lessonsLearned: String,
    val screenshotPath: String?,
    val notes: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
