package com.budgetflow.app.ui.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgetflow.app.domain.DomainConstants
import com.budgetflow.app.domain.model.Category
import com.budgetflow.app.domain.repository.CategoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CategoriesUiState(
    val topLevelCategories: List<Category> = emptyList(),
    val subcategoriesByParent: Map<Long, List<Category>> = emptyMap(),
    val activeSubcategories: List<Category> = emptyList(),
    val archivedCategories: List<Category> = emptyList(),
    val archivedSubcategories: List<Category> = emptyList(),
    val categoryLookup: Map<Long, Category> = emptyMap()
)

@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val repository: CategoryRepository
) : ViewModel() {

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    val uiState: StateFlow<CategoriesUiState> = repository.observeCategories(includeArchived = true)
        .map { all ->
            val active = all.filter { !it.isArchived }
            val archived = all.filter { it.isArchived }
            val topLevel = active.filter { it.parentCategoryId == null }
            val subs = active.filter { it.parentCategoryId != null }
            CategoriesUiState(
                topLevelCategories = topLevel,
                subcategoriesByParent = subs.groupBy { it.parentCategoryId!! },
                activeSubcategories = subs,
                archivedCategories = archived.filter { it.parentCategoryId == null },
                archivedSubcategories = archived.filter { it.parentCategoryId != null },
                categoryLookup = all.associateBy { it.id }
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoriesUiState())

    fun saveTopLevelCategory(existing: Category?, name: String, colorHex: Long) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            _messages.tryEmit("Category name cannot be empty.")
            return
        }
        viewModelScope.launch {
            repository.saveCategory(
                Category(
                    id = existing?.id ?: 0L,
                    name = trimmed,
                    iconName = existing?.iconName ?: DomainConstants.DEFAULT_CATEGORY_ICON,
                    colorHex = colorHex,
                    isArchived = existing?.isArchived ?: false,
                    parentCategoryId = null
                )
            )
        }
    }

    fun saveSubcategory(existing: Category?, name: String, parentCategoryId: Long, colorHex: Long) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            _messages.tryEmit("Subcategory name cannot be empty.")
            return
        }
        viewModelScope.launch {
            repository.saveCategory(
                Category(
                    id = existing?.id ?: 0L,
                    name = trimmed,
                    iconName = existing?.iconName ?: DomainConstants.DEFAULT_CATEGORY_ICON,
                    colorHex = colorHex,
                    isArchived = existing?.isArchived ?: false,
                    parentCategoryId = parentCategoryId
                )
            )
        }
    }

    fun promoteToTopLevel(subcategory: Category) {
        viewModelScope.launch {
            repository.saveCategory(subcategory.copy(parentCategoryId = null))
        }
    }

    fun archiveCategory(categoryId: Long) {
        viewModelScope.launch { repository.setCategoryArchived(categoryId, true) }
    }

    fun restoreCategory(categoryId: Long) {
        viewModelScope.launch { repository.setCategoryArchived(categoryId, false) }
    }

    fun deleteCategory(categoryId: Long) {
        viewModelScope.launch { repository.deleteCategory(categoryId) }
    }
}
