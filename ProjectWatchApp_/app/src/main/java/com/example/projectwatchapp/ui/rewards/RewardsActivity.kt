package com.example.projectwatchapp.ui.rewards

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.Spinner
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
import com.example.projectwatchapp.ui.dashboard.DashboardActivity
import com.example.projectwatchapp.ui.expense.ExpenseActivity
import com.example.projectwatchapp.ui.goals.GoalsActivity
import com.example.projectwatchapp.ui.reports.ReportsActivity
import com.example.projectwatchapp.ui.common.PopupMenuUtils
import com.example.projectwatchapp.ui.dashboard.DashboardActivity.Companion.MENU_INFO
import com.example.projectwatchapp.utils.SessionManager
import com.example.projectwatchapp.viewmodel.RewardsViewModel
import kotlinx.coroutines.launch

/**
 * Rewards / gamification UI wired to [RewardsViewModel].
 */
class RewardsActivity : ComponentActivity() {
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
    private val rewardsViewModel: RewardsViewModel by viewModels {
        RewardsViewModelFactory(database)
    }

    private val badgeChoices: List<SessionManager.Badge> = SessionManager.Badge.entries.toList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rewards)

        val userId = intent.getLongExtra(LoginActivity.EXTRA_USER_ID, -1L)
        if (userId <= 0) {
            Toast.makeText(this, "Invalid user. Please login again.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val textLevelName = findViewById<TextView>(R.id.textViewRewardsLevelName)
        val textLevelPoints = findViewById<TextView>(R.id.textViewRewardsLevelPoints)
        val textLevel = findViewById<TextView>(R.id.textViewRewardsLevelSummary)
        val progressLevel = findViewById<ProgressBar>(R.id.progressRewardsLevel)
        val textBadgeXp = findViewById<TextView>(R.id.textViewRewardsBadgeXp)
        val journeyContainer = findViewById<LinearLayout>(R.id.layoutRewardsJourney)
        val badgesContainer = findViewById<LinearLayout>(R.id.layoutRewardsBadges)
        val statsContainer = findViewById<LinearLayout>(R.id.layoutRewardsStats)
        val spinnerBadge = findViewById<Spinner>(R.id.spinnerRewardBadge)
        val buttonAward = findViewById<Button>(R.id.buttonAwardSelectedBadge)
        val menuButton = findViewById<ImageView>(R.id.buttonRewardsMenu)
        val logoutButton = findViewById<ImageView>(R.id.buttonRewardsLogout)
        val navDashboard = findViewById<Button>(R.id.navRewardsDashboard)
        val navExpenses = findViewById<Button>(R.id.navRewardsExpenses)
        val navCategory = findViewById<Button>(R.id.navRewardsCategory)
        val navBudget = findViewById<Button>(R.id.navRewardsBudget)

        val loading = findViewById<TextView>(R.id.textViewRewardsLoading)

        val spinnerLabels = badgeChoices.map { badge ->
            badge.type.replace('_', ' ')
                .split(' ')
                .joinToString(" ") { word ->
                    word.replaceFirstChar { ch -> ch.uppercaseChar() }
                }
        }
        spinnerBadge.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            spinnerLabels
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        rewardsViewModel.loadRewardsData(userId)

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
                    MENU_REWARDS -> Unit
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

        buttonAward.setOnClickListener {
            val index = spinnerBadge.selectedItemPosition
            if (index !in badgeChoices.indices) return@setOnClickListener
            rewardsViewModel.awardBadge(badgeChoices[index])
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                rewardsViewModel.uiState.collect { state ->
                    loading.visibility = if (state.isLoading) View.VISIBLE else View.GONE

                    val user = state.user
                    if (user == null) {
                        textLevel.text = getString(R.string.rewards_loading_profile)
                    } else {
                        val progress = rewardsViewModel.getLevelProgress()
                        textLevelName.text = "Level ${progress.level}: ${levelTitle(progress.level)}"
                        textLevelPoints.text = "${user.xp} pts"
                        textLevel.text = "${progress.xpToNextLevel} points to ${levelTitle(progress.level + 1)}"
                        progressLevel.progress = progress.progressPercent
                        progressLevel.progressTintList = ColorStateList.valueOf(Color.parseColor("#F1CF41"))
                    }

                    val badgeState = rewardsViewModel.getBadgeCollectionState()
                    val earnedCount = badgeState.count { it.isEarned }
                    textBadgeXp.text = "$earnedCount of ${badgeState.size} badges earned"
                    renderLevelJourney(journeyContainer, user?.xp ?: 0)
                    renderBadges(badgesContainer, badgeState)
                    renderStats(statsContainer, user?.xp ?: 0, earnedCount, state.totalBadgeXp, badgeState.size)

                    state.errorMessage?.let {
                        Toast.makeText(this@RewardsActivity, it, Toast.LENGTH_LONG).show()
                        rewardsViewModel.clearMessages()
                    }
                    state.successMessage?.let {
                        Toast.makeText(this@RewardsActivity, it, Toast.LENGTH_SHORT).show()
                        rewardsViewModel.clearMessages()
                    }
                }
            }
        }
    }

    private fun renderLevelJourney(container: LinearLayout, xp: Int) {
        container.removeAllViews()
        val thresholds = listOf(0, 100, 250, 500, 1000, 2000, 3500, 5500)
        val labels = listOf(
            "Beginner Budgeter", "Apprentice Saver", "Budget Enthusiast", "Finance Tracker",
            "Savings Expert", "Budget Master", "Financial Guru", "Money Wizard"
        )
        thresholds.forEachIndexed { index, threshold ->
            val row = layoutInflater.inflate(R.layout.item_rewards_level, container, false)
            val reached = xp >= threshold
            row.findViewById<TextView>(R.id.textViewRewardLevelIcon).text = if (reached) "⭐" else "🔒"
            row.findViewById<TextView>(R.id.textViewRewardLevelName).text = "Level ${index + 1}"
            row.findViewById<TextView>(R.id.textViewRewardLevelSubtitle).text = labels[index]
            row.findViewById<TextView>(R.id.textViewRewardLevelXp).text =
                if (reached) "Current" else "$threshold pts"
            container.addView(row)
        }
    }

    private fun renderBadges(container: LinearLayout, badgeState: List<com.example.projectwatchapp.viewmodel.BadgeUiModel>) {
        container.removeAllViews()
        badgeState.forEach { badge ->
            val row = layoutInflater.inflate(R.layout.item_rewards_badge, container, false)
            row.findViewById<TextView>(R.id.textViewRewardBadgeIcon).text = if (badge.isEarned) "🏅" else "🔒"
            row.findViewById<TextView>(R.id.textViewRewardBadgeName).text = prettyBadgeName(badge.type)
            row.findViewById<TextView>(R.id.textViewRewardBadgeSubtitle).text = "+${badge.xpReward} XP"
            row.findViewById<TextView>(R.id.textViewRewardBadgeState).text = if (badge.isEarned) "✓" else ""
            container.addView(row)
        }
    }

    private fun renderStats(container: LinearLayout, xp: Int, earnedCount: Int, totalBadgeXp: Int, totalBadges: Int) {
        container.removeAllViews()
        val stats = listOf(
            "🎖️ Badges Earned" to earnedCount.toString(),
            "🧩 Badges Total" to totalBadges.toString(),
            "🔥 Current Level" to SessionManager.getLevelFromXp(xp).toString(),
            "📈 XP To Next" to SessionManager.getXpToNextLevel(xp).toString(),
            "🧠 Badge XP" to totalBadgeXp.toString(),
            "🏆 Total Points" to xp.toString()
        )
        stats.chunked(2).forEach { pair ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            pair.forEach { (label, value) ->
                val card = layoutInflater.inflate(R.layout.item_rewards_stat, row, false)
                card.findViewById<TextView>(R.id.textViewRewardStatLabel).text = label
                card.findViewById<TextView>(R.id.textViewRewardStatValue).text = value
                row.addView(card)
            }
            container.addView(row)
        }
    }

    private fun levelTitle(level: Int): String = when (level.coerceIn(1, 8)) {
        1 -> "Beginner Budgeter"
        2 -> "Apprentice Saver"
        3 -> "Budget Enthusiast"
        4 -> "Finance Tracker"
        5 -> "Savings Expert"
        6 -> "Budget Master"
        7 -> "Financial Guru"
        else -> "Money Wizard"
    }

    private fun prettyBadgeName(type: String): String {
        return type.replace('_', ' ').split(' ').joinToString(" ") { word ->
            word.replaceFirstChar { it.uppercaseChar() }
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
    private fun navigateInfo(userId: Long) {
        if (userId <= 0) return
        startActivity(Intent(this, com.example.projectwatchapp.ui.info.InfoHelpActivity::class.java).putExtra(LoginActivity.EXTRA_USER_ID, userId))
    }
    private fun navigateGoals(userId: Long) {
        startActivity(Intent(this, GoalsActivity::class.java).putExtra(LoginActivity.EXTRA_USER_ID, userId))
    }

    private fun navigateReports(userId: Long) {
        startActivity(Intent(this, ReportsActivity::class.java).putExtra(LoginActivity.EXTRA_USER_ID, userId))
    }
}

class RewardsViewModelFactory(
    private val database: AppDatabase
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RewardsViewModel::class.java)) {
            return RewardsViewModel(
                database.earnedBadgeDao(),
                database.userDao()
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
