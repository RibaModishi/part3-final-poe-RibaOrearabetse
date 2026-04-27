package com.example.projectwatchapp.data.dao

import androidx.room.*
import com.example.projectwatchapp.data.entities.Expense
import kotlinx.coroutines.flow.Flow

@Dao
// Ref: Android Developers (n.d.e) - Accessing data using Room DAOs.
interface ExpenseDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: Expense): Long

    @Update
    suspend fun updateExpense(expense: Expense)

    @Delete
    suspend fun deleteExpense(expense: Expense)

    @Query("SELECT * FROM expenses WHERE userId = :userId ORDER BY date DESC")
    fun getExpensesForUser(userId: Long): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE userId = :userId AND date BETWEEN :startDate AND :endDate")
    fun getExpensesBetween(userId: Long, startDate: Long, endDate: Long): Flow<List<Expense>>

    @Query("SELECT SUM(amount) FROM expenses WHERE userId = :userId AND categoryId = :categoryId AND date BETWEEN :startDate AND :endDate")
    suspend fun getTotalSpentForCategory(userId: Long, categoryId: Long, startDate: Long, endDate: Long): Double?

    @Query("SELECT SUM(amount) FROM expenses WHERE userId = :userId AND date BETWEEN :startDate AND :endDate")
    suspend fun getTotalSpent(userId: Long, startDate: Long, endDate: Long): Double?

    /**
     * Added by Riba to support live budget status updates.
     * Emits again when any matching expense row changes (for live budget vs spend).
     *
     * Ref: Android Developers (n.d.e) - Accessing data using Room DAOs.
     */
    @Query("SELECT SUM(amount) FROM expenses WHERE userId = :userId AND date BETWEEN :startDate AND :endDate")
    fun observeTotalSpentBetween(userId: Long, startDate: Long, endDate: Long): Flow<Double?>

    @Query("SELECT SUM(amount) FROM expenses WHERE userId = :userId AND categoryId = :categoryId AND date >= :sinceDate")
    suspend fun getSpentSince(userId: Long, categoryId: Long, sinceDate: Long): Double?
}