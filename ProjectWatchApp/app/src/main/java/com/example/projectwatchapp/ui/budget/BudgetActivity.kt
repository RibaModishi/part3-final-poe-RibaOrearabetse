package com.example.projectwatchapp.ui.budget

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
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
import com.example.projectwatchapp.data.entities.Category
import com.example.projectwatchapp.ui.auth.LoginActivity
import com.example.projectwatchapp.viewmodel.BudgetViewModel
import com.example.projectwatchapp.viewmodel.MonthlyStatus
import java.text.NumberFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * Budget screen: min/max monthly goals via [SeekBar], formatted money via [NumberFormat],
 * per-category budget cap via spinner + seekbar, list and delete active budgets.
 */
class BudgetActivity : ComponentActivity() {

    private val database by lazy { AppDatabase.getDatabase(this) }
    private val budgetViewModel: BudgetViewModel by viewModels {
        BudgetViewModelFactory(database)
    }

    private val zoneId: ZoneId = ZoneId.systemDefault()

    /** Each SeekBar progress unit = this many currency units (assignment: SeekBar + NumberFormat). */
    private val monthlyGoalStep = 50.0
    private val categoryBudgetStep = 20.0

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

        val seekBarMin = findViewById<SeekBar>(R.id.seekBarMinMonthlyGoal)
        val seekBarMax = findViewById<SeekBar>(R.id.seekBarMaxMonthlyGoal)
        val textViewMinValue = findViewById<TextView>(R.id.textViewMinGoalValue)
        val textViewMaxValue = findViewById<TextView>(R.id.textViewMaxGoalValue)
        val buttonApplyGoals = findViewById<Button>(R.id.buttonApplyMonthlyGoals)
        val textViewSummary = findViewById<TextView>(R.id.textViewBudgetSummary)
        val spinnerCategory = findViewById<Spinner>(R.id.spinnerBudgetCategory)
        val seekBarCategoryAmount = findViewById<SeekBar>(R.id.seekBarCategoryBudgetAmount)
        val textViewCategoryAmountValue = findViewById<TextView>(R.id.textViewCategoryBudgetValue)
        val buttonSaveCategoryBudget = findViewById<Button>(R.id.buttonSaveCategoryBudget)
        val textViewBudgetList = findViewById<TextView>(R.id.textViewActiveBudgetsList)
        val editDeleteBudgetId = findViewById<EditText>(R.id.editTextDeleteBudgetId)
        val buttonDeleteBudget = findViewById<Button>(R.id.buttonDeleteBudget)
        val loadingText = findViewById<TextView>(R.id.textViewBudgetLoading)

        fun minFromSeek(): Double = seekBarMin.progress * monthlyGoalStep
        fun maxFromSeek(): Double = seekBarMax.progress * monthlyGoalStep

        fun refreshMinMaxLabels() {
            textViewMinValue.text = getString(R.string.budget_seek_value, moneyFormat.format(minFromSeek()))
            textViewMaxValue.text = getString(R.string.budget_seek_value, moneyFormat.format(maxFromSeek()))
        }

        fun categoryAmountFromSeek(): Double = seekBarCategoryAmount.progress * categoryBudgetStep

        fun refreshCategoryBudgetLabel() {
            textViewCategoryAmountValue.text = getString(
                R.string.budget_seek_value,
                moneyFormat.format(categoryAmountFromSeek())
            )
        }

        // Sensible starting positions (can drag before Apply).
        seekBarMin.progress = 100  // 5 000
        seekBarMax.progress = 400 // 20 000
        seekBarCategoryAmount.progress = 50 // 1 000
        refreshMinMaxLabels()
        refreshCategoryBudgetLabel()

        seekBarMin.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                refreshMinMaxLabels()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        seekBarMax.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                refreshMinMaxLabels()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        seekBarCategoryAmount.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                refreshCategoryBudgetLabel()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

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
            val minGoal = minFromSeek()
            val maxGoal = maxFromSeek()
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
            val amount = categoryAmountFromSeek()
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

                    val minStr = state.minMonthlyGoal?.let { moneyFormat.format(it) } ?: "—"
                    val maxStr = state.maxMonthlyGoal?.let { moneyFormat.format(it) } ?: "—"
                    val allocStr = moneyFormat.format(state.allocatedTotal)
                    val unallocStr = state.unallocatedAmount?.let { moneyFormat.format(it) } ?: "—"

                    textViewSummary.text = buildString {
                        append(getString(R.string.budget_summary_line_min, minStr))
                        append("\n")
                        append(getString(R.string.budget_summary_line_max, maxStr))
                        append("\n")
                        append(getString(R.string.budget_summary_line_allocated, allocStr))
                        append("\n")
                        append(getString(R.string.budget_summary_line_unallocated, unallocStr))
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
