package com.example.projectwatchapp.ui.category

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import android.app.AlertDialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.projectwatchapp.R
import com.example.projectwatchapp.data.AppDatabase
import com.example.projectwatchapp.data.entities.Category
import com.example.projectwatchapp.ui.auth.LoginActivity
import com.example.projectwatchapp.ui.budget.BudgetActivity
import com.example.projectwatchapp.ui.dashboard.DashboardActivity
import com.example.projectwatchapp.ui.expense.ExpenseActivity
import com.example.projectwatchapp.ui.goals.GoalsActivity
import com.example.projectwatchapp.ui.reports.ReportsActivity
import com.example.projectwatchapp.ui.rewards.RewardsActivity
import com.example.projectwatchapp.ui.common.PopupMenuUtils
import com.example.projectwatchapp.ui.dashboard.DashboardActivity.Companion.MENU_INFO
import com.example.projectwatchapp.viewmodel.CategoryViewModel
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

class CategoryActivity : ComponentActivity() {
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

    private var categoriesCache: List<Category> = emptyList()
    private var categoryExpenseCount: Map<Long, Int> = emptyMap()
    private var categoryExpenseMonthSpent: Map<Long, Double> = emptyMap()
    private var categoryBudgets: Map<Long, Double> = emptyMap()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_category)

        val userId = intent.getLongExtra(LoginActivity.EXTRA_USER_ID, -1L)
        val menuButton = findViewById<ImageView>(R.id.buttonCategoryMenu)
        val logoutButton = findViewById<ImageView>(R.id.buttonCategoryLogout)
        val addCategoryButton = findViewById<Button>(R.id.buttonAddCategoryPage)
        val loadingText = findViewById<TextView>(R.id.textViewCategoryLoading)
        val categoryContainer = findViewById<LinearLayout>(R.id.layoutCategoryItems)
        val scrollView = findViewById<ScrollView>(R.id.scrollViewCategory)

        addCategoryButton.setOnClickListener { showAddCategorySheet() }

        logoutButton.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
        val navDashboard = findViewById<Button>(R.id.navCategoryDashboard)
        val navExpenses  = findViewById<Button>(R.id.navCategoryExpenses)
        val navCategory  = findViewById<Button>(R.id.navCategoryCategory)
        val navBudget    = findViewById<Button>(R.id.navCategoryBudget)

        navDashboard.setOnClickListener { navigateDashboard(userId) }
        navExpenses.setOnClickListener  { navigateExpenses(userId) }
        navCategory.setOnClickListener  { }
        navBudget.setOnClickListener    { navigateBudget(userId) }

        menuButton.setOnClickListener { anchor ->
            val popup = PopupMenu(this, anchor)
            popup.menu.add(0, MENU_DASHBOARD, 0, getString(R.string.dashboard_nav_goals))
            popup.menu.add(0, MENU_EXPENSES, 1, getString(R.string.dashboard_nav_expenses))
            popup.menu.add(0, MENU_CATEGORY, 2, getString(R.string.dashboard_nav_category))
            popup.menu.add(0, MENU_BUDGET, 3, getString(R.string.dashboard_nav_budget))
            popup.menu.add(0, MENU_GOALS, 4, getString(R.string.action_open_goals))
            popup.menu.add(0, MENU_REWARDS, 5, getString(R.string.action_open_rewards))
            popup.menu.add(0, MENU_REPORTS, 6, getString(R.string.reports_title))
            popup.menu.add(0, MENU_INFO, 7, getString(R.string.info_help_title))
            popup.menu.add(0, MENU_LOGOUT, 7, getString(R.string.dashboard_back_to_login))
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
                    MENU_DASHBOARD -> navigateDashboard(userId)
                    MENU_EXPENSES -> navigateExpenses(userId)
                    MENU_CATEGORY -> scrollView.smoothScrollTo(0, 0)
                    MENU_BUDGET -> navigateBudget(userId)
                    MENU_GOALS -> navigateGoals(userId)
                    MENU_REWARDS -> navigateRewards(userId)
                    MENU_REPORTS -> navigateReports(userId)
                    MENU_INFO -> navigateInfo(userId)
                    MENU_LOGOUT -> {
                        startActivity(Intent(this, LoginActivity::class.java))
                        finish()
                    }
                }
                true
            }
            popup.show()
        }

        if (userId > 0) {
            categoryViewModel.loadCategories(userId)

            // Observe categories
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    categoryViewModel.uiState.collect { state ->
                        categoriesCache = state.categories
                        loadingText.visibility = if (state.isLoading) View.VISIBLE else View.GONE
                        renderCategoryItems(categoryContainer, categoriesCache, userId)

                        state.errorMessage?.let { message ->
                            Toast.makeText(this@CategoryActivity, message, Toast.LENGTH_SHORT).show()
                            categoryViewModel.clearMessages()
                        }
                        state.successMessage?.let { message ->
                            val duration = if (message.contains("XP")) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
                            Toast.makeText(this@CategoryActivity, message, duration).show()
                            categoryViewModel.clearMessages()
                        }
                    }
                }
            }

            // Observe expenses — feeds expense count and monthly spend per category
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    database.expenseDao().getExpensesForUser(userId).collect { expenses ->
                        val monthStart = currentMonthStartMillis()
                        categoryExpenseCount = expenses
                            .filter { it.categoryId != null }
                            .groupingBy { it.categoryId!! }
                            .eachCount()
                        categoryExpenseMonthSpent = expenses
                            .filter { it.categoryId != null && it.date >= monthStart }
                            .groupBy { it.categoryId!! }
                            .mapValues { (_, list) -> list.sumOf { it.amount } }
                        renderCategoryItems(categoryContainer, categoriesCache, userId)
                    }
                }
            }

            // Observe budgets — feeds the progress bar limit per category
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    database.budgetDao().getActiveBudgetsForUser(userId).collect { budgets ->
                        categoryBudgets = budgets
                            .filter { it.categoryId != null }
                            .associate { it.categoryId!! to it.amount }
                        renderCategoryItems(categoryContainer, categoriesCache, userId)
                    }
                }
            }

        } else {
            Toast.makeText(this, "User is not available.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun renderCategoryItems(container: LinearLayout, categories: List<Category>, userId: Long) {
        container.removeAllViews()

        if (categories.isEmpty()) {
            val empty = TextView(this).apply {
                text = getString(R.string.no_categories)
                textSize = 14f
                setTextColor(0xFF666666.toInt())
            }
            container.addView(empty)
            return
        }

        categories.forEach { category ->
            val row = layoutInflater.inflate(R.layout.item_category_row, container, false)

            // Name and icon
            row.findViewById<TextView>(R.id.textViewCategoryName).text = category.name
            row.findViewById<TextView>(R.id.textViewCategoryEmoji).text = emojiForCategory(category.name)

            // Expense count and monthly spend
            val count = categoryExpenseCount[category.categoryId] ?: 0
            val spent = categoryExpenseMonthSpent[category.categoryId] ?: 0.0
            val budget = categoryBudgets[category.categoryId] ?: 0.0

            row.findViewById<TextView>(R.id.textViewCategoryMeta).text =
                getString(R.string.category_row_meta, count)

            // Budget progress text — show "This Month" if no budget set
            val budgetText = row.findViewById<TextView>(R.id.textViewCategoryBudgetProgress)
            if (budget > 0) {
                budgetText.text = "R ${formatAmount(spent)} of R ${formatAmount(budget)}"
            } else {
                budgetText.text = "This Month: R ${formatAmount(spent)}"
            }

            // Progress bar — only meaningful when a budget exists
            val progressBar = row.findViewById<ProgressBar>(R.id.progressBarCategoryBudget)
            val progress = if (budget > 0) ((spent / budget) * 100).toInt().coerceIn(0, 100) else 0
            progressBar.progress = progress

            // Colour: green under 80%, orange 80–99%, red at/over 100%
            val tintColor = when {
                budget <= 0   -> Color.parseColor("#E0E0E0")
                progress >= 100 -> Color.parseColor("#F44336")
                progress >= 80  -> Color.parseColor("#FF9800")
                else            -> Color.parseColor("#4CAF50")
            }
            progressBar.progressTintList = ColorStateList.valueOf(tintColor)

            // Add expense button — opens Expense screen pre-filtered to this category
            row.findViewById<ImageButton>(R.id.buttonCategoryAddExpense).setOnClickListener {
                startActivity(
                    Intent(this, ExpenseActivity::class.java)
                        .putExtra(LoginActivity.EXTRA_USER_ID, userId)
                        .putExtra("EXTRA_CATEGORY_ID", category.categoryId)
                )
            }

            // Edit button — opens the same Add Category form pre-filled
            row.findViewById<ImageButton>(R.id.buttonCategoryEdit).setOnClickListener {
                showEditCategorySheet(category, budget)
            }

            // Delete button
            row.findViewById<ImageButton>(R.id.buttonCategoryDelete).setOnClickListener {
                categoryViewModel.deleteCategory(category.categoryId)
            }

            container.addView(row)
        }
    }

    private fun showAddCategorySheet() {
        val dialogView = layoutInflater.inflate(R.layout.bottom_sheet_add_category, null)
        val nameInput = dialogView.findViewById<EditText>(R.id.editTextSheetCategoryName)
        val budgetInput = dialogView.findViewById<EditText>(R.id.editTextSheetBudget)
        val iconButton = dialogView.findViewById<Button>(R.id.buttonAddIconSheet)
        val closeButton = dialogView.findViewById<ImageButton>(R.id.buttonCloseAddCategorySheet)
        val saveButton = dialogView.findViewById<Button>(R.id.buttonSaveCategorySheet)

        val colorChipIds = listOf(
            R.id.colorChip1, R.id.colorChip2, R.id.colorChip3, R.id.colorChip4,
            R.id.colorChip5, R.id.colorChip6, R.id.colorChip7, R.id.colorChip8,
            R.id.colorChip9, R.id.colorChip10, R.id.colorChip11, R.id.colorChip12
        )
        val colorValues = listOf(
            "#F44336", "#F57C00", "#FBC02D", "#4CAF50", "#26A69A", "#00BCD4",
            "#2196F3", "#9C27B0", "#E91E63", "#8E24AA", "#C2185B", "#FF5252"
        )
        var selectedColor = colorValues.first()

        fun applyChipState(selected: String) {
            colorChipIds.forEachIndexed { index, id ->
                val chip = dialogView.findViewById<View>(id)
                val color = colorValues[index]
                val drawable = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor(color))
                    setStroke(if (color == selected) 3 else 0, Color.parseColor("#222222"))
                }
                chip.background = drawable
            }
        }
        applyChipState(selectedColor)

        colorChipIds.forEachIndexed { index, id ->
            dialogView.findViewById<View>(id).setOnClickListener {
                selectedColor = colorValues[index]
                applyChipState(selectedColor)
            }
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        iconButton.setOnClickListener {
            Toast.makeText(this, getString(R.string.category_icon_coming_soon), Toast.LENGTH_SHORT).show()
        }
        closeButton.setOnClickListener { dialog.dismiss() }
        saveButton.setOnClickListener {
            val userId = intent.getLongExtra(LoginActivity.EXTRA_USER_ID, -1L)
            val budgetAmount = budgetInput.text.toString().toDoubleOrNull()

            categoryViewModel.addCategory(
                name = nameInput.text.toString(),
                colorHex = selectedColor
            )

            // If a budget was entered, save it to the budgets table for this category
            if (budgetAmount != null && budgetAmount > 0.0 && userId > 0) {
                // We observe the category list — once the new category appears, save its budget
                lifecycleScope.launch {
                    // Wait for the newly inserted category to appear in the state
                    categoryViewModel.uiState.collect { state ->
                        val newCat = state.categories.firstOrNull {
                            it.name.trim().equals(nameInput.text.toString().trim(), ignoreCase = true)
                        }
                        if (newCat != null) {
                            val monthStart = java.time.LocalDate.now()
                                .withDayOfMonth(1)
                                .atStartOfDay(java.time.ZoneId.systemDefault())
                                .toInstant().toEpochMilli()
                            database.budgetDao().insertBudget(
                                com.example.projectwatchapp.data.entities.Budget(
                                    userId = userId,
                                    categoryId = newCat.categoryId,
                                    amount = budgetAmount,
                                    period = "monthly",
                                    startDate = monthStart,
                                    endDate = null,
                                    isActive = true
                                )
                            )
                            return@collect  // stop collecting once done
                        }
                    }
                }
            }

            dialog.dismiss()
        }
        dialog.show()
    }

    private fun showEditCategorySheet(category: Category, currentBudget: Double) {
        val dialogView = layoutInflater.inflate(R.layout.bottom_sheet_add_category, null)
        val nameInput = dialogView.findViewById<EditText>(R.id.editTextSheetCategoryName)
        val budgetInput = dialogView.findViewById<EditText>(R.id.editTextSheetBudget)
        val iconButton = dialogView.findViewById<Button>(R.id.buttonAddIconSheet)
        val closeButton = dialogView.findViewById<ImageButton>(R.id.buttonCloseAddCategorySheet)
        val saveButton = dialogView.findViewById<Button>(R.id.buttonSaveCategorySheet)

        // Pre-fill with existing values
        nameInput.setText(category.name)
        if (currentBudget > 0.0) budgetInput.setText(currentBudget.toBigDecimal().stripTrailingZeros().toPlainString())
        saveButton.text = getString(R.string.action_save)

        val colorChipIds = listOf(
            R.id.colorChip1, R.id.colorChip2, R.id.colorChip3, R.id.colorChip4,
            R.id.colorChip5, R.id.colorChip6, R.id.colorChip7, R.id.colorChip8,
            R.id.colorChip9, R.id.colorChip10, R.id.colorChip11, R.id.colorChip12
        )
        val colorValues = listOf(
            "#F44336", "#F57C00", "#FBC02D", "#4CAF50", "#26A69A", "#00BCD4",
            "#2196F3", "#9C27B0", "#E91E63", "#8E24AA", "#C2185B", "#FF5252"
        )
        // Pre-select the category's existing color, or default to first
        var selectedColor = colorValues.firstOrNull { it.equals(category.colorHex, ignoreCase = true) }
            ?: colorValues.first()

        fun applyChipState(selected: String) {
            colorChipIds.forEachIndexed { index, id ->
                val chip = dialogView.findViewById<View>(id)
                val color = colorValues[index]
                val drawable = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor(color))
                    setStroke(if (color == selected) 3 else 0, Color.parseColor("#222222"))
                }
                chip.background = drawable
            }
        }
        applyChipState(selectedColor)

        colorChipIds.forEachIndexed { index, id ->
            dialogView.findViewById<View>(id).setOnClickListener {
                selectedColor = colorValues[index]
                applyChipState(selectedColor)
            }
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        iconButton.setOnClickListener {
            Toast.makeText(this, getString(R.string.category_icon_coming_soon), Toast.LENGTH_SHORT).show()
        }
        closeButton.setOnClickListener { dialog.dismiss() }
        saveButton.setOnClickListener {
            val userId = intent.getLongExtra(LoginActivity.EXTRA_USER_ID, -1L)
            val newBudgetAmount = budgetInput.text.toString().toDoubleOrNull()

            // Update the category name and color
            categoryViewModel.updateCategory(
                categoryId = category.categoryId,
                name = nameInput.text.toString(),
                colorHex = selectedColor,
                iconName = category.iconName
            )

            // Update or insert the budget for this category
            if (userId > 0) {
                lifecycleScope.launch {
                    val monthStart = java.time.LocalDate.now()
                        .withDayOfMonth(1)
                        .atStartOfDay(java.time.ZoneId.systemDefault())
                        .toInstant().toEpochMilli()
                    val existing = database.budgetDao().getActiveBudgetForCategory(userId, category.categoryId)
                    if (newBudgetAmount != null && newBudgetAmount > 0.0) {
                        if (existing == null) {
                            database.budgetDao().insertBudget(
                                com.example.projectwatchapp.data.entities.Budget(
                                    userId = userId,
                                    categoryId = category.categoryId,
                                    amount = newBudgetAmount,
                                    period = "monthly",
                                    startDate = monthStart,
                                    endDate = null,
                                    isActive = true
                                )
                            )
                        } else {
                            database.budgetDao().updateBudget(
                                existing.copy(amount = newBudgetAmount, startDate = monthStart)
                            )
                        }
                    }
                }
            }

            dialog.dismiss()
        }
        dialog.show()
    }

    private fun navigateDashboard(userId: Long) {
        if (userId > 0) {
            startActivity(Intent(this, DashboardActivity::class.java).putExtra(LoginActivity.EXTRA_USER_ID, userId))
            finish()
        }
    }

    private fun navigateExpenses(userId: Long) {
        if (userId > 0) startActivity(Intent(this, ExpenseActivity::class.java).putExtra(LoginActivity.EXTRA_USER_ID, userId))
    }

    private fun navigateBudget(userId: Long) {
        if (userId > 0) startActivity(Intent(this, BudgetActivity::class.java).putExtra(LoginActivity.EXTRA_USER_ID, userId))
    }

    private fun navigateGoals(userId: Long) {
        if (userId > 0) startActivity(Intent(this, GoalsActivity::class.java).putExtra(LoginActivity.EXTRA_USER_ID, userId))
    }

    private fun navigateRewards(userId: Long) {
        if (userId > 0) startActivity(Intent(this, RewardsActivity::class.java).putExtra(LoginActivity.EXTRA_USER_ID, userId))
    }

    private fun navigateReports(userId: Long) {
        if (userId > 0) startActivity(Intent(this, ReportsActivity::class.java).putExtra(LoginActivity.EXTRA_USER_ID, userId))
    }

    private fun navigateInfo(userId: Long) {
        if (userId <= 0) return
        startActivity(Intent(this, com.example.projectwatchapp.ui.info.InfoHelpActivity::class.java).putExtra(LoginActivity.EXTRA_USER_ID, userId))
    }

    private fun currentMonthStartMillis(): Long {
        return Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun emojiForCategory(name: String): String {
        val value = name.lowercase(Locale.getDefault())
        return when {
            "groc" in value || "food" in value -> "🛒"
            "rent" in value || "home" in value -> "🏠"
            "trans" in value || "car" in value -> "🚗"
            "health" in value || "med" in value -> "💊"
            "entertain" in value || "fun" in value -> "🎬"
            "util" in value || "elect" in value -> "💡"
            "cloth" in value || "wear" in value -> "👗"
            "travel" in value || "trip" in value -> "✈️"
            "edu" in value || "school" in value -> "📚"
            else -> "📁"
        }
    }

    private fun formatAmount(amount: Double): String {
        return if (amount == amount.toLong().toDouble()) {
            amount.toLong().toString()
        } else {
            String.format(Locale.getDefault(), "%.2f", amount)
        }
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