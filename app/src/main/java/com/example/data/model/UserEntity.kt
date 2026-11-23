package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class UserEntity(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val email: String = "",
    val role: String = UserRole.STUDENT.name,
    val status: String = AccountStatus.ACTIVE.name,
    val dailyProfitGoal: Double = 250.0,
    val dailyMaxLossLimit: Double = 100.0,
    val maxTradesPerDay: Int = 5,
    val maxRiskPerTrade: Double = 50.0,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    @get:Exclude
    val roleEnum: UserRole
        get() = UserRole.entries.firstOrNull { it.name == role } ?: UserRole.STUDENT

    @get:Exclude
    val statusEnum: AccountStatus
        get() = AccountStatus.entries.firstOrNull { it.name == status } ?: AccountStatus.ACTIVE
}

@Keep
@IgnoreExtraProperties
data class StudentInvite(
    @DocumentId val id: String = "",
    val name: String = "",
    val email: String = "",
    val status: String = AccountStatus.ACTIVE.name,
    val dailyProfitGoal: Double = 250.0,
    val dailyMaxLossLimit: Double = 100.0,
    val maxTradesPerDay: Int = 5,
    val maxRiskPerTrade: Double = 50.0,
    val createdByUid: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    @get:Exclude
    val statusEnum: AccountStatus
        get() = AccountStatus.entries.firstOrNull { it.name == status } ?: AccountStatus.ACTIVE
}
