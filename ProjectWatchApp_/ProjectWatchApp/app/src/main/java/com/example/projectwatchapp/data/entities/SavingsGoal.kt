package com.example.projectwatchapp.data.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "savings_goals",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["userId"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("userId")]
)
data class SavingsGoal(
    @PrimaryKey(autoGenerate = true)
    val goalId: Long = 0,
    val userId: Long,
    val name: String,
    val targetAmount: Double,
    val currentAmount: Double = 0.0,
    val deadline: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val isCompleted: Boolean = false,
    val colorHex: String = "#4CAF50",
    val iconName: String = "savings"
)