package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.TradeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TradeDao {
    @Query("SELECT * FROM trades WHERE userId = :userId ORDER BY tradeDateMillis DESC, id DESC")
    fun observeTradesByUser(userId: Long): Flow<List<TradeEntity>>

    @Query("SELECT * FROM trades ORDER BY tradeDateMillis DESC, id DESC")
    fun observeAllTrades(): Flow<List<TradeEntity>>

    @Query("SELECT * FROM trades WHERE id = :tradeId LIMIT 1")
    suspend fun getTradeById(tradeId: Long): TradeEntity?

    @Query("SELECT * FROM trades WHERE userId = :userId ORDER BY tradeDateMillis DESC, id DESC")
    suspend fun getTradesByUserOnce(userId: Long): List<TradeEntity>

    @Query("SELECT * FROM trades ORDER BY tradeDateMillis DESC, id DESC")
    suspend fun getAllTradesOnce(): List<TradeEntity>

    @Query("SELECT COALESCE(MAX(id), 0) FROM trades")
    suspend fun getMaxTradeId(): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrade(trade: TradeEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrades(trades: List<TradeEntity>)

    @Update
    suspend fun updateTrade(trade: TradeEntity)

    @Query("DELETE FROM trades WHERE id = :tradeId")
    suspend fun deleteTradeById(tradeId: Long)

    @Query("DELETE FROM trades")
    suspend fun deleteAllTrades()
}
