package com.example.projectwatchapp.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.projectwatchapp.data.dao.*
import com.example.projectwatchapp.data.entities.*

/*
 * Riba change summary:
 * 1) Upgraded DB version to 3.
 * 2) Replaced destructive migration path with explicit migrations.
 * 3) Added migration columns for assignment requirements:
 *    - expenses.photoUri
 *    - expenses.startTime
 *    - expenses.endTime
 *    - budgets.minimumGoalAmount
 *    - budgets.maximumGoalAmount
 */
@Database(
    entities = [
        User::class,
        Category::class,
        Expense::class,
        Budget::class,
        SavingsGoal::class,
        GoalDeposit::class,
        EarnedBadge::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun categoryDao(): CategoryDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun budgetDao(): BudgetDao
    abstract fun savingsGoalDao(): SavingsGoalDao
    abstract fun goalDepositDao(): GoalDepositDao
    abstract fun earnedBadgeDao(): EarnedBadgeDao

    companion object {
        // Riba: migration for fields introduced in version 2.
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE expenses ADD COLUMN photoUri TEXT")
                db.execSQL("ALTER TABLE budgets ADD COLUMN minimumGoalAmount REAL")
                db.execSQL("ALTER TABLE budgets ADD COLUMN maximumGoalAmount REAL")
            }
        }

        // Riba: migration for start/end time fields on expenses.
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE expenses ADD COLUMN startTime INTEGER")
                db.execSQL("ALTER TABLE expenses ADD COLUMN endTime INTEGER")
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                // Singleton pattern: build DB once, then reuse it app-wide.
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pocketwatch_database"
                )
                    // Riba: safer for team development than wiping user data.
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}