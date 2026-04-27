package com.example.projectwatchapp.ui.dashboard

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.projectwatchapp.R
import com.example.projectwatchapp.data.AppDatabase
import com.example.projectwatchapp.ui.auth.LoginActivity
import com.example.projectwatchapp.ui.budget.BudgetActivity
import com.example.projectwatchapp.ui.goals.GoalsActivity
import com.example.projectwatchapp.ui.rewards.RewardsActivity
import com.example.projectwatchapp.ui.expense.ExpenseActivity
import com.example.projectwatchapp.viewmodel.CategoryViewModel
import kotlinx.coroutines.launch

/**
 * Dashboard now demonstrates another wired flow:
 * - Loads categories for current user
 * - Lets user add a category
 * - Shows current category list
 */
class DashboardActivity : ComponentActivity() {

    private val database by lazy { AppDatabase.getDatabase(this) }
    private val categoryViewModel: CategoryViewModel by viewModels {
        CategoryViewModelFactory(database)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        val userId = intent.getLongExtra(LoginActivity.EXTRA_USER_ID, -1L)
        val welcomeText = findViewById<TextView>(R.id.textViewDashboardWelcome)
        val categoryInput = findViewById<EditText>(R.id.editTextCategoryName)
        val addButton = findViewById<Button>(R.id.buttonAddCategory)
        val openExpenseButton = findViewById<Button>(R.id.buttonOpenExpenses)
        val openBudgetButton = findViewById<Button>(R.id.buttonOpenBudget)
        val openGoalsButton = findViewById<Button>(R.id.buttonOpenGoals)
        val openRewardsButton = findViewById<Button>(R.id.buttonOpenRewards)
        val categoryListText = findViewById<TextView>(R.id.textViewCategoryList)
        val loadingText = findViewById<TextView>(R.id.textViewDashboardLoading)

        welcomeText.text = if (userId > 0) {
            "Welcome to Dashboard!\nUser ID: $userId"
        } else {
            "Welcome to Dashboard!"
        }

        if (userId > 0) {
            categoryViewModel.loadCategories(userId)
        }

        addButton.setOnClickListener {
            categoryViewModel.addCategory(name = categoryInput.text.toString())
        }

        // Opens the Expense screen to test creation + period filtering.
        openExpenseButton.setOnClickListener {
            if (userId > 0) {
                startActivity(
                    Intent(this, ExpenseActivity::class.java)
                        .putExtra(LoginActivity.EXTRA_USER_ID, userId)
                )
            } else {
                Toast.makeText(this, "User is not available.", Toast.LENGTH_SHORT).show()
            }
        }

        openBudgetButton.setOnClickListener {
            if (userId > 0) {
                startActivity(
                    Intent(this, BudgetActivity::class.java)
                        .putExtra(LoginActivity.EXTRA_USER_ID, userId)
                )
            } else {
                Toast.makeText(this, "User is not available.", Toast.LENGTH_SHORT).show()
            }
        }

        openGoalsButton.setOnClickListener {
            if (userId > 0) {
                startActivity(
                    Intent(this, GoalsActivity::class.java)
                        .putExtra(LoginActivity.EXTRA_USER_ID, userId)
                )
            } else {
                Toast.makeText(this, "User is not available.", Toast.LENGTH_SHORT).show()
            }
        }

        openRewardsButton.setOnClickListener {
            if (userId > 0) {
                startActivity(
                    Intent(this, RewardsActivity::class.java)
                        .putExtra(LoginActivity.EXTRA_USER_ID, userId)
                )
            } else {
                Toast.makeText(this, "User is not available.", Toast.LENGTH_SHORT).show()
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                categoryViewModel.uiState.collect { state ->
                    loadingText.visibility = if (state.isLoading) View.VISIBLE else View.GONE

                    categoryListText.text = if (state.categories.isEmpty()) {
                        "No categories yet."
                    } else {
                        state.categories.joinToString(separator = "\n") { "• ${it.name}" }
                    }

                    state.errorMessage?.let { message ->
                        Toast.makeText(this@DashboardActivity, message, Toast.LENGTH_SHORT).show()
                        categoryViewModel.clearMessages()
                    }
                    state.successMessage?.let { message ->
                        Toast.makeText(this@DashboardActivity, message, Toast.LENGTH_SHORT).show()
                        categoryInput.text?.clear()
                        categoryViewModel.clearMessages()
                    }
                }
            }
        }
    }
}

/**
 * Factory needed because CategoryViewModel needs a DAO constructor parameter.
 */
class CategoryViewModelFactory(
    private val database: AppDatabase
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CategoryViewModel::class.java)) {
            return CategoryViewModel(database.categoryDao()) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
