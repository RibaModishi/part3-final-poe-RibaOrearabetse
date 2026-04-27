package com.example.projectwatchapp.ui.expense

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
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
import com.example.projectwatchapp.viewmodel.ExpenseFilter
import com.example.projectwatchapp.viewmodel.ExpenseViewModel
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

/**
 * ExpenseActivity wires the ExpenseViewModel to a testable screen.
 *
 * Features on this screen:
 * - Add expense entry (category from dropdown, date from calendar)
 * - Filter by period (calendar pickers for start/end)
 * - Show total spent in active period
 * - Show expense list
 * - Tap list line to auto-fill delete expense ID
 * - Optional receipt image (attach, clear, view via FileProvider)
 */
class ExpenseActivity : ComponentActivity() {

    /** Copied file path for the next insert; cleared after a successful add. */
    private var pendingPhotoPath: String? = null

    private val pickReceiptLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri == null) return@registerForActivityResult
            discardPendingReceiptFile()
            val path = copyPickedImageToReceipts(uri)
            val status = findViewById<TextView>(R.id.textViewReceiptStatus)
            if (path != null) {
                pendingPhotoPath = path
                refreshReceiptStatus(status)
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

    /** Epoch ms at start of selected calendar day for the expense row. */
    private var selectedExpenseDateMillis: Long = 0L

    /** Inclusive period filter: start of first day, end of last day (23:59:59.999). */
    private var filterStartMillis: Long = 0L
    private var filterEndMillis: Long = 0L

    /** Parallel to spinner row index: null = "None". */
    private var categoryIdsBySpinnerIndex: List<Long?> = listOf(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_expense)

        val userId = intent.getLongExtra(LoginActivity.EXTRA_USER_ID, -1L)
        if (userId <= 0) {
            Toast.makeText(this, "Invalid user. Please login again.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val amountInput = findViewById<EditText>(R.id.editTextExpenseAmount)
        val descriptionInput = findViewById<EditText>(R.id.editTextExpenseDescription)
        val spinnerCategory = findViewById<Spinner>(R.id.spinnerExpenseCategory)
        val textViewSelectedExpenseDate = findViewById<TextView>(R.id.textViewSelectedExpenseDate)
        val buttonPickExpenseDate = findViewById<Button>(R.id.buttonPickExpenseDate)
        val textReceiptStatus = findViewById<TextView>(R.id.textViewReceiptStatus)
        val buttonAttachReceipt = findViewById<Button>(R.id.buttonAttachReceipt)
        val buttonClearReceipt = findViewById<Button>(R.id.buttonClearReceipt)
        val textViewFilterStartDate = findViewById<TextView>(R.id.textViewFilterStartDate)
        val textViewFilterEndDate = findViewById<TextView>(R.id.textViewFilterEndDate)
        val buttonPickFilterStart = findViewById<Button>(R.id.buttonPickFilterStart)
        val buttonPickFilterEnd = findViewById<Button>(R.id.buttonPickFilterEnd)
        val addButton = findViewById<Button>(R.id.buttonAddExpense)
        val filterButton = findViewById<Button>(R.id.buttonApplyPeriodFilter)
        val clearFilterButton = findViewById<Button>(R.id.buttonClearFilter)
        val refreshTotalsButton = findViewById<Button>(R.id.buttonRefreshTotals)
        val deleteExpenseIdInput = findViewById<EditText>(R.id.editTextDeleteExpenseId)
        val deleteExpenseButton = findViewById<Button>(R.id.buttonDeleteExpense)
        val buttonViewReceipt = findViewById<Button>(R.id.buttonViewReceipt)
        val loadingText = findViewById<TextView>(R.id.textViewExpenseLoading)
        val totalsText = findViewById<TextView>(R.id.textViewExpenseTotals)
        val listText = findViewById<TextView>(R.id.textViewExpenseList)

        var expenseLineRanges: List<Pair<IntRange, Long>> = emptyList()

        // Default expense date = today (start of day).
        selectedExpenseDateMillis = LocalDate.now().atStartOfDay(zoneId).toInstant().toEpochMilli()
        textViewSelectedExpenseDate.text = formatEpoch(selectedExpenseDateMillis)

        // Default filter = first day of month .. end of today.
        val today = LocalDate.now()
        filterStartMillis = today.withDayOfMonth(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        filterEndMillis = endOfDayEpoch(today)
        textViewFilterStartDate.text = formatEpoch(filterStartMillis)
        textViewFilterEndDate.text = formatEpoch(filterEndMillis)

        expenseViewModel.loadAllExpenses(userId)

        refreshReceiptStatus(textReceiptStatus)

        buttonAttachReceipt.setOnClickListener {
            pickReceiptLauncher.launch("image/*")
        }

        buttonClearReceipt.setOnClickListener {
            discardPendingReceiptFile()
            refreshReceiptStatus(textReceiptStatus)
        }

        // Load categories into spinner whenever Room data changes.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                database.categoryDao().getCategoriesForUser(userId).collect { categories ->
                    val names = mutableListOf(getString(R.string.category_spinner_none))
                    val ids = mutableListOf<Long?>(null)
                    categories.sortedBy { it.name }.forEach { cat ->
                        names.add(cat.name)
                        ids.add(cat.categoryId)
                    }
                    categoryIdsBySpinnerIndex = ids
                    val adapter = ArrayAdapter(
                        this@ExpenseActivity,
                        android.R.layout.simple_spinner_item,
                        names
                    )
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    spinnerCategory.adapter = adapter
                }
            }
        }

        buttonPickExpenseDate.setOnClickListener {
            openDatePickerStartOfDay(selectedExpenseDateMillis) { millis ->
                selectedExpenseDateMillis = millis
                textViewSelectedExpenseDate.text = formatEpoch(millis)
            }
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
            val amount = amountInput.text.toString().toDoubleOrNull()
            val description = descriptionInput.text.toString()
            val categoryId = selectedCategoryId(spinnerCategory)
            val date = selectedExpenseDateMillis

            if (amount == null) {
                Toast.makeText(this, "Enter a valid amount.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            expenseViewModel.addExpense(
                amount = amount,
                description = description,
                categoryId = categoryId,
                date = date,
                photoPath = pendingPhotoPath
            )
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

        listText.setOnTouchListener { view, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                val textView = view as TextView
                val layout = textView.layout ?: return@setOnTouchListener false

                val y = (event.y - textView.totalPaddingTop + textView.scrollY).toInt()
                val tappedLine = layout.getLineForVertical(y) + 1
                val matchedExpenseId = expenseLineRanges.firstOrNull { tappedLine in it.first }?.second

                if (matchedExpenseId != null) {
                    deleteExpenseIdInput.setText(matchedExpenseId.toString())
                    Toast.makeText(this, "Selected Expense ID: $matchedExpenseId", Toast.LENGTH_SHORT).show()
                }
            }
            true
        }

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

                    if (state.expenses.isEmpty()) {
                        expenseLineRanges = emptyList()
                        listText.text = "No expenses found."
                    } else {
                        val blocks = mutableListOf<String>()
                        val ranges = mutableListOf<Pair<IntRange, Long>>()
                        var currentLine = 1

                        state.expenses.forEachIndexed { index, exp ->
                            val receiptLine =
                                if (exp.photoPath.isNullOrBlank()) {
                                    "Receipt: no"
                                } else {
                                    "Receipt: yes (${File(exp.photoPath).name})"
                                }
                            val block =
                                "${index + 1}) ID ${exp.expenseId} | R${"%.2f".format(exp.amount)}\n" +
                                    "${exp.description}\n" +
                                    "Date: ${formatEpoch(exp.date)} | Category: ${exp.categoryId ?: "None"}\n" +
                                    receiptLine
                            blocks.add(block)

                            val start = currentLine
                            val end = currentLine + 3
                            ranges.add((start..end) to exp.expenseId)

                            currentLine = end + 2
                        }

                        expenseLineRanges = ranges
                        listText.text = blocks.joinToString(separator = "\n\n")
                    }

                    state.errorMessage?.let { message ->
                        Toast.makeText(this@ExpenseActivity, message, Toast.LENGTH_SHORT).show()
                        expenseViewModel.clearMessages()
                    }

                    state.successMessage?.let { message ->
                        Toast.makeText(this@ExpenseActivity, message, Toast.LENGTH_SHORT).show()
                        amountInput.text?.clear()
                        descriptionInput.text?.clear()
                        deleteExpenseIdInput.text?.clear()
                        if (message == ExpenseViewModel.SUCCESS_MESSAGE_EXPENSE_ADDED) {
                            pendingPhotoPath = null
                            refreshReceiptStatus(textReceiptStatus)
                        }
                        expenseViewModel.loadTotalSpentForActivePeriod()
                        expenseViewModel.clearMessages()
                    }
                }
            }
        }
    }

    private fun receiptsDir(): File = File(filesDir, "receipts")

    private fun refreshReceiptStatus(statusView: TextView) {
        val path = pendingPhotoPath
        statusView.text =
            if (path.isNullOrBlank()) {
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
        return if (pos in categoryIdsBySpinnerIndex.indices) {
            categoryIdsBySpinnerIndex[pos]
        } else {
            null
        }
    }

    private fun openDatePickerStartOfDay(initialEpochMs: Long, onPicked: (Long) -> Unit) {
        val initial = Instant.ofEpochMilli(initialEpochMs).atZone(zoneId).toLocalDate()
        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val picked = LocalDate.of(year, month + 1, dayOfMonth)
                val millis = picked.atStartOfDay(zoneId).toInstant().toEpochMilli()
                onPicked(millis)
            },
            initial.year,
            initial.monthValue - 1,
            initial.dayOfMonth
        ).show()
    }

    /** User picks a calendar day; we store last instant of that day for inclusive BETWEEN queries. */
    private fun openDatePickerEndOfDayInclusive(initialEpochMs: Long, onPicked: (Long) -> Unit) {
        val initial = Instant.ofEpochMilli(initialEpochMs).atZone(zoneId).toLocalDate()
        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val picked = LocalDate.of(year, month + 1, dayOfMonth)
                onPicked(endOfDayEpoch(picked))
            },
            initial.year,
            initial.monthValue - 1,
            initial.dayOfMonth
        ).show()
    }

    private fun endOfDayEpoch(day: LocalDate): Long {
        return day.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1L
    }

    private fun formatEpoch(epochMs: Long): String {
        return runCatching {
            val date = Instant.ofEpochMilli(epochMs).atZone(zoneId).toLocalDate()
            dateFormatter.format(date)
        }.getOrDefault("-")
    }
}

class ExpenseViewModelFactory(
    private val database: AppDatabase
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ExpenseViewModel::class.java)) {
            return ExpenseViewModel(database.expenseDao()) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
