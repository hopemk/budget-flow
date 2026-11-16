package com.budgetflow.app.ui.shopping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgetflow.app.domain.model.Category
import com.budgetflow.app.domain.model.CurrencyCode
import com.budgetflow.app.domain.model.LineItem
import com.budgetflow.app.domain.model.ShoppingList
import com.budgetflow.app.domain.model.ShoppingListItem
import com.budgetflow.app.domain.repository.CategoryRepository
import com.budgetflow.app.domain.repository.ShoppingListRepository
import com.budgetflow.app.domain.usecase.CurrencyConverter
import com.budgetflow.app.domain.util.TimeProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ShoppingListsUiState(
    val lists: List<ShoppingList> = emptyList(),
    val selectedListId: Long? = null,
    val categories: List<Category> = emptyList()
) {
    val activeList: ShoppingList?
        get() = lists.firstOrNull { it.id == selectedListId } ?: lists.firstOrNull()
}

@HiltViewModel
class ShoppingListViewModel @Inject constructor(
    private val repository: ShoppingListRepository,
    categoryRepository: CategoryRepository,
    private val timeProvider: TimeProvider
) : ViewModel() {

    private val selectedIdFlow = MutableStateFlow<Long?>(null)
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    val uiState: StateFlow<ShoppingListsUiState> = combine(
        repository.observeShoppingLists(),
        selectedIdFlow,
        categoryRepository.observeCategories()
    ) { lists, selectedId, categories ->
        ShoppingListsUiState(
            lists = lists,
            selectedListId = selectedId ?: lists.firstOrNull()?.id,
            categories = categories
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShoppingListsUiState())

    fun selectList(listId: Long) {
        selectedIdFlow.value = listId
    }

    fun saveList(existing: ShoppingList?, name: String, currency: CurrencyCode, categoryId: Long?) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            _messages.tryEmit("List name cannot be empty.")
            return
        }
        viewModelScope.launch {
            val savedId = repository.saveShoppingList(
                (existing ?: ShoppingList(
                    name = trimmed,
                    currency = currency,
                    createdAtMillis = timeProvider.currentTimeMillis()
                )).copy(
                    name = trimmed,
                    currency = currency,
                    categoryId = categoryId
                )
            )
            if (existing == null) selectedIdFlow.value = savedId
        }
    }

    fun deleteList(listId: Long) {
        viewModelScope.launch {
            repository.deleteShoppingList(listId)
            if (selectedIdFlow.value == listId) selectedIdFlow.value = null
        }
    }

    fun saveItem(
        listId: Long,
        existing: ShoppingListItem?,
        name: String,
        quantityInput: String,
        estimatedPriceInput: String
    ) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            _messages.tryEmit("Item name cannot be empty.")
            return
        }
        val qty = quantityInput.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val estCents = if (estimatedPriceInput.isBlank()) {
            0L
        } else {
            CurrencyConverter.parseToCents(estimatedPriceInput) ?: run {
                _messages.tryEmit("Enter a valid estimated price.")
                return
            }
        }
        viewModelScope.launch {
            repository.saveShoppingListItem(
                (existing ?: ShoppingListItem(listId = listId, name = trimmed)).copy(
                    listId = listId,
                    name = trimmed,
                    quantity = qty,
                    estimatedUnitCents = estCents.coerceAtLeast(0L)
                )
            )
        }
    }

    fun confirmInShopItem(
        item: ShoppingListItem,
        quantityInput: String,
        actualPriceInput: String
    ) {
        val qty = quantityInput.toIntOrNull()?.coerceAtLeast(1) ?: item.quantity
        val actualCents = if (actualPriceInput.isBlank()) {
            null
        } else {
            CurrencyConverter.parseToCents(actualPriceInput)?.takeIf { it > 0L } ?: run {
                _messages.tryEmit("Enter a valid actual price.")
                return
            }
        }
        viewModelScope.launch {
            repository.saveShoppingListItem(
                item.copy(
                    quantity = qty,
                    actualUnitCents = actualCents,
                    isChecked = true
                )
            )
        }
    }

    fun uncheckItem(itemId: Long) {
        viewModelScope.launch { repository.setShoppingListItemChecked(itemId, false) }
    }

    fun deleteItem(itemId: Long) {
        viewModelScope.launch { repository.deleteShoppingListItem(itemId) }
    }

    fun finishCheckout(list: ShoppingList, reuseList: Boolean): List<LineItem> {
        val checkedItems = list.items.filter { it.isChecked }
        val lineItems = checkedItems.map { item ->
            val label = if (item.quantity > 1) "${item.name} ×${item.quantity}" else item.name
            LineItem(name = label, amountCents = item.totalCents)
        }
        viewModelScope.launch {
            if (reuseList) {
                repository.uncheckAllShoppingListItems(list.id)
            } else {
                repository.deleteCheckedShoppingListItems(list.id)
            }
        }
        return lineItems
    }
}
