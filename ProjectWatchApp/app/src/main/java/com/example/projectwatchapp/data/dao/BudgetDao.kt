package com.example.projectwatchapp.data.dao

import androidx.room.*
import com.example.projectwatchapp.data.entities.Budget
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBudget(budget: Budget): Long

    @Update
    suspend fun updateBudget(budget: Budget)

    @Delete
    suspend fun deleteBudget(budget: Budget)

    @Query("SELECT * FROM budgets WHERE userId = :userId AND isActive = 1 ORDER BY startDate DESC")
    fun getActiveBudgetsForUser(userId: Long): Flow<List<Budget>>

    @Query("SELECT * FROM budgets WHERE userId = :userId AND categoryId = :categoryId AND isActive = 1 LIMIT 1")
    suspend fun getActiveBudgetForCategory(userId: Long, categoryId: Long): Budget?
}