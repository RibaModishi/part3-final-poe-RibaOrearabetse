package com.example.projectwatchapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.projectwatchapp.data.dao.BudgetDao
import com.example.projectwatchapp.data.dao.ExpenseDao
import com.example.projectwatchapp.data.entities.Budget
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * BudgetViewModel = business logic for monthly budget planning.
 * Ref: Android Developers (n.d.a) - ViewModel overview.
 * Ref: Android Developers (n.d.c) - ViewModel with Kotlin coroutines.
 *
 * What this class helps with:
 * - Set monthly minimum and maximum spending goals
 * - Set per-category budget limits
 * - Live calculation of allocated vs unallocated budget
 * - Warning when category allocation exceeds max monthly goal
 */
class BudgetViewModel(
    private val budgetDao: BudgetDao,
    private val expenseDao: ExpenseDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(BudgetUiState())
    val uiState: StateFlow<BudgetUiState> = _uiState.asStateFlow()

    private var budgetsObserverJob: Job? = null

    private val zoneId: ZoneId = ZoneId.systemDefault()

    /**
     * Loads and observes active budgets for one user.
     * Also observes **actual expense totals for the current calendar month** so status vs max uses real spend.
     *
     * Ref: Android Developers (n.d.b) - StateFlow and SharedFlow.
     * Ref: Android Developers (n.d.e) - Accessing data using Room DAOs.
     */
    fun loadBudgets(userId: Long) {
        _uiState.value = _uiState.value.copy(activeUserId = userId, errorMessage = null)

        budgetsObserverJob?.cancel()
        budgetsObserverJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val (monthStart, monthEnd) = currentMonthWindowEpochMillis()
            coroutineScope {
                launch {
                    budgetDao.getActiveBudgetsForUser(userId).collect { budgets ->
                        val allocatedTotal = budgets.sumOf { it.amount }
                        val maxGoal = _uiState.value.maxMonthlyGoal
                        val unallocated = if (maxGoal != null) maxGoal - allocatedTotal else null
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            budgets = budgets,
                            allocatedTotal = allocatedTotal,
                            unallocatedAmount = unallocated
                        )
                    }
                }
                launch {
                    expenseDao.observeTotalSpentBetween(userId, monthStart, monthEnd).collect { raw ->
                        val spend = raw ?: 0.0
                        _uiState.value = _uiState.value.copy(actualSpendCurrentMonth = spend)
                    }
                }
            }
        }
    }

    /** First instant of the month through last instant of the month (inclusive), epoch ms. */
    private fun currentMonthWindowEpochMillis(): Pair<Long, Long> {
        val today = LocalDate.now(zoneId)
        val start = today.withDayOfMonth(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val end = today.withDayOfMonth(today.lengthOfMonth())
            .plusDays(1)
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli() - 1L
        return start to end
    }

    /**
     * Set minimum/maximum monthly spending goals.
     *
     * Example:
     * - min = 1000.0
     * - max = 3000.0
     */
    fun setMonthlyGoals(minGoal: Double?, maxGoal: Double?) {
        if (minGoal != null && minGoal < 0.0) {
            _uiState.value = _uiState.value.copy(errorMessage = "Minimum goal cannot be negative.")
            return
        }
        if (maxGoal != null && maxGoal <= 0.0) {
            _uiState.value = _uiState.value.copy(errorMessage = "Maximum goal must be greater than zero.")
            return
        }
        if (minGoal != null && maxGoal != null && minGoal > maxGoal) {
            _uiState.value = _uiState.value.copy(errorMessage = "Minimum goal cannot be greater than maximum goal.")
            return
        }

        val allocatedTotal = _uiState.value.allocatedTotal
        val unallocated = if (maxGoal != null) maxGoal - allocatedTotal else null
        _uiState.value = _uiState.value.copy(
            minMonthlyGoal = minGoal,
            maxMonthlyGoal = maxGoal,
            unallocatedAmount = unallocated,
            errorMessage = null,
            successMessage = "Monthly goals updated."
        )
    }

    /**
     * Insert or update a budget limit for one category.
     * If the category already has an active budget, update it.
     */
    fun upsertCategoryBudget(
        categoryId: Long,
        amount: Double,
        period: String = "monthly",
        startDate: Long,
        endDate: Long? = null
    ) {
        val userId = _uiState.value.activeUserId
        if (userId == null) {
            _uiState.value = _uiState.value.copy(errorMessage = "Load budgets first to set active user.")
            return
        }
        if (amount <= 0.0) {
            _uiState.value = _uiState.value.copy(errorMessage = "Budget amount must be greater than 0.")
            return
        }
        if (period.lowercase() !in setOf("monthly", "weekly", "yearly")) {
            _uiState.value = _uiState.value.copy(errorMessage = "Period must be monthly, weekly, or yearly.")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            val existing = budgetDao.getActiveBudgetForCategory(userId, categoryId)
            if (existing == null) {
                budgetDao.insertBudget(
                    Budget(
                        userId = userId,
                        categoryId = categoryId,
                        amount = amount,
                        period = period.lowercase(),
                        startDate = startDate,
                        endDate = endDate,
                        isActive = true
                    )
                )
            } else {
                budgetDao.updateBudget(
                    existing.copy(
                        amount = amount,
                        period = period.lowercase(),
                        startDate = startDate,
                        endDate = endDate,
                        isActive = true
                    )
                )
            }

            val projectedAllocated = projectedAllocatedTotal(categoryId = categoryId, newAmount = amount)
            val maxGoal = _uiState.value.maxMonthlyGoal
            val warning = if (maxGoal != null && projectedAllocated > maxGoal) {
                "Warning: allocated budgets are above the max monthly goal."
            } else {
                null
            }

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                successMessage = "Category budget saved.",
                warningMessage = warning
            )
        }
    }

    /**
     * Delete one budget entry by id.
     */
    fun deleteBudget(budgetId: Long) {
        val existing = _uiState.value.budgets.firstOrNull { it.budgetId == budgetId }
        if (existing == null) {
            _uiState.value = _uiState.value.copy(errorMessage = "Budget not found.")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            budgetDao.deleteBudget(existing)
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                successMessage = "Budget deleted."
            )
        }
    }

    /**
     * Helpful for UI color coding:
     * - GREEN: spent < 80% of max
     * - YELLOW: spent 80-100% of max
     * - RED: spent > 100% of max
     */
    fun getMonthlyStatusForSpent(spentAmount: Double): MonthlyStatus {
        val maxGoal = _uiState.value.maxMonthlyGoal ?: return MonthlyStatus.NO_MAX_GOAL
        if (maxGoal <= 0.0) return MonthlyStatus.NO_MAX_GOAL

        val ratio = spentAmount / maxGoal
        return when {
            ratio < 0.8 -> MonthlyStatus.GREEN
            ratio <= 1.0 -> MonthlyStatus.YELLOW
            else -> MonthlyStatus.RED
        }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(
            errorMessage = null,
            successMessage = null,
            warningMessage = null
        )
    }

    private fun projectedAllocatedTotal(categoryId: Long, newAmount: Double): Double {
        var sum = 0.0
        _uiState.value.budgets.forEach { budget ->
            sum += if (budget.categoryId == categoryId) newAmount else budget.amount
        }

        val hasExisting = _uiState.value.budgets.any { it.categoryId == categoryId }
        if (!hasExisting) sum += newAmount
        return sum
    }
}

enum class MonthlyStatus {
    NO_MAX_GOAL,
    GREEN,
    YELLOW,
    RED
}

data class BudgetUiState(
    val isLoading: Boolean = false,
    val activeUserId: Long? = null,
    val budgets: List<Budget> = emptyList(),
    val minMonthlyGoal: Double? = null,
    val maxMonthlyGoal: Double? = null,
    val allocatedTotal: Double = 0.0,
    /** Sum of expense amounts in the current calendar month (live from [ExpenseDao]). */
    val actualSpendCurrentMonth: Double = 0.0,
    val unallocatedAmount: Double? = null,
    val errorMessage: String? = null,
    val warningMessage: String? = null,
    val successMessage: String? = null
)
