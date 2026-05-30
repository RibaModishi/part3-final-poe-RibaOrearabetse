package com.example.projectwatchapp.ui.reports

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
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

        val fromDateText   = findViewById<TextView>(R.id.textViewReportsFromDate)
        val toDateText     = findViewById<TextView>(R.id.textViewReportsToDate)
        val totalSpentText = findViewById<TextView>(R.id.textViewReportsTotalSpent)
        val transactionsText = findViewById<TextView>(R.id.textViewReportsTransactions)
        val avgDailyText   = findViewById<TextView>(R.id.textViewReportsAvgDaily)
        val daysText       = findViewById<TextView>(R.id.textViewReportsDays)
        val trendTitle     = findViewById<TextView>(R.id.textViewReportsTrendTitle)
        val trendSubtitle  = findViewById<TextView>(R.id.textViewReportsTrendSubtitle)
        val trendBars      = findViewById<LinearLayout>(R.id.layoutReportsTrendBars)
        val categoryLegend = findViewById<LinearLayout>(R.id.layoutReportsCategoryLegend)
        val breakdownContainer = findViewById<LinearLayout>(R.id.layoutReportsBreakdown)

        // Card containers — shown only on the Category tab.
        val cardPieChart  = findViewById<LinearLayout>(R.id.cardReportsPieChart)
        val cardBreakdown = findViewById<LinearLayout>(R.id.cardReportsBreakdown)

        val dailyButton    = findViewById<Button>(R.id.buttonReportsDaily)
        val weeklyButton   = findViewById<Button>(R.id.buttonReportsWeekly)
        val categoryButton = findViewById<Button>(R.id.buttonReportsCategory)

        val menuButton   = findViewById<ImageView>(R.id.buttonReportsMenu)
        val logoutButton = findViewById<ImageView>(R.id.buttonReportsLogout)

        // Bottom-nav buttons (matched to Dashboard style)
        val navDashboard = findViewById<Button>(R.id.navReportsDashboard)
        val navExpenses  = findViewById<Button>(R.id.navReportsExpenses)
        val navCategory  = findViewById<Button>(R.id.navReportsCategory)
        val navBudget    = findViewById<Button>(R.id.navReportsBudget)

        fun syncDateViews() {
            fromDateText.text = startDate.toString()
            toDateText.text   = endDate.toString()
        }
        syncDateViews()

        fromDateText.setOnClickListener {
            openDatePicker(startDate) { picked ->
                startDate = picked
                if (startDate.isAfter(endDate)) endDate = startDate
                syncDateViews()
                renderAll(totalSpentText, transactionsText, avgDailyText, daysText,
                    trendTitle, trendSubtitle, trendBars, categoryLegend, breakdownContainer,
                    cardPieChart, cardBreakdown)
            }
        }
        toDateText.setOnClickListener {
            openDatePicker(endDate) { picked ->
                endDate = picked
                if (endDate.isBefore(startDate)) startDate = endDate
                syncDateViews()
                renderAll(totalSpentText, transactionsText, avgDailyText, daysText,
                    trendTitle, trendSubtitle, trendBars, categoryLegend, breakdownContainer,
                    cardPieChart, cardBreakdown)
            }
        }

        fun setTab(next: ReportTab) {
            tab = next
            dailyButton.background    = getDrawable(if (next == ReportTab.DAILY)    R.drawable.btn_chip_dark  else R.drawable.btn_chip_light)
            weeklyButton.background   = getDrawable(if (next == ReportTab.WEEKLY)   R.drawable.btn_chip_dark  else R.drawable.btn_chip_light)
            categoryButton.background = getDrawable(if (next == ReportTab.CATEGORY) R.drawable.btn_chip_dark  else R.drawable.btn_chip_light)
            dailyButton.setTextColor(if (next == ReportTab.DAILY)    0xFFFFFFFF.toInt() else 0xFF1A1A1A.toInt())
            weeklyButton.setTextColor(if (next == ReportTab.WEEKLY)  0xFFFFFFFF.toInt() else 0xFF1A1A1A.toInt())
            categoryButton.setTextColor(if (next == ReportTab.CATEGORY) 0xFFFFFFFF.toInt() else 0xFF1A1A1A.toInt())
            renderAll(totalSpentText, transactionsText, avgDailyText, daysText,
                trendTitle, trendSubtitle, trendBars, categoryLegend, breakdownContainer,
                cardPieChart, cardBreakdown)
        }
        dailyButton.setOnClickListener    { setTab(ReportTab.DAILY) }
        weeklyButton.setOnClickListener   { setTab(ReportTab.WEEKLY) }
        categoryButton.setOnClickListener { setTab(ReportTab.CATEGORY) }
        setTab(ReportTab.DAILY)

        menuButton.setOnClickListener { anchor ->
            val popup = PopupMenu(this, anchor)
            popup.menu.add(0, MENU_DASHBOARD, 0, getString(R.string.dashboard_nav_goals))
            popup.menu.add(0, MENU_EXPENSES,  1, getString(R.string.dashboard_nav_expenses))
            popup.menu.add(0, MENU_CATEGORY,  2, getString(R.string.dashboard_nav_category))
            popup.menu.add(0, MENU_BUDGET,    3, getString(R.string.dashboard_nav_budget))
            popup.menu.add(0, MENU_GOALS,     4, getString(R.string.action_open_goals))
            popup.menu.add(0, MENU_REWARDS,   5, getString(R.string.action_open_rewards))
            popup.menu.add(0, MENU_REPORTS,   6, getString(R.string.reports_title))
            popup.menu.add(0, MENU_LOGOUT,    7, getString(R.string.dashboard_back_to_login))
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
                    MENU_EXPENSES  -> navigateExpenses(userId)
                    MENU_CATEGORY  -> navigateCategory(userId)
                    MENU_BUDGET    -> navigateBudget(userId)
                    MENU_GOALS     -> navigateGoals(userId)
                    MENU_REWARDS   -> navigateRewards(userId)
                    MENU_REPORTS   -> Unit
                    MENU_LOGOUT    -> {
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
        navExpenses.setOnClickListener  { navigateExpenses(userId) }
        navCategory.setOnClickListener  { navigateCategory(userId) }
        navBudget.setOnClickListener    { navigateBudget(userId) }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                database.categoryDao().getCategoriesForUser(userId).collect { categories ->
                    categoriesById = categories.associate { it.categoryId to it.name }
                    renderAll(totalSpentText, transactionsText, avgDailyText, daysText,
                        trendTitle, trendSubtitle, trendBars, categoryLegend, breakdownContainer,
                        cardPieChart, cardBreakdown)
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                database.expenseDao().getExpensesForUser(userId).collect { expenses ->
                    expensesCache = expenses
                    renderAll(totalSpentText, transactionsText, avgDailyText, daysText,
                        trendTitle, trendSubtitle, trendBars, categoryLegend, breakdownContainer,
                        cardPieChart, cardBreakdown)
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
        breakdownContainer: LinearLayout,
        cardPieChart: LinearLayout,
        cardBreakdown: LinearLayout
    ) {
        val filtered = expensesCache.filter { e ->
            val day = Instant.ofEpochMilli(e.date).atZone(zoneId).toLocalDate()
            !day.isBefore(startDate) && !day.isAfter(endDate)
        }

        val total      = filtered.sumOf { it.amount }
        val daysCount  = filtered.map { Instant.ofEpochMilli(it.date).atZone(zoneId).toLocalDate() }
            .distinct().size.coerceAtLeast(1)
        val avg        = if (filtered.isEmpty()) 0.0 else total / daysCount

        totalSpentText.text  = "R${"%.0f".format(total)}"
        transactionsText.text = filtered.size.toString()
        avgDailyText.text    = "R${"%.0f".format(avg)}"
        daysText.text        = daysCount.toString()

        val byCategory = filtered.groupBy { it.categoryId }
            .mapValues { (_, list) -> list.sumOf { it.amount } }
            .toList()
            .sortedByDescending { it.second }

        // ── Show/hide the pie-chart and breakdown cards based on active tab ──
        val isCategoryTab = tab == ReportTab.CATEGORY
        cardPieChart.visibility  = if (isCategoryTab) View.VISIBLE else View.GONE
        cardBreakdown.visibility = if (isCategoryTab) View.VISIBLE else View.GONE

        if (isCategoryTab) {
            renderPieChart(categoryLegend, byCategory, total)

            breakdownContainer.removeAllViews()
            val totalAmount = if (total == 0.0) 1.0 else total
            val barColors   = listOf("#5A84D6", "#7D57C2", "#40A769", "#9B9B9B", "#D8A93F")
            byCategory.forEachIndexed { index, (categoryId, amount) ->
                val name   = categoryId?.let { categoriesById[it] } ?: getString(R.string.category_spinner_none)
                val row    = layoutInflater.inflate(R.layout.item_report_breakdown_row, breakdownContainer, false)
                val percent = (amount / totalAmount) * 100

                row.findViewById<View>(R.id.viewReportBreakdownDot).backgroundTintList =
                    ColorStateList.valueOf(Color.parseColor(barColors[index % barColors.size]))
                row.findViewById<TextView>(R.id.textViewReportBreakdownName).text = name
                row.findViewById<TextView>(R.id.textViewReportBreakdownAmount).text =
                    "R${"%.2f".format(amount)} (${"%.1f".format(percent)}%)"
                row.findViewById<ProgressBar>(R.id.progressReportBreakdown).apply {
                    progress = percent.toInt()
                    progressTintList           = ColorStateList.valueOf(Color.parseColor(barColors[index % barColors.size]))
                    progressBackgroundTintList = ColorStateList.valueOf(Color.parseColor("#E6E6E6"))
                }
                breakdownContainer.addView(row)
            }
        }

        // ── Render only the chart that belongs to the active tab ──
        when (tab) {
            ReportTab.DAILY -> {
                trendTitle.text    = getString(R.string.reports_daily_trend)
                trendSubtitle.text = getString(R.string.reports_daily_sub)
                renderDailyTrend(trendBars, filtered)
            }
            ReportTab.WEEKLY -> {
                trendTitle.text    = getString(R.string.reports_weekly_trend)
                trendSubtitle.text = getString(R.string.reports_weekly_sub)
                val fields  = WeekFields.of(Locale.getDefault())
                val byWeek  = filtered.groupBy {
                    val day = Instant.ofEpochMilli(it.date).atZone(zoneId).toLocalDate()
                    day.get(fields.weekOfWeekBasedYear())
                }.mapValues { (_, list) -> list.sumOf { it.amount } }
                    .toList()
                    .sortedBy { it.first }
                renderWeeklyTrend(trendBars, byWeek)
            }
            ReportTab.CATEGORY -> {
                trendTitle.text    = getString(R.string.reports_category_trend)
                trendSubtitle.text = getString(R.string.reports_category_sub)
                val byCategoryLabel = byCategory.take(6).associate { (id, amount) ->
                    (id?.let { categoriesById[it] } ?: getString(R.string.category_spinner_none)) to amount
                }
                renderTrendBars(trendBars, byCategoryLabel)
            }
        }
    }

    private fun renderPieChart(container: LinearLayout, data: List<Pair<Long?, Double>>, total: Double) {
        container.removeAllViews()
        if (data.isEmpty()) {
            container.addView(TextView(this).apply {
                text    = "No data to display"
                gravity = android.view.Gravity.CENTER
            })
            return
        }
        val pieView = PieChartView(this, data, categoriesById, total)
        container.addView(pieView)
    }

    private fun renderDailyTrend(container: LinearLayout, expenses: List<Expense>) {
        container.removeAllViews()
        if (expenses.isEmpty()) {
            container.addView(TextView(this).apply {
                text    = "No data for selected range"
                gravity = android.view.Gravity.CENTER
                setTextColor(0xFF888888.toInt())
            })
            return
        }

        // Group by day, sum amounts, show last 7 days
        val byDay = expenses.groupBy {
            Instant.ofEpochMilli(it.date).atZone(zoneId).toLocalDate()
        }.mapValues { (_, list) -> list.sumOf { it.amount } }
            .toList()
            .sortedBy { it.first }
            .takeLast(7)

        val maxVal    = byDay.maxOf { it.second }.coerceAtLeast(1.0)
        val chartH    = 160
        val barColors = listOf("#5A84D6", "#7D57C2", "#40A769", "#9B9B9B", "#D8A93F",
            "#E05C5C", "#40BFB0")

        // Horizontal row of vertical bars, bottom-aligned
        val row = LinearLayout(this).apply {
            orientation  = LinearLayout.HORIZONTAL
            gravity      = android.view.Gravity.BOTTOM
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        byDay.forEachIndexed { index, (date, amount) ->
            val barHeight = ((amount / maxVal) * chartH).toInt().coerceAtLeast(6)
            val col = LinearLayout(this).apply {
                orientation  = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                gravity      = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
                setPadding(4, 0, 4, 0)
            }
            // Amount label above bar
            col.addView(TextView(this).apply {
                text      = "R${amount.toInt()}"
                textSize  = 8f
                gravity   = android.view.Gravity.CENTER
                setTextColor(0xFF555555.toInt())
            })
            // Bar
            col.addView(View(this).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, barHeight)
                setBackgroundColor(Color.parseColor(barColors[index % barColors.size]))
            })
            // Day label below bar
            col.addView(TextView(this).apply {
                text      = date.month.name.take(3) + "\n" + date.dayOfMonth
                textSize  = 9f
                gravity   = android.view.Gravity.CENTER
                setTextColor(0xFF888888.toInt())
            })
            row.addView(col)
        }
        container.addView(row)
    }

    private fun renderWeeklyTrend(container: LinearLayout, data: List<Pair<Int, Double>>) {
        container.removeAllViews()
        if (data.isEmpty()) {
            container.addView(TextView(this).apply {
                text    = "No data for selected range"
                gravity = android.view.Gravity.CENTER
                setTextColor(0xFF888888.toInt())
            })
            return
        }

        val maxVal = data.maxOf { it.second }.coerceAtLeast(1.0)
        val chartH = 160
        val barColor = "#7D57C2"

        val row = LinearLayout(this).apply {
            orientation  = LinearLayout.HORIZONTAL
            gravity      = android.view.Gravity.BOTTOM
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        data.forEach { (week, amount) ->
            val barHeight = ((amount / maxVal) * chartH).toInt().coerceAtLeast(6)
            val col = LinearLayout(this).apply {
                orientation  = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                gravity      = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
                setPadding(6, 0, 6, 0)
            }
            // Amount label above bar
            col.addView(TextView(this).apply {
                text      = "R${amount.toInt()}"
                textSize  = 8f
                gravity   = android.view.Gravity.CENTER
                setTextColor(0xFF555555.toInt())
            })
            // Bar
            col.addView(View(this).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, barHeight)
                setBackgroundColor(Color.parseColor(barColor))
            })
            // Week label below bar
            col.addView(TextView(this).apply {
                text      = "Wk $week"
                textSize  = 9f
                gravity   = android.view.Gravity.CENTER
                setTextColor(0xFF888888.toInt())
            })
            row.addView(col)
        }
        container.addView(row)
    }

    private fun renderTrendBars(container: LinearLayout, entries: Map<String, Double>) {
        container.removeAllViews()
        if (entries.isEmpty()) {
            container.addView(TextView(this).apply {
                text    = "No data for selected range"
                gravity = android.view.Gravity.CENTER
                setTextColor(0xFF888888.toInt())
            })
            return
        }

        val maxValue  = entries.values.maxOrNull() ?: 1.0
        val chartH    = 160
        val barColors = listOf("#5A84D6", "#7D57C2", "#40A769", "#9B9B9B", "#D8A93F")

        val row = LinearLayout(this).apply {
            orientation  = LinearLayout.HORIZONTAL
            gravity      = android.view.Gravity.BOTTOM
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        entries.entries.forEachIndexed { index, (label, amount) ->
            val barHeight = ((amount / maxValue) * chartH).toInt().coerceAtLeast(6)
            val col = LinearLayout(this).apply {
                orientation  = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                gravity      = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
                setPadding(4, 0, 4, 0)
            }
            // Amount label above bar
            col.addView(TextView(this).apply {
                text      = "R${amount.toInt()}"
                textSize  = 8f
                gravity   = android.view.Gravity.CENTER
                setTextColor(0xFF555555.toInt())
            })
            // Bar
            col.addView(View(this).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, barHeight)
                setBackgroundColor(Color.parseColor(barColors[index % barColors.size]))
            })
            // Category label below bar
            col.addView(TextView(this).apply {
                text      = label.take(6)
                textSize  = 9f
                gravity   = android.view.Gravity.CENTER
                setTextColor(0xFF888888.toInt())
            })
            row.addView(col)
        }
        container.addView(row)
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

    inner class PieChartView(
        context: Context,
        val data: List<Pair<Long?, Double>>,
        val catNames: Map<Long, String>,
        val total: Double
    ) : View(context) {
        private val paint  = Paint(Paint.ANTI_ALIAS_FLAG)
        private val colors = listOf("#5A84D6", "#7D57C2", "#40A769", "#9B9B9B", "#D8A93F")

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), 450)
        }

        override fun onDraw(canvas: Canvas) {
            val rect = RectF(width / 2f - 150f, 50f, width / 2f + 150f, 350f)
            var startAngle = 0f
            data.forEachIndexed { i, item ->
                val sweep = (item.second / total * 360).toFloat()
                paint.color = Color.parseColor(colors[i % colors.size])
                canvas.drawArc(rect, startAngle, sweep, true, paint)
                if (sweep > 15) {
                    val angle = Math.toRadians((startAngle + sweep / 2).toDouble())
                    val x     = (width / 2f + Math.cos(angle) * 190).toFloat()
                    val y     = (200f + Math.sin(angle) * 190).toFloat()
                    paint.color    = Color.BLACK
                    paint.textSize = 24f
                    val name = item.first?.let { catNames[it] } ?: "Other"
                    canvas.drawText("$name: R${item.second.toInt()}", x - 40, y, paint)
                }
                startAngle += sweep
            }
        }
    }
}