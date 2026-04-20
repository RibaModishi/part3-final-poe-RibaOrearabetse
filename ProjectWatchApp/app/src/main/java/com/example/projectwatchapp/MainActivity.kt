package com.example.projectwatchapp

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.example.projectwatchapp.data.AppDatabase
import com.example.projectwatchapp.data.entities.User
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(this@MainActivity)
            val testUser = User(
                username = "TestUser",
                email = "test@example.com",
                passwordHash = "hashed_password"
            )
            val userId = db.userDao().insertUser(testUser)
            Log.d("PocketWatch", "Inserted user with ID: $userId")

            val retrieved = db.userDao().getUserById(userId)
            retrieved.collect { user ->
                Log.d("PocketWatch", "Retrieved user: ${user?.username}")
            }
        }
    }
}