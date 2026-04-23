package com.example.projectwatchapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.projectwatchapp.data.dao.BudgetDao
import com.example.projectwatchapp.data.dao.ExpenseDao
import com.example.projectwatchapp.data.entities.Budget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BudgetViewModel(
    private val budgetDao: BudgetDao,
    private val expenseDao: ExpenseDao
) : ViewModel() {

    private val _activeUserId = MutableStateFlow<Long?>(null)
    val activeUserId: StateFlow<Long?> = _activeUserId.asStateFlow()

    val activeBudgets = _activeUserId
        .flatMapLatest { userId ->
            if (userId == null) flowOf(emptyList()) else budgetDao.getActiveBudgetsForUser(userId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun setActiveUser(userId: Long?) {
        _activeUserId.value = userId
    }

    fun upsertBudget(
        userId: Long,
        categoryId: Long,
        amount: Double,
        period: String = "monthly",
        startDate: Long = System.currentTimeMillis()
    ) {
        viewModelScope.launch {
            try {
                val current = budgetDao.getActiveBudgetForCategory(userId, categoryId)
                if (current == null) {
                    budgetDao.insertBudget(
                        Budget(
                            userId = userId,
                            categoryId = categoryId,
                            amount = amount,
                            period = period,
                            startDate = startDate
                        )
                    )
                } else {
                    budgetDao.updateBudget(
                        current.copy(
                            amount = amount,
                            period = period,
                            startDate = startDate,
                            isActive = true
                        )
                    )
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to save budget."
            }
        }
    }

    fun removeBudget(budget: Budget) {
        viewModelScope.launch {
            try {
                budgetDao.deleteBudget(budget)
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to delete budget."
            }
        }
    }

    fun getSpentForCategorySince(
        userId: Long,
        categoryId: Long,
        sinceDate: Long,
        onResult: (Double) -> Unit
    ) {
        viewModelScope.launch {
            val spent = expenseDao.getSpentSince(userId, categoryId, sinceDate) ?: 0.0
            onResult(spent)
        }
    }
}
