package com.example.projectwatchapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.projectwatchapp.data.dao.ExpenseDao
import com.example.projectwatchapp.data.entities.Expense
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/*
 * Riba change summary:
 * - Expanded addExpense(...) to support assignment-required fields:
 *   startTime, endTime, and photoUri.
 * - Kept filtering logic in ViewModel so UI stays simple.
 */
class ExpenseViewModel(
    private val expenseDao: ExpenseDao
) : ViewModel() {

    private val _activeUserId = MutableStateFlow<Long?>(null)
    val activeUserId: StateFlow<Long?> = _activeUserId.asStateFlow()

    private val _filterRange = MutableStateFlow<Pair<Long, Long>?>(null)
    val filterRange: StateFlow<Pair<Long, Long>?> = _filterRange.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Reacts to both active user and date range changes.
    val expenses = combine(_activeUserId, _filterRange) { userId, range -> userId to range }
        .flatMapLatest { (userId, range) ->
            if (userId == null) {
                flowOf(emptyList())
            } else if (range == null) {
                // No filter selected -> show all expenses for the user.
                expenseDao.getExpensesForUser(userId)
            } else {
                // Filter selected -> show only expenses in that period.
                expenseDao.getExpensesBetween(
                    userId = userId,
                    startDate = range.first,
                    endDate = range.second
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setActiveUser(userId: Long?) {
        _activeUserId.value = userId
    }

    fun setDateRangeFilter(startDate: Long, endDate: Long) {
        _filterRange.value = startDate to endDate
    }

    fun clearDateRangeFilter() {
        _filterRange.value = null
    }

    fun addExpense(
        userId: Long,
        categoryId: Long?,
        amount: Double,
        description: String,
        date: Long,
        // Riba added: optional times for assignment requirement.
        startTime: Long? = null,
        endTime: Long? = null,
        // Riba added: optional photo path/URI.
        photoUri: String? = null,
        notes: String? = null,
        xpEarned: Int = 5
    ) {
        viewModelScope.launch {
            try {
                expenseDao.insertExpense(
                    Expense(
                        userId = userId,
                        categoryId = categoryId,
                        amount = amount,
                        // Trim to avoid leading/trailing spaces from user input.
                        description = description.trim(),
                        date = date,
                        startTime = startTime,
                        endTime = endTime,
                        photoUri = photoUri,
                        notes = notes,
                        xpEarned = xpEarned
                    )
                )
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to save expense."
            }
        }
    }

    fun updateExpense(expense: Expense) {
        viewModelScope.launch {
            try {
                expenseDao.updateExpense(expense)
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to update expense."
            }
        }
    }

    fun deleteExpense(expense: Expense) {
        viewModelScope.launch {
            try {
                expenseDao.deleteExpense(expense)
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to delete expense."
            }
        }
    }

    fun getTotalSpentInRange(
        userId: Long,
        startDate: Long,
        endDate: Long,
        onResult: (Double) -> Unit
    ) {
        viewModelScope.launch {
            // If DAO returns null (no data), default to 0.0 for safe UI display.
            val total = expenseDao.getTotalSpent(userId, startDate, endDate) ?: 0.0
            onResult(total)
        }
    }

    fun getCategoryTotalInRange(
        userId: Long,
        categoryId: Long,
        startDate: Long,
        endDate: Long,
        onResult: (Double) -> Unit
    ) {
        viewModelScope.launch {
            // Category total in selected period; null becomes 0.0.
            val total = expenseDao.getTotalSpentForCategory(userId, categoryId, startDate, endDate) ?: 0.0
            onResult(total)
        }
    }
}
