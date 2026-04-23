package com.example.projectwatchapp.ui.auth

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import com.example.projectwatchapp.viewmodel.AppViewModelFactory
import com.example.projectwatchapp.viewmodel.UserViewModel
import kotlinx.coroutines.launch

/*
 * Riba change summary:
 * - Added ViewModel wiring for login flow.
 * - No UI XML created/changed here (UI team still owns layout/design).
 * - Activity exposes onLoginSubmitted(...) so UI can call it from button click.
 */
class LoginActivity : ComponentActivity() {

    private lateinit var userViewModel: UserViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Build UserViewModel using the shared app factory (Room + DAO injected).
        userViewModel = ViewModelProvider(
            this,
            AppViewModelFactory(application)
        )[UserViewModel::class.java]

        // Start listening to login result/error state.
        observeAuthState()
    }

    private fun observeAuthState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    // Success stream (true means login passed in UserViewModel).
                    userViewModel.authSuccess.collect { isSuccess ->
                        if (isSuccess) {
                            Log.d("LoginActivity", "Login successful")
                            // UI teammate: navigate to Dashboard here.
                            userViewModel.clearAuthFlags()
                        }
                    }
                }
                launch {
                    // Error stream (invalid credentials or DB issue).
                    userViewModel.errorMessage.collect { message ->
                        if (!message.isNullOrBlank()) {
                            Log.e("LoginActivity", message)
                            // UI teammate: show Snackbar/Toast here.
                        }
                    }
                }
            }
        }
    }

    // UI teammate can call this from a login button once input fields are ready.
    fun onLoginSubmitted(username: String, password: String) {
        userViewModel.login(username, password)
    }
}
