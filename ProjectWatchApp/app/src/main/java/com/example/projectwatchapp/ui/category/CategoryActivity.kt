package com.example.projectwatchapp.ui.category

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
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
        private const val MENU_LOGOUT = 8
    }

    private val database by lazy { AppDatabase.getDatabase(this) }
    private val categoryViewModel: CategoryViewModel by viewModels {
        CategoryPageViewModelFactory(database)
    }

    private var categoriesCache: List<Category> = emptyList()
    private var categoryExpenseCount: Map<Long, Int> = emptyMap()
    private var categoryExpenseMonthSpent: Map<Long, Double> = emptyMap()

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
                    MENU_CATEGORY -> scrollView.smoothScrollTo(0, 0)
                    MENU_BUDGET -> navigateBudget(userId)
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

        if (userId > 0) {
            categoryViewModel.loadCategories(userId)
        } else {
            Toast.makeText(this, "User is not available.", Toast.LENGTH_SHORT).show()
        }

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
                        Toast.makeText(this@CategoryActivity, message, Toast.LENGTH_SHORT).show()
                        categoryViewModel.clearMessages()
                    }
                }
            }
        }

        if (userId > 0) {
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
            row.findViewById<TextView>(R.id.textViewCategoryName).text = category.name
            row.findViewById<TextView>(R.id.textViewCategoryEmoji).text = emojiForCategory(category.name)

            val count = categoryExpenseCount[category.categoryId] ?: 0
            val spent = categoryExpenseMonthSpent[category.categoryId] ?: 0.0

            row.findViewById<TextView>(R.id.textViewCategoryMeta).text =
                getString(R.string.category_row_meta, count)
            row.findViewById<TextView>(R.id.textViewCategorySpent).text =
                getString(R.string.category_row_spent, formatRand(spent))

            row.findViewById<ImageButton>(R.id.buttonCategoryAddExpense).setOnClickListener {
                navigateExpenses(userId)
            }
            row.findViewById<ImageButton>(R.id.buttonCategoryEdit).setOnClickListener {
                promptCategoryName(
                    title = getString(R.string.category_dialog_edit_title),
                    initialValue = category.name
                ) { newName ->
                    categoryViewModel.updateCategory(
                        categoryId = category.categoryId,
                        name = newName,
                        colorHex = category.colorHex,
                        iconName = category.iconName
                    )
                }
            }
            row.findViewById<ImageButton>(R.id.buttonCategoryDelete).setOnClickListener {
                categoryViewModel.deleteCategory(category.categoryId)
            }
            container.addView(row)
        }
    }

    private fun showAddCategorySheet() {
        val dialogView = layoutInflater.inflate(R.layout.bottom_sheet_add_category, null)
        val nameInput = dialogView.findViewById<EditText>(R.id.editTextSheetCategoryName)
        val iconButton = dialogView.findViewById<Button>(R.id.buttonAddIconSheet)
        val closeButton = dialogView.findViewById<ImageButton>(R.id.buttonCloseAddCategorySheet)
        val saveButton = dialogView.findViewById<Button>(R.id.buttonSaveCategorySheet)

        val colorChipIds = listOf(
            R.id.colorChip1, R.id.colorChip2, R.id.colorChip3, R.id.colorChip4, R.id.colorChip5, R.id.colorChip6,
            R.id.colorChip7, R.id.colorChip8, R.id.colorChip9, R.id.colorChip10, R.id.colorChip11, R.id.colorChip12
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
                    setColor(android.graphics.Color.parseColor(color))
                    setStroke(if (color == selected) 3 else 0, android.graphics.Color.parseColor("#222222"))
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
            categoryViewModel.addCategory(
                name = nameInput.text.toString(),
                colorHex = selectedColor
            )
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun promptCategoryName(title: String, initialValue: String, onSubmit: (String) -> Unit) {
        val input = EditText(this).apply { setText(initialValue) }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(input)
            .setPositiveButton(getString(R.string.action_save)) { _, _ ->
                onSubmit(input.text.toString())
            }
            .setNegativeButton(getString(R.string.action_cancel), null)
            .show()
    }

    private fun navigateDashboard(userId: Long) {
        if (userId > 0) {
            startActivity(
                Intent(this, DashboardActivity::class.java)
                    .putExtra(LoginActivity.EXTRA_USER_ID, userId)
            )
            finish()
        } else {
            Toast.makeText(this, "User is not available.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun navigateExpenses(userId: Long) {
        if (userId > 0) {
            startActivity(
                Intent(this, ExpenseActivity::class.java)
                    .putExtra(LoginActivity.EXTRA_USER_ID, userId)
            )
        } else {
            Toast.makeText(this, "User is not available.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun navigateBudget(userId: Long) {
        if (userId > 0) {
            startActivity(
                Intent(this, BudgetActivity::class.java)
                    .putExtra(LoginActivity.EXTRA_USER_ID, userId)
            )
        } else {
            Toast.makeText(this, "User is not available.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun navigateGoals(userId: Long) {
        if (userId > 0) {
            startActivity(
                Intent(this, GoalsActivity::class.java)
                    .putExtra(LoginActivity.EXTRA_USER_ID, userId)
            )
        } else {
            Toast.makeText(this, "User is not available.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun navigateRewards(userId: Long) {
        if (userId > 0) {
            startActivity(
                Intent(this, RewardsActivity::class.java)
                    .putExtra(LoginActivity.EXTRA_USER_ID, userId)
            )
        } else {
            Toast.makeText(this, "User is not available.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun navigateReports(userId: Long) {
        if (userId > 0) {
            startActivity(
                Intent(this, ReportsActivity::class.java)
                    .putExtra(LoginActivity.EXTRA_USER_ID, userId)
            )
        } else {
            Toast.makeText(this, "User is not available.", Toast.LENGTH_SHORT).show()
        }
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
            else -> "📁"
        }
    }

    private fun formatRand(amount: Double): String {
        return String.format(Locale.getDefault(), "R%.0f", amount)
    }
}

class CategoryPageViewModelFactory(
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
