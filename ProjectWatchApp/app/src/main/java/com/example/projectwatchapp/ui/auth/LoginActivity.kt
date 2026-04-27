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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.projectwatchapp.R
import com.example.projectwatchapp.data.AppDatabase
import com.example.projectwatchapp.ui.dashboard.DashboardActivity
import com.example.projectwatchapp.viewmodel.UserViewModel
import kotlinx.coroutines.launch

/**
 * LoginActivity = UI screen for sign-in.
 *
 * This class demonstrates the "UI -> ViewModel -> DAO/DB" flow:
 * 1) Read user inputs
 * 2) Call ViewModel function
 * 3) Observe state updates and react in UI
 */
class LoginActivity : ComponentActivity() {

    // Lazy DB creation for this Activity context.
    private val database by lazy { AppDatabase.getDatabase(this) }

    // Because UserViewModel needs a UserDao constructor parameter,
    // we provide a tiny factory.
    private val userViewModel: UserViewModel by viewModels {
        UserViewModelFactory(database)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val usernameInput = findViewById<EditText>(R.id.editTextUsername)
        val passwordInput = findViewById<EditText>(R.id.editTextPassword)
        val loginButton = findViewById<Button>(R.id.buttonLogin)
        val openRegisterButton = findViewById<Button>(R.id.buttonOpenRegister)
        val loadingBar = findViewById<ProgressBar>(R.id.progressBarLogin)

        // Login: uses existing username + password.
        loginButton.setOnClickListener {
            userViewModel.loginUser(
                username = usernameInput.text.toString(),
                password = passwordInput.text.toString()
            )
        }

        // Navigate to the proper registration screen.
        openRegisterButton.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        // Observe ViewModel state in lifecycle-safe way.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                userViewModel.uiState.collect { state ->
                    loadingBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE

                    state.errorMessage?.let { message ->
                        Toast.makeText(this@LoginActivity, message, Toast.LENGTH_LONG).show()
                        userViewModel.clearMessages()
                    }

                    val userId = state.currentUserId
                    if (state.isLoggedIn && userId != null) {
                        Toast.makeText(this@LoginActivity, "Login successful!", Toast.LENGTH_SHORT).show()

                        // Move to Dashboard and pass user id.
                        startActivity(
                            Intent(this@LoginActivity, DashboardActivity::class.java)
                                .putExtra(EXTRA_USER_ID, userId)
                        )
                        finish()
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_USER_ID = "extra_user_id"
    }
}

/**
 * Simple factory to create UserViewModel with DB dependency.
 */
class UserViewModelFactory(
    private val database: AppDatabase
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(UserViewModel::class.java)) {
            return UserViewModel(database.userDao()) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
