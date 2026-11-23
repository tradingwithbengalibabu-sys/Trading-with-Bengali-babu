package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.UserEntity
import com.example.data.model.UserRole
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    fun observeUserById(id: Long): Flow<UserEntity?>

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    suspend fun getUserById(id: Long): UserEntity?

    @Query("SELECT * FROM users WHERE LOWER(email) = LOWER(:email) LIMIT 1")
    suspend fun getUserByEmail(email: String): UserEntity?

    @Query("SELECT COUNT(*) FROM users WHERE role = :role")
    fun observeCountByRole(role: UserRole = UserRole.ADMIN): Flow<Int>

    @Query("SELECT COUNT(*) FROM users WHERE role = :role")
    suspend fun getCountByRole(role: UserRole = UserRole.ADMIN): Int

    @Query("SELECT * FROM users WHERE role = :role ORDER BY name ASC")
    fun observeUsersByRole(role: UserRole = UserRole.STUDENT): Flow<List<UserEntity>>

    @Query("SELECT * FROM users ORDER BY role ASC, name ASC")
    suspend fun getAllUsersOnce(): List<UserEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertUser(user: UserEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUsers(users: List<UserEntity>)

    @Update
    suspend fun updateUser(user: UserEntity)

    @Query("DELETE FROM users WHERE id = :userId")
    suspend fun deleteUserById(userId: Long)

    @Query("DELETE FROM users")
    suspend fun deleteAllUsers()
}
