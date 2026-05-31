package com.example.projectwatchapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.projectwatchapp.data.dao.BudgetDao
import com.example.projectwatchapp.data.dao.CategoryDao
import com.example.projectwatchapp.data.dao.EarnedBadgeDao
import com.example.projectwatchapp.data.dao.ExpenseDao
import com.example.projectwatchapp.data.dao.SavingsGoalDao
import com.example.projectwatchapp.data.dao.UserDao
import com.example.projectwatchapp.data.entities.EarnedBadge
import com.example.projectwatchapp.data.entities.User
import com.example.projectwatchapp.utils.SessionManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class RewardsViewModel(
    private val earnedBadgeDao: EarnedBadgeDao,
    private val userDao: UserDao,
    private val expenseDao: ExpenseDao,
    private val savingsGoalDao: SavingsGoalDao,
    private val categoryDao: CategoryDao,
    private val budgetDao: BudgetDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(RewardsUiState())
    val uiState: StateFlow<RewardsUiState> = _uiState.asStateFlow()

    private var userObserverJob: Job? = null
    private var badgesObserverJob: Job? = null

    fun loadRewardsData(userId: Long) {
        _uiState.value = _uiState.value.copy(activeUserId = userId, errorMessage = null)
        observeUser(userId)
        observeBadges(userId)
        viewModelScope.launch {
            checkAndAutoAwardBadges(userId)
        }
    }

    /**
     * Automatically checks conditions and awards badges the user has earned
     * but hasn't been given yet. Skips: night_owl, pocket_watch_guardian, week_streak.
     */
    private suspend fun checkAndAutoAwardBadges(userId: Long) {
        val user = _uiState.value.user ?: userDao.getUserById(userId).first() ?: return

        // --- FIRST_EXPENSE: has at least 1 expense ---
        val expenseCount = expenseDao.countExpensesForUser(userId)
        if (expenseCount >= 1) {
            tryAutoAward(userId, user, SessionManager.Badge.FIRST_EXPENSE)
        }

        // --- SAVINGS_STARTER: has at least 1 savings goal created ---
        val goals = savingsGoalDao.getGoalsForUser(userId).first()
        if (goals.isNotEmpty()) {
            tryAutoAward(userId, user, SessionManager.Badge.SAVINGS_STARTER)
        }

        // --- GOAL_CRUSHER: has at least 1 completed savings goal ---
        val completedGoals = goals.filter { it.isCompleted }
        if (completedGoals.isNotEmpty()) {
            tryAutoAward(userId, user, SessionManager.Badge.GOAL_CRUSHER)
        }

        // --- CATEGORY_WIZARD: has at least 3 categories ---
        val categories = categoryDao.getCategoriesForUser(userId).first()
        if (categories.size >= 3) {
            tryAutoAward(userId, user, SessionManager.Badge.CATEGORY_WIZARD)
        }

        // --- BUDGET_MASTER: has at least 1 active budget set ---
        val budgets = budgetDao.getActiveBudgetsForUser(userId).first()
        if (budgets.isNotEmpty()) {
            tryAutoAward(userId, user, SessionManager.Badge.BUDGET_MASTER)
        }
    }

    /**
     * Awards a badge silently (no toast) if not already earned.
     * Re-fetches current user XP fresh each time to avoid stale accumulation.
     */
    private suspend fun tryAutoAward(userId: Long, user: User, badge: SessionManager.Badge) {
        val alreadyEarned = earnedBadgeDao.hasUserEarnedBadge(userId, badge.type)
        if (alreadyEarned) return

        earnedBadgeDao.insertBadge(
            EarnedBadge(
                userId = userId,
                badgeType = badge.type,
                xpReward = badge.xpReward
            )
        )

        // Re-fetch latest user to get current XP (avoids double-adding from stale state)
        val freshUser = userDao.getUserById(userId).first() ?: return
        val updatedXp = freshUser.xp + badge.xpReward
        val updatedLevel = SessionManager.getLevelFromXp(updatedXp)
        userDao.updateXpAndLevel(
            userId = userId,
            newXp = updatedXp,
            newLevel = updatedLevel
        )
    }

    /**
     * Manual award from the spinner/button (used for testing or manual grants).
     */
    fun awardBadge(badge: SessionManager.Badge) {
        val userId = _uiState.value.activeUserId
        if (userId == null) {
            _uiState.value = _uiState.value.copy(errorMessage = "Load rewards first to set active user.")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            val alreadyEarned = earnedBadgeDao.hasUserEarnedBadge(userId, badge.type)
            if (alreadyEarned) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    successMessage = "Badge already earned."
                )
                return@launch
            }

            val user = _uiState.value.user
            if (user == null) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "User profile not loaded yet."
                )
                return@launch
            }

            earnedBadgeDao.insertBadge(
                EarnedBadge(
                    userId = userId,
                    badgeType = badge.type,
                    xpReward = badge.xpReward
                )
            )

            val updatedXp = user.xp + badge.xpReward
            val updatedLevel = SessionManager.getLevelFromXp(updatedXp)
            userDao.updateXpAndLevel(
                userId = userId,
                newXp = updatedXp,
                newLevel = updatedLevel
            )

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                successMessage = "New badge earned: ${badge.type}"
            )
        }
    }

    fun getLevelProgress(): LevelProgress {
        val user = _uiState.value.user ?: return LevelProgress(0, 0, 0)
        val currentXp = user.xp
        val currentLevel = SessionManager.getLevelFromXp(currentXp)
        val xpToNext = SessionManager.getXpToNextLevel(currentXp)

        val nextThreshold = currentXp + xpToNext
        val levelBandSize = when {
            xpToNext == 0 -> 1
            else -> (nextThreshold - estimateLevelStart(currentXp)).coerceAtLeast(1)
        }
        val progressInBand = when {
            xpToNext == 0 -> 100
            else -> (((levelBandSize - xpToNext).toDouble() / levelBandSize) * 100).toInt().coerceIn(0, 100)
        }

        return LevelProgress(
            level = currentLevel,
            xpToNextLevel = xpToNext,
            progressPercent = progressInBand
        )
    }

    fun getBadgeCollectionState(): List<BadgeUiModel> {
        val earnedTypes = _uiState.value.earnedBadges.map { it.badgeType }.toSet()
        return SessionManager.Badge.entries.map { badge ->
            BadgeUiModel(
                type = badge.type,
                xpReward = badge.xpReward,
                isEarned = badge.type in earnedTypes
            )
        }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(errorMessage = null, successMessage = null)
    }

    private fun observeUser(userId: Long) {
        userObserverJob?.cancel()
        userObserverJob = viewModelScope.launch {
            userDao.getUserById(userId).collect { loadedUser ->
                _uiState.value = _uiState.value.copy(
                    user = loadedUser,
                    isLoading = false
                )
            }
        }
    }

    private fun observeBadges(userId: Long) {
        badgesObserverJob?.cancel()
        badgesObserverJob = viewModelScope.launch {
            earnedBadgeDao.getBadgesForUser(userId).collect { badges ->
                _uiState.value = _uiState.value.copy(
                    earnedBadges = badges,
                    totalBadgeXp = badges.sumOf { it.xpReward }
                )
            }
        }
    }

    private fun estimateLevelStart(currentXp: Int): Int {
        val level = SessionManager.getLevelFromXp(currentXp)
        var candidate = currentXp
        while (candidate > 0 && SessionManager.getLevelFromXp(candidate - 1) == level) {
            candidate--
        }
        return candidate
    }
}

data class RewardsUiState(
    val isLoading: Boolean = false,
    val activeUserId: Long? = null,
    val user: User? = null,
    val earnedBadges: List<EarnedBadge> = emptyList(),
    val totalBadgeXp: Int = 0,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

data class LevelProgress(
    val level: Int,
    val xpToNextLevel: Int,
    val progressPercent: Int
)

data class BadgeUiModel(
    val type: String,
    val xpReward: Int,
    val isEarned: Boolean
)