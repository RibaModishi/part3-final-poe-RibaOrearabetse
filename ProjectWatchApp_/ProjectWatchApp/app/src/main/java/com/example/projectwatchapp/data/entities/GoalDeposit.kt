package com.example.projectwatchapp.data.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "goal_deposits",
    foreignKeys = [
        ForeignKey(
            entity = SavingsGoal::class,
            parentColumns = ["goalId"],
            childColumns = ["goalId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("goalId"), Index("date")]
)
data class GoalDeposit(
    @PrimaryKey(autoGenerate = true)
    val depositId: Long = 0,
    val goalId: Long,
    val amount: Double,
    val date: Long = System.currentTimeMillis(),
    val note: String? = null,
    val xpEarned: Int = 0
)