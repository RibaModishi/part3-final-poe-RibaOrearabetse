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
    version = 2,
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
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}