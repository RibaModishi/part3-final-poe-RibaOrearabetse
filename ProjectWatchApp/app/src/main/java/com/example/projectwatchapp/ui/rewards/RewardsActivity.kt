package com.example.projectwatchapp.ui.rewards

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
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
import com.example.projectwatchapp.utils.SessionManager
import com.example.projectwatchapp.viewmodel.RewardsViewModel
import kotlinx.coroutines.launch

/**
 * Rewards / gamification UI wired to [RewardsViewModel].
 */
class RewardsActivity : ComponentActivity() {

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

        val textLevel = findViewById<TextView>(R.id.textViewRewardsLevelSummary)
        val textBadgeXp = findViewById<TextView>(R.id.textViewRewardsBadgeXp)
        val textBadges = findViewById<TextView>(R.id.textViewRewardsBadgeList)
        val spinnerBadge = findViewById<Spinner>(R.id.spinnerRewardBadge)
        val buttonAward = findViewById<Button>(R.id.buttonAwardSelectedBadge)
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
                        textLevel.text = getString(
                            R.string.rewards_level_format,
                            progress.level,
                            user.xp,
                            progress.xpToNextLevel,
                            progress.progressPercent
                        )
                    }

                    textBadgeXp.text = getString(R.string.rewards_total_badge_xp, state.totalBadgeXp)

                    val lines = rewardsViewModel.getBadgeCollectionState().joinToString("\n") { item ->
                        val status = if (item.isEarned) "✓" else "○"
                        "$status ${item.type} (+${item.xpReward} XP)"
                    }
                    textBadges.text = lines.ifEmpty { getString(R.string.rewards_badges_placeholder) }

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
