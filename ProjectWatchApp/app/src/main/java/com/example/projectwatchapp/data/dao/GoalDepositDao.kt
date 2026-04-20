package com.example.projectwatchapp.data.dao

import androidx.room.*
import com.example.projectwatchapp.data.entities.GoalDeposit
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalDepositDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeposit(deposit: GoalDeposit): Long

    @Delete
    suspend fun deleteDeposit(deposit: GoalDeposit)

    @Query("SELECT * FROM goal_deposits WHERE goalId = :goalId ORDER BY date DESC")
    fun getDepositsForGoal(goalId: Long): Flow<List<GoalDeposit>>

    @Query("SELECT SUM(amount) FROM goal_deposits WHERE goalId = :goalId")
    suspend fun getTotalDepositsForGoal(goalId: Long): Double?
}