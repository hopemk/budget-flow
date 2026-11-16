package com.budgetflow.app.ui.spend

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgetflow.app.domain.DomainConstants
import com.budgetflow.app.domain.model.Account
import com.budgetflow.app.domain.model.Category
import com.budgetflow.app.domain.model.CurrencyCode
import com.budgetflow.app.domain.model.ExchangeRateConfig
import com.budgetflow.app.domain.model.LineItem
import com.budgetflow.app.domain.model.ParsedReceipt
import com.budgetflow.app.domain.model.Transaction
import com.budgetflow.app.domain.model.TransactionType
import com.budgetflow.app.domain.repository.AccountRepository
import com.budgetflow.app.domain.repository.CategoryRepository
import com.budgetflow.app.domain.repository.PreferencesRepository
import com.budgetflow.app.domain.repository.TransactionRepository
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

data class SpendFormState(
    val editingTransactionId: Long? = null,
    val type: TransactionType = TransactionType.EXPENSE,
    val merchant: String = "",
    val amountInput: String = "",
    val note: String = "",
    val selectedAccountId: Long? = null,
    val targetAccountId: Long? = null,
    val selectedCategoryId: Long? = null,
    val timestampMillis: Long = System.currentTimeMillis(),
    val items: List<LineItem> = emptyList(),
    val receiptUri: String? = null
)

data class SpendUiState(
    val form: SpendFormState = SpendFormState(),
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val rates: ExchangeRateConfig = ExchangeRateConfig(),
    val exchangeRate: BigDecimal = DomainConstants.DEFAULT_EXCHANGE_RATE
)

@HiltViewModel
class SpendViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    categoryRepository: CategoryRepository,
    preferencesRepository: PreferencesRepository,
    private val timeProvider: TimeProvider
) : ViewModel() {

    private val formState = MutableStateFlow(SpendFormState(timestampMillis = timeProvider.currentTimeMillis()))
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    val uiState: StateFlow<SpendUiState> = combine(
        formState,
        accountRepository.observeAccounts(),
        categoryRepository.observeCategories(),
        preferencesRepository.observeExchangeRate()
    ) { form, accounts, categories, rate ->
        val resolvedSelectedAccountId = form.selectedAccountId ?: accounts.firstOrNull()?.id
        val resolvedTargetAccountId = form.targetAccountId?.takeIf { it != resolvedSelectedAccountId }
            ?: accounts.firstOrNull { it.id != resolvedSelectedAccountId }?.id
        val resolvedForm = form.copy(
            selectedAccountId = resolvedSelectedAccountId,
            targetAccountId = resolvedTargetAccountId,
            selectedCategoryId = form.selectedCategoryId ?: categories.firstOrNull()?.id
        )
        SpendUiState(
            form = resolvedForm,
            accounts = accounts,
            categories = categories,
            rates = rate,
            exchangeRate = rate.zwgPerUsd
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SpendUiState())

    fun setType(type: TransactionType) = formState.update { it.copy(type = type) }
    fun setMerchant(value: String) = formState.update { it.copy(merchant = value) }
    fun setAmount(value: String) = formState.update { it.copy(amountInput = value) }
    fun setNote(value: String) = formState.update { it.copy(note = value) }
    fun setAccount(id: Long) = formState.update { it.copy(selectedAccountId = id) }
    fun setTargetAccount(id: Long) = formState.update { it.copy(targetAccountId = id) }
    fun setCategory(id: Long) = formState.update { it.copy(selectedCategoryId = id) }
    fun setDateMillis(millis: Long) = formState.update { it.copy(timestampMillis = millis) }

    fun startNewTransaction() {
        formState.value = SpendFormState(timestampMillis = timeProvider.currentTimeMillis())
    }

    fun loadFromShoppingList(
        listName: String,
        categoryId: Long?,
        currency: CurrencyCode,
        items: List<LineItem>
    ) {
        viewModelScope.launch {
            val accounts = uiState.value.accounts.ifEmpty { accountRepository.observeAccounts().first() }
            val matchingAccount = accounts.firstOrNull { it.currency == currency } ?: accounts.firstOrNull()
            val totalCents = items.sumOf { it.amountCents }
            val amountMajor = if (totalCents > 0L) {
                CurrencyConverter.formatInput(totalCents)
            } else ""
            formState.value = SpendFormState(
                type = TransactionType.EXPENSE,
                merchant = listName,
                amountInput = amountMajor,
                selectedAccountId = matchingAccount?.id,
                selectedCategoryId = categoryId,
                timestampMillis = timeProvider.currentTimeMillis(),
                items = items
            )
        }
    }

    fun loadTransaction(transactionId: Long) {
        viewModelScope.launch {
            val tx = transactionRepository.getTransactionById(transactionId) ?: return@launch
            formState.value = SpendFormState(
                editingTransactionId = tx.id,
                type = tx.type,
                merchant = tx.merchant,
                amountInput = CurrencyConverter.formatInput(tx.amountCents),
                note = tx.note,
                selectedAccountId = tx.accountId,
                targetAccountId = tx.targetAccountId,
                selectedCategoryId = tx.categoryId,
                timestampMillis = tx.timestampMillis,
                items = tx.items,
                receiptUri = tx.receiptUri
            )
        }
    }

    fun addLineItem(name: String, priceInput: String) {
        if (name.isBlank()) {
            _messages.tryEmit("Item name cannot be empty.")
            return
        }
        val cents = CurrencyConverter.parseToCents(priceInput)
        if (cents == null || cents <= 0L) {
            _messages.tryEmit("Enter a valid item price.")
            return
        }
        formState.update { state ->
            val updated = state.items + LineItem(name = name.trim(), amountCents = cents)
            val sumMajor = CurrencyConverter.formatInput(updated.sumOf { it.amountCents })
            state.copy(items = updated, amountInput = sumMajor)
        }
    }

    fun removeLineItem(index: Int) {
        formState.update { state ->
            val updated = state.items.filterIndexed { i, _ -> i != index }
            val sumMajor = if (updated.isNotEmpty()) {
                CurrencyConverter.formatInput(updated.sumOf { it.amountCents })
            } else state.amountInput
            state.copy(items = updated, amountInput = sumMajor)
        }
    }

    fun applyParsedReceipt(parsed: ParsedReceipt) {
        viewModelScope.launch {
            val accounts = uiState.value.accounts.ifEmpty { accountRepository.observeAccounts().first() }
            val matchingAccount = accounts.firstOrNull { it.currency == parsed.currency } ?: accounts.firstOrNull()
            formState.update { state ->
                state.copy(
                    merchant = parsed.merchant.ifBlank { state.merchant },
                    items = parsed.items.ifEmpty { state.items },
                    selectedAccountId = matchingAccount?.id ?: state.selectedAccountId,
                    amountInput = parsed.totalCents?.let {
                        CurrencyConverter.formatInput(it)
                    } ?: state.amountInput
                )
            }
        }
    }

    fun savePurchase(onSaved: () -> Unit) {
        val current = uiState.value
        val account = current.accounts.firstOrNull { it.id == current.form.selectedAccountId }
        if (account == null) {
            _messages.tryEmit("Create or select an account first.")
            return
        }
        val amountCents = CurrencyConverter.parseToCents(current.form.amountInput)
            ?: current.form.items.sumOf { it.amountCents }.takeIf { it > 0L }
        if (amountCents == null || amountCents <= 0L) {
            _messages.tryEmit("Enter a valid amount greater than zero.")
            return
        }

        val targetAccount = if (current.form.type == TransactionType.TRANSFER) {
            val target = current.accounts.firstOrNull { it.id == current.form.targetAccountId && it.id != account.id }
                ?: current.accounts.firstOrNull { it.id != account.id }
            if (target == null) {
                _messages.tryEmit("Add another account to make a transfer.")
                return
            }
            target
        } else null
        val merchantText = current.form.merchant.trim().ifBlank {
            if (targetAccount != null) "${account.name} → ${targetAccount.name}" else ""
        }

        viewModelScope.launch {
            transactionRepository.saveTransaction(
                Transaction(
                    id = current.form.editingTransactionId ?: 0L,
                    accountId = account.id,
                    targetAccountId = targetAccount?.id,
                    categoryId = if (current.form.type == TransactionType.EXPENSE) current.form.selectedCategoryId else null,
                    type = current.form.type,
                    currency = account.currency,
                    amountCents = amountCents,
                    exchangeRateUsed = current.rates.rateForTransaction(account.currency, targetAccount?.currency),
                    merchant = merchantText,
                    note = current.form.note.trim(),
                    receiptUri = current.form.receiptUri,
                    timestampMillis = current.form.timestampMillis,
                    items = current.form.items
                )
            )
            formState.value = SpendFormState(timestampMillis = timeProvider.currentTimeMillis())
            onSaved()
        }
    }
}
