package com.example.projectwatchapp.data.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "budgets",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["userId"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Category::class,
            parentColumns = ["categoryId"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("userId"), Index("categoryId")]
)
data class Budget(
    @PrimaryKey(autoGenerate = true)
    val budgetId: Long = 0,
    val userId: Long,
    val categoryId: Long,
    val amount: Double,
    val minimumGoalAmount: Double? = null,
    val maximumGoalAmount: Double? = null,
    val period: String,            // "monthly", "weekly", "yearly"
    val startDate: Long,
    val endDate: Long? = null,
    val isActive: Boolean = true
)