package com.example.projectwatchapp.ui.goals

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
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
import com.example.projectwatchapp.data.entities.SavingsGoal
import com.example.projectwatchapp.ui.auth.LoginActivity
import com.example.projectwatchapp.viewmodel.GoalsViewModel
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * Savings goals UI wired to [GoalsViewModel]: create goals, deposits, history, pin, delete.
 */
class GoalsActivity : ComponentActivity() {

    private val database by lazy { AppDatabase.getDatabase(this) }
    private val goalsViewModel: GoalsViewModel by viewModels {
        GoalsViewModelFactory(database)
    }

    private val zoneId: ZoneId = ZoneId.systemDefault()
    private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val moneyFormat: NumberFormat = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }

    private var goalIdsBySpinnerIndex: List<Long?> = listOf(null)
    private var optionalDeadlineMillis: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_goals)

        val userId = intent.getLongExtra(LoginActivity.EXTRA_USER_ID, -1L)
        if (userId <= 0) {
            Toast.makeText(this, "Invalid user. Please login again.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val editGoalName = findViewById<EditText>(R.id.editTextGoalName)
        val editGoalTarget = findViewById<EditText>(R.id.editTextGoalTarget)
        val textDeadline = findViewById<TextView>(R.id.textViewSelectedGoalDeadline)
        val buttonPickDeadline = findViewById<Button>(R.id.buttonPickGoalDeadline)
        val buttonClearDeadline = findViewById<Button>(R.id.buttonClearGoalDeadline)
        val buttonAddGoal = findViewById<Button>(R.id.buttonAddGoal)
        val textGoalsList = findViewById<TextView>(R.id.textViewGoalsList)
        val spinnerGoals = findViewById<Spinner>(R.id.spinnerGoals)
        val editDepositAmount = findViewById<EditText>(R.id.editTextDepositAmount)
        val editDepositNote = findViewById<EditText>(R.id.editTextDepositNote)
        val buttonDeposit = findViewById<Button>(R.id.buttonAddDeposit)
        val buttonTogglePin = findViewById<Button>(R.id.buttonTogglePinGoal)
        val textDepositHistory = findViewById<TextView>(R.id.textViewDepositHistory)
        val editDeleteGoalId = findViewById<EditText>(R.id.editTextDeleteGoalId)
        val buttonDeleteGoal = findViewById<Button>(R.id.buttonDeleteGoal)
        val loadingText = findViewById<TextView>(R.id.textViewGoalsLoading)

        fun refreshDeadlineLabel() {
            textDeadline.text = optionalDeadlineMillis?.let { formatEpoch(it) }
                ?: getString(R.string.goal_deadline_none)
        }
        refreshDeadlineLabel()

        buttonPickDeadline.setOnClickListener {
            val initial = optionalDeadlineMillis
                ?: LocalDate.now().atStartOfDay(zoneId).toInstant().toEpochMilli()
            val ld = Instant.ofEpochMilli(initial).atZone(zoneId).toLocalDate()
            DatePickerDialog(
                this,
                { _, y, m, d ->
                    val picked = LocalDate.of(y, m + 1, d)
                    optionalDeadlineMillis = picked.atStartOfDay(zoneId).toInstant().toEpochMilli()
                    refreshDeadlineLabel()
                },
                ld.year,
                ld.monthValue - 1,
                ld.dayOfMonth
            ).show()
        }

        buttonClearDeadline.setOnClickListener {
            optionalDeadlineMillis = null
            refreshDeadlineLabel()
        }

        goalsViewModel.loadGoals(userId)

        spinnerGoals.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position <= 0 || position >= goalIdsBySpinnerIndex.size) return
                val gid = goalIdsBySpinnerIndex[position] ?: return
                goalsViewModel.loadDepositHistory(gid)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        buttonAddGoal.setOnClickListener {
            val target = editGoalTarget.text.toString().toDoubleOrNull()
            if (target == null || target <= 0.0) {
                Toast.makeText(this, getString(R.string.goals_invalid_target), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            goalsViewModel.addGoal(
                name = editGoalName.text.toString(),
                targetAmount = target,
                deadline = optionalDeadlineMillis
            )
        }

        buttonDeposit.setOnClickListener {
            val pos = spinnerGoals.selectedItemPosition
            val goalId = if (pos in goalIdsBySpinnerIndex.indices) goalIdsBySpinnerIndex[pos] else null
            if (goalId == null) {
                Toast.makeText(this, getString(R.string.goals_pick_goal_first), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val amount = editDepositAmount.text.toString().toDoubleOrNull()
            if (amount == null || amount <= 0.0) {
                Toast.makeText(this, getString(R.string.goals_invalid_deposit), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            goalsViewModel.addDeposit(
                goalId = goalId,
                amount = amount,
                note = editDepositNote.text.toString().takeIf { it.isNotBlank() }
            )
        }

        buttonTogglePin.setOnClickListener {
            val pos = spinnerGoals.selectedItemPosition
            val goalId = if (pos in goalIdsBySpinnerIndex.indices) goalIdsBySpinnerIndex[pos] else null
            if (goalId == null) {
                Toast.makeText(this, getString(R.string.goals_pick_goal_first), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            goalsViewModel.togglePinGoal(goalId)
        }

        buttonDeleteGoal.setOnClickListener {
            val id = editDeleteGoalId.text.toString().toLongOrNull()
            if (id == null) {
                Toast.makeText(this, getString(R.string.goals_invalid_goal_id), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            goalsViewModel.deleteGoal(id)
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                goalsViewModel.uiState.collect { state ->
                    loadingText.visibility = if (state.isLoading) View.VISIBLE else View.GONE

                    val sortedGoals = state.goals.sortedWith(
                        compareByDescending<SavingsGoal> { state.pinnedGoalIds.contains(it.goalId) }
                            .thenByDescending { it.createdAt }
                    )

                    textGoalsList.text = if (sortedGoals.isEmpty()) {
                        getString(R.string.goals_empty_list)
                    } else {
                        sortedGoals.joinToString("\n\n") { g ->
                            val pin = if (state.pinnedGoalIds.contains(g.goalId)) "📌 " else ""
                            val done = if (g.isCompleted) " [Done]" else ""
                            val pct = goalsViewModel.getGoalProgressPercentage(g)
                            val remaining = goalsViewModel.getRemainingAmount(g)
                            "$pin${g.name}$done (ID ${g.goalId})\n" +
                                "${moneyFormat.format(g.currentAmount)} / ${moneyFormat.format(g.targetAmount)} " +
                                "($pct%)\n" +
                                getString(R.string.goals_remaining, moneyFormat.format(remaining))
                        }
                    }

                    val names = mutableListOf(getString(R.string.goals_spinner_pick))
                    val ids = mutableListOf<Long?>(null)
                    sortedGoals.forEach { g ->
                        val pin = if (state.pinnedGoalIds.contains(g.goalId)) "📌 " else ""
                        val done = if (g.isCompleted) " ✓" else ""
                        names.add("$pin${g.name}$done")
                        ids.add(g.goalId)
                    }
                    goalIdsBySpinnerIndex = ids
                    spinnerGoals.adapter = ArrayAdapter(
                        this@GoalsActivity,
                        android.R.layout.simple_spinner_item,
                        names
                    ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

                    textDepositHistory.text = when {
                        state.selectedGoalId == null ->
                            getString(R.string.goals_no_deposits)
                        state.selectedGoalDeposits.isEmpty() ->
                            getString(R.string.goals_no_deposits_yet)
                        else ->
                            state.selectedGoalDeposits.joinToString("\n") { dep ->
                                val whenStr = formatEpoch(dep.date)
                                val note = dep.note?.let { " — $it" } ?: ""
                                "${moneyFormat.format(dep.amount)} on $whenStr$note"
                            }
                    }

                    state.errorMessage?.let {
                        Toast.makeText(this@GoalsActivity, it, Toast.LENGTH_LONG).show()
                        goalsViewModel.clearMessages()
                    }
                    state.successMessage?.let {
                        Toast.makeText(this@GoalsActivity, it, Toast.LENGTH_SHORT).show()
                        editGoalName.text?.clear()
                        editGoalTarget.text?.clear()
                        optionalDeadlineMillis = null
                        refreshDeadlineLabel()
                        editDepositAmount.text?.clear()
                        editDepositNote.text?.clear()
                        editDeleteGoalId.text?.clear()
                        goalsViewModel.clearMessages()
                    }
                }
            }
        }
    }

    private fun formatEpoch(epochMs: Long): String {
        return runCatching {
            Instant.ofEpochMilli(epochMs).atZone(zoneId).toLocalDate().format(dateFormatter)
        }.getOrDefault("-")
    }
}

class GoalsViewModelFactory(
    private val database: AppDatabase
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GoalsViewModel::class.java)) {
            return GoalsViewModel(
                database.savingsGoalDao(),
                database.goalDepositDao()
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
