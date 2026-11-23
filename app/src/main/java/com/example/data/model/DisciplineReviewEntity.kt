package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "discipline_reviews",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["userId", "periodType", "periodKey"], unique = true)
    ]
)
data class DisciplineReviewEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val userId: Long,
    val periodType: ReviewPeriod,
    val periodKey: String,
    val emotionState: EmotionState,
    val psychologyNotes: String,
    val slAdherenceRating: Int,
    val lessonsSummary: String,
    val nextActionPlan: String,
    val updatedAt: Long = System.currentTimeMillis()
)
