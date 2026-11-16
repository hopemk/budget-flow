package com.budgetflow.app.ui.forecast

import com.budgetflow.app.FakeAccountRepository
import com.budgetflow.app.FakeBudgetConfigRepository
import com.budgetflow.app.FakeCategoryRepository
import com.budgetflow.app.FakePreferencesRepository
import com.budgetflow.app.FakeTimeProvider
import com.budgetflow.app.FakeTransactionRepository
import com.budgetflow.app.MainDispatcherRule
import com.budgetflow.app.domain.model.Account
import com.budgetflow.app.domain.model.AccountType
import com.budgetflow.app.domain.model.Budget
import com.budgetflow.app.domain.model.Category
import com.budgetflow.app.domain.model.CurrencyCode
import com.budgetflow.app.domain.model.RecurringBill
import com.budgetflow.app.domain.model.SavingsGoal
import com.budgetflow.app.domain.model.ShoppingList
import com.budgetflow.app.domain.model.ShoppingListItem
import com.budgetflow.app.domain.repository.BillsGoalsRepository
import com.budgetflow.app.domain.repository.ShoppingListRepository
import com.budgetflow.app.domain.usecase.BudgetCalculator
import com.budgetflow.app.domain.usecase.BudgetForecastCalculator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class ForecastViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val today = LocalDate.of(2026, 11, 16)
    private val novMillis = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    private class StubBillsGoalsRepository(initialBills: List<RecurringBill>) : BillsGoalsRepository {
        private val bills = MutableStateFlow(initialBills)
        private val goals = MutableStateFlow<List<SavingsGoal>>(emptyList())
        override fun observeRecurringBills(): Flow<List<RecurringBill>> = bills
        override fun observeSavingsGoals(): Flow<List<SavingsGoal>> = goals
        override suspend fun saveRecurringBill(bill: RecurringBill) {}
        override suspend fun toggleBillPaid(billId: Long, paid: Boolean) {}
        override suspend fun payRecurringBill(billId: Long, debitAccountId: Long?, categoryId: Long?) {}
        override suspend fun resetAllBillsPaid() {}
        override suspend fun deleteRecurringBill(billId: Long) {}
        override suspend fun saveSavingsGoal(goal: SavingsGoal) {}
        override suspend fun addGoalContribution(goalId: Long, deltaCents: Long) {}
        override suspend fun contributeToGoal(goalId: Long, amountCents: Long, debitAccountId: Long?) {}
        override suspend fun deleteSavingsGoal(goalId: Long) {}
    }

    private class StubShoppingListRepository(initialLists: List<ShoppingList>) : ShoppingListRepository {
        private val lists = MutableStateFlow(initialLists)
        override fun observeShoppingLists(): Flow<List<ShoppingList>> = lists
        override suspend fun saveShoppingList(list: ShoppingList): Long = 0L
        override suspend fun deleteShoppingList(listId: Long) {}
        override suspend fun saveShoppingListItem(item: ShoppingListItem): Long = 0L
        override suspend fun deleteShoppingListItem(itemId: Long) {}
        override suspend fun setShoppingListItemChecked(itemId: Long, checked: Boolean) {}
        override suspend fun uncheckAllShoppingListItems(listId: Long) {}
        override suspend fun deleteCheckedShoppingListItems(listId: Long) {}
    }

    private fun createViewModel(
        budgetRepo: FakeBudgetConfigRepository = FakeBudgetConfigRepository(
            listOf(
                Budget(1L, categoryId = 1L, currency = CurrencyCode.USD, limitCents = 20000L),
                Budget(2L, categoryId = 2L, currency = CurrencyCode.USD, limitCents = 10000L)
            )
        )
    ): ForecastViewModel {
        val budgetCalculator = BudgetCalculator(ZoneOffset.UTC)
        return ForecastViewModel(
            accountRepository = FakeAccountRepository(
                listOf(Account(1L, "Bank", AccountType.BANK, CurrencyCode.USD, 50000L))
            ),
            categoryRepository = FakeCategoryRepository(
                listOf(
                    Category(1L, "Groceries", "cart", 0xFF059669L),
                    Category(2L, "Transport", "bus", 0xFF3B82F6L)
                )
            ),
            transactionRepository = FakeTransactionRepository(),
            budgetConfigRepository = budgetRepo,
            billsGoalsRepository = StubBillsGoalsRepository(
                listOf(
                    RecurringBill(1L, "Internet", 5000L, CurrencyCode.USD, null, 20, isPaidThisMonth = false),
                    RecurringBill(2L, "Already Paid", 3000L, CurrencyCode.USD, null, 5, isPaidThisMonth = true)
                )
            ),
            shoppingListRepository = StubShoppingListRepository(
                listOf(
                    ShoppingList(
                        id = 1L,
                        name = "Weekend Shop",
                        currency = CurrencyCode.USD,
                        categoryId = 1L,
                        items = listOf(
                            ShoppingListItem(1L, 1L, "Bread", 2, 200L, isChecked = false),
                            ShoppingListItem(2L, 1L, "Milk", 1, 300L, isChecked = true)
                        )
                    )
                )
            ),
            preferencesRepository = FakePreferencesRepository(),
            budgetCalculator = budgetCalculator,
            forecastCalculator = BudgetForecastCalculator(budgetCalculator, ZoneOffset.UTC),
            timeProvider = FakeTimeProvider(millis = novMillis, date = today)
        )
    }

    @Test
    fun `seeds unpaid bills, unchecked shopping lists, and active budgets`() = runTest {
        val vm = createViewModel()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect {}
        }

        val state = vm.uiState.value
        assertEquals(2, state.scenario.expenseItems.size)
        assertTrue(state.scenario.expenseItems.any { it.label == "Internet" && it.amountCents == 5000L })
        assertTrue(state.scenario.expenseItems.any { it.label == "Weekend Shop" && it.amountCents == 400L })
        assertEquals(2, state.scenario.budgetAdjustments.size)
        assertFalse(state.hasModifiedBudgets)
        job.cancel()
    }

    @Test
    fun `what-if rate override updates effectiveRates and reset restores baseline`() = runTest {
        val vm = createViewModel()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect {}
        }

        vm.setSalaryInput("1000.00")
        vm.setRateOverride(CurrencyCode.ZWG, "30.00")
        assertEquals(BigDecimal("30.00"), vm.uiState.value.effectiveRates.rateFor(CurrencyCode.ZWG))

        vm.resetToBaseline()
        assertEquals("", vm.uiState.value.scenario.salaryInput)
        assertTrue(vm.uiState.value.scenario.rateOverrides.isEmpty())
        job.cancel()
    }

    @Test
    fun `applyBudgetChanges persists only modified budgets`() = runTest {
        val budgetRepo = FakeBudgetConfigRepository(
            listOf(
                Budget(1L, categoryId = 1L, currency = CurrencyCode.USD, limitCents = 20000L),
                Budget(2L, categoryId = 2L, currency = CurrencyCode.USD, limitCents = 10000L)
            )
        )
        val vm = createViewModel(budgetRepo)
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect {}
        }

        vm.setBudgetLimit(1L, "250.00")
        assertTrue(vm.uiState.value.hasModifiedBudgets)

        vm.applyBudgetChanges()
        assertFalse(vm.uiState.value.hasModifiedBudgets)
        assertEquals(25000L, budgetRepo.budgets.value.first { it.categoryId == 1L }.limitCents)
        assertEquals(10000L, budgetRepo.budgets.value.first { it.categoryId == 2L }.limitCents)
        job.cancel()
    }
}
