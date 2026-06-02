package com.example.projectwatchapp.firebase

import com.example.projectwatchapp.data.entities.Expense

/**
 * Plain data object stored in Firebase Realtime Database (online).
 * Firebase serializes this to JSON under each user's expenses node.
 */
data class FirebaseExpenseRecord(
    val expenseId: Long = 0L,
    val userId: Long = 0L,
    val categoryId: Long? = null,
    val amount: Double = 0.0,
    val description: String = "",
    val date: Long = 0L,
    val isRecurring: Boolean = false,
    val recurringInterval: String? = null,
    val notes: String? = null,
    val xpEarned: Int = 0,
    val photoPath: String? = null
) {
    /** Converts a local Room [Expense] into the cloud-friendly record shape. */
    fun toExpense(): Expense = Expense(
        expenseId = expenseId,
        userId = userId,
        categoryId = categoryId,
        amount = amount,
        description = description,
        date = date,
        isRecurring = isRecurring,
        recurringInterval = recurringInterval,
        notes = notes,
        xpEarned = xpEarned,
        photoPath = photoPath
    )

    companion object {
        /** Builds a [FirebaseExpenseRecord] from a local [Expense] before writing online. */
        fun fromExpense(expense: Expense): FirebaseExpenseRecord = FirebaseExpenseRecord(
            expenseId = expense.expenseId,
            userId = expense.userId,
            categoryId = expense.categoryId,
            amount = expense.amount,
            description = expense.description,
            date = expense.date,
            isRecurring = expense.isRecurring,
            recurringInterval = expense.recurringInterval,
            notes = expense.notes,
            xpEarned = expense.xpEarned,
            photoPath = expense.photoPath
        )
    }
}
