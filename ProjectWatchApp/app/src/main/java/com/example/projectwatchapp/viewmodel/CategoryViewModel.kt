package com.example.projectwatchapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.projectwatchapp.data.dao.CategoryDao
import com.example.projectwatchapp.data.entities.Category
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CategoryViewModel(
    private val categoryDao: CategoryDao
) : ViewModel() {

    private val _activeUserId = MutableStateFlow<Long?>(null)
    val activeUserId: StateFlow<Long?> = _activeUserId.asStateFlow()

    val categories = _activeUserId
        .flatMapLatest { userId ->
            if (userId == null) flowOf(emptyList()) else categoryDao.getCategoriesForUser(userId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun clearError() {
        _errorMessage.value = null
    }

    fun setActiveUser(userId: Long?) {
        _activeUserId.value = userId
    }

    fun addCategory(
        userId: Long,
        name: String,
        colorHex: String = "#FFBB86FC",
        iconName: String = "default_icon"
    ) {
        viewModelScope.launch {
            try {
                categoryDao.insertCategory(
                    Category(
                        userId = userId,
                        name = name.trim(),
                        colorHex = colorHex,
                        iconName = iconName
                    )
                )
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to create category."
            }
        }
    }

    fun updateCategory(category: Category) {
        viewModelScope.launch {
            try {
                categoryDao.updateCategory(category)
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to update category."
            }
        }
    }

    fun deleteCategory(category: Category) {
        viewModelScope.launch {
            try {
                categoryDao.deleteCategory(category)
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to delete category."
            }
        }
    }
}
