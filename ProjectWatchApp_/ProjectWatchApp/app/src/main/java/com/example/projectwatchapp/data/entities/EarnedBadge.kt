package com.example.projectwatchapp.data.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "earned_badges",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["userId"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("userId"), Index("badgeType")]
)
data class EarnedBadge(
    @PrimaryKey(autoGenerate = true)
    val badgeId: Long = 0,
    val userId: Long,
    val badgeType: String,
    val earnedDate: Long = System.currentTimeMillis(),
    val xpReward: Int
)