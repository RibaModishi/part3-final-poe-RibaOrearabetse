package com.example.projectwatchapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.projectwatchapp.data.dao.EarnedBadgeDao
import com.example.projectwatchapp.data.dao.ExpenseDao
import com.example.projectwatchapp.data.dao.UserDao
import com.example.projectwatchapp.data.entities.EarnedBadge
import com.example.projectwatchapp.data.entities.Expense
import com.example.projectwatchapp.firebase.FirebaseRealtimeDatabaseService
import com.example.projectwatchapp.utils.SessionManager
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ExpenseViewModel(
    private val expenseDao: ExpenseDao,
    private val userDao: UserDao,
    private val earnedBadgeDao: EarnedBadgeDao,
    private val firebaseDatabase: FirebaseRealtimeDatabaseService = FirebaseRealtimeDatabaseService()
) : ViewModel() {

    companion object {
        const val SUCCESS_MESSAGE_EXPENSE_ADDED = "Expense added."
    }

    private val _uiState = MutableStateFlow(ExpenseUiState())
    val uiState: StateFlow<ExpenseUiState> = _uiState.asStateFlow()

    private var expensesObserverJob: Job? = null

    fun loadAllExpenses(userId: Long) {
        _uiState.value = _uiState.value.copy(
            activeUserId = userId,
            activeFilter = ExpenseFilter.All,
            errorMessage = null
        )
        observeExpenses(ExpenseFilter.All)
    }

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

            val isFirstExpense = expenseDao.countExpensesForUser(userId) == 0L

            val newExpense = Expense(
                userId = userId,
                categoryId = categoryId,
                amount = amount,
                description = cleanDescription,
                date = date,
                notes = notes?.trim()?.takeIf { it.isNotEmpty() },
                photoPath = photoPath?.trim()?.takeIf { it.isNotEmpty() }
            )
            val insertedId = expenseDao.insertExpense(newExpense)
            val expenseForCloud = newExpense.copy(expenseId = insertedId)

            // Push the new expense to the online Firebase database
            val cloudSyncError = firebaseDatabase.writeExpense(userId, expenseForCloud)
                .exceptionOrNull()
                ?.message

            // Award XP for every expense
            awardXp(userId, SessionManager.calculateXpForAction("ADD_EXPENSE"))

            // Award FIRST_EXPENSE badge if this is their very first expense
            var badgeAwarded: SessionManager.Badge? = null
            if (isFirstExpense) {
                val alreadyEarned = earnedBadgeDao.hasUserEarnedBadge(userId, SessionManager.Badge.FIRST_EXPENSE.type)
                if (!alreadyEarned) {
                    earnedBadgeDao.insertBadge(
                        EarnedBadge(
                            userId = userId,
                            badgeType = SessionManager.Badge.FIRST_EXPENSE.type,
                            xpReward = SessionManager.Badge.FIRST_EXPENSE.xpReward
                        )
                    )
                    awardXp(userId, SessionManager.Badge.FIRST_EXPENSE.xpReward)
                    badgeAwarded = SessionManager.Badge.FIRST_EXPENSE
                }
            }

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                successMessage = SUCCESS_MESSAGE_EXPENSE_ADDED,
                badgeEarned = badgeAwarded,
                cloudSyncMessage = cloudSyncError?.let {
                    "Saved on device only. Cloud sync failed: $it"
                }
            )
        }
    }

    fun deleteExpense(expenseId: Long) {
        val expense = _uiState.value.expenses.firstOrNull { it.expenseId == expenseId }
        if (expense == null) {
            _uiState.value = _uiState.value.copy(errorMessage = "Expense not found.")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            expense.photoPath?.let { path -> runCatching { File(path).delete() } }
            expenseDao.deleteExpense(expense)
            firebaseDatabase.deleteExpense(expense.userId, expense.expenseId)
            _uiState.value = _uiState.value.copy(isLoading = false, successMessage = "Expense deleted.")
        }
    }

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

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(errorMessage = null, successMessage = null)
    }

    fun clearBadge() {
        _uiState.value = _uiState.value.copy(badgeEarned = null)
    }

    fun clearCloudSyncMessage() {
        _uiState.value = _uiState.value.copy(cloudSyncMessage = null)
    }

    /**
     * Pulls expenses from Firebase and merges any missing rows into local Room storage.
     * Call after login or when the user opens the expenses screen with network available.
     */
    fun syncExpensesFromCloud() {
        val userId = _uiState.value.activeUserId ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, cloudSyncMessage = null)
            firebaseDatabase.readExpensesForUser(userId)
                .onSuccess { cloudExpenses ->
                    cloudExpenses.forEach { cloudExpense ->
                        val exists = expenseDao.getExpenseByIdOnce(cloudExpense.expenseId) != null
                        if (!exists) {
                            expenseDao.insertExpense(cloudExpense)
                        }
                    }
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        cloudSyncMessage = "Synced ${cloudExpenses.size} expense(s) from cloud."
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        cloudSyncMessage = "Cloud sync failed: ${error.message}"
                    )
                }
        }
    }

    private suspend fun awardXp(userId: Long, xp: Int) {
        if (xp <= 0) return
        val user = userDao.getUserByIdOnce(userId) ?: return
        val newXp = user.xp + xp
        val newLevel = SessionManager.getLevelFromXp(newXp)
        userDao.updateXpAndLevel(userId, newXp, newLevel)
    }

    private fun observeExpenses(filter: ExpenseFilter) {
        val userId = _uiState.value.activeUserId ?: return
        expensesObserverJob?.cancel()
        expensesObserverJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            when (filter) {
                ExpenseFilter.All -> {
                    expenseDao.getExpensesForUser(userId).collect { list ->
                        _uiState.value = _uiState.value.copy(isLoading = false, expenses = list)
                    }
                }
                is ExpenseFilter.Period -> {
                    expenseDao.getExpensesBetween(userId, filter.startDate, filter.endDate).collect { list ->
                        _uiState.value = _uiState.value.copy(isLoading = false, expenses = list)
                    }
                }
            }
        }
    }
}

sealed class ExpenseFilter {
    data object All : ExpenseFilter()
    data class Period(val startDate: Long, val endDate: Long) : ExpenseFilter()
}

data class ExpenseUiState(
    val isLoading: Boolean = false,
    val activeUserId: Long? = null,
    val activeFilter: ExpenseFilter = ExpenseFilter.All,
    val expenses: List<Expense> = emptyList(),
    val totalSpentInActivePeriod: Double = 0.0,
    val categoryTotalsInActivePeriod: Map<Long, Double> = emptyMap(),
    val badgeEarned: SessionManager.Badge? = null,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val cloudSyncMessage: String? = null
)