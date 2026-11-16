package com.budgetflow.app.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgetflow.app.domain.model.Account
import com.budgetflow.app.domain.model.Category
import com.budgetflow.app.domain.model.Transaction
import com.budgetflow.app.domain.model.TransactionType
import com.budgetflow.app.domain.repository.AccountRepository
import com.budgetflow.app.domain.repository.CategoryRepository
import com.budgetflow.app.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TransactionsFilterState(
    val query: String = "",
    val typeFilter: TransactionType? = null,
    val accountIdFilter: Long? = null
)

data class TransactionsUiState(
    val filter: TransactionsFilterState = TransactionsFilterState(),
    val transactions: List<Transaction> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val categoriesById: Map<Long, Category> = emptyMap()
)

@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    accountRepository: AccountRepository,
    categoryRepository: CategoryRepository
) : ViewModel() {

    private val filterState = MutableStateFlow(TransactionsFilterState())

    val uiState: StateFlow<TransactionsUiState> = combine(
        filterState,
        transactionRepository.observeTransactions(),
        accountRepository.observeAccounts(),
        categoryRepository.observeCategories(includeArchived = true)
    ) { filter, transactions, accounts, categories ->
        val q = filter.query.trim().lowercase()
        val filtered = transactions.filter { tx ->
            val matchesType = filter.typeFilter == null || tx.type == filter.typeFilter
            val matchesAccount = filter.accountIdFilter == null ||
                tx.accountId == filter.accountIdFilter ||
                tx.targetAccountId == filter.accountIdFilter
            val matchesQuery = q.isEmpty() ||
                tx.merchant.lowercase().contains(q) ||
                tx.note.lowercase().contains(q) ||
                tx.items.any { it.name.lowercase().contains(q) }
            matchesType && matchesAccount && matchesQuery
        }
        TransactionsUiState(
            filter = filter,
            transactions = filtered,
            accounts = accounts,
            categoriesById = categories.associateBy { it.id }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransactionsUiState())

    fun setQuery(query: String) = filterState.update { it.copy(query = query) }
    fun setTypeFilter(type: TransactionType?) = filterState.update { it.copy(typeFilter = type) }
    fun setAccountFilter(accountId: Long?) = filterState.update { it.copy(accountIdFilter = accountId) }

    fun deleteTransaction(id: Long) {
        viewModelScope.launch { transactionRepository.deleteTransaction(id) }
    }
}
