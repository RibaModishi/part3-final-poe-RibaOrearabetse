package com.example.projectwatchapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.projectwatchapp.data.dao.GoalDepositDao
import com.example.projectwatchapp.data.dao.SavingsGoalDao
import com.example.projectwatchapp.data.entities.GoalDeposit
import com.example.projectwatchapp.data.entities.SavingsGoal
import com.example.projectwatchapp.utils.SessionManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * GoalsViewModel = business logic for savings goals + deposits.
 *
 * Supports:
 * - Create/update/delete goals
 * - Add deposits and track history
 * - Progress percentage calculations
 * - Goal completion logic
 * - Pin/unpin goals in UI
 */
class GoalsViewModel(
    private val savingsGoalDao: SavingsGoalDao,
    private val goalDepositDao: GoalDepositDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(GoalsUiState())
    val uiState: StateFlow<GoalsUiState> = _uiState.asStateFlow()

    private var goalsObserverJob: Job? = null
    private var depositsObserverJob: Job? = null

    /**
     * Observe all goals for the current user.
     * Flow means list auto-refreshes whenever DB data changes.
     */
    fun loadGoals(userId: Long) {
        _uiState.value = _uiState.value.copy(activeUserId = userId, errorMessage = null)

        goalsObserverJob?.cancel()
        goalsObserverJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            savingsGoalDao.getGoalsForUser(userId).collect { goals ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    goals = goals
                )
            }
        }
    }

    /**
     * Create a new savings goal.
     */
    fun addGoal(
        name: String,
        targetAmount: Double,
        deadline: Long? = null,
        colorHex: String = "#4CAF50",
        iconName: String = "savings"
    ) {
        val userId = _uiState.value.activeUserId
        if (userId == null) {
            _uiState.value = _uiState.value.copy(errorMessage = "Load goals first to set active user.")
            return
        }

        val cleanName = name.trim()
        if (cleanName.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Goal name cannot be empty.")
            return
        }
        if (targetAmount <= 0.0) {
            _uiState.value = _uiState.value.copy(errorMessage = "Target amount must be greater than 0.")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            savingsGoalDao.insertGoal(
                SavingsGoal(
                    userId = userId,
                    name = cleanName,
                    targetAmount = targetAmount,
                    deadline = deadline,
                    colorHex = colorHex,
                    iconName = iconName
                )
            )
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                successMessage = "Goal created."
            )
        }
    }

    /**
     * Add a deposit to a goal and update goal progress.
     * Also marks goal complete if target is reached.
     */
    fun addDeposit(goalId: Long, amount: Double, note: String? = null) {
        if (amount <= 0.0) {
            _uiState.value = _uiState.value.copy(errorMessage = "Deposit amount must be greater than 0.")
            return
        }

        val goal = _uiState.value.goals.firstOrNull { it.goalId == goalId }
        if (goal == null) {
            _uiState.value = _uiState.value.copy(errorMessage = "Goal not found.")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            val xp = SessionManager.calculateXpForAction("DEPOSIT_TO_GOAL", amount)
            goalDepositDao.insertDeposit(
                GoalDeposit(
                    goalId = goalId,
                    amount = amount,
                    note = note?.trim()?.takeIf { it.isNotEmpty() },
                    xpEarned = xp
                )
            )

            savingsGoalDao.addToCurrentAmount(goalId, amount)

            // If target reached/exceeded, mark complete.
            val projectedAmount = goal.currentAmount + amount
            if (projectedAmount >= goal.targetAmount) {
                savingsGoalDao.markGoalCompleted(goalId)
            }

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                successMessage = if (projectedAmount >= goal.targetAmount) {
                    "Deposit added. Goal completed!"
                } else {
                    "Deposit added."
                }
            )
        }
    }

    /**
     * Observe deposit history for one selected goal.
     * Useful for a "Progress History" screen.
     */
    fun loadDepositHistory(goalId: Long) {
        depositsObserverJob?.cancel()
        depositsObserverJob = viewModelScope.launch {
            goalDepositDao.getDepositsForGoal(goalId).collect { deposits ->
                _uiState.value = _uiState.value.copy(
                    selectedGoalId = goalId,
                    selectedGoalDeposits = deposits
                )
            }
        }
    }

    /**
     * Update mutable goal fields.
     */
    fun updateGoal(
        goalId: Long,
        name: String,
        targetAmount: Double,
        deadline: Long? = null
    ) {
        val existing = _uiState.value.goals.firstOrNull { it.goalId == goalId }
        if (existing == null) {
            _uiState.value = _uiState.value.copy(errorMessage = "Goal not found.")
            return
        }

        val cleanName = name.trim()
        if (cleanName.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Goal name cannot be empty.")
            return
        }
        if (targetAmount <= 0.0) {
            _uiState.value = _uiState.value.copy(errorMessage = "Target amount must be greater than 0.")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            savingsGoalDao.updateGoal(
                existing.copy(
                    name = cleanName,
                    targetAmount = targetAmount,
                    deadline = deadline,
                    isCompleted = existing.currentAmount >= targetAmount
                )
            )
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                successMessage = "Goal updated."
            )
        }
    }

    fun deleteGoal(goalId: Long) {
        val goal = _uiState.value.goals.firstOrNull { it.goalId == goalId }
        if (goal == null) {
            _uiState.value = _uiState.value.copy(errorMessage = "Goal not found.")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            savingsGoalDao.deleteGoal(goal)
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                successMessage = "Goal deleted."
            )
        }
    }

    /**
     * Simple pin/unpin behavior for UI priority list.
     *
     * Note: current DB entity has no "isPinned" field yet,
     * so this pin state is temporary (in-memory only).
     * If you need persistence, add isPinned to SavingsGoal entity later.
     */
    fun togglePinGoal(goalId: Long) {
        val updatedPins = _uiState.value.pinnedGoalIds.toMutableSet()
        if (!updatedPins.add(goalId)) {
            updatedPins.remove(goalId)
        }
        _uiState.value = _uiState.value.copy(pinnedGoalIds = updatedPins)
    }

    /**
     * Helpers for UI progress labels.
     */
    fun getGoalProgressPercentage(goal: SavingsGoal): Int {
        if (goal.targetAmount <= 0.0) return 0
        return ((goal.currentAmount / goal.targetAmount) * 100.0).toInt().coerceIn(0, 100)
    }

    fun getRemainingAmount(goal: SavingsGoal): Double {
        return (goal.targetAmount - goal.currentAmount).coerceAtLeast(0.0)
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(errorMessage = null, successMessage = null)
    }
}

data class GoalsUiState(
    val isLoading: Boolean = false,
    val activeUserId: Long? = null,
    val goals: List<SavingsGoal> = emptyList(),
    val pinnedGoalIds: Set<Long> = emptySet(),
    val selectedGoalId: Long? = null,
    val selectedGoalDeposits: List<GoalDeposit> = emptyList(),
    val errorMessage: String? = null,
    val successMessage: String? = null
)
