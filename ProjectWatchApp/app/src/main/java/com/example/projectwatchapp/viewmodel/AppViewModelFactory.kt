package com.example.projectwatchapp.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.projectwatchapp.data.AppDatabase

/*
 * Riba change summary:
 * Central factory that creates each ViewModel with the correct DAO(s),
 * so Activities/Fragments do not manually build database dependencies.
 */
class AppViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {

    // Lazy = only create DB instance when first ViewModel is requested.
    private val database: AppDatabase by lazy {
        AppDatabase.getDatabase(application)
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        // Map requested ViewModel type -> concrete constructor + DAOs.
        return when {
            modelClass.isAssignableFrom(UserViewModel::class.java) ->
                UserViewModel(database.userDao()) as T

            modelClass.isAssignableFrom(CategoryViewModel::class.java) ->
                CategoryViewModel(database.categoryDao()) as T

            modelClass.isAssignableFrom(ExpenseViewModel::class.java) ->
                ExpenseViewModel(database.expenseDao()) as T

            modelClass.isAssignableFrom(BudgetViewModel::class.java) ->
                BudgetViewModel(database.budgetDao(), database.expenseDao()) as T

            modelClass.isAssignableFrom(GoalsViewModel::class.java) ->
                GoalsViewModel(database.savingsGoalDao(), database.goalDepositDao()) as T

            modelClass.isAssignableFrom(RewardsViewModel::class.java) ->
                RewardsViewModel(
                    database.earnedBadgeDao(),
                    database.userDao()
                ) as T

            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
