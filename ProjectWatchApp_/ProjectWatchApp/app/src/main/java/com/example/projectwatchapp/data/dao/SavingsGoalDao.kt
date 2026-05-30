package com.example.projectwatchapp.data.dao

import androidx.room.*
import com.example.projectwatchapp.data.entities.SavingsGoal
import kotlinx.coroutines.flow.Flow

@Dao
interface SavingsGoalDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGoal(goal: SavingsGoal): Long

    @Update
    suspend fun updateGoal(goal: SavingsGoal)

    @Delete
    suspend fun deleteGoal(goal: SavingsGoal)

    @Query("SELECT * FROM savings_goals WHERE userId = :userId ORDER BY createdAt DESC")
    fun getGoalsForUser(userId: Long): Flow<List<SavingsGoal>>

    @Query("UPDATE savings_goals SET currentAmount = currentAmount + :amount WHERE goalId = :goalId")
    suspend fun addToCurrentAmount(goalId: Long, amount: Double)

    @Query("UPDATE savings_goals SET isCompleted = 1 WHERE goalId = :goalId")
    suspend fun markGoalCompleted(goalId: Long)
}