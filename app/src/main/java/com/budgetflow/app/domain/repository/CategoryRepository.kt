package com.budgetflow.app.domain.repository

import com.budgetflow.app.domain.model.Category
import kotlinx.coroutines.flow.Flow

interface CategoryRepository {
    fun observeCategories(includeArchived: Boolean = false): Flow<List<Category>>
    suspend fun saveCategory(category: Category): Long
    suspend fun setCategoryArchived(categoryId: Long, archived: Boolean)
    suspend fun deleteCategory(categoryId: Long)
}
