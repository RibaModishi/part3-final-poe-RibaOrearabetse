package com.example.projectwatchapp.ui.goals

import android.app.DatePickerDialog
import android.app.AlertDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ProgressBar
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
import com.example.projectwatchapp.data.entities.SavingsGoal
import com.example.projectwatchapp.ui.auth.LoginActivity
import com.example.projectwatchapp.ui.budget.BudgetActivity
import com.example.projectwatchapp.ui.category.CategoryActivity
import com.example.projectwatchapp.ui.dashboard.DashboardActivity
import com.example.projectwatchapp.ui.expense.ExpenseActivity
import com.example.projectwatchapp.ui.reports.ReportsActivity
import com.example.projectwatchapp.ui.rewards.RewardsActivity
import com.example.projectwatchapp.ui.common.PopupMenuUtils
import com.example.projectwatchapp.viewmodel.GoalsViewModel
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * Savings goals UI wired to [GoalsViewModel]: create goals, deposits, history, pin, delete.
 */
class GoalsActivity : ComponentActivity() {
    companion object {
        private const val MENU_DASHBOARD = 1
        private const val MENU_EXPENSES = 2
        private const val MENU_CATEGORY = 3
        private const val MENU_BUDGET = 4
        private const val MENU_GOALS = 5
        private const val MENU_REWARDS = 6
        private const val MENU_REPORTS = 7
        private const val MENU_LOGOUT = 8
    }

    private val database by lazy { AppDatabase.getDatabase(this) }
    private val goalsViewModel: GoalsViewModel by viewModels {
        GoalsViewModelFactory(database)
    }

    private val zoneId: ZoneId = ZoneId.systemDefault()
    private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val moneyFormat: NumberFormat = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }

    private var optionalDeadlineMillis: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_goals)

        val userId = intent.getLongExtra(LoginActivity.EXTRA_USER_ID, -1L)
        if (userId <= 0) {
            Toast.makeText(this, "Invalid user. Please login again.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val headerText = findViewById<TextView>(R.id.textViewGoalsHeader)
        val buttonAddGoalQuick = findViewById<Button>(R.id.buttonAddGoalQuick)
        val goalsCountText = findViewById<TextView>(R.id.textViewGoalsCount)
        val goalsCompletedText = findViewById<TextView>(R.id.textViewGoalsCompletedCount)
        val totalTargetText = findViewById<TextView>(R.id.textViewGoalsTotalTarget)
        val pinnedContainer = findViewById<LinearLayout>(R.id.layoutPinnedGoalsContainer)
        val menuButton = findViewById<ImageView>(R.id.buttonGoalsMenu)
        val logoutButton = findViewById<ImageView>(R.id.buttonGoalsLogout)
        val navDashboard = findViewById<Button>(R.id.navGoalsDashboard)
        val navExpenses = findViewById<Button>(R.id.navGoalsExpenses)
        val navCategory = findViewById<Button>(R.id.navGoalsCategory)
        val navBudget = findViewById<Button>(R.id.navGoalsBudget)
        val loadingText = findViewById<TextView>(R.id.textViewGoalsLoading)

        goalsViewModel.loadGoals(userId)

        menuButton.setOnClickListener { anchor ->
            val popup = PopupMenu(this, anchor)
            popup.menu.add(0, MENU_DASHBOARD, 0, getString(R.string.dashboard_nav_goals))
            popup.menu.add(0, MENU_EXPENSES, 1, getString(R.string.dashboard_nav_expenses))
            popup.menu.add(0, MENU_CATEGORY, 2, getString(R.string.dashboard_nav_category))
            popup.menu.add(0, MENU_BUDGET, 3, getString(R.string.dashboard_nav_budget))
            popup.menu.add(0, MENU_GOALS, 4, getString(R.string.goals_title))
            popup.menu.add(0, MENU_REWARDS, 5, getString(R.string.action_open_rewards))
            popup.menu.add(0, MENU_REPORTS, 6, getString(R.string.reports_title))
            popup.menu.add(0, MENU_LOGOUT, 7, getString(R.string.dashboard_back_to_login))
            popup.menu.findItem(MENU_DASHBOARD)?.setIcon(R.drawable.ic_nav_dashboard)
            popup.menu.findItem(MENU_EXPENSES)?.setIcon(R.drawable.ic_nav_expenses)
            popup.menu.findItem(MENU_CATEGORY)?.setIcon(R.drawable.ic_nav_category)
            popup.menu.findItem(MENU_BUDGET)?.setIcon(R.drawable.ic_nav_budget)
            popup.menu.findItem(MENU_GOALS)?.setIcon(android.R.drawable.ic_menu_myplaces)
            popup.menu.findItem(MENU_REWARDS)?.setIcon(android.R.drawable.star_big_on)
            popup.menu.findItem(MENU_REPORTS)?.setIcon(android.R.drawable.ic_menu_sort_by_size)
            popup.menu.findItem(MENU_LOGOUT)?.setIcon(R.drawable.ic_dash_logout)
            PopupMenuUtils.forceShowIcons(popup)
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    MENU_DASHBOARD -> navigateDashboard(userId)
                    MENU_EXPENSES -> navigateExpenses(userId)
                    MENU_CATEGORY -> navigateCategory(userId)
                    MENU_BUDGET -> navigateBudget(userId)
                    MENU_GOALS -> Unit
                    MENU_REWARDS -> navigateRewards(userId)
                    MENU_REPORTS -> navigateReports(userId)
                    MENU_LOGOUT -> {
                        startActivity(Intent(this, LoginActivity::class.java))
                        finish()
                    }
                }
                true
            }
            popup.show()
        }
        logoutButton.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
        navDashboard.setOnClickListener { navigateDashboard(userId) }
        navExpenses.setOnClickListener { navigateExpenses(userId) }
        navCategory.setOnClickListener { navigateCategory(userId) }
        navBudget.setOnClickListener { navigateBudget(userId) }

        buttonAddGoalQuick.setOnClickListener { showAddGoalDialog() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                goalsViewModel.uiState.collect { state ->
                    loadingText.visibility = if (state.isLoading) View.VISIBLE else View.GONE
                    headerText.text = getString(R.string.goals_header_format, "Your")

                    val sortedGoals = state.goals.sortedWith(
                        compareByDescending<SavingsGoal> { state.pinnedGoalIds.contains(it.goalId) }
                            .thenByDescending { it.createdAt }
                    )
                    goalsCountText.text = sortedGoals.size.toString()
                    goalsCompletedText.text = sortedGoals.count { it.isCompleted }.toString()
                    totalTargetText.text = "R${moneyFormat.format(sortedGoals.sumOf { it.targetAmount })}"
                    renderPinnedGoals(
                        container = pinnedContainer,
                        goals = sortedGoals,
                        pinnedIds = state.pinnedGoalIds
                    )

                    state.errorMessage?.let {
                        Toast.makeText(this@GoalsActivity, it, Toast.LENGTH_LONG).show()
                        goalsViewModel.clearMessages()
                    }
                    state.successMessage?.let {
                        val duration = if (it.contains("XP")) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
                        Toast.makeText(this@GoalsActivity, it, duration).show()
                        goalsViewModel.clearMessages()
                    }
                }
            }
        }
    }

    private fun renderPinnedGoals(
        container: LinearLayout,
        goals: List<SavingsGoal>,
        pinnedIds: Set<Long>
    ) {
        container.removeAllViews()
        if (goals.isEmpty()) {
            container.addView(TextView(this).apply {
                text = getString(R.string.goals_empty_list)
                textSize = 14f
                setTextColor(0xFF777777.toInt())
            })
            return
        }
        val displayGoals = (goals.filter { pinnedIds.contains(it.goalId) } + goals).distinctBy { it.goalId }.take(2)
        displayGoals.forEach { goal ->
            val row = layoutInflater.inflate(R.layout.item_goal_pinned, container, false)
            row.findViewById<TextView>(R.id.textViewPinnedGoalName).text = goal.name
            row.findViewById<TextView>(R.id.textViewPinnedGoalMeta).text =
                "${daysRemaining(goal.deadline)} days remaining · Target ${goal.deadline?.let { formatEpoch(it) } ?: "No deadline"}"
            val progressPct = goalsViewModel.getGoalProgressPercentage(goal)
            row.findViewById<TextView>(R.id.textViewPinnedGoalProgress).text =
                "Progress ${moneyFormat.format(goal.currentAmount)}/${moneyFormat.format(goal.targetAmount)} ($progressPct%)"
            row.findViewById<ProgressBar>(R.id.progressPinnedGoal).apply {
                progress = progressPct
                progressTintList = ColorStateList.valueOf(Color.parseColor("#5A84D6"))
            }
            row.findViewById<TextView>(R.id.textViewPinnedGoalRemaining).text =
                "${moneyFormat.format(goalsViewModel.getRemainingAmount(goal))} to go"

            val amountInput = row.findViewById<EditText>(R.id.editTextPinnedGoalDeposit)
            row.findViewById<Button>(R.id.buttonPinnedGoalUpdate).setOnClickListener {
                val amount = amountInput.text.toString().toDoubleOrNull()
                if (amount == null || amount <= 0.0) {
                    Toast.makeText(this, getString(R.string.goals_invalid_deposit), Toast.LENGTH_SHORT).show()
                } else {
                    goalsViewModel.addDeposit(goal.goalId, amount, "Updated from goals card")
                }
            }
            row.findViewById<ImageButton>(R.id.buttonPinnedGoalPin).setOnClickListener {
                goalsViewModel.togglePinGoal(goal.goalId)
            }
            row.findViewById<ImageButton>(R.id.buttonPinnedGoalDelete).setOnClickListener {
                goalsViewModel.deleteGoal(goal.goalId)
            }
            row.findViewById<Button>(R.id.buttonPinnedGoalHistory).setOnClickListener {
                goalsViewModel.loadDepositHistory(goal.goalId)
            }
            container.addView(row)
        }
    }

    private fun showAddGoalDialog() {
        val nameInput = EditText(this).apply { hint = getString(R.string.hint_goal_name) }
        val targetInput = EditText(this).apply {
            hint = getString(R.string.hint_goal_target)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val dateText = TextView(this).apply { text = getString(R.string.goal_deadline_none) }
        val pickDate = Button(this).apply { text = getString(R.string.action_pick_date) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 12, 24, 0)
            addView(nameInput)
            addView(targetInput)
            addView(dateText)
            addView(pickDate)
        }
        optionalDeadlineMillis = null
        pickDate.setOnClickListener {
            val initial = LocalDate.now()
            DatePickerDialog(
                this,
                { _, y, m, d ->
                    optionalDeadlineMillis = LocalDate.of(y, m + 1, d).atStartOfDay(zoneId).toInstant().toEpochMilli()
                    dateText.text = optionalDeadlineMillis?.let { formatEpoch(it) } ?: getString(R.string.goal_deadline_none)
                },
                initial.year, initial.monthValue - 1, initial.dayOfMonth
            ).show()
        }
        AlertDialog.Builder(this)
            .setTitle("Add Goal")
            .setView(root)
            .setPositiveButton(getString(R.string.action_add_goal)) { _, _ ->
                val target = targetInput.text.toString().toDoubleOrNull()
                if (target == null || target <= 0.0) {
                    Toast.makeText(this, getString(R.string.goals_invalid_target), Toast.LENGTH_SHORT).show()
                } else {
                    goalsViewModel.addGoal(nameInput.text.toString(), target, optionalDeadlineMillis)
                }
            }
            .setNegativeButton(getString(R.string.action_cancel), null)
            .show()
    }

    private fun daysRemaining(deadline: Long?): Int {
        deadline ?: return 0
        val today = LocalDate.now()
        val due = Instant.ofEpochMilli(deadline).atZone(zoneId).toLocalDate()
        return java.time.temporal.ChronoUnit.DAYS.between(today, due).toInt().coerceAtLeast(0)
    }

    private fun formatEpoch(epochMs: Long): String {
        return runCatching {
            Instant.ofEpochMilli(epochMs).atZone(zoneId).toLocalDate().format(dateFormatter)
        }.getOrDefault("-")
    }

    private fun navigateDashboard(userId: Long) {
        startActivity(Intent(this, DashboardActivity::class.java).putExtra(LoginActivity.EXTRA_USER_ID, userId))
        finish()
    }

    private fun navigateExpenses(userId: Long) {
        startActivity(Intent(this, ExpenseActivity::class.java).putExtra(LoginActivity.EXTRA_USER_ID, userId))
    }

    private fun navigateCategory(userId: Long) {
        startActivity(Intent(this, CategoryActivity::class.java).putExtra(LoginActivity.EXTRA_USER_ID, userId))
    }

    private fun navigateBudget(userId: Long) {
        startActivity(Intent(this, BudgetActivity::class.java).putExtra(LoginActivity.EXTRA_USER_ID, userId))
    }

    private fun navigateRewards(userId: Long) {
        startActivity(Intent(this, RewardsActivity::class.java).putExtra(LoginActivity.EXTRA_USER_ID, userId))
    }

    private fun navigateReports(userId: Long) {
        startActivity(Intent(this, ReportsActivity::class.java).putExtra(LoginActivity.EXTRA_USER_ID, userId))
    }
}

class GoalsViewModelFactory(
    private val database: AppDatabase
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GoalsViewModel::class.java)) {
            return GoalsViewModel(
                database.savingsGoalDao(),
                database.goalDepositDao(),
                database.userDao()
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
