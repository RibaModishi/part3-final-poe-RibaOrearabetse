package com.example.projectwatchapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.projectwatchapp.data.dao.CategoryDao
import com.example.projectwatchapp.data.dao.UserDao
import com.example.projectwatchapp.data.entities.Category
import com.example.projectwatchapp.utils.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CategoryViewModel(
    private val categoryDao: CategoryDao,
    private val userDao: UserDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(CategoryUiState())
    val uiState: StateFlow<CategoryUiState> = _uiState.asStateFlow()

    fun loadCategories(userId: Long) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(activeUserId = userId, isLoading = true, errorMessage = null)
            categoryDao.getCategoriesForUser(userId).collect { categories ->
                _uiState.value = _uiState.value.copy(isLoading = false, categories = categories, errorMessage = null)
            }
        }
    }

    fun addCategory(name: String, colorHex: String = "#FFBB86FC", iconName: String = "default_icon") {
        val userId = _uiState.value.activeUserId
        if (userId == null) {
            _uiState.value = _uiState.value.copy(errorMessage = "Load categories first to set the active user.")
            return
        }
        val cleanName = name.trim()
        if (cleanName.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Category name cannot be empty.")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            categoryDao.insertCategory(Category(userId = userId, name = cleanName, colorHex = colorHex, iconName = iconName))

            // Award XP for creating a category (CATEGORY_WIZARD badge reward)
            awardXp(userId, SessionManager.Badge.CATEGORY_WIZARD.xpReward)

            _uiState.value = _uiState.value.copy(isLoading = false, successMessage = "Category created.")
        }
    }

    fun updateCategory(categoryId: Long, name: String, colorHex: String, iconName: String) {
        val existing = _uiState.value.categories.firstOrNull { it.categoryId == categoryId }
        if (existing == null) {
            _uiState.value = _uiState.value.copy(errorMessage = "Category not found in current list.")
            return
        }
        val cleanName = name.trim()
        if (cleanName.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Category name cannot be empty.")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            categoryDao.updateCategory(existing.copy(name = cleanName, colorHex = colorHex, iconName = iconName))
            _uiState.value = _uiState.value.copy(isLoading = false, successMessage = "Category updated.")
        }
    }

    fun deleteCategory(categoryId: Long) {
        val category = _uiState.value.categories.firstOrNull { it.categoryId == categoryId }
        if (category == null) {
            _uiState.value = _uiState.value.copy(errorMessage = "Category not found.")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            categoryDao.deleteCategory(category)
            _uiState.value = _uiState.value.copy(isLoading = false, successMessage = "Category deleted.")
        }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(errorMessage = null, successMessage = null)
    }

    private suspend fun awardXp(userId: Long, xp: Int) {
        if (xp <= 0) return
        val user = userDao.getUserByIdOnce(userId) ?: return
        val newXp = user.xp + xp
        val newLevel = SessionManager.getLevelFromXp(newXp)
        userDao.updateXpAndLevel(userId, newXp, newLevel)
    }
}

data class CategoryUiState(
    val isLoading: Boolean = false,
    val activeUserId: Long? = null,
    val categories: List<Category> = emptyList(),
    val errorMessage: String? = null,
    val successMessage: String? = null
)