package com.example.projectwatchapp.ui.reports

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Bundle
import android.view.Gravity
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
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import java.util.Locale
import kotlinx.coroutines.launch

class ReportsActivity : ComponentActivity() {

    companion object {
        private const val MENU_DASHBOARD = 1
        private const val MENU_EXPENSES  = 2
        private const val MENU_CATEGORY  = 3
        private const val MENU_BUDGET    = 4
        private const val MENU_GOALS     = 5
        private const val MENU_REWARDS   = 6
        private const val MENU_REPORTS   = 7
        private const val MENU_LOGOUT    = 8
    }

    private enum class ReportTab { DAILY, WEEKLY, CATEGORY }

    private val database by lazy { AppDatabase.getDatabase(this) }
    private val zoneId: ZoneId = ZoneId.systemDefault()
    private var tab: ReportTab = ReportTab.DAILY
    private var categoriesById: Map<Long, String> = emptyMap()
    private var expensesCache: List<Expense> = emptyList()
    private var startDate: LocalDate = LocalDate.now().withDayOfMonth(1)
    private var endDate: LocalDate   = LocalDate.now()

    private val chartColors = listOf("#5A84D6", "#7D57C2", "#40A769", "#D8A93F", "#E05C5C", "#40BFB0", "#9B9B9B")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reports)

        val userId = intent.getLongExtra(LoginActivity.EXTRA_USER_ID, -1L)
        if (userId <= 0) {
            Toast.makeText(this, "Invalid user. Please login again.", Toast.LENGTH_LONG).show()
            finish(); return
        }

        val fromDateText       = findViewById<TextView>(R.id.textViewReportsFromDate)
        val toDateText         = findViewById<TextView>(R.id.textViewReportsToDate)
        val totalSpentText     = findViewById<TextView>(R.id.textViewReportsTotalSpent)
        val transactionsText   = findViewById<TextView>(R.id.textViewReportsTransactions)
        val avgDailyText       = findViewById<TextView>(R.id.textViewReportsAvgDaily)
        val daysText           = findViewById<TextView>(R.id.textViewReportsDays)
        val trendTitle         = findViewById<TextView>(R.id.textViewReportsTrendTitle)
        val trendSubtitle      = findViewById<TextView>(R.id.textViewReportsTrendSubtitle)
        val trendBars          = findViewById<LinearLayout>(R.id.layoutReportsTrendBars)
        val categoryLegend     = findViewById<LinearLayout>(R.id.layoutReportsCategoryLegend)
        val breakdownContainer = findViewById<LinearLayout>(R.id.layoutReportsBreakdown)
        val cardPieChart       = findViewById<LinearLayout>(R.id.cardReportsPieChart)
        val cardBreakdown      = findViewById<LinearLayout>(R.id.cardReportsBreakdown)
        val dailyButton        = findViewById<Button>(R.id.buttonReportsDaily)
        val weeklyButton       = findViewById<Button>(R.id.buttonReportsWeekly)
        val categoryButton     = findViewById<Button>(R.id.buttonReportsCategory)
        val menuButton         = findViewById<ImageView>(R.id.buttonReportsMenu)
        val logoutButton       = findViewById<ImageView>(R.id.buttonReportsLogout)
        val navDashboard       = findViewById<Button>(R.id.navReportsDashboard)
        val navExpenses        = findViewById<Button>(R.id.navReportsExpenses)
        val navCategory        = findViewById<Button>(R.id.navReportsCategory)
        val navBudget          = findViewById<Button>(R.id.navReportsBudget)

        fun syncDateViews() {
            fromDateText.text = startDate.toString()
            toDateText.text   = endDate.toString()
        }
        syncDateViews()

        fun renderAll() = renderAll(
            totalSpentText, transactionsText, avgDailyText, daysText,
            trendTitle, trendSubtitle, trendBars,
            categoryLegend, breakdownContainer, cardPieChart, cardBreakdown
        )

        fromDateText.setOnClickListener {
            openDatePicker(startDate) { picked ->
                startDate = picked
                if (startDate.isAfter(endDate)) endDate = startDate
                syncDateViews(); renderAll()
            }
        }
        toDateText.setOnClickListener {
            openDatePicker(endDate) { picked ->
                endDate = picked
                if (endDate.isBefore(startDate)) startDate = endDate
                syncDateViews(); renderAll()
            }
        }

        fun setTab(next: ReportTab) {
            tab = next
            listOf(dailyButton to ReportTab.DAILY, weeklyButton to ReportTab.WEEKLY, categoryButton to ReportTab.CATEGORY)
                .forEach { (btn, t) ->
                    btn.background = getDrawable(if (t == next) R.drawable.btn_chip_dark else R.drawable.btn_chip_light)
                    btn.setTextColor(if (t == next) 0xFFFFFFFF.toInt() else 0xFF1A1A1A.toInt())
                }
            renderAll()
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
                    MENU_LOGOUT    -> { startActivity(Intent(this, LoginActivity::class.java)); finish() }
                }
                true
            }
            popup.show()
        }

        logoutButton.setOnClickListener { startActivity(Intent(this, LoginActivity::class.java)); finish() }
        navDashboard.setOnClickListener { navigateDashboard(userId) }
        navExpenses.setOnClickListener  { navigateExpenses(userId) }
        navCategory.setOnClickListener  { navigateCategory(userId) }
        navBudget.setOnClickListener    { navigateBudget(userId) }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                database.categoryDao().getCategoriesForUser(userId).collect { cats ->
                    categoriesById = cats.associate { it.categoryId to it.name }
                    renderAll()
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                database.expenseDao().getExpensesForUser(userId).collect { expenses ->
                    expensesCache = expenses
                    renderAll()
                }
            }
        }
    }

    // ── dp helper ─────────────────────────────────────────────────────────────
    private fun Int.dp() = (this * resources.displayMetrics.density).toInt()

    private fun openDatePicker(initial: LocalDate, onPicked: (LocalDate) -> Unit) {
        DatePickerDialog(this,
            { _, y, m, d -> onPicked(LocalDate.of(y, m + 1, d)) },
            initial.year, initial.monthValue - 1, initial.dayOfMonth
        ).show()
    }

    // ── Master render ─────────────────────────────────────────────────────────
    private fun renderAll(
        totalSpentText: TextView, transactionsText: TextView,
        avgDailyText: TextView, daysText: TextView,
        trendTitle: TextView, trendSubtitle: TextView,
        trendBars: LinearLayout, categoryLegend: LinearLayout,
        breakdownContainer: LinearLayout,
        cardPieChart: LinearLayout, cardBreakdown: LinearLayout
    ) {
        val filtered = expensesCache.filter { e ->
            val day = Instant.ofEpochMilli(e.date).atZone(zoneId).toLocalDate()
            !day.isBefore(startDate) && !day.isAfter(endDate)
        }

        val total      = filtered.sumOf { it.amount }
        val rangeLen   = ChronoUnit.DAYS.between(startDate, endDate).toInt() + 1
        val avg        = if (rangeLen > 0) total / rangeLen else 0.0
        val activeDays = filtered.map { Instant.ofEpochMilli(it.date).atZone(zoneId).toLocalDate() }.distinct().size

        totalSpentText.text   = "R${"%.2f".format(total)}"
        transactionsText.text = filtered.size.toString()
        avgDailyText.text     = "R${"%.2f".format(avg)}"
        daysText.text         = activeDays.toString()

        // Category totals — used by CATEGORY tab and the daily legend
        val byCategory = filtered
            .groupBy { it.categoryId }
            .mapValues { (_, list) -> list.sumOf { it.amount } }
            .toList().sortedByDescending { it.second }

        val isCategoryTab = tab == ReportTab.CATEGORY
        cardPieChart.visibility  = if (isCategoryTab) View.VISIBLE else View.GONE
        cardBreakdown.visibility = if (isCategoryTab) View.VISIBLE else View.GONE

        when (tab) {
            // ── DAILY: rising vertical bar chart, one bar per day, colour legend below ──
            ReportTab.DAILY -> {
                trendTitle.text    = getString(R.string.reports_daily_trend)
                trendSubtitle.text = getString(R.string.reports_daily_sub)

                val byDay = filtered
                    .groupBy { Instant.ofEpochMilli(it.date).atZone(zoneId).toLocalDate() }
                    .mapValues { (_, list) -> list.sumOf { it.amount } }
                    .toList().sortedBy { it.first }.takeLast(7)

                trendBars.removeAllViews()
                if (byDay.isEmpty()) { trendBars.addView(emptyLabel("No data for selected range")); return }

                val labels = byDay.map { (date, _) ->
                    date.month.name.take(3) + " " + date.dayOfMonth
                }
                val values = byDay.map { it.second }
                trendBars.addView(buildRisingBarChart(values, labels, chartColors))

                // Colour legend: one dot per category
                // Reuse the trendBars container for the legend row
                val legendRow = LinearLayout(this).apply {
                    orientation  = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    setPadding(0, 8.dp(), 0, 0)
                }
                byCategory.take(chartColors.size).forEachIndexed { i, (catId, _) ->
                    val name = catId?.let { categoriesById[it] } ?: "Other"
                    val dot = View(this).apply {
                        layoutParams = LinearLayout.LayoutParams(10.dp(), 10.dp()).also {
                            it.setMargins(0, 2.dp(), 4.dp(), 0)
                        }
                        setBackgroundColor(Color.parseColor(chartColors[i % chartColors.size]))
                    }
                    val label = TextView(this).apply {
                        text      = name
                        textSize  = 10f
                        setTextColor(0xFF555555.toInt())
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).also { it.setMargins(0, 0, 12.dp(), 0) }
                    }
                    legendRow.addView(dot)
                    legendRow.addView(label)
                }
                trendBars.addView(legendRow)
            }

            // ── WEEKLY: line chart with dots and Y-axis labels ──────────────────
            ReportTab.WEEKLY -> {
                trendTitle.text    = getString(R.string.reports_weekly_trend)
                trendSubtitle.text = getString(R.string.reports_weekly_sub)

                val fields = WeekFields.of(Locale.getDefault())
                val byWeek = filtered
                    .groupBy {
                        val day = Instant.ofEpochMilli(it.date).atZone(zoneId).toLocalDate()
                        day.get(fields.weekOfWeekBasedYear())
                    }
                    .mapValues { (_, list) -> list.sumOf { it.amount } }
                    .toList().sortedBy { it.first }

                trendBars.removeAllViews()
                if (byWeek.isEmpty()) { trendBars.addView(emptyLabel("No data for selected range")); return }

                val labels = byWeek.map { (week, _) -> "Wk $week" }
                val values = byWeek.map { it.second }
                trendBars.addView(LineChartView(this, values, labels))
            }

            // ── CATEGORY: donut pie + breakdown bars ────────────────────────────
            ReportTab.CATEGORY -> {
                trendTitle.text    = getString(R.string.reports_category_trend)
                trendSubtitle.text = getString(R.string.reports_category_sub)

                // Bar chart in the trend card showing category totals
                trendBars.removeAllViews()
                if (byCategory.isEmpty()) {
                    trendBars.addView(emptyLabel("No data for selected range"))
                } else {
                    val labels = byCategory.take(6).map { (id, _) ->
                        (id?.let { categoriesById[it] } ?: "Other").take(7)
                    }
                    val values = byCategory.take(6).map { it.second }
                    trendBars.addView(buildRisingBarChart(values, labels, chartColors))
                }

                // Donut pie chart
                renderDonutChart(categoryLegend, byCategory, total)
                // Progress bar breakdown
                renderBreakdown(breakdownContainer, byCategory, total)
            }
        }
    }

    // ── Rising vertical bar chart ─────────────────────────────────────────────
    /**
     * Bars grow upward from the bottom inside a fixed-height container.
     * Amount label sits just above each bar, x-axis label sits below.
     */
    private fun buildRisingBarChart(
        values: List<Double>,
        labels: List<String>,
        colors: List<String>
    ): LinearLayout {
        val chartHPx = 180.dp()
        val maxVal   = values.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0

        val wrapper = LinearLayout(this).apply {
            orientation  = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Fixed-height row — bars grow up inside this
        val barsRow = LinearLayout(this).apply {
            orientation  = LinearLayout.HORIZONTAL
            gravity      = Gravity.BOTTOM
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, chartHPx)
        }

        values.forEachIndexed { i, value ->
            val barH = ((value / maxVal) * (chartHPx - 24.dp())).toInt().coerceAtLeast(4.dp())
            val col  = LinearLayout(this).apply {
                orientation  = LinearLayout.VERTICAL
                gravity      = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                setPadding(3.dp(), 0, 3.dp(), 0)
            }
            // Amount above bar
            col.addView(TextView(this).apply {
                text      = "R${value.toInt()}"
                textSize  = 7.5f
                gravity   = Gravity.CENTER
                setTextColor(0xFF444444.toInt())
            })
            // The bar
            col.addView(View(this).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, barH)
                setBackgroundColor(Color.parseColor(colors[i % colors.size]))
            })
            barsRow.addView(col)
        }
        wrapper.addView(barsRow)

        // X-axis labels below
        val labelsRow = LinearLayout(this).apply {
            orientation  = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        labels.forEach { label ->
            labelsRow.addView(TextView(this).apply {
                text      = label
                textSize  = 9f
                gravity   = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setTextColor(0xFF888888.toInt())
                setPadding(2.dp(), 4.dp(), 2.dp(), 0)
            })
        }
        wrapper.addView(labelsRow)
        return wrapper
    }

    // ── Donut / ring pie chart ────────────────────────────────────────────────
    private fun renderDonutChart(container: LinearLayout, data: List<Pair<Long?, Double>>, total: Double) {
        container.removeAllViews()
        if (data.isEmpty()) { container.addView(emptyLabel("No data to display")); return }
        container.addView(DonutChartView(this, data, categoriesById, total, chartColors))
    }

    // ── Category breakdown progress bars ─────────────────────────────────────
    private fun renderBreakdown(container: LinearLayout, byCategory: List<Pair<Long?, Double>>, total: Double) {
        container.removeAllViews()
        val safe = if (total == 0.0) 1.0 else total
        byCategory.forEachIndexed { i, (catId, amount) ->
            val name    = catId?.let { categoriesById[it] } ?: getString(R.string.category_spinner_none)
            val percent = (amount / safe) * 100
            val row     = layoutInflater.inflate(R.layout.item_report_breakdown_row, container, false)
            row.findViewById<View>(R.id.viewReportBreakdownDot).backgroundTintList =
                ColorStateList.valueOf(Color.parseColor(chartColors[i % chartColors.size]))
            row.findViewById<TextView>(R.id.textViewReportBreakdownName).text = name
            row.findViewById<TextView>(R.id.textViewReportBreakdownAmount).text =
                "R${"%.2f".format(amount)} (${"%.1f".format(percent)}%)"
            row.findViewById<ProgressBar>(R.id.progressReportBreakdown).apply {
                progress = percent.toInt()
                progressTintList           = ColorStateList.valueOf(Color.parseColor(chartColors[i % chartColors.size]))
                progressBackgroundTintList = ColorStateList.valueOf(Color.parseColor("#E6E6E6"))
            }
            container.addView(row)
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private fun emptyLabel(msg: String) = TextView(this).apply {
        text      = msg
        gravity   = Gravity.CENTER
        textSize  = 13f
        setTextColor(0xFF888888.toInt())
        setPadding(0, 16.dp(), 0, 16.dp())
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }

    // ── Navigation ────────────────────────────────────────────────────────────
    private fun navigateDashboard(userId: Long) { startActivity(Intent(this, DashboardActivity::class.java).putExtra(LoginActivity.EXTRA_USER_ID, userId)); finish() }
    private fun navigateExpenses(userId: Long)  { startActivity(Intent(this, ExpenseActivity::class.java).putExtra(LoginActivity.EXTRA_USER_ID, userId)) }
    private fun navigateCategory(userId: Long)  { startActivity(Intent(this, CategoryActivity::class.java).putExtra(LoginActivity.EXTRA_USER_ID, userId)) }
    private fun navigateBudget(userId: Long)    { startActivity(Intent(this, BudgetActivity::class.java).putExtra(LoginActivity.EXTRA_USER_ID, userId)) }
    private fun navigateGoals(userId: Long)     { startActivity(Intent(this, GoalsActivity::class.java).putExtra(LoginActivity.EXTRA_USER_ID, userId)) }
    private fun navigateRewards(userId: Long)   { startActivity(Intent(this, RewardsActivity::class.java).putExtra(LoginActivity.EXTRA_USER_ID, userId)) }

    // ══════════════════════════════════════════════════════════════════════════
    // Custom Views
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Line chart with filled dots, Y-axis labels, and a bottom label row.
     * Matches the Weekly Spending Trend screenshot.
     */
    inner class LineChartView(
        context: Context,
        private val values: List<Double>,
        private val labels: List<String>
    ) : View(context) {

        private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color       = Color.parseColor("#7D57C2")
            strokeWidth = 3f
            style       = Paint.Style.STROKE
        }
        private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#7D57C2")
            style = Paint.Style.FILL
        }
        private val dotBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color       = Color.WHITE
            style       = Paint.Style.FILL
        }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color     = Color.parseColor("#888888")
            textSize  = 26f
            textAlign = Paint.Align.RIGHT
        }
        private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color     = Color.parseColor("#888888")
            textSize  = 26f
            textAlign = Paint.Align.CENTER
        }

        private val paddingLeft  = 80f   // space for Y-axis labels
        private val paddingRight = 16f
        private val paddingTop   = 30f
        private val paddingBot   = 50f   // space for X-axis labels

        override fun onMeasure(w: Int, h: Int) {
            setMeasuredDimension(MeasureSpec.getSize(w), 400)
        }

        override fun onDraw(canvas: Canvas) {
            if (values.isEmpty()) return
            val maxVal = values.maxOrNull()!!.coerceAtLeast(1.0)
            val chartW = width  - paddingLeft - paddingRight
            val chartH = height - paddingTop  - paddingBot

            // Y-axis grid lines + labels (4 steps)
            val steps = 4
            val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color       = Color.parseColor("#E0E0E0")
                strokeWidth = 1f
            }
            for (s in 0..steps) {
                val y     = paddingTop + chartH - (s.toFloat() / steps) * chartH
                val value = (maxVal / steps * s).toInt()
                canvas.drawLine(paddingLeft, y, paddingLeft + chartW, y, gridPaint)
                canvas.drawText("$value", paddingLeft - 8f, y + 9f, textPaint)
            }

            // Points
            val pts = values.mapIndexed { i, v ->
                val x = paddingLeft + (i.toFloat() / (values.size - 1).coerceAtLeast(1)) * chartW
                val y = paddingTop  + chartH - ((v / maxVal) * chartH).toFloat()
                Pair(x, y)
            }

            // Draw line segments
            for (i in 0 until pts.size - 1) {
                canvas.drawLine(pts[i].first, pts[i].second, pts[i+1].first, pts[i+1].second, linePaint)
            }

            // Draw dots (white fill with purple border)
            pts.forEach { (x, y) ->
                canvas.drawCircle(x, y, 10f, dotPaint)
                canvas.drawCircle(x, y, 6f,  dotBorderPaint)
            }

            // X-axis labels
            labels.forEachIndexed { i, label ->
                val x = paddingLeft + (i.toFloat() / (labels.size - 1).coerceAtLeast(1)) * chartW
                canvas.drawText(label, x, height - 8f, labelPaint)
            }
        }
    }

    /**
     * Donut (ring) chart matching the Category tab screenshot.
     * Thick ring with a transparent hole in the centre.
     */
    inner class DonutChartView(
        context: Context,
        private val data: List<Pair<Long?, Double>>,
        private val catNames: Map<Long, String>,
        private val total: Double,
        private val colors: List<String>
    ) : View(context) {

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        override fun onMeasure(w: Int, h: Int) {
            setMeasuredDimension(MeasureSpec.getSize(w), 320)
        }

        override fun onDraw(canvas: Canvas) {
            val cx      = width / 2f
            val cy      = height / 2f
            val radius  = (minOf(cx, cy) - 20f)
            val holeR   = radius * 0.55f       // punch out the centre for a donut look
            val arcRect = RectF(cx - radius, cy - radius, cx + radius, cy + radius)

            var startAngle = -90f
            data.forEachIndexed { i, (_, amount) ->
                val sweep = (amount / total * 360f).toFloat()
                paint.color = Color.parseColor(colors[i % colors.size])
                paint.style = Paint.Style.FILL
                canvas.drawArc(arcRect, startAngle, sweep, true, paint)
                startAngle += sweep
            }

            // Draw the white circle in the centre to create the donut hole
            paint.color = Color.WHITE
            paint.style = Paint.Style.FILL
            canvas.drawCircle(cx, cy, holeR, paint)

            // Legend — drawn below the donut as text lines
            var legendY = cy + radius + 24f
            val textP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize  = 28f
                textAlign = Paint.Align.LEFT
            }
            data.forEachIndexed { i, (catId, amount) ->
                val name    = catId?.let { catNames[it] } ?: "Other"
                val percent = "%.1f".format(amount / total * 100)
                paint.color = Color.parseColor(colors[i % colors.size])
                paint.style = Paint.Style.FILL
                canvas.drawCircle(30f, legendY - 8f, 10f, paint)
                textP.color = Color.parseColor("#1A1A1A")
                canvas.drawText("$name: R${amount.toInt()} ($percent%)", 50f, legendY, textP)
                legendY += 36f
            }
        }
    }
}