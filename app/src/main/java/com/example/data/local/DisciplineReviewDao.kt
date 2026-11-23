package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.DisciplineReviewEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DisciplineReviewDao {
    @Query("SELECT * FROM discipline_reviews WHERE userId = :userId ORDER BY updatedAt DESC")
    fun observeReviewsByUser(userId: Long): Flow<List<DisciplineReviewEntity>>

    @Query("SELECT * FROM discipline_reviews ORDER BY updatedAt DESC")
    suspend fun getAllReviewsOnce(): List<DisciplineReviewEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReview(review: DisciplineReviewEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReviews(reviews: List<DisciplineReviewEntity>)

    @Query("DELETE FROM discipline_reviews WHERE id = :reviewId")
    suspend fun deleteReviewById(reviewId: Long)

    @Query("DELETE FROM discipline_reviews")
    suspend fun deleteAllReviews()
}
