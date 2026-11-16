package com.budgetflow.app.ui.spend

import com.budgetflow.app.FakeAccountRepository
import com.budgetflow.app.FakeCategoryRepository
import com.budgetflow.app.FakePreferencesRepository
import com.budgetflow.app.FakeTimeProvider
import com.budgetflow.app.FakeTransactionRepository
import com.budgetflow.app.MainDispatcherRule
import com.budgetflow.app.domain.model.Account
import com.budgetflow.app.domain.model.AccountType
import com.budgetflow.app.domain.model.Category
import com.budgetflow.app.domain.model.CurrencyCode
import com.budgetflow.app.domain.model.LineItem
import com.budgetflow.app.domain.model.ParsedReceipt
import com.budgetflow.app.domain.model.Transaction
import com.budgetflow.app.domain.model.TransactionType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class SpendViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val usdAccount = Account(1L, "Cash", AccountType.CASH, CurrencyCode.USD, 10000L)
    private val zwgAccount = Account(2L, "EcoCash", AccountType.MOBILE_MONEY, CurrencyCode.ZWG, 50000L)
    private val foodCategory = Category(10L, "Food", "restaurant", 0xFF059669L)

    private fun createVm(
        accounts: List<Account> = listOf(usdAccount, zwgAccount),
        categories: List<Category> = listOf(foodCategory),
        transactions: List<Transaction> = emptyList(),
        timeProvider: FakeTimeProvider = FakeTimeProvider(millis = 1_700_000_000_000L)
    ): Quad {
        val txRepo = FakeTransactionRepository(transactions)
        val accRepo = FakeAccountRepository(accounts)
        val catRepo = FakeCategoryRepository(categories)
        val prefsRepo = FakePreferencesRepository()
        val vm = SpendViewModel(txRepo, accRepo, catRepo, prefsRepo, timeProvider)
        return Quad(vm, txRepo, accRepo, timeProvider)
    }

    private data class Quad(
        val vm: SpendViewModel,
        val txRepo: FakeTransactionRepository,
        val accRepo: FakeAccountRepository,
        val timeProvider: FakeTimeProvider
    )

    @Test
    fun `uiState resolves default accounts, category, and TimeProvider timestamp`() = runTest {
        val (vm, _, _, _) = createVm()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

        val state = vm.uiState.value
        assertEquals(1L, state.form.selectedAccountId)
        assertEquals(2L, state.form.targetAccountId)
        assertEquals(10L, state.form.selectedCategoryId)
        assertEquals(1_700_000_000_000L, state.form.timestampMillis)
    }

    @Test
    fun `addLineItem and removeLineItem validate inputs and auto-sum amountInput`() = runTest {
        val (vm, _, _, _) = createVm()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

        val messages = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.messages.collect { messages.add(it) }
        }

        vm.addLineItem("   ", "5.00")
        assertEquals("Item name cannot be empty.", messages.last())

        vm.addLineItem("Milk", "0")
        assertEquals("Enter a valid item price.", messages.last())

        vm.addLineItem("Milk", "2.50")
        vm.addLineItem("Bread", "4.00")
        assertEquals(2, vm.uiState.value.form.items.size)
        assertEquals("6.50", vm.uiState.value.form.amountInput)

        vm.removeLineItem(0)
        assertEquals(1, vm.uiState.value.form.items.size)
        assertEquals("4.00", vm.uiState.value.form.amountInput)
    }

    @Test
    fun `savePurchase validates account, amount, and transfer target before saving`() = runTest {
        val (vmNoAcc, _, _, _) = createVm(accounts = emptyList())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vmNoAcc.uiState.collect {} }
        val noAccMessages = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vmNoAcc.messages.collect { noAccMessages.add(it) }
        }
        vmNoAcc.savePurchase {}
        assertEquals("Create or select an account first.", noAccMessages.last())

        val (vmSingleAcc, _, _, _) = createVm(accounts = listOf(usdAccount))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vmSingleAcc.uiState.collect {} }
        val singleMessages = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vmSingleAcc.messages.collect { singleMessages.add(it) }
        }
        vmSingleAcc.setAmount("0")
        vmSingleAcc.savePurchase {}
        assertEquals("Enter a valid amount greater than zero.", singleMessages.last())

        vmSingleAcc.setAmount("15.00")
        vmSingleAcc.setType(TransactionType.TRANSFER)
        vmSingleAcc.savePurchase {}
        assertEquals("Add another account to make a transfer.", singleMessages.last())
    }

    @Test
    fun `savePurchase persists expense and transfer, resets form using TimeProvider, and invokes onSaved`() = runTest {
        val timeProvider = FakeTimeProvider(millis = 1_700_000_000_000L)
        val (vm, txRepo, _, _) = createVm(timeProvider = timeProvider)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

        vm.setMerchant("  Fresh Mart  ")
        vm.setAmount("12.50")
        vm.setNote("  Groceries  ")
        timeProvider.millis = 1_700_000_050_000L

        var savedCalled = false
        vm.savePurchase { savedCalled = true }

        assertTrue(savedCalled)
        val savedTx = txRepo.transactions.value.single()
        assertEquals("Fresh Mart", savedTx.merchant)
        assertEquals("Groceries", savedTx.note)
        assertEquals(1250L, savedTx.amountCents)
        assertEquals(10L, savedTx.categoryId)
        assertEquals(1_700_000_000_000L, savedTx.timestampMillis)
        // Form reset uses updated TimeProvider
        assertEquals("", vm.uiState.value.form.merchant)
        assertEquals(1_700_000_050_000L, vm.uiState.value.form.timestampMillis)

        // Now save a transfer with blank merchant
        vm.setType(TransactionType.TRANSFER)
        vm.setAmount("20.00")
        vm.savePurchase {}

        val transferTx = txRepo.transactions.value.last()
        assertEquals(TransactionType.TRANSFER, transferTx.type)
        assertEquals("Cash → EcoCash", transferTx.merchant)
        assertNull(transferTx.categoryId)
        assertEquals(2L, transferTx.targetAccountId)
    }

    @Test
    fun `loadFromShoppingList, applyParsedReceipt, and loadTransaction populate form`() = runTest {
        val existingTx = Transaction(
            id = 55L,
            accountId = 2L,
            targetAccountId = null,
            categoryId = 10L,
            type = TransactionType.EXPENSE,
            currency = CurrencyCode.ZWG,
            amountCents = 7500L,
            exchangeRateUsed = BigDecimal.ONE,
            merchant = "OK Mart",
            note = "Receipt",
            timestampMillis = 12345L,
            items = listOf(LineItem(1L, 55L, "Sugar", 7500L))
        )
        val (vm, _, _, _) = createVm(transactions = listOf(existingTx))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

        vm.loadFromShoppingList(
            listName = "Weekend Shop",
            categoryId = 10L,
            currency = CurrencyCode.ZWG,
            items = listOf(LineItem(name = "Apples", amountCents = 300L), LineItem(name = "Juice", amountCents = 200L))
        )
        assertEquals("Weekend Shop", vm.uiState.value.form.merchant)
        assertEquals(2L, vm.uiState.value.form.selectedAccountId)
        assertEquals("5.00", vm.uiState.value.form.amountInput)

        vm.applyParsedReceipt(
            ParsedReceipt(
                merchant = "Spar",
                currency = CurrencyCode.USD,
                items = listOf(LineItem(name = "Coffee", amountCents = 450L)),
                totalCents = 450L
            )
        )
        assertEquals("Spar", vm.uiState.value.form.merchant)
        assertEquals(1L, vm.uiState.value.form.selectedAccountId)
        assertEquals("4.50", vm.uiState.value.form.amountInput)

        vm.loadTransaction(55L)
        assertEquals(55L, vm.uiState.value.form.editingTransactionId)
        assertEquals("OK Mart", vm.uiState.value.form.merchant)
        assertEquals("75.00", vm.uiState.value.form.amountInput)
        assertEquals(2L, vm.uiState.value.form.selectedAccountId)
    }

    @Test
    fun `editing transaction preserves id on save, removing last item keeps amount, and startNewTransaction resets`() = runTest {
        val existingTx = Transaction(
            id = 55L,
            accountId = 1L,
            targetAccountId = null,
            categoryId = 10L,
            type = TransactionType.EXPENSE,
            currency = CurrencyCode.USD,
            amountCents = 2500L,
            exchangeRateUsed = BigDecimal.ONE,
            merchant = "Old Mart",
            note = "Old note",
            timestampMillis = 12345L,
            items = listOf(LineItem(1L, 55L, "Coffee", 2500L))
        )
        val timeProvider = FakeTimeProvider(millis = 1_700_000_000_000L)
        val (vm, txRepo, _, _) = createVm(transactions = listOf(existingTx), timeProvider = timeProvider)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

        vm.loadTransaction(55L)
        vm.setMerchant("New Mart")
        // Removing the last line item preserves amountInput
        vm.removeLineItem(0)
        assertTrue(vm.uiState.value.form.items.isEmpty())
        assertEquals("25.00", vm.uiState.value.form.amountInput)

        vm.savePurchase {}
        assertEquals(1, txRepo.transactions.value.size)
        assertEquals(55L, txRepo.transactions.value.single().id)
        assertEquals("New Mart", txRepo.transactions.value.single().merchant)

        // Fallback to line items sum when amountInput is blank
        vm.addLineItem("Tea", "3.50")
        vm.setAmount("")
        vm.savePurchase {}
        assertEquals(2, txRepo.transactions.value.size)
        assertEquals(350L, txRepo.transactions.value.last().amountCents)

        // startNewTransaction resets form with updated TimeProvider
        vm.setMerchant("Draft")
        timeProvider.millis = 1_800_000_000_000L
        vm.startNewTransaction()
        assertEquals("", vm.uiState.value.form.merchant)
        assertEquals(1_800_000_000_000L, vm.uiState.value.form.timestampMillis)
    }
}
