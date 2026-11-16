package com.budgetflow.app.ui.budgets

import com.budgetflow.app.FakeBudgetConfigRepository
import com.budgetflow.app.FakeCategoryRepository
import com.budgetflow.app.FakePreferencesRepository
import com.budgetflow.app.FakeTimeProvider
import com.budgetflow.app.FakeTransactionRepository
import com.budgetflow.app.MainDispatcherRule
import com.budgetflow.app.domain.DomainConstants
import com.budgetflow.app.domain.model.Budget
import com.budgetflow.app.domain.model.Category
import com.budgetflow.app.domain.model.CurrencyCode
import com.budgetflow.app.domain.model.RolloverMode
import com.budgetflow.app.domain.model.Transaction
import com.budgetflow.app.domain.model.TransactionType
import com.budgetflow.app.domain.usecase.BudgetCalculator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class BudgetsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val today = LocalDate.of(2026, 11, 16)
    private val novMillis = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    @Test
    fun `uiState computes budget statuses, health counts, and separates archived categories`() = runTest {
        val cat1 = Category(1L, "Food", "restaurant", 0xFF059669L, isArchived = false)
        val cat2 = Category(2L, "Transport", "directions_bus", 0xFF3B82F6L, isArchived = false)
        val archivedCat = Category(3L, "Old", "label", 0xFF64748BL, isArchived = true)

        val budgets = listOf(
            Budget(1L, categoryId = 1L, currency = CurrencyCode.USD, limitCents = 10000L, alertThresholdPct = 80),
            Budget(2L, categoryId = 2L, currency = CurrencyCode.USD, limitCents = 5000L, alertThresholdPct = 80)
        )
        val transactions = listOf(
            Transaction(1L, 1L, null, 1L, TransactionType.EXPENSE, CurrencyCode.USD, 12000L, BigDecimal.ONE, "Mart", "", null, novMillis),
            Transaction(2L, 1L, null, 2L, TransactionType.EXPENSE, CurrencyCode.USD, 4500L, BigDecimal.ONE, "Bus", "", null, novMillis)
        )

        val vm = BudgetsViewModel(
            budgetConfigRepository = FakeBudgetConfigRepository(budgets),
            categoryRepository = FakeCategoryRepository(listOf(cat1, cat2, archivedCat)),
            transactionRepository = FakeTransactionRepository(transactions),
            preferencesRepository = FakePreferencesRepository(),
            calculator = BudgetCalculator(ZoneOffset.UTC),
            timeProvider = FakeTimeProvider(millis = novMillis, date = today)
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

        val state = vm.uiState.value
        assertEquals(2, state.categories.size)
        assertEquals(1, state.archivedCategories.size)
        assertEquals(2, state.statuses.size)
        assertEquals(0, state.onTrackCount)
        assertEquals(1, state.warningCount)
        assertEquals(1, state.overCount)
        assertEquals(CurrencyCode.USD, state.displayCurrency)
    }

    @Test
    fun `saveBudget validates limit and stamps createdAtMillis from TimeProvider only for new budgets`() = runTest {
        val budgetRepo = FakeBudgetConfigRepository()
        val timeProvider = FakeTimeProvider(millis = 9_999L, date = today)
        val vm = BudgetsViewModel(
            budgetConfigRepository = budgetRepo,
            categoryRepository = FakeCategoryRepository(),
            transactionRepository = FakeTransactionRepository(),
            preferencesRepository = FakePreferencesRepository(),
            calculator = BudgetCalculator(ZoneOffset.UTC),
            timeProvider = timeProvider
        )
        val messages = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.messages.collect { messages.add(it) } }

        vm.saveBudget(0L, 1L, CurrencyCode.USD, "0", RolloverMode.RESET_MONTHLY)
        assertEquals("Enter a valid budget limit greater than zero.", messages.last())
        assertTrue(budgetRepo.budgets.value.isEmpty())

        vm.saveBudget(0L, 1L, CurrencyCode.USD, "250.00", RolloverMode.ROLLOVER_SURPLUS)
        val created = budgetRepo.budgets.value.single()
        assertEquals(25000L, created.limitCents)
        assertEquals(9_999L, created.createdAtMillis)

        timeProvider.millis = 88_888L
        vm.saveBudget(created.id, 1L, CurrencyCode.USD, "300.00", RolloverMode.RESET_MONTHLY, existingCreatedAtMillis = 9_999L)
        val updated = budgetRepo.budgets.value.single()
        assertEquals(30000L, updated.limitCents)
        assertEquals(9_999L, updated.createdAtMillis)

        vm.deleteBudget(updated.id)
        assertTrue(budgetRepo.budgets.value.isEmpty())
    }

    @Test
    fun `category creation, renaming, archiving, and restoring validate and update repository`() = runTest {
        val catRepo = FakeCategoryRepository()
        val vm = BudgetsViewModel(
            budgetConfigRepository = FakeBudgetConfigRepository(),
            categoryRepository = catRepo,
            transactionRepository = FakeTransactionRepository(),
            preferencesRepository = FakePreferencesRepository(),
            calculator = BudgetCalculator(ZoneOffset.UTC),
            timeProvider = FakeTimeProvider(millis = novMillis, date = today)
        )
        val messages = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.messages.collect { messages.add(it) } }

        vm.createCategory("   ", 0xFF123456L)
        assertEquals("Category name cannot be empty.", messages.last())

        vm.createCategory("  Dining  ", 0xFF123456L, parentCategoryId = null)
        val created = catRepo.categories.value.single()
        assertEquals("Dining", created.name)
        assertEquals(DomainConstants.DEFAULT_CATEGORY_ICON, created.iconName)

        vm.renameCategory(created, "")
        assertEquals("Category name cannot be empty.", messages.last())

        vm.renameCategory(created, "  Eating Out  ")
        assertEquals("Eating Out", catRepo.categories.value.single().name)

        vm.archiveCategory(created.id)
        assertTrue(catRepo.categories.value.single().isArchived)

        vm.restoreCategory(created.id)
        assertEquals(false, catRepo.categories.value.single().isArchived)
    }
}
