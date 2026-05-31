package com.example.projectwatchapp.ui.dashboard

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.ScrollView
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
import com.example.projectwatchapp.ui.category.CategoryActivity
import com.example.projectwatchapp.ui.goals.GoalsActivity
import com.example.projectwatchapp.ui.rewards.RewardsActivity
import com.example.projectwatchapp.ui.expense.ExpenseActivity
import com.example.projectwatchapp.ui.reports.ReportsActivity
import com.example.projectwatchapp.ui.info.InfoHelpActivity
import com.example.projectwatchapp.ui.common.PopupMenuUtils
import com.example.projectwatchapp.utils.SessionManager
import com.example.projectwatchapp.viewmodel.CategoryViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import android.widget.FrameLayout

class DashboardActivity : ComponentActivity() {
    companion object {
        private const val MENU_DASHBOARD = 1
        private const val MENU_EXPENSES = 2
        private const val MENU_CATEGORY = 3
        private const val MENU_BUDGET = 4
        private const val MENU_GOALS = 5
        private const val MENU_REWARDS = 6
        private const val MENU_REPORTS = 7
        const val MENU_INFO = 8
        private const val MENU_LOGOUT = 9
    }

    private val database by lazy { AppDatabase.getDatabase(this) }
    private val categoryViewModel: CategoryViewModel by viewModels {
        CategoryViewModelFactory(database)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        val userId = intent.getLongExtra(LoginActivity.EXTRA_USER_ID, -1L)

        // --- View bindings ---
        val welcomeText          = findViewById<TextView>(R.id.textViewDashboardWelcome)
        val categoryInput        = findViewById<EditText>(R.id.editTextCategoryName)
        val addButton            = findViewById<Button>(R.id.buttonAddCategory)
        val menuButton           = findViewById<ImageView>(R.id.buttonMenu)
        val scrollView           = findViewById<ScrollView>(R.id.scrollViewDashboard)
        val openExpenseButton    = findViewById<Button>(R.id.buttonOpenExpenses)
        val openBudgetButton     = findViewById<Button>(R.id.buttonOpenBudget)
        val openDashboardButton  = findViewById<Button>(R.id.buttonOpenGoals)
        val openCategoryButton   = findViewById<Button>(R.id.buttonOpenRewards)
        val backToLoginButton    = findViewById<Button>(R.id.buttonBackToLogin)
        val viewExpensesInlineButton = findViewById<Button>(R.id.buttonViewExpensesInline)
        val categoryListText     = findViewById<TextView>(R.id.textViewCategoryList)
        val recentExpensesText   = findViewById<TextView>(R.id.textViewRecentExpenses)
        val loadingText          = findViewById<TextView>(R.id.textViewDashboardLoading)
        val goalsCountText       = findViewById<TextView>(R.id.textViewGoalsCount)
        val badgesCountText      = findViewById<TextView>(R.id.textViewBadgesCount)
        val levelPointsText      = findViewById<TextView>(R.id.textViewLevelPoints)
        val updateBudgetButton   = findViewById<TextView>(R.id.buttonDashboardUpdate)
        val budgetValueText      = findViewById<TextView>(R.id.textViewDashboardBudgetValue)
        val remainingValueText   = findViewById<TextView>(R.id.textViewDashboardRemainingValue)
        val spentValueText       = findViewById<TextView>(R.id.textViewDashboardSpentValue)
        val progressBarTrack     = findViewById<FrameLayout>(R.id.progressBarLevel)
        val dateFormat = SimpleDateFormat("dd MMM", Locale.getDefault())

        if (userId > 0) {
            categoryViewModel.loadCategories(userId)
        }

        // --- Welcome username ---
        if (userId > 0) {
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    database.userDao().getUserById(userId).collect { user ->
                        val username = user?.username?.trim().orEmpty()
                        welcomeText.text = if (username.isNotEmpty()) {
                            getString(R.string.dashboard_welcome_back_user, username)
                        } else {
                            getString(R.string.dashboard_welcome_back)
                        }
                    }
                }
            }
        }

        // --- Level XP progress bar ---
        if (userId > 0) {
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    database.userDao().getUserById(userId).collect { user ->
                        val xp = user?.xp ?: 0
                        val level = SessionManager.getLevelFromXp(xp)
                        val xpToNext = SessionManager.getXpToNextLevel(xp)
                        levelPointsText.text = "Level $level  •  $xp pts"
                        // The layout uses a FrameLayout track with a child View whose marginEnd
// represents the unfilled portion. marginEnd=0 means 100% full.
                        val fillerView = progressBarTrack.getChildAt(0)
                        if (fillerView != null) {
                            val nextThreshold = xp + xpToNext
                            val bandSize = if (xpToNext == 0) 1
                            else (nextThreshold - estimateLevelStart(xp)).coerceAtLeast(1)
                            val percent = if (xpToNext == 0) 100
                            else (((bandSize - xpToNext).toDouble() / bandSize) * 100)
                                .toInt().coerceIn(0, 100)
                            val params = fillerView.layoutParams as FrameLayout.LayoutParams
                            // marginEnd shrinks as percent grows: 0% filled = full width margin, 100% = 0 margin
                            progressBarTrack.post {
                                val totalWidth = progressBarTrack.width
                                params.marginEnd = ((100 - percent) / 100.0 * totalWidth).toInt()
                                fillerView.layoutParams = params
                            }
                        }
                    }
                }
            }
        }

        // --- Goals count ---
        if (userId > 0) {
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    database.savingsGoalDao().getGoalsForUser(userId).collect { goals ->
                        val active = goals.count { !it.isCompleted }
                        goalsCountText.text = active.toString()
                    }
                }
            }
        }

        // --- Badges count ---
        if (userId > 0) {
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    database.earnedBadgeDao().getBadgesForUser(userId).collect { badges ->
                        badgesCountText.text = badges.size.toString()
                    }
                }
            }
        }

        // --- Monthly Summary: Budget / Spent / Remaining ---
        if (userId > 0) {
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    // Get start and end of current month
                    val cal = Calendar.getInstance()
                    cal.set(Calendar.DAY_OF_MONTH, 1)
                    cal.set(Calendar.HOUR_OF_DAY, 0)
                    cal.set(Calendar.MINUTE, 0)
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                    val monthStart = cal.timeInMillis
                    cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
                    cal.set(Calendar.HOUR_OF_DAY, 23)
                    cal.set(Calendar.MINUTE, 59)
                    cal.set(Calendar.SECOND, 59)
                    val monthEnd = cal.timeInMillis

                    combine(
                        database.budgetDao().getActiveBudgetsForUser(userId),
                        database.expenseDao().observeTotalSpentBetween(userId, monthStart, monthEnd)
                    ) { budgets, spent ->
                        val totalBudget = budgets.sumOf { it.amount }
                        val totalSpent = spent ?: 0.0
                        val remaining = (totalBudget - totalSpent).coerceAtLeast(0.0)
                        Triple(totalBudget, totalSpent, remaining)
                    }.collect { (budget, spent, remaining) ->
                        budgetValueText.text    = "R%.0f".format(budget)
                        spentValueText.text     = "R%.0f".format(spent)
                        remainingValueText.text = "R%.0f".format(remaining)
                    }
                }
            }
        }

        // --- Recent expenses ---
        if (userId > 0) {
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    database.expenseDao().getExpensesForUser(userId).collect { expenses ->
                        val recent = expenses.take(3)
                        recentExpensesText.text = if (recent.isEmpty()) {
                            getString(R.string.dashboard_recent_expenses_empty)
                        } else {
                            recent.joinToString(separator = "\n\n") { expense ->
                                val dateText = dateFormat.format(Date(expense.date))
                                val amount = String.format(Locale.getDefault(), "-R%.0f", expense.amount)
                                "${expense.description}\n$dateText  •  $amount"
                            }
                        }
                    }
                }
            }
        }

        // --- Category list ---
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

        // --- Button listeners ---
        addButton.setOnClickListener {
            categoryViewModel.addCategory(name = categoryInput.text.toString())
        }

        menuButton.setOnClickListener {
            val popup = PopupMenu(this, it)
            popup.menu.add(0, MENU_DASHBOARD, 0, getString(R.string.dashboard_nav_goals))
            popup.menu.add(0, MENU_EXPENSES, 1, getString(R.string.dashboard_nav_expenses))
            popup.menu.add(0, MENU_CATEGORY, 2, getString(R.string.dashboard_nav_category))
            popup.menu.add(0, MENU_BUDGET, 3, getString(R.string.dashboard_nav_budget))
            popup.menu.add(0, MENU_GOALS, 4, getString(R.string.action_open_goals))
            popup.menu.add(0, MENU_REWARDS, 5, getString(R.string.action_open_rewards))
            popup.menu.add(0, MENU_REPORTS, 6, getString(R.string.reports_title))
            popup.menu.add(0, MENU_INFO, 7, getString(R.string.info_help_title))
            popup.menu.add(0, MENU_LOGOUT, 8, getString(R.string.dashboard_back_to_login))
            popup.menu.findItem(MENU_DASHBOARD)?.setIcon(R.drawable.ic_nav_dashboard)
            popup.menu.findItem(MENU_EXPENSES)?.setIcon(R.drawable.ic_nav_expenses)
            popup.menu.findItem(MENU_CATEGORY)?.setIcon(R.drawable.ic_nav_category)
            popup.menu.findItem(MENU_BUDGET)?.setIcon(R.drawable.ic_nav_budget)
            popup.menu.findItem(MENU_GOALS)?.setIcon(android.R.drawable.ic_menu_myplaces)
            popup.menu.findItem(MENU_REWARDS)?.setIcon(android.R.drawable.star_big_on)
            popup.menu.findItem(MENU_REPORTS)?.setIcon(android.R.drawable.ic_menu_sort_by_size)
            popup.menu.findItem(MENU_INFO)?.setIcon(android.R.drawable.ic_menu_help)
            popup.menu.findItem(MENU_LOGOUT)?.setIcon(R.drawable.ic_dash_logout)
            PopupMenuUtils.forceShowIcons(popup)
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    MENU_DASHBOARD -> scrollView.smoothScrollTo(0, 0)
                    MENU_EXPENSES  -> navigateToExpense(userId)
                    MENU_CATEGORY  -> navigateToCategory(userId)
                    MENU_BUDGET    -> navigateToBudget(userId)
                    MENU_GOALS     -> navigateToGoals(userId)
                    MENU_REWARDS   -> navigateToRewards(userId)
                    MENU_REPORTS   -> navigateToReports(userId)
                    MENU_INFO      -> navigateToInfo(userId)
                    MENU_LOGOUT    -> {
                        startActivity(Intent(this, LoginActivity::class.java))
                        finish()
                    }
                }
                true
            }
            popup.show()
        }

        backToLoginButton.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
        viewExpensesInlineButton.setOnClickListener { navigateToExpense(userId) }
        openExpenseButton.setOnClickListener { navigateToExpense(userId) }
        openBudgetButton.setOnClickListener { navigateToBudget(userId) }
        updateBudgetButton.setOnClickListener { navigateToBudget(userId) }
        openDashboardButton.setOnClickListener { scrollView.smoothScrollTo(0, 0) }
        openCategoryButton.setOnClickListener { navigateToCategory(userId) }
    }

    /** Mirrors estimateLevelStart from RewardsViewModel for progress bar calculation. */
    private fun estimateLevelStart(currentXp: Int): Int {
        val level = SessionManager.getLevelFromXp(currentXp)
        var candidate = currentXp
        while (candidate > 0 && SessionManager.getLevelFromXp(candidate - 1) == level) {
            candidate--
        }
        return candidate
    }

    private fun navigateToExpense(userId: Long) {
        if (userId > 0) startActivity(Intent(this, ExpenseActivity::class.java).putExtra(LoginActivity.EXTRA_USER_ID, userId))
        else Toast.makeText(this, "User is not available.", Toast.LENGTH_SHORT).show()
    }
    private fun navigateToBudget(userId: Long) {
        if (userId > 0) startActivity(Intent(this, BudgetActivity::class.java).putExtra(LoginActivity.EXTRA_USER_ID, userId))
        else Toast.makeText(this, "User is not available.", Toast.LENGTH_SHORT).show()
    }
    private fun navigateToCategory(userId: Long) {
        if (userId > 0) startActivity(Intent(this, CategoryActivity::class.java).putExtra(LoginActivity.EXTRA_USER_ID, userId))
        else Toast.makeText(this, "User is not available.", Toast.LENGTH_SHORT).show()
    }
    private fun navigateToGoals(userId: Long) {
        if (userId > 0) startActivity(Intent(this, GoalsActivity::class.java).putExtra(LoginActivity.EXTRA_USER_ID, userId))
        else Toast.makeText(this, "User is not available.", Toast.LENGTH_SHORT).show()
    }
    private fun navigateToRewards(userId: Long) {
        if (userId > 0) startActivity(Intent(this, RewardsActivity::class.java).putExtra(LoginActivity.EXTRA_USER_ID, userId))
        else Toast.makeText(this, "User is not available.", Toast.LENGTH_SHORT).show()
    }
    private fun navigateToReports(userId: Long) {
        if (userId > 0) startActivity(Intent(this, ReportsActivity::class.java).putExtra(LoginActivity.EXTRA_USER_ID, userId))
        else Toast.makeText(this, "User is not available.", Toast.LENGTH_SHORT).show()
    }
    private fun navigateToInfo(userId: Long) {
        if (userId > 0) startActivity(Intent(this, InfoHelpActivity::class.java).putExtra(LoginActivity.EXTRA_USER_ID, userId))
        else Toast.makeText(this, "User is not available.", Toast.LENGTH_SHORT).show()
    }
}

class CategoryViewModelFactory(
    private val database: AppDatabase
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CategoryViewModel::class.java)) {
            return CategoryViewModel(
                database.categoryDao(),
                database.userDao()
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}