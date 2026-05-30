package com.example.projectwatchapp

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.example.projectwatchapp.data.AppDatabase
import com.example.projectwatchapp.data.entities.User
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(this@MainActivity)
            val existing = db.userDao().getUserByEmail("test@example.com")
            val userId = if (existing == null) {
                // Must match UserViewModel.loginUser() hashing logic.
                val testUser = User(
                    username = "TestUser",
                    email = "test@example.com",
                    passwordHash = sha256("password123")
                )
                db.userDao().insertUser(testUser)
            } else {
                existing.userId
            }
            Log.d("PocketWatch", "Test user available with ID: $userId")

            val retrieved = db.userDao().getUserById(userId).first()
            Log.d("PocketWatch", "Retrieved user: ${retrieved?.username}")
        }
    }

    private fun sha256(raw: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray(StandardCharsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}