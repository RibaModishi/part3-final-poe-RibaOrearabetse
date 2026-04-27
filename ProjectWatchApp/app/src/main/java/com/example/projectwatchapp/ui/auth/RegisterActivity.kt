package com.example.projectwatchapp.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.projectwatchapp.R
import com.example.projectwatchapp.data.AppDatabase
import com.example.projectwatchapp.ui.dashboard.DashboardActivity
import com.example.projectwatchapp.viewmodel.UserViewModel
import kotlinx.coroutines.launch

/**
 * Dedicated registration screen.
 * This is a proper replacement for the old "quick create" button in Login.
 */
class RegisterActivity : ComponentActivity() {

    private val database by lazy { AppDatabase.getDatabase(this) }
    private val userViewModel: UserViewModel by viewModels {
        UserViewModelFactory(database)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        val usernameInput = findViewById<EditText>(R.id.editTextRegisterUsername)
        val emailInput = findViewById<EditText>(R.id.editTextRegisterEmail)
        val passwordInput = findViewById<EditText>(R.id.editTextRegisterPassword)
        val createButton = findViewById<Button>(R.id.buttonRegister)
        val backToLoginButton = findViewById<Button>(R.id.buttonBackToLogin)
        val loadingBar = findViewById<ProgressBar>(R.id.progressBarRegister)

        createButton.setOnClickListener {
            userViewModel.registerUser(
                username = usernameInput.text.toString(),
                email = emailInput.text.toString(),
                password = passwordInput.text.toString()
            )
        }

        backToLoginButton.setOnClickListener {
            finish()
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                userViewModel.uiState.collect { state ->
                    loadingBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE

                    state.errorMessage?.let { message ->
                        Toast.makeText(this@RegisterActivity, message, Toast.LENGTH_SHORT).show()
                        userViewModel.clearMessages()
                    }

                    val userId = state.currentUserId
                    if (state.isLoggedIn && userId != null) {
                        Toast.makeText(this@RegisterActivity, "Account created!", Toast.LENGTH_SHORT).show()
                        startActivity(
                            Intent(this@RegisterActivity, DashboardActivity::class.java)
                                .putExtra(LoginActivity.EXTRA_USER_ID, userId)
                        )
                        finish()
                    }
                }
            }
        }
    }
}
