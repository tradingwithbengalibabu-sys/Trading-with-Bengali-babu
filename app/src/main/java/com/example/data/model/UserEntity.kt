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
    val signalAccess: String = SignalAccess.ALLOWED.name,
    val preferredCurrency: String = TradingCurrency.USD.name,
    val dailyProfitGoal: Double = 250.0,
    val dailyMaxLossLimit: Double = 100.0,
    val maxTradesPerDay: Int = 5,
    val maxRiskPerTrade: Double = 50.0,
    val lastAnnouncementReadMillis: Long = 0L,
    val slAlertLookbackCount: Int = 10,
    val slAlertThreshold: Int = 10,
    val strategySlThreshold: Int = 5,
    val breakRecommendedUntilMillis: Long = 0L,
    val mobileNumber: String = "",
    val whatsappNumber: String = "",
    val dateOfBirth: String = "",
    val gender: String = "",
    val fullAddress: String = "",
    val profilePhotoUrl: String = "",
    val profilePhotoPath: String = "",
    val profileCompleted: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    @get:Exclude
    val roleEnum: UserRole
        get() = UserRole.entries.firstOrNull { it.name == role } ?: UserRole.STUDENT

    @get:Exclude
    val statusEnum: AccountStatus
        get() = AccountStatus.entries.firstOrNull { it.name == status } ?: AccountStatus.ACTIVE

    @get:Exclude
    val signalAccessEnum: SignalAccess
        get() = SignalAccess.entries.firstOrNull { it.name == signalAccess } ?: SignalAccess.ALLOWED

    @get:Exclude
    val preferredCurrencyEnum: TradingCurrency
        get() = TradingCurrency.entries.firstOrNull { it.name.equals(preferredCurrency, ignoreCase = true) }
            ?: TradingCurrency.USD

    @get:Exclude
    val effectiveSlLookbackCount: Int
        get() = slAlertLookbackCount.coerceIn(2, 50)

    @get:Exclude
    val effectiveSlAlertThreshold: Int
        get() = slAlertThreshold.coerceIn(2, effectiveSlLookbackCount)

    @get:Exclude
    val effectiveStrategySlThreshold: Int
        get() = strategySlThreshold.coerceIn(1, 50)

    @get:Exclude
    val hasIncompleteProfile: Boolean
        get() = !profileCompleted ||
            name.isBlank() ||
            mobileNumber.isBlank() ||
            whatsappNumber.isBlank() ||
            dateOfBirth.isBlank() ||
            gender.isBlank() ||
            fullAddress.isBlank()

    @get:Exclude
    val profileCompletionPercent: Int
        get() {
            val checks = listOf(
                name.isNotBlank(),
                profilePhotoUrl.isNotBlank(),
                mobileNumber.isNotBlank(),
                whatsappNumber.isNotBlank(),
                email.isNotBlank(),
                dateOfBirth.isNotBlank(),
                gender.isNotBlank(),
                fullAddress.isNotBlank()
            )
            val completed = checks.count { it }
            return ((completed * 100f) / checks.size).toInt().coerceIn(0, 100)
        }

    @get:Exclude
    val initials: String
        get() {
            val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            return when {
                parts.size >= 2 -> "${parts[0].first().uppercaseChar()}${parts[1].first().uppercaseChar()}"
                parts.size == 1 -> parts[0].take(2).uppercase(java.util.Locale.US)
                email.isNotBlank() -> email.take(2).uppercase(java.util.Locale.US)
                else -> "ST"
            }
        }
}

@Keep
@IgnoreExtraProperties
data class StudentInvite(
    @DocumentId val id: String = "",
    val name: String = "",
    val email: String = "",
    val status: String = AccountStatus.ACTIVE.name,
    val preferredCurrency: String = TradingCurrency.USD.name,
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

    @get:Exclude
    val preferredCurrencyEnum: TradingCurrency
        get() = TradingCurrency.entries.firstOrNull { it.name.equals(preferredCurrency, ignoreCase = true) }
            ?: TradingCurrency.USD
}
