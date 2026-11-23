package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class DisciplineReviewEntity(
    @DocumentId val id: String = "",
    val userId: String = "",
    val periodType: String = ReviewPeriod.WEEKLY.name,
    val periodKey: String = "",
    val emotionState: String = EmotionState.CALM.name,
    val psychologyNotes: String = "",
    val slAdherenceRating: Int = 3,
    val lessonsSummary: String = "",
    val nextActionPlan: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    @get:Exclude
    val periodTypeEnum: ReviewPeriod
        get() = ReviewPeriod.entries.firstOrNull { it.name == periodType } ?: ReviewPeriod.WEEKLY

    @get:Exclude
    val emotionStateEnum: EmotionState
        get() = EmotionState.entries.firstOrNull { it.name == emotionState } ?: EmotionState.CALM
}
