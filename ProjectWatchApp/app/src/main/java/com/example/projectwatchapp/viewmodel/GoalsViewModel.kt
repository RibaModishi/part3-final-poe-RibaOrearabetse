package com.example.projectwatchapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.projectwatchapp.data.dao.GoalDepositDao
import com.example.projectwatchapp.data.dao.SavingsGoalDao
import com.example.projectwatchapp.data.entities.GoalDeposit
import com.example.projectwatchapp.data.entities.SavingsGoal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class GoalsViewModel(
    private val savingsGoalDao: SavingsGoalDao,
    private val goalDepositDao: GoalDepositDao
) : ViewModel() {

    private val _activeUserId = MutableStateFlow<Long?>(null)
    val activeUserId: StateFlow<Long?> = _activeUserId.asStateFlow()

    private val _selectedGoalId = MutableStateFlow<Long?>(null)
    val selectedGoalId: StateFlow<Long?> = _selectedGoalId.asStateFlow()

    val goals = _activeUserId
        .flatMapLatest { userId ->
            if (userId == null) flowOf(emptyList()) else savingsGoalDao.getGoalsForUser(userId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val selectedGoalDeposits = _selectedGoalId
        .flatMapLatest { goalId ->
            if (goalId == null) flowOf(emptyList()) else goalDepositDao.getDepositsForGoal(goalId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun setActiveUser(userId: Long?) {
        _activeUserId.value = userId
    }

    fun selectGoal(goalId: Long?) {
        _selectedGoalId.value = goalId
    }

    fun addGoal(
        userId: Long,
        name: String,
        targetAmount: Double,
        deadline: Long? = null,
        colorHex: String = "#4CAF50",
        iconName: String = "savings"
    ) {
        viewModelScope.launch {
            try {
                savingsGoalDao.insertGoal(
                    SavingsGoal(
                        userId = userId,
                        name = name.trim(),
                        targetAmount = targetAmount,
                        deadline = deadline,
                        colorHex = colorHex,
                        iconName = iconName
                    )
                )
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to create goal."
            }
        }
    }

    fun updateGoal(goal: SavingsGoal) {
        viewModelScope.launch {
            try {
                savingsGoalDao.updateGoal(goal)
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to update goal."
            }
        }
    }

    fun deleteGoal(goal: SavingsGoal) {
        viewModelScope.launch {
            try {
                savingsGoalDao.deleteGoal(goal)
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to delete goal."
            }
        }
    }

    fun addDeposit(goalId: Long, amount: Double, note: String? = null, xpEarned: Int = 10) {
        viewModelScope.launch {
            try {
                goalDepositDao.insertDeposit(
                    GoalDeposit(
                        goalId = goalId,
                        amount = amount,
                        note = note,
                        xpEarned = xpEarned
                    )
                )
                savingsGoalDao.addToCurrentAmount(goalId, amount)
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to add goal deposit."
            }
        }
    }

    fun markGoalCompleted(goalId: Long) {
        viewModelScope.launch {
            try {
                savingsGoalDao.markGoalCompleted(goalId)
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to mark goal as completed."
            }
        }
    }

    fun getGoalDepositTotal(goalId: Long, onResult: (Double) -> Unit) {
        viewModelScope.launch {
            val total = goalDepositDao.getTotalDepositsForGoal(goalId) ?: 0.0
            onResult(total)
        }
    }
}
