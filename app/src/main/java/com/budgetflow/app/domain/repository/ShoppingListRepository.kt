package com.budgetflow.app.domain.repository

import com.budgetflow.app.domain.model.ShoppingList
import com.budgetflow.app.domain.model.ShoppingListItem
import kotlinx.coroutines.flow.Flow

interface ShoppingListRepository {
    fun observeShoppingLists(): Flow<List<ShoppingList>>
    suspend fun saveShoppingList(list: ShoppingList): Long
    suspend fun deleteShoppingList(listId: Long)
    suspend fun saveShoppingListItem(item: ShoppingListItem): Long
    suspend fun deleteShoppingListItem(itemId: Long)
    suspend fun setShoppingListItemChecked(itemId: Long, checked: Boolean)
    suspend fun uncheckAllShoppingListItems(listId: Long)
    suspend fun deleteCheckedShoppingListItems(listId: Long)
}
