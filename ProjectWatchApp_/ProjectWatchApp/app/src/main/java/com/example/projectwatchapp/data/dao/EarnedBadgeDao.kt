package com.example.projectwatchapp.data.dao

import androidx.room.*
import com.example.projectwatchapp.data.entities.EarnedBadge
import kotlinx.coroutines.flow.Flow

@Dao
interface EarnedBadgeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBadge(badge: EarnedBadge)

    @Query("SELECT * FROM earned_badges WHERE userId = :userId ORDER BY earnedDate DESC")
    fun getBadgesForUser(userId: Long): Flow<List<EarnedBadge>>

    @Query("SELECT COUNT(*) FROM earned_badges WHERE userId = :userId AND badgeType = :badgeType")
    suspend fun hasUserEarnedBadge(userId: Long, badgeType: String): Boolean

    @Query("SELECT SUM(xpReward) FROM earned_badges WHERE userId = :userId")
    suspend fun getTotalXpFromBadges(userId: Long): Int?
}