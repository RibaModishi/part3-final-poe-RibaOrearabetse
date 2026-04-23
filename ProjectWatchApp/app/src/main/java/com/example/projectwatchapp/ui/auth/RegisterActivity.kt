package com.example.projectwatchapp.ui.auth

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.projectwatchapp.viewmodel.AppViewModelFactory
import com.example.projectwatchapp.viewmodel.UserViewModel
import kotlinx.coroutines.launch

/*
 * Riba change summary:
 * - Added ViewModel wiring for registration flow.
 * - No UI XML created/changed (design stays with UI teammate).
 * - Activity exposes onRegisterSubmitted(...) for button action hookup.
 */
class RegisterActivity : ComponentActivity() {

    private lateinit var userViewModel: UserViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Shared factory supplies UserDao to UserViewModel via AppDatabase.
        userViewModel = ViewModelProvider(
            this,
            AppViewModelFactory(application)
        )[UserViewModel::class.java]

        // Start observing success/error state from ViewModel.
        observeRegistrationState()
    }

    private fun observeRegistrationState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    // Registration succeeded.
                    userViewModel.authSuccess.collect { isSuccess ->
                        if (isSuccess) {
                            Log.d("RegisterActivity", "Registration successful")
                            // UI teammate: navigate to Dashboard/Login here.
                            userViewModel.clearAuthFlags()
                        }
                    }
                }
                launch {
                    // Validation/login/database related registration errors.
                    userViewModel.errorMessage.collect { message ->
                        if (!message.isNullOrBlank()) {
                            Log.e("RegisterActivity", message)
                            // UI teammate: show inline error / Toast here.
                        }
                    }
                }
            }
        }
    }

    // UI teammate can call this from the Register button.
    fun onRegisterSubmitted(username: String, email: String, password: String) {
        userViewModel.registerUser(username, email, password)
    }
}
