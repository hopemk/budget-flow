package com.budgetflow.app.ui.bills

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgetflow.app.domain.model.Account
import com.budgetflow.app.domain.model.AccountType
import com.budgetflow.app.domain.model.Category
import com.budgetflow.app.domain.model.CurrencyCode
import com.budgetflow.app.domain.model.Payable
import com.budgetflow.app.domain.model.Receivable
import com.budgetflow.app.domain.model.RecurringBill
import com.budgetflow.app.domain.model.SavingsGoal
import com.budgetflow.app.domain.model.Transaction
import com.budgetflow.app.domain.model.TransactionType
import com.budgetflow.app.domain.repository.AccountRepository
import com.budgetflow.app.domain.repository.BillsGoalsRepository
import com.budgetflow.app.domain.repository.CategoryRepository
import com.budgetflow.app.domain.repository.DebtRepository
import com.budgetflow.app.domain.repository.PreferencesRepository
import com.budgetflow.app.domain.repository.TransactionRepository
import com.budgetflow.app.domain.usecase.AccountUseCases
import com.budgetflow.app.domain.usecase.CurrencyConverter
import com.budgetflow.app.domain.util.TimeProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BillsGoalsUiState(
    val bills: List<RecurringBill> = emptyList(),
    val goals: List<SavingsGoal> = emptyList(),
    val receivables: List<Receivable> = emptyList(),
    val payables: List<Payable> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList()
)

@HiltViewModel
class BillsGoalsViewModel @Inject constructor(
    private val billsGoalsRepository: BillsGoalsRepository,
    private val debtRepository: DebtRepository,
    accountRepository: AccountRepository,
    private val accountUseCases: AccountUseCases,
    categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository,
    private val preferencesRepository: PreferencesRepository,
    private val timeProvider: TimeProvider
) : ViewModel() {

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        viewModelScope.launch {
            val currentMonth = timeProvider.currentMonth().toString()
            val lastReset = preferencesRepository.observeLastBillResetMonth().first()
            if (lastReset != null && lastReset != currentMonth) {
                billsGoalsRepository.resetAllBillsPaid()
            }
            if (lastReset != currentMonth) {
                preferencesRepository.setLastBillResetMonth(currentMonth)
            }
        }
    }

    val uiState: StateFlow<BillsGoalsUiState> = combine(
        combine(
            billsGoalsRepository.observeRecurringBills(),
            billsGoalsRepository.observeSavingsGoals(),
            debtRepository.observeReceivables()
        ) { bills, goals, receivables -> Triple(bills, goals, receivables) },
        combine(
            debtRepository.observePayables(),
            accountRepository.observeAccounts(),
            categoryRepository.observeCategories()
        ) { payables, accounts, categories -> Triple(payables, accounts, categories) }
    ) { (bills, goals, receivables), (payables, accounts, categories) ->
        BillsGoalsUiState(
            bills = bills,
            goals = goals,
            receivables = receivables,
            payables = payables,
            accounts = accounts,
            categories = categories
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BillsGoalsUiState())

    fun addBill(name: String, amountInput: String, currency: CurrencyCode, dueDay: Int) {
        if (name.isBlank()) {
            _messages.tryEmit("Bill name cannot be empty.")
            return
        }
        val cents = CurrencyConverter.parseToCents(amountInput)
        if (cents == null || cents <= 0L) {
            _messages.tryEmit("Enter a valid bill amount greater than zero.")
            return
        }
        viewModelScope.launch {
            billsGoalsRepository.saveRecurringBill(
                RecurringBill(
                    name = name.trim(),
                    amountCents = cents,
                    currency = currency,
                    categoryId = null,
                    dueDayOfMonth = dueDay.coerceIn(1, 28)
                )
            )
        }
    }

    fun toggleBillPaid(billId: Long, paid: Boolean) {
        viewModelScope.launch { billsGoalsRepository.toggleBillPaid(billId, paid) }
    }

    fun payBill(billId: Long, debitAccountId: Long?, categoryId: Long?) {
        viewModelScope.launch { billsGoalsRepository.payRecurringBill(billId, debitAccountId, categoryId) }
    }

    fun resetAllBillsPaid() {
        viewModelScope.launch {
            billsGoalsRepository.resetAllBillsPaid()
            preferencesRepository.setLastBillResetMonth(timeProvider.currentMonth().toString())
        }
    }

    fun deleteBill(billId: Long) {
        viewModelScope.launch { billsGoalsRepository.deleteRecurringBill(billId) }
    }

    fun deleteGoal(goalId: Long) {
        viewModelScope.launch { billsGoalsRepository.deleteSavingsGoal(goalId) }
    }

    fun deleteReceivable(receivableId: Long) {
        viewModelScope.launch { debtRepository.deleteReceivable(receivableId) }
    }

    fun deletePayable(payableId: Long) {
        viewModelScope.launch { debtRepository.deletePayable(payableId) }
    }

    fun updateBill(bill: RecurringBill, name: String, amountInput: String, currency: CurrencyCode, dueDay: Int) {
        if (name.isBlank()) {
            _messages.tryEmit("Bill name cannot be empty.")
            return
        }
        val cents = CurrencyConverter.parseToCents(amountInput)
        if (cents == null || cents <= 0L) {
            _messages.tryEmit("Enter a valid bill amount greater than zero.")
            return
        }
        viewModelScope.launch {
            billsGoalsRepository.saveRecurringBill(
                bill.copy(
                    name = name.trim(),
                    amountCents = cents,
                    currency = currency,
                    dueDayOfMonth = dueDay.coerceIn(1, 28)
                )
            )
        }
    }

    fun updateGoal(goal: SavingsGoal, name: String, targetInput: String, currentInput: String, currency: CurrencyCode) {
        if (name.isBlank()) {
            _messages.tryEmit("Goal name cannot be empty.")
            return
        }
        val targetCents = CurrencyConverter.parseToCents(targetInput)
        if (targetCents == null || targetCents <= 0L) {
            _messages.tryEmit("Enter a valid target amount greater than zero.")
            return
        }
        val currentCents = if (currentInput.isBlank()) {
            goal.currentCents
        } else {
            CurrencyConverter.parseToCents(currentInput) ?: run {
                _messages.tryEmit("Enter a valid saved amount.")
                return
            }
        }
        viewModelScope.launch {
            billsGoalsRepository.saveSavingsGoal(
                goal.copy(
                    name = name.trim(),
                    targetCents = targetCents,
                    currentCents = currentCents.coerceAtLeast(0L),
                    currency = currency
                )
            )
        }
    }

    fun updateReceivable(rec: Receivable, personName: String, description: String, amountInput: String, currency: CurrencyCode) {
        if (personName.isBlank()) {
            _messages.tryEmit("Person name cannot be empty.")
            return
        }
        val cents = CurrencyConverter.parseToCents(amountInput)
        if (cents == null || cents <= 0L) {
            _messages.tryEmit("Enter a valid amount greater than zero.")
            return
        }
        viewModelScope.launch {
            debtRepository.saveReceivable(
                rec.copy(
                    personName = personName.trim(),
                    description = description.trim(),
                    totalOwedCents = cents,
                    currency = currency,
                    isSettled = rec.paidBackCents >= cents
                )
            )
        }
    }

    fun updatePayable(payable: Payable, personName: String, description: String, amountInput: String, currency: CurrencyCode) {
        if (personName.isBlank()) {
            _messages.tryEmit("Person name cannot be empty.")
            return
        }
        val cents = CurrencyConverter.parseToCents(amountInput)
        if (cents == null || cents <= 0L) {
            _messages.tryEmit("Enter a valid amount greater than zero.")
            return
        }
        viewModelScope.launch {
            debtRepository.savePayable(
                payable.copy(
                    personName = personName.trim(),
                    description = description.trim(),
                    totalOweCents = cents,
                    currency = currency,
                    isSettled = payable.paidCents >= cents
                )
            )
        }
    }

    fun addGoal(name: String, targetInput: String, currency: CurrencyCode) {
        if (name.isBlank()) {
            _messages.tryEmit("Goal name cannot be empty.")
            return
        }
        val targetCents = CurrencyConverter.parseToCents(targetInput)
        if (targetCents == null || targetCents <= 0L) {
            _messages.tryEmit("Enter a valid target amount greater than zero.")
            return
        }
        viewModelScope.launch {
            billsGoalsRepository.saveSavingsGoal(
                SavingsGoal(
                    name = name.trim(),
                    currency = currency,
                    targetCents = targetCents,
                    currentCents = 0L
                )
            )
        }
    }

    fun contributeToGoal(goalId: Long, amountInput: String, debitAccountId: Long?) {
        val cents = CurrencyConverter.parseToCents(amountInput)
        if (cents == null || cents <= 0L) {
            _messages.tryEmit("Enter a valid contribution amount greater than zero.")
            return
        }
        viewModelScope.launch { billsGoalsRepository.contributeToGoal(goalId, cents, debitAccountId) }
    }

    fun addReceivable(personName: String, description: String, amountInput: String, currency: CurrencyCode) {
        if (personName.isBlank()) {
            _messages.tryEmit("Person name cannot be empty.")
            return
        }
        val cents = CurrencyConverter.parseToCents(amountInput)
        if (cents == null || cents <= 0L) {
            _messages.tryEmit("Enter a valid amount greater than zero.")
            return
        }
        viewModelScope.launch {
            debtRepository.saveReceivable(
                Receivable(
                    personName = personName.trim(),
                    description = description.trim(),
                    currency = currency,
                    totalOwedCents = cents,
                    createdAtMillis = timeProvider.currentTimeMillis()
                )
            )
        }
    }

    fun recordPayment(receivableId: Long, amountInput: String, creditAccountId: Long?) {
        val cents = CurrencyConverter.parseToCents(amountInput)
        if (cents == null || cents <= 0L) {
            _messages.tryEmit("Enter a valid payment amount greater than zero.")
            return
        }
        viewModelScope.launch {
            debtRepository.recordReceivablePayment(receivableId, cents, creditAccountId)
        }
    }

    fun addPayable(personName: String, description: String, amountInput: String, currency: CurrencyCode) {
        if (personName.isBlank()) {
            _messages.tryEmit("Person name cannot be empty.")
            return
        }
        val cents = CurrencyConverter.parseToCents(amountInput)
        if (cents == null || cents <= 0L) {
            _messages.tryEmit("Enter a valid amount greater than zero.")
            return
        }
        viewModelScope.launch {
            debtRepository.savePayable(
                Payable(
                    personName = personName.trim(),
                    description = description.trim(),
                    currency = currency,
                    totalOweCents = cents,
                    createdAtMillis = timeProvider.currentTimeMillis()
                )
            )
        }
    }

    fun recordPayablePayment(payableId: Long, amountInput: String, debitAccountId: Long?) {
        val cents = CurrencyConverter.parseToCents(amountInput)
        if (cents == null || cents <= 0L) {
            _messages.tryEmit("Enter a valid payment amount greater than zero.")
            return
        }
        viewModelScope.launch {
            debtRepository.recordPayablePayment(payableId, cents, debitAccountId)
        }
    }

    fun addSavingsAccount(name: String, currency: CurrencyCode, balanceInput: String) {
        viewModelScope.launch {
            accountUseCases.addAccount(name, AccountType.SAVINGS, currency, balanceInput)
                .onFailure { err -> err.message?.let { _messages.tryEmit(it) } }
        }
    }

    fun updateAccount(account: Account, newName: String, newType: AccountType, balanceInput: String, showOnHome: Boolean = account.showOnHome) {
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

    /** Moves funds between a savings account and an operating wallet (or vice versa). */
    fun transferBetweenAccounts(
        fromAccount: Account,
        toAccount: Account,
        amountInput: String,
        note: String
    ) {
        if (fromAccount.id == toAccount.id) {
            _messages.tryEmit("Choose a different destination account.")
            return
        }
        val cents = CurrencyConverter.parseToCents(amountInput)
        if (cents == null || cents <= 0L) {
            _messages.tryEmit("Enter a valid transfer amount greater than zero.")
            return
        }
        viewModelScope.launch {
            val rateConfig = preferencesRepository.observeExchangeRate().first()
            transactionRepository.saveTransaction(
                Transaction(
                    accountId = fromAccount.id,
                    targetAccountId = toAccount.id,
                    categoryId = null,
                    type = TransactionType.TRANSFER,
                    currency = fromAccount.currency,
                    amountCents = cents,
                    exchangeRateUsed = rateConfig.rateForTransaction(fromAccount.currency, toAccount.currency),
                    merchant = "${fromAccount.name} → ${toAccount.name}",
                    note = note.trim(),
                    timestampMillis = timeProvider.currentTimeMillis()
                )
            )
        }
    }
}
