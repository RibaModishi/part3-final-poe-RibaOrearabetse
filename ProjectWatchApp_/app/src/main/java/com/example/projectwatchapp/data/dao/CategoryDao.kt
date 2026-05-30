package com.example.projectwatchapp.data.dao

import androidx.room.*
import com.example.projectwatchapp.data.entities.Category
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: Category): Long

    @Update
    suspend fun updateCategory(category: Category)

    @Delete
    suspend fun deleteCategory(category: Category)

    @Query("SELECT * FROM categories WHERE userId = :userId ORDER BY name ASC")
    fun getCategoriesForUser(userId: Long): Flow<List<Category>>

    @Query("SELECT * FROM categories WHERE categoryId = :categoryId")
    suspend fun getCategoryById(categoryId: Long): Category?
}