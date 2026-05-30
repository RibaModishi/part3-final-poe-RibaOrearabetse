package com.example.projectwatchapp.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.projectwatchapp.data.dao.*
import com.example.projectwatchapp.data.entities.*

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
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Added by Riba for the receipt-photo feature rollout.
         * Adds optional receipt path on expenses without dropping tables (preserves users, etc.).
         *
         * Ref: Android Developers (n.d.f) - Migrate Room databases.
         * Ref: Android Developers (n.d.d) - Room persistence library.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE expenses ADD COLUMN photoPath TEXT")
            }
        }

        /**
         * Migration 2 → 3: allow categoryId to be NULL in the budgets table.
         * The monthly-goal budget row is stored with categoryId = NULL to
         * distinguish it from per-category budget rows.
         *
         * SQLite does not support ALTER COLUMN, so we recreate the table.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Create the new table with categoryId nullable
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS budgets_new (
                        budgetId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        userId INTEGER NOT NULL,
                        categoryId INTEGER,
                        amount REAL NOT NULL,
                        period TEXT NOT NULL,
                        startDate INTEGER NOT NULL,
                        endDate INTEGER,
                        isActive INTEGER NOT NULL,
                        FOREIGN KEY(userId) REFERENCES users(userId) ON DELETE CASCADE,
                        FOREIGN KEY(categoryId) REFERENCES categories(categoryId) ON DELETE CASCADE
                    )
                """.trimIndent())
                // 2. Copy existing data across
                db.execSQL("""
                    INSERT INTO budgets_new (budgetId, userId, categoryId, amount, period, startDate, endDate, isActive)
                    SELECT budgetId, userId, categoryId, amount, period, startDate, endDate, isActive
                    FROM budgets
                """.trimIndent())
                // 3. Swap tables
                db.execSQL("DROP TABLE budgets")
                db.execSQL("ALTER TABLE budgets_new RENAME TO budgets")
                // 4. Recreate indexes
                db.execSQL("CREATE INDEX IF NOT EXISTS index_budgets_userId ON budgets(userId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_budgets_categoryId ON budgets(categoryId)")
            }
        }

        /**
         * Shared singleton database instance for the app process.
         * Note: migration is explicit to avoid destructive data loss.
         *
         * Ref: Android Developers (n.d.d) - Room persistence library.
         */
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pocketwatch_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}