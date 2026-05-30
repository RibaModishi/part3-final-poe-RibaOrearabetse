package com.example.projectwatchapp.ui.budget

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import android.widget.PopupMenu
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.projectwatchapp.R
import com.example.projectwatchapp.data.AppDatabase
import com.example.projectwatchapp.data.entities.Category
import com.example.projectwatchapp.ui.auth.LoginActivity
import com.example.projectwatchapp.ui.category.CategoryActivity
import com.example.projectwatchapp.ui.dashboard.DashboardActivity
import com.example.projectwatchapp.ui.expense.ExpenseActivity
import com.example.projectwatchapp.ui.goals.GoalsActivity
import com.example.projectwatchapp.ui.reports.ReportsActivity
import com.example.projectwatchapp.ui.rewards.RewardsActivity
import com.example.projectwatchapp.ui.common.PopupMenuUtils
import com.example.projectwatchapp.viewmodel.BudgetViewModel
import com.example.projectwatchapp.viewmodel.MonthlyStatus
import java.text.NumberFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * Budget screen with textbox budget inputs, formatted money via [NumberFormat],
 * per-category budget cap via spinner + amount textbox, list and delete active budgets.
 */
class BudgetActivity : ComponentActivity() {
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
    private val budgetViewModel: BudgetViewModel by viewModels {
        BudgetViewModelFactory(database)
    }

    private val zoneId: ZoneId = ZoneId.systemDefault()

    private val moneyFormat: NumberFormat = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }

    private var cachedCategories: List<Category> = emptyList()
    private var budgetCategoryIdsBySpinnerIndex: List<Long?> = listOf(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_budget)

        val userId = intent.getLongExtra(LoginActivity.EXTRA_USER_ID, -1L)
        if (userId <= 0) {
            Toast.makeText(this, "Invalid user. Please login again.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val minGoalInput = findViewById<EditText>(R.id.editTextMinMonthlyGoal)
        val maxGoalInput = findViewById<EditText>(R.id.editTextMaxMonthlyGoal)
        val buttonApplyGoals = findViewById<Button>(R.id.buttonApplyMonthlyGoals)
        val textViewSummary = findViewById<TextView>(R.id.textViewBudgetSummary)
        val totalBudgetValue = findViewById<TextView>(R.id.textViewTotalBudgetValue)
        val allocatedValue = findViewById<TextView>(R.id.textViewAllocatedBudgetValue)
        val unallocatedValue = findViewById<TextView>(R.id.textViewUnallocatedBudgetValue)
        val spinnerCategory = findViewById<Spinner>(R.id.spinnerBudgetCategory)
        val categoryAmountInput = findViewById<EditText>(R.id.editTextCategoryBudgetAmount)
        val buttonSaveCategoryBudget = findViewById<Button>(R.id.buttonSaveCategoryBudget)
        val categoryRowsContainer = findViewById<LinearLayout>(R.id.layoutBudgetCategoryRows)
        val textViewBudgetList = findViewById<TextView>(R.id.textViewActiveBudgetsList)
        val editDeleteBudgetId = findViewById<EditText>(R.id.editTextDeleteBudgetId)
        val buttonDeleteBudget = findViewById<Button>(R.id.buttonDeleteBudget)
        val loadingText = findViewById<TextView>(R.id.textViewBudgetLoading)
        val menuButton = findViewById<ImageView>(R.id.buttonBudgetMenu)
        val logoutButton = findViewById<ImageView>(R.id.buttonBudgetLogout)
        val navDashboard = findViewById<TextView>(R.id.navBudgetDashboard)
        val navExpenses = findViewById<TextView>(R.id.navBudgetExpenses)
        val navCategory = findViewById<TextView>(R.id.navBudgetCategory)
        val navBudget = findViewById<TextView>(R.id.navBudgetBudget)

        minGoalInput.setText("")
        maxGoalInput.setText("")
        categoryAmountInput.setText("1000")

        logoutButton.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
        menuButton.setOnClickListener { anchor ->
            val popup = PopupMenu(this, anchor)
            popup.menu.add(0, MENU_DASHBOARD, 0, getString(R.string.dashboard_nav_goals))
            popup.menu.add(0, MENU_EXPENSES, 1, getString(R.string.dashboard_nav_expenses))
            popup.menu.add(0, MENU_CATEGORY, 2, getString(R.string.dashboard_nav_category))
            popup.menu.add(0, MENU_BUDGET, 3, getString(R.string.dashboard_nav_budget))
            popup.menu.add(0, MENU_GOALS, 4, getString(R.string.action_open_goals))
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
                    MENU_BUDGET -> Unit
                    MENU_GOALS -> navigateGoals(userId)
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
        navDashboard.setOnClickListener { navigateDashboard(userId) }
        navExpenses.setOnClickListener { navigateExpenses(userId) }
        navCategory.setOnClickListener { navigateCategory(userId) }
        navBudget.setOnClickListener { }

        budgetViewModel.loadBudgets(userId)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                database.categoryDao().getCategoriesForUser(userId).collect { categories ->
                    cachedCategories = categories
                    val names = mutableListOf(getString(R.string.budget_spinner_pick_category))
                    val ids = mutableListOf<Long?>(null)
                    categories.sortedBy { it.name }.forEach { cat ->
                        names.add(cat.name)
                        ids.add(cat.categoryId)
                    }
                    budgetCategoryIdsBySpinnerIndex = ids
                    val adapter = ArrayAdapter(
                        this@BudgetActivity,
                        android.R.layout.simple_spinner_item,
                        names
                    )
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    spinnerCategory.adapter = adapter
                }
            }
        }

        buttonApplyGoals.setOnClickListener {
            val minGoal = minGoalInput.text.toString().toDoubleOrNull()
            if (minGoal == null) {
                Toast.makeText(this, getString(R.string.budget_invalid_amount), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // Single monthly budget in UI; keep min and max aligned for existing status logic.
            val maxGoal = minGoal
            maxGoalInput.setText(maxGoal.toString())
            budgetViewModel.setMonthlyGoals(minGoal = minGoal, maxGoal = maxGoal)
        }

        buttonSaveCategoryBudget.setOnClickListener {
            val pos = spinnerCategory.selectedItemPosition
            val categoryId = if (pos in budgetCategoryIdsBySpinnerIndex.indices) {
                budgetCategoryIdsBySpinnerIndex[pos]
            } else null
            if (categoryId == null) {
                Toast.makeText(this, getString(R.string.budget_pick_category_first), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val amount = categoryAmountInput.text.toString().toDoubleOrNull()
            if (amount == null) {
                Toast.makeText(this, getString(R.string.budget_invalid_amount), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
            budgetViewModel.upsertCategoryBudget(
                categoryId = categoryId,
                amount = amount,
                period = "monthly",
                startDate = monthStart,
                endDate = null
            )
        }

        buttonDeleteBudget.setOnClickListener {
            val id = editDeleteBudgetId.text.toString().toLongOrNull()
            if (id == null) {
                Toast.makeText(this, getString(R.string.budget_invalid_budget_id), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            budgetViewModel.deleteBudget(id)
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                budgetViewModel.uiState.collect { state ->
                    loadingText.visibility = if (state.isLoading) View.VISIBLE else View.GONE

                    // Populate the input field if the goal was restored from DB and the field is empty
                    if (state.minMonthlyGoal != null && minGoalInput.text.isNullOrEmpty()) {
                        minGoalInput.setText(state.minMonthlyGoal.toBigDecimal().stripTrailingZeros().toPlainString())
                    }

                    val minStr = state.minMonthlyGoal?.let { moneyFormat.format(it) } ?: "—"
                    val maxStr = state.maxMonthlyGoal?.let { moneyFormat.format(it) } ?: minStr
                    val allocStr = moneyFormat.format(state.allocatedTotal)
                    val unallocStr = state.unallocatedAmount?.let { moneyFormat.format(it) } ?: "—"

                    totalBudgetValue.text = "R$maxStr"
                    allocatedValue.text = "R$allocStr"
                    unallocatedValue.text = "R$unallocStr"

                    textViewSummary.text = buildString {
                        append(getString(R.string.budget_summary_line_max, maxStr))
                        append("\n")
                        append(
                            getString(
                                R.string.budget_summary_line_actual_spend,
                                moneyFormat.format(state.actualSpendCurrentMonth)
                            )
                        )
                        append("\n")
                        when (budgetViewModel.getMonthlyStatusForSpent(state.actualSpendCurrentMonth)) {
                            MonthlyStatus.GREEN -> append(getString(R.string.budget_status_green))
                            MonthlyStatus.YELLOW -> append(getString(R.string.budget_status_yellow))
                            MonthlyStatus.RED -> append(getString(R.string.budget_status_red))
                            MonthlyStatus.NO_MAX_GOAL -> append(getString(R.string.budget_status_no_max))
                        }
                    }

                    textViewBudgetList.text = if (state.budgets.isEmpty()) {
                        getString(R.string.budget_no_active_budgets)
                    } else {
                        val nameById = cachedCategories.associate { it.categoryId to it.name }
                        state.budgets.joinToString(separator = "\n\n") { b ->
                            val catName = nameById[b.categoryId] ?: ("#" + b.categoryId)
                            "ID ${b.budgetId} · $catName\n" +
                                    getString(
                                        R.string.budget_line_amount_period,
                                        moneyFormat.format(b.amount),
                                        b.period
                                    )
                        }
                    }
                    renderCategoryRows(
                        container = categoryRowsContainer,
                        categories = cachedCategories.sortedBy { it.name },
                        budgetsByCategory = state.budgets.filter { it.categoryId != null }.associateBy { it.categoryId!! },
                        onSaveCategoryAmount = { categoryId, amount ->
                            val monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
                            budgetViewModel.upsertCategoryBudget(
                                categoryId = categoryId,
                                amount = amount,
                                period = "monthly",
                                startDate = monthStart,
                                endDate = null
                            )
                        }
                    )

                    state.errorMessage?.let {
                        Toast.makeText(this@BudgetActivity, it, Toast.LENGTH_LONG).show()
                        budgetViewModel.clearMessages()
                    }
                    state.successMessage?.let {
                        Toast.makeText(this@BudgetActivity, it, Toast.LENGTH_SHORT).show()
                        editDeleteBudgetId.text?.clear()
                        budgetViewModel.clearMessages()
                    }
                    state.warningMessage?.let {
                        Toast.makeText(this@BudgetActivity, it, Toast.LENGTH_LONG).show()
                        budgetViewModel.clearMessages()
                    }
                }
            }
        }
    }

    private fun renderCategoryRows(
        container: LinearLayout,
        categories: List<Category>,
        budgetsByCategory: Map<Long, com.example.projectwatchapp.data.entities.Budget>,
        onSaveCategoryAmount: (Long, Double) -> Unit
    ) {
        container.removeAllViews()
        categories.take(3).forEach { category ->
            val row = layoutInflater.inflate(R.layout.item_budget_category_row, container, false)
            row.findViewById<TextView>(R.id.textViewBudgetCategoryEmoji).text = emojiForCategory(category.name)
            row.findViewById<TextView>(R.id.textViewBudgetCategoryName).text = category.name
            val savedAmount = budgetsByCategory[category.categoryId]?.amount ?: 0.0
            val amountView = row.findViewById<TextView>(R.id.textViewBudgetCategoryAmount)
            amountView.text = "R ${"%.2f".format(savedAmount)}"
            amountView.setOnClickListener {
                promptCategoryAmount(category.name, savedAmount) { newAmount ->
                    onSaveCategoryAmount(category.categoryId, newAmount)
                }
            }
            container.addView(row)
        }
    }

    private fun promptCategoryAmount(
        categoryName: String,
        initialAmount: Double,
        onSave: (Double) -> Unit
    ) {
        val input = EditText(this).apply {
            setText(initialAmount.toString())
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        AlertDialog.Builder(this)
            .setTitle("Set budget for $categoryName")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val amount = input.text.toString().toDoubleOrNull()
                if (amount == null) {
                    Toast.makeText(this, getString(R.string.budget_invalid_amount), Toast.LENGTH_SHORT).show()
                } else {
                    onSave(amount)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun emojiForCategory(name: String): String {
        val value = name.lowercase(Locale.getDefault())
        return when {
            "groc" in value || "food" in value -> "\uD83D\uDED2"
            "rent" in value || "home" in value -> "\uD83C\uDFE0"
            "trans" in value || "car" in value || "taxi" in value -> "\uD83D\uDE97"
            else -> "\uD83D\uDCC1"
        }
    }

    private fun navigateDashboard(userId: Long) {
        if (userId <= 0) return
        startActivity(Intent(this, DashboardActivity::class.java).putExtra(LoginActivity.EXTRA_USER_ID, userId))
        finish()
    }

    private fun navigateExpenses(userId: Long) {
        if (userId <= 0) return
        startActivity(Intent(this, ExpenseActivity::class.java).putExtra(LoginActivity.EXTRA_USER_ID, userId))
    }

    private fun navigateCategory(userId: Long) {
        if (userId <= 0) return
        startActivity(Intent(this, CategoryActivity::class.java).putExtra(LoginActivity.EXTRA_USER_ID, userId))
    }

    private fun navigateGoals(userId: Long) {
        if (userId <= 0) return
        startActivity(Intent(this, GoalsActivity::class.java).putExtra(LoginActivity.EXTRA_USER_ID, userId))
    }

    private fun navigateRewards(userId: Long) {
        if (userId <= 0) return
        startActivity(Intent(this, RewardsActivity::class.java).putExtra(LoginActivity.EXTRA_USER_ID, userId))
    }

    private fun navigateReports(userId: Long) {
        if (userId <= 0) return
        startActivity(Intent(this, ReportsActivity::class.java).putExtra(LoginActivity.EXTRA_USER_ID, userId))
    }
}

class BudgetViewModelFactory(
    private val database: AppDatabase
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BudgetViewModel::class.java)) {
            return BudgetViewModel(database.budgetDao(), database.expenseDao()) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}