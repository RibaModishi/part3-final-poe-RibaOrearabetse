package com.example.projectwatchapp.utils

object SessionManager {

    private val levelThresholds = listOf(
        0, 100, 250, 500, 1000, 2000, 3500, 5500, 8000, 11000
    )

    fun getLevelFromXp(xp: Int): Int {
        var level = 1
        for (i in levelThresholds.indices) {
            if (xp >= levelThresholds[i]) {
                level = i + 1
            } else break
        }
        return level
    }

    fun getXpToNextLevel(currentXp: Int): Int {
        val currentLevel = getLevelFromXp(currentXp)
        if (currentLevel >= levelThresholds.size) return 0
        return levelThresholds[currentLevel] - currentXp
    }

    fun calculateXpForAction(action: String, amount: Double? = null): Int {
        return when (action) {
            "ADD_EXPENSE" -> 5
            "STAY_UNDER_BUDGET" -> 50
            "DEPOSIT_TO_GOAL" -> minOf((amount ?: 0.0).toInt() / 10, 100) + 5
            "COMPLETE_GOAL" -> 200
            "LOGIN_STREAK" -> 10
            else -> 0
        }
    }

    enum class Badge(val type: String, val xpReward: Int) {
        FIRST_EXPENSE("first_expense", 50),
        BUDGET_MASTER("budget_master", 100),
        SAVINGS_STARTER("savings_starter", 75),
        GOAL_CRUSHER("goal_crusher", 150),
        WEEK_STREAK("week_streak", 200),
        CATEGORY_WIZARD("category_wizard", 50),
        NIGHT_OWL("night_owl", 30),
        POCKET_WATCH_GUARDIAN("pocket_watch_guardian", 500)
    }
}