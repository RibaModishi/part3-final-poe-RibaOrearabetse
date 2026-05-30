package com.example.projectwatchapp.ui.info

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.example.projectwatchapp.R
import com.example.projectwatchapp.ui.auth.LoginActivity
import com.example.projectwatchapp.ui.budget.BudgetActivity
import com.example.projectwatchapp.ui.category.CategoryActivity
import com.example.projectwatchapp.ui.dashboard.DashboardActivity
import com.example.projectwatchapp.ui.expense.ExpenseActivity
import com.example.projectwatchapp.ui.goals.GoalsActivity
import com.example.projectwatchapp.ui.reports.ReportsActivity
import com.example.projectwatchapp.ui.rewards.RewardsActivity
import com.example.projectwatchapp.ui.common.PopupMenuUtils

class InfoHelpActivity : ComponentActivity() {
    companion object {
        private const val MENU_DASHBOARD = 1
        private const val MENU_EXPENSES = 2
        private const val MENU_CATEGORY = 3
        private const val MENU_BUDGET = 4
        private const val MENU_GOALS = 5
        private const val MENU_REWARDS = 6
        private const val MENU_REPORTS = 7
        private const val MENU_INFO = 8
        private const val MENU_LOGOUT = 9
    }

    private data class FaqItem(
        val question: String,
        val answer: String,
        val expandedByDefault: Boolean = false
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_info_help)

        val userId = intent.getLongExtra(LoginActivity.EXTRA_USER_ID, -1L)

        val gettingStarted = findViewById<LinearLayout>(R.id.layoutFaqGettingStarted)
        val goalsRewards = findViewById<LinearLayout>(R.id.layoutFaqGoalsRewards)
        val account = findViewById<LinearLayout>(R.id.layoutFaqAccount)
        val menuButton = findViewById<ImageView>(R.id.buttonInfoHelpMenu)
        val logoutButton = findViewById<ImageView>(R.id.buttonInfoHelpLogout)
        val navDashboard = findViewById<TextView>(R.id.navInfoHelpDashboard)
        val navExpenses = findViewById<TextView>(R.id.navInfoHelpExpenses)
        val navCategory = findViewById<TextView>(R.id.navInfoHelpCategory)
        val navBudget = findViewById<TextView>(R.id.navInfoHelpBudget)

        renderSection(
            gettingStarted,
            listOf(
                FaqItem(
                    "How do I add my first expense?",
                    "Tap Expenses in the bottom nav, then + Add Expense. Fill in amount, date and description. Optionally attach a receipt photo, then tap Save.",
                    expandedByDefault = true
                ),
                FaqItem(
                    "How do I create a category?",
                    "Open Category page, tap Add Category, enter a name and save. You can then assign expenses to this category."
                ),
                FaqItem(
                    "How do I set a monthly budget?",
                    "Open Budget page, enter your monthly budget amount, then save category allocations as needed."
                )
            )
        )
        renderSection(
            goalsRewards,
            listOf(
                FaqItem(
                    "How do I add a saving goal?",
                    "Open Goals page, tap + Add Goal, enter goal name and target amount, choose deadline if needed, then save."
                ),
                FaqItem(
                    "How do I earn points and badges?",
                    "As you use budgeting, expense, and savings features, rewards update automatically. You can also test badge assignment on Rewards page."
                ),
                FaqItem(
                    "What is the Level Journey?",
                    "The Level Journey shows your progress path from beginner to advanced levels based on your total XP."
                )
            )
        )
        renderSection(
            account,
            listOf(
                FaqItem(
                    "How do I switch accounts?",
                    "Use the logout icon in the top bar, then sign in with another account."
                ),
                FaqItem(
                    "Will my data be deleted when I update?",
                    "No. Your app is configured with non-destructive Room migration for current schema changes."
                )
            )
        )

        menuButton.setOnClickListener { anchor ->
            val popup = PopupMenu(this, anchor)
            popup.menu.add(0, MENU_DASHBOARD, 0, getString(R.string.dashboard_nav_goals))
            popup.menu.add(0, MENU_EXPENSES, 1, getString(R.string.dashboard_nav_expenses))
            popup.menu.add(0, MENU_CATEGORY, 2, getString(R.string.dashboard_nav_category))
            popup.menu.add(0, MENU_BUDGET, 3, getString(R.string.dashboard_nav_budget))
            popup.menu.add(0, MENU_GOALS, 4, getString(R.string.goals_title))
            popup.menu.add(0, MENU_REWARDS, 5, getString(R.string.rewards_title))
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
                    MENU_DASHBOARD -> navigateDashboard(userId)
                    MENU_EXPENSES -> navigateExpenses(userId)
                    MENU_CATEGORY -> navigateCategory(userId)
                    MENU_BUDGET -> navigateBudget(userId)
                    MENU_GOALS -> navigateGoals(userId)
                    MENU_REWARDS -> navigateRewards(userId)
                    MENU_REPORTS -> navigateReports(userId)
                    MENU_INFO -> Unit
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
    }

    private fun renderSection(container: LinearLayout, items: List<FaqItem>) {
        container.removeAllViews()
        items.forEach { faq ->
            val row = layoutInflater.inflate(R.layout.item_faq_expandable, container, false)
            val question = row.findViewById<TextView>(R.id.textViewFaqQuestion)
            val answer = row.findViewById<TextView>(R.id.textViewFaqAnswer)
            val toggle = row.findViewById<TextView>(R.id.textViewFaqToggle)
            val clickRow = row.findViewById<View>(R.id.layoutFaqQuestionRow)
            question.text = faq.question
            answer.text = faq.answer
            if (faq.expandedByDefault) {
                answer.visibility = View.VISIBLE
                toggle.text = "▲"
            }
            clickRow.setOnClickListener {
                val expanded = answer.visibility == View.VISIBLE
                answer.visibility = if (expanded) View.GONE else View.VISIBLE
                toggle.text = if (expanded) "▼" else "▲"
            }
            container.addView(row)
        }
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

    private fun navigateGoals(userId: Long) {
        startActivity(Intent(this, GoalsActivity::class.java).putExtra(LoginActivity.EXTRA_USER_ID, userId))
    }

    private fun navigateRewards(userId: Long) {
        startActivity(Intent(this, RewardsActivity::class.java).putExtra(LoginActivity.EXTRA_USER_ID, userId))
    }

    private fun navigateReports(userId: Long) {
        startActivity(Intent(this, ReportsActivity::class.java).putExtra(LoginActivity.EXTRA_USER_ID, userId))
    }
}
