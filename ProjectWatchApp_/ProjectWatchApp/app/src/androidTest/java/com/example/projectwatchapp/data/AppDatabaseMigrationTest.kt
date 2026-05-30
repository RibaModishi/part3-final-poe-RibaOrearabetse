package com.example.projectwatchapp.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "migration-test-db"

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    @Test
    fun migration1To2_addsPhotoPathWithoutDroppingExpenseData() {
        context.deleteDatabase(dbName)
        val db = createV1Database()

        db.execSQL(
            "INSERT INTO users (userId, username, email, passwordHash, xp, level, createdAt) " +
                "VALUES (1, 'oreo', 'oreo@test.com', 'hash', 0, 1, 123456789)"
        )
        db.execSQL(
            "INSERT INTO expenses (expenseId, userId, categoryId, amount, description, date, " +
                "isRecurring, recurringInterval, notes, xpEarned) " +
                "VALUES (10, 1, NULL, 45.5, 'Lunch', 1700000000000, 0, NULL, NULL, 0)"
        )

        AppDatabase.MIGRATION_1_2.migrate(db)

        db.query("PRAGMA table_info(expenses)").use { cursor ->
            var hasPhotoPath = false
            while (cursor.moveToNext()) {
                val name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                if (name == "photoPath") {
                    hasPhotoPath = true
                    break
                }
            }
            assertTrue("Expected expenses.photoPath column after migration.", hasPhotoPath)
        }

        db.query("SELECT amount, description FROM expenses WHERE expenseId = 10").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(45.5, cursor.getDouble(0), 0.0001)
            assertEquals("Lunch", cursor.getString(1))
        }
        db.close()
    }

    private fun createV1Database(): SupportSQLiteDatabase {
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(
                object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS users (" +
                                "userId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                "username TEXT NOT NULL, " +
                                "email TEXT NOT NULL, " +
                                "passwordHash TEXT NOT NULL, " +
                                "xp INTEGER NOT NULL, " +
                                "level INTEGER NOT NULL, " +
                                "createdAt INTEGER NOT NULL)"
                        )
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS expenses (" +
                                "expenseId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                "userId INTEGER NOT NULL, " +
                                "categoryId INTEGER, " +
                                "amount REAL NOT NULL, " +
                                "description TEXT NOT NULL, " +
                                "date INTEGER NOT NULL, " +
                                "isRecurring INTEGER NOT NULL, " +
                                "recurringInterval TEXT, " +
                                "notes TEXT, " +
                                "xpEarned INTEGER NOT NULL DEFAULT 0)"
                        )
                    }

                    override fun onUpgrade(
                        db: SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int
                    ) = Unit
                }
            )
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        return helper.writableDatabase
    }
}
