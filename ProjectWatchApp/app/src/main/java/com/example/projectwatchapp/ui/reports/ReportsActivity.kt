package com.example.projectwatchapp.ui.reports

import android.app.DatePickerDialog
import android.content.res.ColorStateList
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.projectwatchapp.R
import com.example.projectwatchapp.data.AppDatabase
import com.example.projectwatchapp.data.entities.Expense
import com.example.projectwatchapp.ui.auth.LoginActivity
import com.example.projectwatchapp.ui.budget.BudgetActivity
import com.example.projectwatchapp.ui.category.CategoryActivity
import com.example.projectwatchapp.ui.dashboard.DashboardActivity
import com.example.projectwatchapp.ui.expense.ExpenseActivity
import com.example.projectwatchapp.ui.goals.GoalsActivity
import com.example.projectwatchapp.ui.rewards.RewardsActivity
import com.example.projectwatchapp.ui.common.PopupMenuUtils
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.WeekFields
import java.util.Locale
import kotlinx.coroutines.launch

class ReportsActivity : ComponentActivity() {
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

    private enum class ReportTab { DAILY, WEEKLY, CATEGORY }

    private val database by lazy { AppDatabase.getDatabase(this) }
    private val zoneId: ZoneId = ZoneId.systemDefault()
    private var tab: ReportTab = ReportTab.DAILY
    private var categoriesById: Map<Long, String> = emptyMap()
    private var expensesCache: List<Expense> = emptyList()
    private var startDate: LocalDate = LocalDate.now().withDayOfMonth(1)
    private var endDate: LocalDate = LocalDate.now()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reports)

        val userId = intent.getLongExtra(LoginActivity.EXTRA_USER_ID, -1L)
        if (userId <= 0) {
            Toast.makeText(this, "Invalid user. Please login again.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val fromDateText = findViewById<TextView>(R.id.textViewReportsFromDate)
        val toDateText = findViewById<TextView>(R.id.textViewReportsToDate)
        val totalSpentText = findViewById<TextView>(R.id.textViewReportsTotalSpent)
        val transactionsText = findViewById<TextView>(R.id.textViewReportsTransactions)
        val avgDailyText = findViewById<TextView>(R.id.textViewReportsAvgDaily)
        val daysText = findViewById<TextView>(R.id.textViewReportsDays)
        val trendTitle = findViewById<TextView>(R.id.textViewReportsTrendTitle)
        val trendSubtitle = findViewById<TextView>(R.id.textViewReportsTrendSubtitle)
        val trendBars = findViewById<LinearLayout>(R.id.layoutReportsTrendBars)
        val categoryLegend = findViewById<LinearLayout>(R.id.layoutReportsCategoryLegend)
        val breakdownContainer = findViewById<LinearLayout>(R.id.layoutReportsBreakdown)

        val dailyButton = findViewById<Button>(R.id.buttonReportsDaily)
        val weeklyButton = findViewById<Button>(R.id.buttonReportsWeekly)
        val categoryButton = findViewById<Button>(R.id.buttonReportsCategory)

        val menuButton = findViewById<ImageView>(R.id.buttonReportsMenu)
        val logoutButton = findViewById<ImageView>(R.id.buttonReportsLogout)
        val navDashboard = findViewById<TextView>(R.id.navReportsDashboard)
        val navExpenses = findViewById<TextView>(R.id.navReportsExpenses)
        val navCategory = findViewById<TextView>(R.id.navReportsCategory)
        val navBudget = findViewById<TextView>(R.id.navReportsBudget)

        fun syncDateViews() {
            fromDateText.text = startDate.toString()
            toDateText.text = endDate.toString()
        }
        syncDateViews()

        fromDateText.setOnClickListener {
            openDatePicker(startDate) { picked ->
                startDate = picked
                if (startDate.isAfter(endDate)) endDate = startDate
                syncDateViews()
                renderAll(totalSpentText, transactionsText, avgDailyText, daysText, trendTitle, trendSubtitle, trendBars, categoryLegend, breakdownContainer)
            }
        }
        toDateText.setOnClickListener {
            openDatePicker(endDate) { picked ->
                endDate = picked
                if (endDate.isBefore(startDate)) startDate = endDate
                syncDateViews()
                renderAll(totalSpentText, transactionsText, avgDailyText, daysText, trendTitle, trendSubtitle, trendBars, categoryLegend, breakdownContainer)
            }
        }

        fun setTab(next: ReportTab) {
            tab = next
            dailyButton.background = getDrawable(if (next == ReportTab.DAILY) R.drawable.btn_chip_dark else R.drawable.btn_chip_light)
            weeklyButton.background = getDrawable(if (next == ReportTab.WEEKLY) R.drawable.btn_chip_dark else R.drawable.btn_chip_light)
            categoryButton.background = getDrawable(if (next == ReportTab.CATEGORY) R.drawable.btn_chip_dark else R.drawable.btn_chip_light)
            dailyButton.setTextColor(if (next == ReportTab.DAILY) 0xFFFFFFFF.toInt() else 0xFF1A1A1A.toInt())
            weeklyButton.setTextColor(if (next == ReportTab.WEEKLY) 0xFFFFFFFF.toInt() else 0xFF1A1A1A.toInt())
            categoryButton.setTextColor(if (next == ReportTab.CATEGORY) 0xFFFFFFFF.toInt() else 0xFF1A1A1A.toInt())
            renderAll(totalSpentText, transactionsText, avgDailyText, daysText, trendTitle, trendSubtitle, trendBars, categoryLegend, breakdownContainer)
        }
        dailyButton.setOnClickListener { setTab(ReportTab.DAILY) }
        weeklyButton.setOnClickListener { setTab(ReportTab.WEEKLY) }
        categoryButton.setOnClickListener { setTab(ReportTab.CATEGORY) }
        setTab(ReportTab.DAILY)

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
                    MENU_BUDGET -> navigateBudget(userId)
                    MENU_GOALS -> navigateGoals(userId)
                    MENU_REWARDS -> navigateRewards(userId)
                    MENU_REPORTS -> Unit
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

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                database.categoryDao().getCategoriesForUser(userId).collect { categories ->
                    categoriesById = categories.associate { it.categoryId to it.name }
                    renderAll(totalSpentText, transactionsText, avgDailyText, daysText, trendTitle, trendSubtitle, trendBars, categoryLegend, breakdownContainer)
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                database.expenseDao().getExpensesForUser(userId).collect { expenses ->
                    expensesCache = expenses
                    renderAll(totalSpentText, transactionsText, avgDailyText, daysText, trendTitle, trendSubtitle, trendBars, categoryLegend, breakdownContainer)
                }
            }
        }
    }

    private fun openDatePicker(initial: LocalDate, onPicked: (LocalDate) -> Unit) {
        DatePickerDialog(
            this,
            { _, year, month, day -> onPicked(LocalDate.of(year, month + 1, day)) },
            initial.year,
            initial.monthValue - 1,
            initial.dayOfMonth
        ).show()
    }

    private fun renderAll(
        totalSpentText: TextView,
        transactionsText: TextView,
        avgDailyText: TextView,
        daysText: TextView,
        trendTitle: TextView,
        trendSubtitle: TextView,
        trendBars: LinearLayout,
        categoryLegend: LinearLayout,
        breakdownContainer: LinearLayout
    ) {
        val filtered = expensesCache.filter { e ->
            val day = Instant.ofEpochMilli(e.date).atZone(zoneId).toLocalDate()
            !day.isBefore(startDate) && !day.isAfter(endDate)
        }

        val total = filtered.sumOf { it.amount }
        val days = filtered.map { Instant.ofEpochMilli(it.date).atZone(zoneId).toLocalDate() }.distinct().size.coerceAtLeast(1)
        val avg = if (filtered.isEmpty()) 0.0 else total / days

        totalSpentText.text = "R${"%.0f".format(total)}"
        transactionsText.text = filtered.size.toString()
        avgDailyText.text = "R${"%.0f".format(avg)}"
        daysText.text = days.toString()

        val byCategory = filtered.groupBy { it.categoryId }
            .mapValues { (_, list) -> list.sumOf { it.amount } }
            .toList()
            .sortedByDescending { it.second }

        renderCategoryLegend(categoryLegend, byCategory)

        breakdownContainer.removeAllViews()
        val max = byCategory.maxOfOrNull { it.second } ?: 1.0
        val barColors = listOf("#5A84D6", "#7D57C2", "#40A769", "#9B9B9B", "#D8A93F")
        byCategory.take(5).forEachIndexed { index, (categoryId, amount) ->
            val name = categoryId?.let { categoriesById[it] } ?: getString(R.string.category_spinner_none)
            val row = layoutInflater.inflate(R.layout.item_report_breakdown_row, breakdownContainer, false)
            row.findViewById<TextView>(R.id.textViewReportBreakdownName).text = name
            row.findViewById<TextView>(R.id.textViewReportBreakdownAmount).text = "R${"%.0f".format(amount)}"
            row.findViewById<ProgressBar>(R.id.progressReportBreakdown).apply {
                progress = ((amount / max) * 100).toInt()
                progressTintList = ColorStateList.valueOf(Color.parseColor(barColors[index % barColors.size]))
                progressBackgroundTintList = ColorStateList.valueOf(Color.parseColor("#E6E6E6"))
            }
            breakdownContainer.addView(row)
        }

        when (tab) {
            ReportTab.DAILY -> {
                trendTitle.text = getString(R.string.reports_daily_trend)
                trendSubtitle.text = getString(R.string.reports_daily_sub)
                val byDay = filtered.groupBy { Instant.ofEpochMilli(it.date).atZone(zoneId).toLocalDate() }
                    .mapValues { (_, list) -> list.sumOf { it.amount } }
                    .toList()
                    .sortedBy { it.first }
                    .takeLast(6)
                    .associate { it.first.toString() to it.second }
                renderDailyTrend(trendBars, byDay, byCategory)
            }
            ReportTab.WEEKLY -> {
                trendTitle.text = getString(R.string.reports_weekly_trend)
                trendSubtitle.text = getString(R.string.reports_weekly_sub)
                val fields = WeekFields.of(Locale.getDefault())
                val byWeek = filtered.groupBy {
                    val day = Instant.ofEpochMilli(it.date).atZone(zoneId).toLocalDate()
                    "W${day.get(fields.weekOfWeekBasedYear())}"
                }.mapValues { (_, list) -> list.sumOf { it.amount } }
                    .toList()
                    .sortedBy { it.first }
                    .takeLast(6)
                    .associate { it.first to it.second }
                renderWeeklyTrend(trendBars, byWeek)
            }
            ReportTab.CATEGORY -> {
                trendTitle.text = getString(R.string.reports_category_trend)
                trendSubtitle.text = getString(R.string.reports_category_sub)
                val byCategoryLabel = byCategory.take(6).associate { (id, amount) ->
                    (id?.let { categoriesById[it] } ?: getString(R.string.category_spinner_none)) to amount
                }
                renderTrendBars(trendBars, byCategoryLabel)
            }
        }
    }

    private fun renderCategoryLegend(
        container: LinearLayout,
        byCategory: List<Pair<Long?, Double>>
    ) {
        container.removeAllViews()
        val colors = listOf("#5A84D6", "#7D57C2", "#40A769", "#9B9B9B")
        byCategory.take(4).forEachIndexed { index, (categoryId, amount) ->
            val name = categoryId?.let { categoriesById[it] } ?: getString(R.string.category_spinner_none)
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 2, 0, 2)
            }
            val dot = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(12, 12).apply { marginEnd = 8 }
                background = getDrawable(R.drawable.btn_chip_light)
                backgroundTintList = ColorStateList.valueOf(Color.parseColor(colors[index % colors.size]))
            }
            val text = TextView(this).apply {
                this.text = "$name: R${"%.0f".format(amount)}"
                textSize = 13f
                setTextColor(0xFF4A4A4A.toInt())
            }
            row.addView(dot)
            row.addView(text)
            container.addView(row)
        }
        if (byCategory.isEmpty()) {
            container.addView(TextView(this).apply {
                text = getString(R.string.expense_none_found)
                setTextColor(0xFF888888.toInt())
                textSize = 12f
            })
        }
    }

    private fun renderDailyTrend(
        container: LinearLayout,
        entries: Map<String, Double>,
        byCategory: List<Pair<Long?, Double>>
    ) {
        container.removeAllViews()
        if (entries.isEmpty()) {
            renderTrendBars(container, entries)
            return
        }
        val points = entries.toList().sortedBy { it.first }.takeLast(7)
        val maxValue = points.maxOfOrNull { it.second } ?: 1.0

        val chartRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.BOTTOM
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                90
            )
        }
        val labelsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        points.forEachIndexed { index, (label, amount) ->
            val barHeight = (16 + (62 * (amount / maxValue))).toInt()
            val barContainer = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                    marginEnd = if (index == points.lastIndex) 0 else 4
                }
            }
            val amountText = TextView(this).apply {
                text = if (amount > 0) "R${"%.0f".format(amount)}" else ""
                textSize = 9f
                setTextColor(0xFF8A8A8A.toInt())
                gravity = android.view.Gravity.CENTER_HORIZONTAL
            }
            val bar = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    barHeight
                )
                if (index == points.lastIndex) {
                    background = getDrawable(R.drawable.bg_progress_yellow)
                } else {
                    setBackgroundColor(0xFFDBDBDB.toInt())
                }
            }
            barContainer.addView(amountText)
            barContainer.addView(bar)
            chartRow.addView(barContainer)

            labelsRow.addView(TextView(this).apply {
                val short = if (label.length >= 10) label.substring(5) else label
                text = short
                textSize = 9f
                setTextColor(0xFF9B9B9B.toInt())
                gravity = android.view.Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
        }
        container.addView(chartRow)
        container.addView(labelsRow)

        val legend = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 8, 0, 0)
        }
        val colors = listOf("#40A769", "#7D57C2", "#5A84D6")
        byCategory.take(3).forEachIndexed { index, (categoryId, _) ->
            val name = categoryId?.let { categoriesById[it] } ?: getString(R.string.category_spinner_none)
            legend.addView(TextView(this).apply {
                text = "● $name  "
                textSize = 10f
                setTextColor(Color.parseColor(colors[index % colors.size]))
            })
        }
        container.addView(legend)
    }

    private fun renderWeeklyTrend(container: LinearLayout, entries: Map<String, Double>) {
        container.removeAllViews()
        if (entries.isEmpty()) {
            renderTrendBars(container, entries)
            return
        }
        val frame = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = getDrawable(R.drawable.input_default)
            setPadding(12, 10, 12, 10)
        }
        val maxValue = entries.values.maxOrNull() ?: 1.0
        val main = entries.entries.last()
        frame.addView(TextView(this).apply {
            text = "8000\n6000\n4000\n2000\n0"
            textSize = 9f
            setTextColor(0xFFB0B0B0.toInt())
        })
        frame.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2).apply { topMargin = 6 }
            setBackgroundColor(0xFFE3E3E3.toInt())
        })
        frame.addView(TextView(this).apply {
            text = "•"
            textSize = 26f
            setTextColor(0xFF7D57C2.toInt())
            gravity = android.view.Gravity.CENTER_HORIZONTAL
        })
        frame.addView(TextView(this).apply {
            text = "${main.key}  R${"%.0f".format(main.value)}"
            textSize = 11f
            gravity = android.view.Gravity.CENTER_HORIZONTAL
            setTextColor(0xFF8A8A8A.toInt())
        })
        container.addView(frame)
        container.addView(TextView(this).apply {
            text = "← Weekly Spending"
            textSize = 12f
            setTextColor(0xFF7D57C2.toInt())
            gravity = android.view.Gravity.CENTER_HORIZONTAL
        })
    }

    private fun renderTrendBars(container: LinearLayout, entries: Map<String, Double>) {
        container.removeAllViews()
        container.weightSum = entries.size.toFloat().coerceAtLeast(1f)
        if (entries.isEmpty()) {
            container.addView(TextView(this).apply {
                text = getString(R.string.expense_none_found)
                setTextColor(0xFF888888.toInt())
                textSize = 12f
            })
            return
        }
        val maxValue = entries.values.maxOrNull() ?: 1.0
        val barsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                125
            )
            gravity = android.view.Gravity.BOTTOM
        }
        val labelsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        entries.forEach { (label, amount) ->
            val barHeight = (95 * (amount / maxValue)).toInt().coerceAtLeast(8)
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                    marginEnd = 6
                }
            }
            val bar = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    barHeight
                )
                background = getDrawable(R.drawable.bg_progress_yellow)
                alpha = 0.9f
            }
            val amountText = TextView(this).apply {
                text = "R${"%.0f".format(amount)}"
                textSize = 10f
                setTextColor(0xFF7A7A7A.toInt())
                gravity = android.view.Gravity.CENTER
            }
            item.addView(amountText)
            item.addView(bar)
            barsRow.addView(item)

            labelsRow.addView(TextView(this).apply {
                text = label
                textSize = 10f
                setTextColor(0xFF8A8A8A.toInt())
                gravity = android.view.Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
        }
        container.addView(barsRow)
        container.addView(labelsRow)
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
}
