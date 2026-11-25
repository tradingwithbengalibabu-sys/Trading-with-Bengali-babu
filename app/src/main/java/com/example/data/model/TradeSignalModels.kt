package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.util.Locale
import kotlin.math.abs

@Keep
@IgnoreExtraProperties
data class TradeSignalEntity(
    @DocumentId val id: String = "",
    val instrument: String = "",
    val direction: String = TradeDirection.BUY.name,
    val entryPrice: Double = 0.0,
    val stopLoss: Double = 0.0,
    val target1: Double = 0.0,
    val target2: Double? = null,
    val riskRewardRatio: String = "",
    val strategyName: String = "",
    val instructions: String = "",
    val status: String = TradeSignalStatus.ACTIVE.name,
    val audienceType: String = SignalAudienceType.ALL_STUDENTS.name,
    val selectedStudentIds: List<String> = emptyList(),
    val screenshotUrls: List<String> = emptyList(),
    val screenshotPaths: List<String> = emptyList(),
    val voiceNoteUrl: String = "",
    val voiceNotePath: String = "",
    val voiceDurationSec: Int = 0,
    val authorId: String = "",
    val authorName: String = "",
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    @get:Exclude
    val directionEnum: TradeDirection
        get() = TradeDirection.entries.firstOrNull { it.name == direction } ?: TradeDirection.BUY

    @get:Exclude
    val statusEnum: TradeSignalStatus
        get() = TradeSignalStatus.entries.firstOrNull { it.name == status } ?: TradeSignalStatus.ACTIVE

    @get:Exclude
    val audienceEnum: SignalAudienceType
        get() = SignalAudienceType.entries.firstOrNull { it.name == audienceType } ?: SignalAudienceType.ALL_STUDENTS

    @get:Exclude
    val hasVoiceNote: Boolean
        get() = voiceNoteUrl.isNotBlank()

    @get:Exclude
    val computedRiskReward: String
        get() {
            if (riskRewardRatio.isNotBlank()) return riskRewardRatio
            return calculateRiskRewardLabel(entryPrice, stopLoss, target1)
        }

    companion object {
        val ALLOWED_REACTIONS = listOf("👍", "❤️", "🔥", "👏", "🚀")
        val PRESET_INSTRUMENTS = listOf("NIFTY", "BANKNIFTY", "SENSEX", "GOLD", "XAUUSD", "CRUDEOIL", "EURUSD", "BTCUSD")

        fun calculateRiskRewardLabel(entry: Double, stopLoss: Double, target1: Double): String {
            val risk = abs(entry - stopLoss)
            val reward = abs(target1 - entry)
            if (entry <= 0.0 || risk <= 0.0 || reward <= 0.0) return "—"
            val ratio = reward / risk
            return "1:${String.format(Locale.US, "%.2f", ratio)}"
        }
    }
}

@Keep
@IgnoreExtraProperties
data class TradeSignalReactionEntity(
    @DocumentId val id: String = "",
    val signalId: String = "",
    val userId: String = "",
    val userName: String = "",
    val emoji: String = "",
    val reactedAtMillis: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class SignalAccessRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val studentName: String = "",
    val studentEmail: String = "",
    val signalAccess: String = SignalAccess.ALLOWED.name,
    val updatedByUid: String = "",
    val updatedAtMillis: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    @get:Exclude
    val signalAccessEnum: SignalAccess
        get() = SignalAccess.entries.firstOrNull { it.name == signalAccess } ?: SignalAccess.ALLOWED
}
