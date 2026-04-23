package com.example.projectwatchapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.projectwatchapp.data.dao.EarnedBadgeDao
import com.example.projectwatchapp.data.dao.UserDao
import com.example.projectwatchapp.data.entities.EarnedBadge
import com.example.projectwatchapp.utils.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RewardsViewModel(
    private val earnedBadgeDao: EarnedBadgeDao,
    private val userDao: UserDao
) : ViewModel() {

    private val _activeUserId = MutableStateFlow<Long?>(null)
    val activeUserId: StateFlow<Long?> = _activeUserId.asStateFlow()

    val badges = _activeUserId
        .flatMapLatest { userId ->
            if (userId == null) flowOf(emptyList()) else earnedBadgeDao.getBadgesForUser(userId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun setActiveUser(userId: Long?) {
        _activeUserId.value = userId
    }

    fun awardBadgeIfMissing(userId: Long, badge: SessionManager.Badge) {
        viewModelScope.launch {
            try {
                val alreadyEarned = earnedBadgeDao.hasUserEarnedBadge(userId, badge.type)
                if (alreadyEarned) return@launch

                earnedBadgeDao.insertBadge(
                    EarnedBadge(
                        userId = userId,
                        badgeType = badge.type,
                        xpReward = badge.xpReward
                    )
                )

                updateUserXpFromBadge(userId, badge.xpReward)
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to award badge."
            }
        }
    }

    fun getTotalBadgeXp(userId: Long, onResult: (Int) -> Unit) {
        viewModelScope.launch {
            val xp = earnedBadgeDao.getTotalXpFromBadges(userId) ?: 0
            onResult(xp)
        }
    }

    private suspend fun updateUserXpFromBadge(userId: Long, xpReward: Int) {
        val user = userDao.getUserById(userId).first() ?: return

        val newXp = user.xp + xpReward
        val newLevel = SessionManager.getLevelFromXp(newXp)
        userDao.updateXpAndLevel(userId, newXp, newLevel)
    }
}
