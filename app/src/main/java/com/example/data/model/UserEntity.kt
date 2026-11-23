package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "users",
    indices = [Index(value = ["email"], unique = true)]
)
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val email: String,
    val passwordHash: String,
    val passwordSalt: String,
    val role: UserRole = UserRole.STUDENT,
    val status: AccountStatus = AccountStatus.ACTIVE,
    val dailyProfitGoal: Double = 250.0,
    val dailyMaxLossLimit: Double = 100.0,
    val maxTradesPerDay: Int = 5,
    val maxRiskPerTrade: Double = 50.0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
