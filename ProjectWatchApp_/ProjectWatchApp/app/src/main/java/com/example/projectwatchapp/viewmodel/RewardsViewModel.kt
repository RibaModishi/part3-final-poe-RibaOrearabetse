package com.example.projectwatchapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.projectwatchapp.data.dao.EarnedBadgeDao
import com.example.projectwatchapp.data.dao.UserDao
import com.example.projectwatchapp.data.entities.EarnedBadge
import com.example.projectwatchapp.data.entities.User
import com.example.projectwatchapp.utils.SessionManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * RewardsViewModel = business logic for gamification.
 *
 * Handles:
 * - Current user level + XP display
 * - Badge collection loading
 * - Granting badges (once only)
 * - Adding badge XP reward to user profile
 * - Level progress helper values for UI
 */
class RewardsViewModel(
    private val earnedBadgeDao: EarnedBadgeDao,
    private val userDao: UserDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(RewardsUiState())
    val uiState: StateFlow<RewardsUiState> = _uiState.asStateFlow()

    private var userObserverJob: Job? = null
    private var badgesObserverJob: Job? = null

    /**
     * Begin observing both the user profile and earned badges.
     * Use this after login when opening Rewards screen.
     */
    fun loadRewardsData(userId: Long) {
        _uiState.value = _uiState.value.copy(activeUserId = userId, errorMessage = null)
        observeUser(userId)
        observeBadges(userId)
    }

    /**
     * Grant a badge to the user if not already earned.
     *
     * Steps:
     * 1) Ensure user exists
     * 2) Ensure badge is not duplicate
     * 3) Insert earned badge
     * 4) Add badge XP to user and update level
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

    /**
     * Helper for UI card:
     * - current XP
     * - XP needed to next level
     * - progress percent in current level band
     */
    fun getLevelProgress(): LevelProgress {
        val user = _uiState.value.user ?: return LevelProgress(0, 0, 0)
        val currentXp = user.xp
        val currentLevel = SessionManager.getLevelFromXp(currentXp)
        val xpToNext = SessionManager.getXpToNextLevel(currentXp)

        // For a simple progress number, we estimate current level start threshold
        // as (current xp - xp gained inside this level band).
        val nextThreshold = currentXp + xpToNext
        val levelBandSize = when {
            xpToNext == 0 -> 1 // already at max range
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

    /**
     * Returns all available badges with lock/unlock state for UI rendering.
     */
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

    /**
     * Approximate start XP for current level by scanning upward from 0.
     * This avoids exposing private threshold list from SessionManager.
     */
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
