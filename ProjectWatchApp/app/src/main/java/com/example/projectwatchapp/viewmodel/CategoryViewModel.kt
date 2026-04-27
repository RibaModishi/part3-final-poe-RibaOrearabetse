package com.example.projectwatchapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.projectwatchapp.data.dao.CategoryDao
import com.example.projectwatchapp.data.entities.Category
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * CategoryViewModel controls all "category" business logic.
 *
 * Why this exists:
 * - Activity/Fragment should focus on showing UI.
 * - ViewModel handles validation + database calls + state updates.
 */
class CategoryViewModel(
    private val categoryDao: CategoryDao
) : ViewModel() {

    // Internal mutable state.
    private val _uiState = MutableStateFlow(CategoryUiState())
    // Exposed read-only state for UI observation.
    val uiState: StateFlow<CategoryUiState> = _uiState.asStateFlow()

    /**
     * Start listening to category updates for a specific user.
     * Because DAO returns Flow, UI will auto-refresh when DB changes.
     */
    fun loadCategories(userId: Long) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(activeUserId = userId, isLoading = true, errorMessage = null)
            categoryDao.getCategoriesForUser(userId).collect { categories ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    categories = categories,
                    errorMessage = null
                )
            }
        }
    }

    /**
     * Create category for currently active user.
     */
    fun addCategory(
        name: String,
        colorHex: String = "#FFBB86FC",
        iconName: String = "default_icon"
    ) {
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
            categoryDao.insertCategory(
                Category(
                    userId = userId,
                    name = cleanName,
                    colorHex = colorHex,
                    iconName = iconName
                )
            )
            _uiState.value = _uiState.value.copy(isLoading = false, successMessage = "Category created.")
        }
    }

    /**
     * Update editable category fields.
     */
    fun updateCategory(
        categoryId: Long,
        name: String,
        colorHex: String,
        iconName: String
    ) {
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
            categoryDao.updateCategory(
                existing.copy(
                    name = cleanName,
                    colorHex = colorHex,
                    iconName = iconName
                )
            )
            _uiState.value = _uiState.value.copy(isLoading = false, successMessage = "Category updated.")
        }
    }

    /**
     * Delete a category by id.
     *
     * Note for your schema:
     * Expense.categoryId uses SET_NULL on category delete,
     * so deleting category will not delete old expenses.
     */
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

    /**
     * UI can call this after showing a toast/snackbar,
     * so one-time messages do not repeat on rotation.
     */
    fun clearMessages() {
        _uiState.value = _uiState.value.copy(errorMessage = null, successMessage = null)
    }
}

/**
 * Single UI state object (easy to observe and debug).
 */
data class CategoryUiState(
    val isLoading: Boolean = false,
    val activeUserId: Long? = null,
    val categories: List<Category> = emptyList(),
    val errorMessage: String? = null,
    val successMessage: String? = null
)
