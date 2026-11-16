package com.budgetflow.app.ui.transactions

import com.budgetflow.app.FakeAccountRepository
import com.budgetflow.app.FakeCategoryRepository
import com.budgetflow.app.FakeTransactionRepository
import com.budgetflow.app.MainDispatcherRule
import com.budgetflow.app.domain.model.Account
import com.budgetflow.app.domain.model.AccountType
import com.budgetflow.app.domain.model.Category
import com.budgetflow.app.domain.model.CurrencyCode
import com.budgetflow.app.domain.model.LineItem
import com.budgetflow.app.domain.model.Transaction
import com.budgetflow.app.domain.model.TransactionType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val acc1 = Account(1L, "Cash", AccountType.CASH, CurrencyCode.USD, 10000L)
    private val acc2 = Account(2L, "Bank", AccountType.BANK, CurrencyCode.USD, 20000L)
    private val catActive = Category(10L, "Food", "restaurant", 0xFF059669L, isArchived = false)
    private val catArchived = Category(11L, "Old", "label", 0xFF64748BL, isArchived = true)

    private val txExpense = Transaction(
        id = 1L,
        accountId = 1L,
        targetAccountId = null,
        categoryId = 10L,
        type = TransactionType.EXPENSE,
        currency = CurrencyCode.USD,
        amountCents = 1500L,
        exchangeRateUsed = BigDecimal.ONE,
        merchant = "Fresh Mart",
        note = "Weekly run",
        timestampMillis = 1000L,
        items = listOf(LineItem(1L, 1L, "Organic Apples", 500L))
    )
    private val txIncome = Transaction(
        id = 2L,
        accountId = 2L,
        targetAccountId = null,
        categoryId = null,
        type = TransactionType.INCOME,
        currency = CurrencyCode.USD,
        amountCents = 50000L,
        exchangeRateUsed = BigDecimal.ONE,
        merchant = "Acme Corp",
        note = "Salary",
        timestampMillis = 2000L
    )
    private val txTransfer = Transaction(
        id = 3L,
        accountId = 1L,
        targetAccountId = 2L,
        categoryId = null,
        type = TransactionType.TRANSFER,
        currency = CurrencyCode.USD,
        amountCents = 3000L,
        exchangeRateUsed = BigDecimal.ONE,
        merchant = "Cash → Bank",
        note = "Deposit",
        timestampMillis = 3000L
    )

    @Test
    fun `uiState filters by query across merchant, note, and line items, plus type and account`() = runTest {
        val txRepo = FakeTransactionRepository(listOf(txExpense, txIncome, txTransfer))
        val vm = TransactionsViewModel(
            transactionRepository = txRepo,
            accountRepository = FakeAccountRepository(listOf(acc1, acc2)),
            categoryRepository = FakeCategoryRepository(listOf(catActive, catArchived))
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

        assertEquals(3, vm.uiState.value.transactions.size)
        // Archived categories are included in categoriesById lookup
        assertEquals(2, vm.uiState.value.categoriesById.size)

        // Query matches merchant (case-insensitive)
        vm.setQuery("fresh")
        assertEquals(listOf(1L), vm.uiState.value.transactions.map { it.id })

        // Query matches line item name
        vm.setQuery("  APPLES  ")
        assertEquals(listOf(1L), vm.uiState.value.transactions.map { it.id })

        // Query matches note
        vm.setQuery("salary")
        assertEquals(listOf(2L), vm.uiState.value.transactions.map { it.id })

        // Non-matching query returns empty list
        vm.setQuery("nonexistent")
        assertEquals(emptyList<Long>(), vm.uiState.value.transactions.map { it.id })

        // Clear query, filter by account 2L (matches txIncome.accountId AND txTransfer.targetAccountId)
        vm.setQuery("")
        vm.setAccountFilter(2L)
        assertEquals(listOf(2L, 3L), vm.uiState.value.transactions.map { it.id })

        // Add type filter TRANSFER
        vm.setTypeFilter(TransactionType.TRANSFER)
        assertEquals(listOf(3L), vm.uiState.value.transactions.map { it.id })

        // Clearing filters restores full list
        vm.setTypeFilter(null)
        vm.setAccountFilter(null)
        assertEquals(listOf(1L, 2L, 3L), vm.uiState.value.transactions.map { it.id })

        // Delete transaction
        vm.deleteTransaction(3L)
        assertEquals(listOf(1L, 2L), vm.uiState.value.transactions.map { it.id })
    }
}
