package com.example.projectwatchapp.firebase

import com.example.projectwatchapp.data.entities.Expense
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import android.util.Log
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await

/**
 * Reads and writes app data to **Firebase Realtime Database** (Google's hosted online database).
 *
 * Setup (one-time, in Firebase Console):
 * 1. Create a project at https://console.firebase.google.com
 * 2. Add an Android app with package `com.example.projectwatchapp`
 * 3. Download `google-services.json` into the `app/` module folder
 * 4. Enable **Realtime Database** → Create database → choose a region
 * 5. For development, set rules to allow read/write (tighten for production)
 *
 * Data layout in the cloud:
 *   project_watch_app / users / {userId} / expenses / {expenseId} → [FirebaseExpenseRecord JSON]
 */
class FirebaseRealtimeDatabaseService(
    private val database: FirebaseDatabase = FirebaseDatabase.getInstance()
) {

    companion object {
        private const val TAG = "FirebaseDatabase"

        /** Top-level node so this app's data stays grouped in the Firebase console. */
        private const val ROOT_NODE = "project_watch_app"

        /** All per-user records live under `users`. */
        private const val USERS_NODE = "users"

        /** Each user's expenses are stored under `expenses`. */
        private const val EXPENSES_NODE = "expenses"
    }

    /**
     * Returns a reference to one user's expense collection in the online database.
     */
    private fun userExpensesRef(userId: Long) =
        database.reference
            .child(ROOT_NODE)
            .child(USERS_NODE)
            .child(userId.toString())
            .child(EXPENSES_NODE)

    /**
     * **Write (create/update):** Saves a single expense to Firebase.
     * Uses the expense id as the key so the same record can be updated later.
     */
    suspend fun writeExpense(userId: Long, expense: Expense): Result<Unit> = runCatching {
        val record = FirebaseExpenseRecord.fromExpense(expense)
        val path = "$ROOT_NODE/$USERS_NODE/$userId/$EXPENSES_NODE/${expense.expenseId}"
        Log.d(TAG, "Writing expense to $path")
        userExpensesRef(userId)
            .child(expense.expenseId.toString())
            .setValue(record)
            .await()
        Log.d(TAG, "Write succeeded for expense ${expense.expenseId}")
        Unit
    }.onFailure { error ->
        Log.e(TAG, "Write failed: ${error.message}", error)
        Unit
    }

    /**
     * **Write (delete):** Removes one expense from the online database.
     */
    suspend fun deleteExpense(userId: Long, expenseId: Long): Result<Unit> = runCatching {
        userExpensesRef(userId)
            .child(expenseId.toString())
            .removeValue()
            .await()
    }

    /**
     * **Read:** Fetches all expenses for a user from Firebase in one request.
     * Returns an empty list if the user has no cloud data yet.
     */
    suspend fun readExpensesForUser(userId: Long): Result<List<Expense>> = runCatching {
        val snapshot = userExpensesRef(userId).get().await()
        snapshot.children.mapNotNull { child ->
            child.getValue(FirebaseExpenseRecord::class.java)?.toExpense()
        }
    }

    /**
     * **Read (live updates):** Subscribes to online changes and delivers the latest list
     * whenever data is added, changed, or removed in Firebase.
     * Call [stopListening] with the returned handle when the screen is destroyed.
     */
    fun listenToExpenses(
        userId: Long,
        onExpensesChanged: (List<Expense>) -> Unit,
        onError: (String) -> Unit
    ): ValueEventListener {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val expenses = snapshot.children.mapNotNull { child ->
                    child.getValue(FirebaseExpenseRecord::class.java)?.toExpense()
                }
                onExpensesChanged(expenses)
            }

            override fun onCancelled(error: DatabaseError) {
                onError(error.message)
            }
        }
        userExpensesRef(userId).addValueEventListener(listener)
        return listener
    }

    /**
     * Stops a listener created by [listenToExpenses] to avoid memory leaks.
     */
    fun stopListening(userId: Long, listener: ValueEventListener) {
        userExpensesRef(userId).removeEventListener(listener)
    }

    /**
     * **Read (single record):** Loads one expense by id from the online database.
     */
    suspend fun readExpense(userId: Long, expenseId: Long): Result<Expense?> = runCatching {
        val snapshot = userExpensesRef(userId).child(expenseId.toString()).get().await()
        snapshot.getValue(FirebaseExpenseRecord::class.java)?.toExpense()
    }

    /**
     * One-shot connectivity check: verifies that Firebase accepted a minimal write.
     * Useful when debugging `google-services.json` or database rules.
     */
    suspend fun pingDatabase(): Result<Unit> = suspendCancellableCoroutine { cont ->
        database.reference.child(ROOT_NODE).child("_ping").setValue(System.currentTimeMillis())
            .addOnSuccessListener { cont.resume(Result.success(Unit)) }
            .addOnFailureListener { cont.resume(Result.failure(it)) }
    }
}
