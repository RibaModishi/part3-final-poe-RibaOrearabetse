package com.example.projectwatchapp.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// Riba change note:
// Added `startTime` and `endTime` so each expense can store a time window
// (assignment asks for start and end times).
@Entity(
    tableName = "expenses",
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
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("userId"),
        Index("categoryId"),
        Index("date")
    ]
)
data class Expense(
    @PrimaryKey(autoGenerate = true)
    val expenseId: Long = 0,
    val userId: Long,
    val categoryId: Long? = null,
    val amount: Double,
    val description: String,
    val date: Long,
    // Riba added: optional start time for an expense event.
    val startTime: Long? = null,
    // Riba added: optional end time for an expense event.
    val endTime: Long? = null,
    // Riba added earlier: optional local URI/path to expense photo evidence.
    val photoUri: String? = null,
    val isRecurring: Boolean = false,
    val recurringInterval: String? = null,
    val notes: String? = null,
    @ColumnInfo(defaultValue = "0")
    val xpEarned: Int = 0
)