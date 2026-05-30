package com.example.projectwatchapp.viewmodel

import com.example.projectwatchapp.data.dao.BudgetDao
import com.example.projectwatchapp.data.dao.ExpenseDao
import com.example.projectwatchapp.data.entities.Budget
import com.example.projectwatchapp.data.entities.Expense
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BudgetViewModelTest {

    @Test
    fun getMonthlyStatusForSpent_returnsCorrectBand() {
        val vm = BudgetViewModel(FakeBudgetDao(), FakeExpenseDao())
        vm.setMonthlyGoals(minGoal = 100.0, maxGoal = 1000.0)

        assertEquals(MonthlyStatus.GREEN, vm.getMonthlyStatusForSpent(200.0))
        assertEquals(MonthlyStatus.YELLOW, vm.getMonthlyStatusForSpent(900.0))
        assertEquals(MonthlyStatus.RED, vm.getMonthlyStatusForSpent(1200.0))
    }

    @Test
    fun setMonthlyGoals_rejectsInvalidRange() {
        val vm = BudgetViewModel(FakeBudgetDao(), FakeExpenseDao())

        vm.setMonthlyGoals(minGoal = 600.0, maxGoal = 500.0)

        assertEquals(
            "Minimum goal cannot be greater than maximum goal.",
            vm.uiState.value.errorMessage
        )
        assertNull(vm.uiState.value.maxMonthlyGoal)
    }
}

private class FakeBudgetDao : BudgetDao {
    private val budgetsFlow = MutableStateFlow<List<Budget>>(emptyList())

    override suspend fun insertBudget(budget: Budget): Long = 1L
    override suspend fun updateBudget(budget: Budget) = Unit
    override suspend fun deleteBudget(budget: Budget) = Unit
    override fun getActiveBudgetsForUser(userId: Long): Flow<List<Budget>> = budgetsFlow
    override suspend fun getActiveBudgetForCategory(userId: Long, categoryId: Long): Budget? = null
}

private class FakeExpenseDao : ExpenseDao {
    override suspend fun insertExpense(expense: Expense): Long = 1L
    override suspend fun updateExpense(expense: Expense) = Unit
    override suspend fun deleteExpense(expense: Expense) = Unit
    override fun getExpensesForUser(userId: Long): Flow<List<Expense>> = MutableStateFlow(emptyList())
    override fun getExpensesBetween(userId: Long, startDate: Long, endDate: Long): Flow<List<Expense>> =
        MutableStateFlow(emptyList())

    override suspend fun getTotalSpentForCategory(
        userId: Long,
        categoryId: Long,
        startDate: Long,
        endDate: Long
    ): Double? = 0.0

    override suspend fun getTotalSpent(userId: Long, startDate: Long, endDate: Long): Double? = 0.0
    override fun observeTotalSpentBetween(userId: Long, startDate: Long, endDate: Long): Flow<Double?> =
        MutableStateFlow(0.0)

    override suspend fun getSpentSince(userId: Long, categoryId: Long, sinceDate: Long): Double? = 0.0
}
