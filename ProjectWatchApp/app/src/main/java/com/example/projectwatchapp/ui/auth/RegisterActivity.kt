package com.example.projectwatchapp.ui.auth

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
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
 * Email is intentionally omitted from UI; a local placeholder is generated in UserViewModel.
 *
 * UI sync note:
 * - Screen look-and-feel was adapted from Khensani's design references.
 * - Field set remains aligned with current product decision (username + password only),
 *   and registration still routes through existing UserViewModel logic.
 */
class RegisterActivity : ComponentActivity() {

    private val database by lazy { AppDatabase.getDatabase(this) }
    private val userViewModel: UserViewModel by viewModels {
        UserViewModelFactory(database)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        // Design-only fields (not persisted yet) to match the agreed sign-up UI.
        findViewById<EditText>(R.id.editTextRegisterFirstName)
        findViewById<EditText>(R.id.editTextRegisterLastName)
        val usernameInput = findViewById<EditText>(R.id.editTextRegisterUsername)
        val passwordInput = findViewById<EditText>(R.id.editTextRegisterPassword)
        val confirmPasswordInput = findViewById<EditText>(R.id.editTextRegisterConfirmPassword)
        val passwordStrengthLabel = findViewById<TextView>(R.id.textViewPasswordStrength)
        val strengthSegments = listOf(
            findViewById<View>(R.id.viewStrength1),
            findViewById<View>(R.id.viewStrength2),
            findViewById<View>(R.id.viewStrength3),
            findViewById<View>(R.id.viewStrength4)
        )
        val createButton = findViewById<Button>(R.id.buttonRegister)
        val backToLoginButton = findViewById<Button>(R.id.buttonBackToLogin)
        val loadingBar = findViewById<ProgressBar>(R.id.progressBarRegister)

        // Khensani UI enhancement integrated into existing flow: visual-only strength feedback.
        passwordInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                renderPasswordStrength(s?.toString().orEmpty(), strengthSegments, passwordStrengthLabel)
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        renderPasswordStrength(passwordInput.text.toString(), strengthSegments, passwordStrengthLabel)

        createButton.setOnClickListener {
            val password = passwordInput.text.toString()
            val confirmPassword = confirmPasswordInput.text.toString()
            if (password != confirmPassword) {
                Toast.makeText(this, getString(R.string.error_password_mismatch), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            userViewModel.registerUser(
                username = usernameInput.text.toString(),
                password = password
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

    private fun renderPasswordStrength(
        password: String,
        strengthSegments: List<View>,
        passwordStrengthLabel: TextView
    ) {
        val score = calculatePasswordScore(password)
        val emptyColor = getColor(R.color.overlay_white_25)
        val weakColor = getColor(R.color.strength_weak)
        val fairColor = getColor(R.color.strength_fair)
        val goodColor = getColor(R.color.strength_good)
        val strongColor = getColor(R.color.strength_strong)

        strengthSegments.forEach { it.setBackgroundColor(emptyColor) }

        when (score) {
            0 -> {
                passwordStrengthLabel.text = getString(R.string.password_strength_hint)
            }
            1 -> {
                strengthSegments[0].setBackgroundColor(weakColor)
                passwordStrengthLabel.text = getString(R.string.password_strength_weak)
            }
            2 -> {
                strengthSegments[0].setBackgroundColor(fairColor)
                strengthSegments[1].setBackgroundColor(fairColor)
                passwordStrengthLabel.text = getString(R.string.password_strength_fair)
            }
            3 -> {
                strengthSegments[0].setBackgroundColor(goodColor)
                strengthSegments[1].setBackgroundColor(goodColor)
                strengthSegments[2].setBackgroundColor(goodColor)
                passwordStrengthLabel.text = getString(R.string.password_strength_good)
            }
            else -> {
                strengthSegments.forEach { it.setBackgroundColor(strongColor) }
                passwordStrengthLabel.text = getString(R.string.password_strength_strong)
            }
        }
    }

    private fun calculatePasswordScore(password: String): Int {
        if (password.isBlank()) return 0
        var score = 0
        if (password.length >= 8) score++
        if (password.any { it.isUpperCase() } && password.any { it.isLowerCase() }) score++
        if (password.any { it.isDigit() }) score++
        if (password.any { !it.isLetterOrDigit() }) score++
        return score.coerceIn(0, 4)
    }
}
