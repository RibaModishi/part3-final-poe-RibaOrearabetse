package com.example.projectwatchapp.ui.expense

import android.app.DatePickerDialog
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.view.View
import android.widget.ImageView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import android.widget.PopupMenu
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.FileProvider
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
import com.example.projectwatchapp.ui.goals.GoalsActivity
import com.example.projectwatchapp.ui.reports.ReportsActivity
import com.example.projectwatchapp.ui.rewards.RewardsActivity
import com.example.projectwatchapp.ui.common.PopupMenuUtils
import com.example.projectwatchapp.ui.dashboard.DashboardActivity.Companion.MENU_INFO
import com.example.projectwatchapp.viewmodel.ExpenseFilter
import com.example.projectwatchapp.viewmodel.ExpenseViewModel
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch
import com.example.projectwatchapp.ui.info.InfoHelpActivity

class ExpenseActivity : ComponentActivity() {
    companion object {
        private const val MENU_DASHBOARD = 1
        private const val MENU_EXPENSES = 2
        private const val MENU_CATEGORY = 3
        private const val MENU_BUDGET = 4
        private const val MENU_GOALS = 5
        private const val MENU_REWARDS = 6
        private const val MENU_REPORTS = 7

        private const val MENU_INFO    = 8

        private const val MENU_LOGOUT = 9
    }

    private var pendingPhotoPath: String? = null
    private var sheetReceiptStatusView: TextView? = null

    private val pickReceiptLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri == null) return@registerForActivityResult
            discardPendingReceiptFile()
            val path = copyPickedImageToReceipts(uri)
            val status = sheetReceiptStatusView
            if (path != null) {
                pendingPhotoPath = path
                status?.let { refreshReceiptStatus(it) }
            } else {
                Toast.makeText(this, R.string.expense_receipt_copy_failed, Toast.LENGTH_SHORT).show()
            }
        }

    private val database by lazy { AppDatabase.getDatabase(this) }
    private val expenseViewModel: ExpenseViewModel by viewModels {
        ExpenseViewModelFactory(database)
    }

    private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val zoneId: ZoneId = ZoneId.systemDefault()

    private var selectedExpenseDateMillis: Long = 0L
    private var preSelectedCategoryId: Long? = null
    private var filterStartMillis: Long = 0L
    private var filterEndMillis: Long = 0L
    private var categoryIdsBySpinnerIndex: List<Long?> = listOf(null)
    private var categoryNameById: Map<Long, String> = emptyMap()

    // Track whether we already auto-opened the sheet (so it only fires once)
    private var autoSheetOpened = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_expense)

        val userId = intent.getLongExtra(LoginActivity.EXTRA_USER_ID, -1L)

        // Store pre-selected category if launched from Category page
        val extraCategoryId = intent.getLongExtra("EXTRA_CATEGORY_ID", -1L)
        if (extraCategoryId > 0) preSelectedCategoryId = extraCategoryId

        if (userId <= 0) {
            Toast.makeText(this, "Invalid user. Please login again.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val spinnerCategory = findViewById<Spinner>(R.id.spinnerExpenseCategory)
        val textViewFilterStartDate = findViewById<TextView>(R.id.textViewFilterStartDate)
        val textViewFilterEndDate = findViewById<TextView>(R.id.textViewFilterEndDate)
        val buttonPickFilterStart = findViewById<Button>(R.id.buttonPickFilterStart)
        val buttonPickFilterEnd = findViewById<Button>(R.id.buttonPickFilterEnd)
        val addButton = findViewById<Button>(R.id.buttonAddExpense)
        val menuButton = findViewById<ImageView>(R.id.buttonExpenseMenu)
        val toggleFiltersButton = findViewById<Button>(R.id.buttonToggleFilters)
        val filtersContainer = findViewById<View>(R.id.layoutExpenseFilters)
        val filterButton = findViewById<Button>(R.id.buttonApplyPeriodFilter)
        val clearFilterButton = findViewById<Button>(R.id.buttonClearFilter)
        val refreshTotalsButton = findViewById<Button>(R.id.buttonRefreshTotals)
        val deleteExpenseIdInput = findViewById<EditText>(R.id.editTextDeleteExpenseId)
        val deleteExpenseButton = findViewById<Button>(R.id.buttonDeleteExpense)
        val buttonViewReceipt = findViewById<Button>(R.id.buttonViewReceipt)
        val loadingText = findViewById<TextView>(R.id.textViewExpenseLoading)
        val totalsText = findViewById<TextView>(R.id.textViewExpenseTotals)
        val listText = findViewById<TextView>(R.id.textViewExpenseList)
        val countText = findViewById<TextView>(R.id.textViewExpenseCount)
        val listContainer = findViewById<LinearLayout>(R.id.layoutExpenseItems)

        selectedExpenseDateMillis = LocalDate.now().atStartOfDay(zoneId).toInstant().toEpochMilli()

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
                    MENU_EXPENSES -> Unit
                    MENU_CATEGORY -> navigateCategory(userId)
                    MENU_BUDGET -> navigateBudget(userId)
                    MENU_GOALS -> navigateGoals(userId)
                    MENU_REWARDS -> navigateRewards(userId)
                    MENU_REPORTS -> navigateReports(userId)
                    MENU_INFO -> navigateInfo(userId)
                    MENU_LOGOUT -> { startActivity(Intent(this, LoginActivity::class.java)); finish() }
                }
                true
            }
            popup.show()
        }

        // Bottom nav
        findViewById<Button>(R.id.navExpenseDashboard).setOnClickListener { navigateDashboard(userId) }
        findViewById<Button>(R.id.navExpenseExpenses).setOnClickListener { /* already here */ }
        findViewById<Button>(R.id.navExpenseCategory).setOnClickListener { navigateCategory(userId) }
        findViewById<Button>(R.id.navExpenseBudget).setOnClickListener { navigateBudget(userId) }

        val today = LocalDate.now()
        filterStartMillis = today.withDayOfMonth(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        filterEndMillis = endOfDayEpoch(today)
        textViewFilterStartDate.text = formatEpoch(filterStartMillis)
        textViewFilterEndDate.text = formatEpoch(filterEndMillis)

        expenseViewModel.loadAllExpenses(userId)
        // Download any expenses stored in Firebase Realtime Database into local Room storage
        expenseViewModel.syncExpensesFromCloud()

        // Load categories into the main spinner AND auto-open sheet if launched from Category page
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                database.categoryDao().getCategoriesForUser(userId).collect { categories ->
                    val names = mutableListOf(getString(R.string.category_spinner_none))
                    val ids = mutableListOf<Long?>(null)
                    categories.sortedBy { it.name }.forEach { cat ->
                        names.add(cat.name)
                        ids.add(cat.categoryId)
                    }
                    categoryNameById = categories.associate { it.categoryId to it.name }
                    categoryIdsBySpinnerIndex = ids

                    val adapter = ArrayAdapter(
                        this@ExpenseActivity,
                        android.R.layout.simple_spinner_item,
                        names
                    )
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    spinnerCategory.adapter = adapter

                    // Auto-open the add expense sheet once categories are loaded,
                    // if we were launched from the Category page
                    if (!autoSheetOpened && preSelectedCategoryId != null && ids.size > 1) {
                        autoSheetOpened = true
                        showAddExpenseSheet(spinnerCategory)
                    }
                }
            }
        }

        toggleFiltersButton.setOnClickListener {
            val visible = filtersContainer.visibility == View.VISIBLE
            filtersContainer.visibility = if (visible) View.GONE else View.VISIBLE
            toggleFiltersButton.text = getString(
                if (visible) R.string.expense_toggle_filters_show else R.string.expense_toggle_filters_hide
            )
        }

        buttonPickFilterStart.setOnClickListener {
            openDatePickerStartOfDay(filterStartMillis) { millis ->
                filterStartMillis = millis
                textViewFilterStartDate.text = formatEpoch(millis)
            }
        }

        buttonPickFilterEnd.setOnClickListener {
            openDatePickerEndOfDayInclusive(filterEndMillis) { millis ->
                filterEndMillis = millis
                textViewFilterEndDate.text = formatEpoch(millis)
            }
        }

        addButton.setOnClickListener {
            showAddExpenseSheet(spinnerCategory)
        }

        filterButton.setOnClickListener {
            if (filterStartMillis > filterEndMillis) {
                Toast.makeText(this, "Start date must be before or equal to end date.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            expenseViewModel.loadExpensesForPeriod(filterStartMillis, filterEndMillis)
            expenseViewModel.loadTotalSpentForActivePeriod()
        }

        clearFilterButton.setOnClickListener {
            expenseViewModel.loadAllExpenses(userId)
            expenseViewModel.loadTotalSpentForActivePeriod()
        }

        refreshTotalsButton.setOnClickListener {
            expenseViewModel.loadTotalSpentForActivePeriod()
            val categoryId = selectedCategoryId(spinnerCategory)
            if (categoryId != null) {
                expenseViewModel.loadCategoryTotalForActivePeriod(categoryId)
            }
        }

        deleteExpenseButton.setOnClickListener {
            val expenseId = deleteExpenseIdInput.text.toString().toLongOrNull()
            if (expenseId == null) {
                Toast.makeText(this, "Enter a valid Expense ID to delete.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            expenseViewModel.deleteExpense(expenseId)
        }

        buttonViewReceipt.setOnClickListener {
            val expenseId = deleteExpenseIdInput.text.toString().toLongOrNull()
            if (expenseId == null) {
                Toast.makeText(this, "Enter expense ID to view receipt.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val expense = expenseViewModel.uiState.value.expenses.firstOrNull { it.expenseId == expenseId }
            val path = expense?.photoPath
            if (path.isNullOrBlank()) {
                Toast.makeText(this, R.string.expense_receipt_missing, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val file = File(path)
            if (!file.exists()) {
                Toast.makeText(this, R.string.expense_receipt_file_missing, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
            val mime = runCatching { contentResolver.getType(uri) }.getOrNull() ?: "image/*"
            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            runCatching {
                startActivity(Intent.createChooser(viewIntent, getString(R.string.action_view_receipt)))
            }.onFailure {
                Toast.makeText(this, R.string.expense_receipt_no_viewer, Toast.LENGTH_SHORT).show()
            }
        }

        listText.text = ""

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                expenseViewModel.uiState.collect { state ->
                    loadingText.visibility = if (state.isLoading) View.VISIBLE else View.GONE

                    val activeFilterLabel = when (val filter = state.activeFilter) {
                        ExpenseFilter.All -> "All dates"
                        is ExpenseFilter.Period -> "Period: ${formatEpoch(filter.startDate)} to ${formatEpoch(filter.endDate)}"
                    }

                    val categoryId = selectedCategoryId(spinnerCategory)
                    val categoryTotal = categoryId?.let { state.categoryTotalsInActivePeriod[it] }

                    totalsText.text = buildString {
                        append("Active filter: $activeFilterLabel\n")
                        append("Total spent: R${"%.2f".format(state.totalSpentInActivePeriod)}")
                        if (categoryId != null && categoryTotal != null) {
                            append("\nCategory $categoryId total: R${"%.2f".format(categoryTotal)}")
                        }
                    }

                    val showingPart = "Showing ${state.expenses.size}"
                    val totalPart = "Total: R${"%.0f".format(state.totalSpentInActivePeriod)}"
                    countText.text = boldSummaryParts("$showingPart · $totalPart", showingPart, totalPart)

                    renderExpenseRows(
                        container = listContainer,
                        expenses = state.expenses,
                        onView = { expense ->
                            val path = expense.photoPath
                            if (path.isNullOrBlank()) {
                                Toast.makeText(this@ExpenseActivity, R.string.expense_receipt_missing, Toast.LENGTH_SHORT).show()
                            } else {
                                openReceiptByPath(path)
                            }
                        },
                        onDelete = { expense ->
                            expenseViewModel.deleteExpense(expense.expenseId)
                        }
                    )

                    state.errorMessage?.let { message ->
                        Toast.makeText(this@ExpenseActivity, message, Toast.LENGTH_SHORT).show()
                        expenseViewModel.clearMessages()
                    }

                    state.successMessage?.let { message ->
                        val duration = if (message.contains("XP")) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
                        Toast.makeText(this@ExpenseActivity, message, duration).show()
                        expenseViewModel.clearMessages()
                    }

                    state.badgeEarned?.let { badge ->
                        showBadgeEarnedDialog(badge)
                        expenseViewModel.clearBadge()
                    }

                    state.cloudSyncMessage?.let { message ->
                        Toast.makeText(this@ExpenseActivity, message, Toast.LENGTH_LONG).show()
                        expenseViewModel.clearCloudSyncMessage()
                    }
                }
            }
        }
    }

    private fun showAddExpenseSheet(spinnerCategory: Spinner) {
        val dialogView = layoutInflater.inflate(R.layout.bottom_sheet_add_expense, null)
        val amountInput = dialogView.findViewById<EditText>(R.id.editTextSheetExpenseAmount)
        val dateInput = dialogView.findViewById<EditText>(R.id.editTextSheetExpenseDate)
        val descriptionInput = dialogView.findViewById<EditText>(R.id.editTextSheetExpenseDescription)
        val uploadArea = dialogView.findViewById<View>(R.id.layoutUploadReceiptArea)
        val receiptStatus = dialogView.findViewById<TextView>(R.id.textViewSheetReceiptStatus)
        val closeButton = dialogView.findViewById<View>(R.id.buttonCloseAddExpenseSheet)
        val saveButton = dialogView.findViewById<Button>(R.id.buttonSaveExpenseSheet)
        val sheetCategorySpinner = dialogView.findViewById<Spinner>(R.id.spinnerSheetExpenseCategory)

        // Populate the sheet's category spinner from the same data as the main spinner
        val names = categoryIdsBySpinnerIndex.mapIndexed { _, id ->
            if (id == null) getString(R.string.category_spinner_none)
            else categoryNameById[id] ?: "Category $id"
        }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, names)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        sheetCategorySpinner.adapter = adapter

        // Pre-select: use preSelectedCategoryId (from Category page) first,
        // otherwise mirror whatever is selected on the main screen spinner
        val preSelectPos = preSelectedCategoryId?.let { catId ->
            categoryIdsBySpinnerIndex.indexOfFirst { it == catId }.takeIf { it >= 0 }
        }
        sheetCategorySpinner.setSelection(preSelectPos ?: spinnerCategory.selectedItemPosition)

        discardPendingReceiptFile()
        sheetReceiptStatusView = receiptStatus
        refreshReceiptStatus(receiptStatus)
        dateInput.setText(formatEpoch(selectedExpenseDateMillis))

        dateInput.setOnClickListener {
            openDatePickerStartOfDay(selectedExpenseDateMillis) { millis ->
                selectedExpenseDateMillis = millis
                dateInput.setText(formatEpoch(millis))
            }
        }
        uploadArea.setOnClickListener { pickReceiptLauncher.launch("image/*") }

        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        closeButton.setOnClickListener {
            discardPendingReceiptFile()
            sheetReceiptStatusView = null
            dialog.dismiss()
        }

        saveButton.setOnClickListener {
            val amount = amountInput.text.toString().toDoubleOrNull()
            if (amount == null) {
                Toast.makeText(this, "Enter a valid amount.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // Read category from the SHEET spinner, not the main screen spinner
            val sheetPos = sheetCategorySpinner.selectedItemPosition
            val selectedCatId = if (sheetPos in categoryIdsBySpinnerIndex.indices) {
                categoryIdsBySpinnerIndex[sheetPos]
            } else null

            expenseViewModel.addExpense(
                amount = amount,
                description = descriptionInput.text.toString(),
                categoryId = selectedCatId,
                date = selectedExpenseDateMillis,
                photoPath = pendingPhotoPath
            )
            pendingPhotoPath = null
            sheetReceiptStatusView = null
            dialog.dismiss()
        }

        dialog.setOnDismissListener {
            sheetReceiptStatusView = null
        }
        dialog.show()
    }

    private fun boldSummaryParts(text: String, vararg segmentsToBold: String): SpannableString {
        val spannable = SpannableString(text)
        segmentsToBold.forEach { segment ->
            val start = text.indexOf(segment)
            if (start >= 0) {
                spannable.setSpan(
                    StyleSpan(Typeface.BOLD),
                    start,
                    start + segment.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
        return spannable
    }

    private fun receiptsDir(): File = File(filesDir, "receipts")

    private fun refreshReceiptStatus(statusView: TextView) {
        val path = pendingPhotoPath
        statusView.text = if (path.isNullOrBlank()) {
            getString(R.string.expense_receipt_none)
        } else {
            getString(R.string.expense_receipt_attached, File(path).name)
        }
    }

    private fun discardPendingReceiptFile() {
        val path = pendingPhotoPath ?: return
        if (path.startsWith(receiptsDir().absolutePath)) {
            runCatching { File(path).delete() }
        }
        pendingPhotoPath = null
    }

    private fun copyPickedImageToReceipts(uri: Uri): String? {
        return runCatching {
            val mime = contentResolver.getType(uri) ?: "image/jpeg"
            val ext = when {
                mime.contains("png") -> "png"
                mime.contains("webp") -> "webp"
                mime.contains("gif") -> "gif"
                else -> "jpg"
            }
            val dir = receiptsDir().apply { mkdirs() }
            val outFile = File(dir, "receipt_${System.currentTimeMillis()}.$ext")
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(outFile).use { output -> input.copyTo(output) }
            } ?: return null
            outFile.absolutePath
        }.getOrNull()
    }

    private fun selectedCategoryId(spinner: Spinner): Long? {
        val pos = spinner.selectedItemPosition
        return if (pos in categoryIdsBySpinnerIndex.indices) categoryIdsBySpinnerIndex[pos] else null
    }

    private fun renderExpenseRows(
        container: LinearLayout,
        expenses: List<com.example.projectwatchapp.data.entities.Expense>,
        onView: (com.example.projectwatchapp.data.entities.Expense) -> Unit,
        onDelete: (com.example.projectwatchapp.data.entities.Expense) -> Unit
    ) {
        container.removeAllViews()
        if (expenses.isEmpty()) {
            val empty = TextView(this).apply {
                text = getString(R.string.expense_none_found)
                setTextColor(0xFF666666.toInt())
                textSize = 13f
            }
            container.addView(empty)
            return
        }
        expenses.forEachIndexed { index, exp ->
            val row = layoutInflater.inflate(R.layout.item_expense, container, false)
            row.findViewById<TextView>(R.id.textViewExpenseItemDescription).text = exp.description
            val categoryName = exp.categoryId?.let { categoryNameById[it] } ?: getString(R.string.category_spinner_none)
            row.findViewById<TextView>(R.id.textViewExpenseIcon).text = emojiForCategory(categoryName)
            row.findViewById<TextView>(R.id.textViewExpenseItemMeta).text =
                "$categoryName · ${formatEpoch(exp.date)}"
            row.findViewById<TextView>(R.id.textViewExpenseItemAmount).text =
                String.format(Locale.getDefault(), "-R%.0f", exp.amount)
            row.findViewById<View>(R.id.buttonExpenseItemView).setOnClickListener { onView(exp) }
            row.findViewById<View>(R.id.buttonExpenseItemDelete).setOnClickListener { onDelete(exp) }
            container.addView(row)

            if (index != expenses.lastIndex) {
                val divider = View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1
                    )
                    setBackgroundColor(0xFFEAEAEA.toInt())
                }
                container.addView(divider)
            }
        }
    }

    private fun emojiForCategory(categoryName: String): String {
        val value = categoryName.lowercase(Locale.getDefault())
        return when {
            "groc" in value || "food" in value -> "\uD83D\uDED2"
            "rent" in value || "home" in value -> "\uD83C\uDFE0"
            "trans" in value || "car" in value || "taxi" in value -> "\uD83D\uDE97"
            "school" in value || "book" in value -> "\uD83D\uDCDA"
            else -> "\uD83D\uDCC1"
        }
    }

    private fun openReceiptByPath(path: String) {
        val file = File(path)
        if (!file.exists()) {
            Toast.makeText(this, R.string.expense_receipt_file_missing, Toast.LENGTH_SHORT).show()
            return
        }
        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        val mime = runCatching { contentResolver.getType(uri) }.getOrNull() ?: "image/*"
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching {
            startActivity(Intent.createChooser(viewIntent, getString(R.string.action_view_receipt)))
        }.onFailure {
            Toast.makeText(this, R.string.expense_receipt_no_viewer, Toast.LENGTH_SHORT).show()
        }
    }

    private fun openDatePickerStartOfDay(initialEpochMs: Long, onPicked: (Long) -> Unit) {
        val initial = Instant.ofEpochMilli(initialEpochMs).atZone(zoneId).toLocalDate()
        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val picked = LocalDate.of(year, month + 1, dayOfMonth)
                onPicked(picked.atStartOfDay(zoneId).toInstant().toEpochMilli())
            },
            initial.year, initial.monthValue - 1, initial.dayOfMonth
        ).show()
    }

    private fun openDatePickerEndOfDayInclusive(initialEpochMs: Long, onPicked: (Long) -> Unit) {
        val initial = Instant.ofEpochMilli(initialEpochMs).atZone(zoneId).toLocalDate()
        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val picked = LocalDate.of(year, month + 1, dayOfMonth)
                onPicked(endOfDayEpoch(picked))
            },
            initial.year, initial.monthValue - 1, initial.dayOfMonth
        ).show()
    }

    private fun endOfDayEpoch(day: LocalDate): Long =
        day.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1L

    private fun formatEpoch(epochMs: Long): String {
        return runCatching {
            val date = Instant.ofEpochMilli(epochMs).atZone(zoneId).toLocalDate()
            dateFormatter.format(date)
        }.getOrDefault("-")
    }

    private fun showBadgeEarnedDialog(badge: com.example.projectwatchapp.utils.SessionManager.Badge) {
        val prettyName = badge.type.replace('_', ' ')
            .split(' ')
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }

        val emoji = when (badge) {
            com.example.projectwatchapp.utils.SessionManager.Badge.FIRST_EXPENSE -> "🎉"
            com.example.projectwatchapp.utils.SessionManager.Badge.BUDGET_MASTER -> "💰"
            com.example.projectwatchapp.utils.SessionManager.Badge.SAVINGS_STARTER -> "🐷"
            com.example.projectwatchapp.utils.SessionManager.Badge.GOAL_CRUSHER -> "🏆"
            com.example.projectwatchapp.utils.SessionManager.Badge.WEEK_STREAK -> "🔥"
            com.example.projectwatchapp.utils.SessionManager.Badge.CATEGORY_WIZARD -> "🧙"
            com.example.projectwatchapp.utils.SessionManager.Badge.NIGHT_OWL -> "🦉"
            com.example.projectwatchapp.utils.SessionManager.Badge.POCKET_WATCH_GUARDIAN -> "⌚"
        }

        android.app.AlertDialog.Builder(this)
            .setTitle("$emoji Badge Unlocked!")
            .setMessage(
                "Congratulations! You've earned the\n\n" +
                        "🏅 $prettyName\n\n" +
                        "+${badge.xpReward} XP has been added to your profile.\n\n" +
                        "Keep it up — check the Rewards screen to see all your badges!"
            )
            .setPositiveButton("Awesome! 🙌") { dialog, _ -> dialog.dismiss() }
            .setCancelable(true)
            .show()
    }

    private fun navigateDashboard(userId: Long) {
        if (userId <= 0) return
        startActivity(Intent(this, DashboardActivity::class.java).putExtra(LoginActivity.EXTRA_USER_ID, userId))
        finish()
    }

    private fun navigateCategory(userId: Long) {
        if (userId <= 0) return
        startActivity(Intent(this, CategoryActivity::class.java).putExtra(LoginActivity.EXTRA_USER_ID, userId))
    }

    private fun navigateBudget(userId: Long) {
        if (userId <= 0) return
        startActivity(Intent(this, BudgetActivity::class.java).putExtra(LoginActivity.EXTRA_USER_ID, userId))
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

    private fun navigateInfo(userId: Long) {
        startActivity(Intent(this, InfoHelpActivity::class.java).putExtra(LoginActivity.EXTRA_USER_ID, userId))
    }
}

class ExpenseViewModelFactory(
    private val database: AppDatabase
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ExpenseViewModel::class.java)) {
            return ExpenseViewModel(
                database.expenseDao(),
                database.userDao(),
                database.earnedBadgeDao()
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}