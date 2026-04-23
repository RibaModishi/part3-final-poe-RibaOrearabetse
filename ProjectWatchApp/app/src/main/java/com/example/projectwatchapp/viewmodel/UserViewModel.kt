package com.example.projectwatchapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.projectwatchapp.data.dao.UserDao
import com.example.projectwatchapp.data.entities.User
import com.example.projectwatchapp.utils.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class UserViewModel(
    private val userDao: UserDao
) : ViewModel() {

    private val _activeUserId = MutableStateFlow<Long?>(null)
    val activeUserId: StateFlow<Long?> = _activeUserId.asStateFlow()

    val activeUser = _activeUserId
        .flatMapLatest { userId ->
            if (userId == null) flowOf(null) else userDao.getUserById(userId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _authSuccess = MutableStateFlow(false)
    val authSuccess: StateFlow<Boolean> = _authSuccess.asStateFlow()

    fun clearAuthFlags() {
        _authSuccess.value = false
        _errorMessage.value = null
    }

    fun setActiveUser(userId: Long?) {
        _activeUserId.value = userId
    }

    fun registerUser(username: String, email: String, password: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            _authSuccess.value = false
            try {
                val existing = userDao.getUserByEmail(email.trim())
                if (existing != null) {
                    _errorMessage.value = "An account with this email already exists."
                    return@launch
                }
                val newUserId = userDao.insertUser(
                    User(
                        username = username.trim(),
                        email = email.trim(),
                        passwordHash = password.trim()
                    )
                )
                _activeUserId.value = newUserId
                _authSuccess.value = true
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to register user."
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun login(username: String, password: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            _authSuccess.value = false
            try {
                val user = userDao.login(username.trim(), password.trim())
                if (user == null) {
                    _errorMessage.value = "Invalid username or password."
                    return@launch
                }
                _activeUserId.value = user.userId
                _authSuccess.value = true
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Login failed."
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun logout() {
        _activeUserId.value = null
        _authSuccess.value = false
    }

    fun addXp(userId: Long, xpToAdd: Int) {
        viewModelScope.launch {
            try {
                val user = userDao.getUserById(userId).first() ?: return@launch

                val newXp = (user.xp + xpToAdd).coerceAtLeast(0)
                val newLevel = SessionManager.getLevelFromXp(newXp)
                userDao.updateXpAndLevel(userId, newXp, newLevel)
            } catch (_: Exception) {
                // Keep UI stable if XP update fails.
            }
        }
    }
}
