package com.example.projectwatchapp.data.dao

import androidx.room.*
import com.example.projectwatchapp.data.entities.User
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User): Long

    @Update
    suspend fun updateUser(user: User)

    @Delete
    suspend fun deleteUser(user: User)

    // Flow version — used by RewardsActivity to observe live XP changes
    @Query("SELECT * FROM users WHERE userId = :userId")
    fun getUserById(userId: Long): Flow<User?>

    // Suspend version — used by ViewModels to read XP once before updating
    @Query("SELECT * FROM users WHERE userId = :userId LIMIT 1")
    suspend fun getUserByIdOnce(userId: Long): User?

    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    suspend fun getUserByEmail(email: String): User?

    @Query("SELECT * FROM users WHERE LOWER(username) = LOWER(:username) AND passwordHash = :passwordHash LIMIT 1")
    suspend fun login(username: String, passwordHash: String): User?

    @Query("UPDATE users SET xp = :newXp, level = :newLevel WHERE userId = :userId")
    suspend fun updateXpAndLevel(userId: Long, newXp: Int, newLevel: Int)
}