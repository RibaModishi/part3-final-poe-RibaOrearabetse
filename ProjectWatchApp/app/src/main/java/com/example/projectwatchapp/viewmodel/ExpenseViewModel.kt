package com.example.projectwatchapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.projectwatchapp.data.dao.ExpenseDao
import com.example.projectwatchapp.data.entities.Expense
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ExpenseViewModel = business logic for expense actions.
 *
 * Rubric mapping:
 * - Create expense entries
 * - View entries in a selected period
 * - View total spent (overall / by category in a period)
 */
class ExpenseViewModel(
    private val expenseDao: ExpenseDao
) : ViewModel() {

    companion object {
        /** Shown after insert; UI may clear a draft receipt path. */
        const val SUCCESS_MESSAGE_EXPENSE_ADDED = "Expense added."
    }

    // Internal mutable state holder.
    private val _uiState = MutableStateFlow(ExpenseUiState())
    // Read-only state for UI.
    val uiState: StateFlow<ExpenseUiState> = _uiState.asStateFlow()

    // Keep track of the currently running list-collector job so we can swap queries.
    private var expensesObserverJob: Job? = null

    /**
     * Set active user and load all expenses in reverse date order.
     */
    fun loadAllExpenses(userId: Long) {
        _uiState.value = _uiState.value.copy(
            activeUserId = userId,
            activeFilter = ExpenseFilter.All,
            errorMessage = null
        )
        observeExpenses(ExpenseFilter.All)
    }

    /**
     * Filter expenses by a date range (inclusive).
     * Dates are expected as epoch milliseconds.
     */
    fun loadExpensesForPeriod(startDate: Long, endDate: Long) {
        val userId = _uiState.value.activeUserId
        if (userId == null) {
            _uiState.value = _uiState.value.copy(errorMessage = "Load expenses first to set active user.")
            return
        }
        if (startDate > endDate) {
            _uiState.value = _uiState.value.copy(errorMessage = "Start date must be before end date.")
            return
        }

        val filter = ExpenseFilter.Period(startDate, endDate)
        _uiState.value = _uiState.value.copy(activeFilter = filter, errorMessage = null)
        observeExpenses(filter)
    }

    /**
     * Add a new expense.
     * Required fields for this project: amount, date, description.
     * Category is optional in your entity (nullable).
     */
    fun addExpense(
        amount: Double,
        date: Long,
        description: String,
        categoryId: Long? = null,
        notes: String? = null,
        photoPath: String? = null
    ) {
        val userId = _uiState.value.activeUserId
        if (userId == null) {
            _uiState.value = _uiState.value.copy(errorMessage = "Load expenses first to set active user.")
            return
        }

        val cleanDescription = description.trim()
        if (amount <= 0.0) {
            _uiState.value = _uiState.value.copy(errorMessage = "Amount must be greater than 0.")
            return
        }
        if (cleanDescription.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Description cannot be empty.")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            expenseDao.insertExpense(
                Expense(
                    userId = userId,
                    categoryId = categoryId,
                    amount = amount,
                    description = cleanDescription,
                    date = date,
                    notes = notes?.trim()?.takeIf { it.isNotEmpty() },
                    photoPath = photoPath?.trim()?.takeIf { it.isNotEmpty() }
                )
            )
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                successMessage = SUCCESS_MESSAGE_EXPENSE_ADDED
            )
        }
    }

    /**
     * Delete an expense by id from the currently loaded list.
     */
    fun deleteExpense(expenseId: Long) {
        val expense = _uiState.value.expenses.firstOrNull { it.expenseId == expenseId }
        if (expense == null) {
            _uiState.value = _uiState.value.copy(errorMessage = "Expense not found.")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            expense.photoPath?.let { path ->
                runCatching { File(path).delete() }
            }
            expenseDao.deleteExpense(expense)
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                successMessage = "Expense deleted."
            )
        }
    }

    /**
     * Calculate total spent for currently active period.
     * If no period filter is active, uses a very broad date range.
     */
    fun loadTotalSpentForActivePeriod() {
        val userId = _uiState.value.activeUserId
        if (userId == null) {
            _uiState.value = _uiState.value.copy(errorMessage = "No active user.")
            return
        }

        val (startDate, endDate) = when (val filter = _uiState.value.activeFilter) {
            is ExpenseFilter.Period -> filter.startDate to filter.endDate
            ExpenseFilter.All -> 0L to Long.MAX_VALUE
        }

        viewModelScope.launch {
            val total = expenseDao.getTotalSpent(userId, startDate, endDate) ?: 0.0
            _uiState.value = _uiState.value.copy(totalSpentInActivePeriod = total)
        }
    }

    /**
     * Calculate total spent for one category in currently active period.
     */
    fun loadCategoryTotalForActivePeriod(categoryId: Long) {
        val userId = _uiState.value.activeUserId
        if (userId == null) {
            _uiState.value = _uiState.value.copy(errorMessage = "No active user.")
            return
        }

        val (startDate, endDate) = when (val filter = _uiState.value.activeFilter) {
            is ExpenseFilter.Period -> filter.startDate to filter.endDate
            ExpenseFilter.All -> 0L to Long.MAX_VALUE
        }

        viewModelScope.launch {
            val total = expenseDao.getTotalSpentForCategory(userId, categoryId, startDate, endDate) ?: 0.0
            val updatedMap = _uiState.value.categoryTotalsInActivePeriod.toMutableMap()
            updatedMap[categoryId] = total
            _uiState.value = _uiState.value.copy(categoryTotalsInActivePeriod = updatedMap)
        }
    }

    /**
     * Clear temporary messages after showing toast/snackbar.
     */
    fun clearMessages() {
        _uiState.value = _uiState.value.copy(errorMessage = null, successMessage = null)
    }

    private fun observeExpenses(filter: ExpenseFilter) {
        val userId = _uiState.value.activeUserId ?: return

        // Stop previous observer to avoid multiple simultaneous collectors.
        expensesObserverJob?.cancel()
        expensesObserverJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            when (filter) {
                ExpenseFilter.All -> {
                    expenseDao.getExpensesForUser(userId).collect { list ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            expenses = list
                        )
                    }
                }

                is ExpenseFilter.Period -> {
                    expenseDao.getExpensesBetween(userId, filter.startDate, filter.endDate).collect { list ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            expenses = list
                        )
                    }
                }
            }
        }
    }
}

/**
 * Filter model used by this ViewModel for expense list queries.
 */
sealed class ExpenseFilter {
    data object All : ExpenseFilter()
    data class Period(val startDate: Long, val endDate: Long) : ExpenseFilter()
}

/**
 * UI state for expense screens.
 */
data class ExpenseUiState(
    val isLoading: Boolean = false,
    val activeUserId: Long? = null,
    val activeFilter: ExpenseFilter = ExpenseFilter.All,
    val expenses: List<Expense> = emptyList(),
    val totalSpentInActivePeriod: Double = 0.0,
    val categoryTotalsInActivePeriod: Map<Long, Double> = emptyMap(),
    val errorMessage: String? = null,
    val successMessage: String? = null
)
