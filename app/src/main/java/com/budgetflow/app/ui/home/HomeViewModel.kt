package com.budgetflow.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgetflow.app.domain.model.Account
import com.budgetflow.app.domain.model.AccountType
import com.budgetflow.app.domain.model.Category
import com.budgetflow.app.domain.model.CurrencyCode
import com.budgetflow.app.domain.model.Transaction
import com.budgetflow.app.domain.repository.AccountRepository
import com.budgetflow.app.domain.repository.CategoryRepository
import com.budgetflow.app.domain.repository.TransactionRepository
import com.budgetflow.app.domain.usecase.AccountUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val allAccounts: List<Account> = emptyList(),
    val homeAccounts: List<Account> = emptyList(),
    val recentTransactions: List<Transaction> = emptyList(),
    val categoriesById: Map<Long, Category> = emptyMap()
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    accountRepository: AccountRepository,
    private val accountUseCases: AccountUseCases,
    private val transactionRepository: TransactionRepository,
    categoryRepository: CategoryRepository
) : ViewModel() {

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    val uiState: StateFlow<HomeUiState> = combine(
        accountRepository.observeAccounts(),
        transactionRepository.observeTransactions(),
        categoryRepository.observeCategories(includeArchived = true)
    ) { accounts, transactions, categories ->
        HomeUiState(
            allAccounts = accounts,
            homeAccounts = accounts.filter { it.showOnHome },
            recentTransactions = transactions.take(10),
            categoriesById = categories.associateBy { it.id }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun deleteTransaction(id: Long) {
        viewModelScope.launch { transactionRepository.deleteTransaction(id) }
    }

    fun addAccount(name: String, type: AccountType, currency: CurrencyCode, balanceInput: String) {
        viewModelScope.launch {
            accountUseCases.addAccount(name, type, currency, balanceInput, showOnHome = true)
                .onFailure { err -> err.message?.let { _messages.tryEmit(it) } }
        }
    }

    fun toggleAccountOnHome(accountId: Long, showOnHome: Boolean) {
        viewModelScope.launch { accountUseCases.setShowOnHome(accountId, showOnHome) }
    }

    fun updateAccount(
        account: Account,
        newName: String,
        newType: AccountType,
        balanceInput: String,
        showOnHome: Boolean
    ) {
        viewModelScope.launch {
            accountUseCases.updateAccount(account, newName, newType, balanceInput, showOnHome)
                .onFailure { err -> err.message?.let { _messages.tryEmit(it) } }
        }
    }

    fun archiveAccount(accountId: Long) {
        viewModelScope.launch { accountUseCases.archiveAccount(accountId) }
    }

    fun deleteAccount(accountId: Long) {
        viewModelScope.launch { accountUseCases.deleteAccount(accountId) }
    }
}
