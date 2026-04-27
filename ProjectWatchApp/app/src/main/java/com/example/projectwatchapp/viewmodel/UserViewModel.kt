package com.example.projectwatchapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.projectwatchapp.data.dao.UserDao
import com.example.projectwatchapp.data.entities.User
import com.example.projectwatchapp.utils.SessionManager
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel = "brain" of the screen.
 * It keeps UI data and handles logic (login/register/etc) so Activities stay simple.
 *
 * Why this matters:
 * - Activity/Fragment = UI
 * - ViewModel = logic + state
 * - DAO = database operations
 */
class UserViewModel(
    private val userDao: UserDao
) : ViewModel() {

    // Mutable inside ViewModel only.
    private val _uiState = MutableStateFlow(UserUiState())
    // Read-only version exposed to UI layer.
    val uiState: StateFlow<UserUiState> = _uiState.asStateFlow()

    fun registerUser(
        username: String,
        email: String,
        password: String
    ) {
        // Basic input cleanup before validation/querying.
        val cleanUsername = username.trim()
        val cleanEmail = email.trim().lowercase()

        if (cleanUsername.isBlank() || cleanEmail.isBlank() || password.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "All fields are required.")
            return
        }

        // viewModelScope.launch starts a coroutine tied to this ViewModel lifecycle.
        // It automatically cancels when ViewModel is cleared.
        viewModelScope.launch {
            // UI can show a loading spinner from this flag.
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            // Prevent duplicate accounts by email.
            val existingUser = userDao.getUserByEmail(cleanEmail)
            if (existingUser != null) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "An account with this email already exists."
                )
                return@launch
            }

            val createdUser = User(
                username = cleanUsername,
                email = cleanEmail,
                // Never store plain text passwords in DB.
                passwordHash = hashPassword(password)
            )
            val newUserId = userDao.insertUser(createdUser)
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                currentUserId = newUserId,
                isLoggedIn = true,
                errorMessage = null
            )
        }
    }

    fun loginUser(username: String, password: String) {
        if (username.isBlank() || password.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Username and password are required.")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            // Hash entered password and compare to stored hash.
            val user = userDao.login(username.trim(), hashPassword(password))
            if (user == null) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isLoggedIn = false,
                    errorMessage = INVALID_LOGIN_MESSAGE
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    currentUserId = user.userId,
                    isLoggedIn = true,
                    errorMessage = null
                )
            }
        }
    }

    fun loadUser(userId: Long) {
        viewModelScope.launch {
            // Flow emits updates whenever this user row changes in Room.
            userDao.getUserById(userId).collect { user ->
                _uiState.value = _uiState.value.copy(currentUser = user)
            }
        }
    }

    fun awardXp(action: String, amount: Double? = null) {
        // If no user is loaded, nothing to update.
        val user = _uiState.value.currentUser ?: return
        viewModelScope.launch {
            val deltaXp = SessionManager.calculateXpForAction(action, amount)
            if (deltaXp <= 0) return@launch

            val updatedXp = user.xp + deltaXp
            val updatedLevel = SessionManager.getLevelFromXp(updatedXp)
            userDao.updateXpAndLevel(
                userId = user.userId,
                newXp = updatedXp,
                newLevel = updatedLevel
            )
        }
    }

    fun logout() {
        // Reset all user/session UI state.
        _uiState.value = UserUiState()
    }

    /** Clears transient messages so the next login attempt always produces a new [UserUiState] emission. */
    fun clearMessages() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    private fun hashPassword(password: String): String {
        // NOTE: Good for a student project, but production apps should use
        // stronger password hashing (bcrypt/Argon2 + salt) on a backend.
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(password.toByteArray(StandardCharsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val INVALID_LOGIN_MESSAGE =
            "Invalid username or password. If you updated the app, local data was reset—use Create Account."
    }
}

/**
 * Single state object for UI.
 * UI observes this and renders: loading, errors, logged-in state, etc.
 */
data class UserUiState(
    val isLoading: Boolean = false,
    val isLoggedIn: Boolean = false,
    val currentUserId: Long? = null,
    val currentUser: User? = null,
    val errorMessage: String? = null
)
