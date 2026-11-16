package com.budgetflow.app

import com.budgetflow.app.domain.DomainConstants
import com.budgetflow.app.domain.model.Account
import com.budgetflow.app.domain.model.Budget
import com.budgetflow.app.domain.model.Category
import com.budgetflow.app.domain.model.CurrencyCode
import com.budgetflow.app.domain.model.DisplayCurrencyMode
import com.budgetflow.app.domain.model.ExchangeRateConfig
import com.budgetflow.app.domain.model.ThemeMode
import com.budgetflow.app.domain.model.Transaction
import com.budgetflow.app.domain.repository.AccountRepository
import com.budgetflow.app.domain.repository.BudgetConfigRepository
import com.budgetflow.app.domain.repository.CategoryRepository
import com.budgetflow.app.domain.repository.PreferencesRepository
import com.budgetflow.app.domain.repository.TransactionRepository
import com.budgetflow.app.domain.util.TimeProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

class FakeTimeProvider(
    var millis: Long = 1_700_000_000_000L,
    var date: LocalDate = LocalDate.of(2026, 11, 16)
) : TimeProvider {
    override fun currentTimeMillis(): Long = millis
    override fun today(): LocalDate = date
    override fun currentMonth(): YearMonth = YearMonth.from(date)
}

class FakeAccountRepository(initial: List<Account> = emptyList()) : AccountRepository {
    val accounts = MutableStateFlow(initial)
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0L) + 1L

    override fun observeAccounts(): Flow<List<Account>> =
        accounts.map { list -> list.filter { !it.isArchived } }

    override fun observeAllAccounts(): Flow<List<Account>> = accounts

    override suspend fun saveAccount(account: Account) {
        val saved = if (account.id == 0L) account.copy(id = nextId++) else account
        accounts.value = accounts.value.filterNot { it.id == saved.id } + saved
    }

    override suspend fun setAccountArchived(accountId: Long, archived: Boolean) {
        accounts.value = accounts.value.map { if (it.id == accountId) it.copy(isArchived = archived) else it }
    }

    override suspend fun setAccountShowOnHome(accountId: Long, showOnHome: Boolean) {
        accounts.value = accounts.value.map { if (it.id == accountId) it.copy(showOnHome = showOnHome) else it }
    }

    override suspend fun deleteAccount(accountId: Long) {
        setAccountArchived(accountId, true)
    }
}

class FakeCategoryRepository(initial: List<Category> = emptyList()) : CategoryRepository {
    val categories = MutableStateFlow(initial)
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0L) + 1L

    override fun observeCategories(includeArchived: Boolean): Flow<List<Category>> =
        categories.map { list -> if (includeArchived) list else list.filter { !it.isArchived } }

    override suspend fun saveCategory(category: Category): Long {
        val id = if (category.id == 0L) nextId++ else category.id
        val saved = category.copy(id = id)
        categories.value = categories.value.filterNot { it.id == id } + saved
        return id
    }

    override suspend fun setCategoryArchived(categoryId: Long, archived: Boolean) {
        categories.value = categories.value.map { if (it.id == categoryId) it.copy(isArchived = archived) else it }
    }

    override suspend fun deleteCategory(categoryId: Long) {
        categories.value = categories.value.filterNot { it.id == categoryId }
    }
}

class FakeTransactionRepository(initial: List<Transaction> = emptyList()) : TransactionRepository {
    val transactions = MutableStateFlow(initial)
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0L) + 1L

    override fun observeTransactions(): Flow<List<Transaction>> = transactions

    override suspend fun getTransactionById(transactionId: Long): Transaction? =
        transactions.value.firstOrNull { it.id == transactionId }

    override suspend fun saveTransaction(transaction: Transaction) {
        val saved = if (transaction.id == 0L) transaction.copy(id = nextId++) else transaction
        transactions.value = transactions.value.filterNot { it.id == saved.id } + saved
    }

    override suspend fun deleteTransaction(transactionId: Long) {
        transactions.value = transactions.value.filterNot { it.id == transactionId }
    }
}

class FakeBudgetConfigRepository(initial: List<Budget> = emptyList()) : BudgetConfigRepository {
    val budgets = MutableStateFlow(initial)
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0L) + 1L

    override fun observeBudgets(): Flow<List<Budget>> = budgets

    override suspend fun saveBudget(budget: Budget) {
        val saved = if (budget.id == 0L) budget.copy(id = nextId++) else budget
        budgets.value = budgets.value.filterNot { it.id == saved.id } + saved
    }

    override suspend fun deleteBudget(budgetId: Long) {
        budgets.value = budgets.value.filterNot { it.id == budgetId }
    }
}

class FakePreferencesRepository(
    initialThemeMode: ThemeMode = ThemeMode.LIGHT,
    initialDisplayMode: DisplayCurrencyMode = DisplayCurrencyMode.COMBINED_USD,
    initialRates: ExchangeRateConfig = ExchangeRateConfig(DomainConstants.DEFAULT_RATES_PER_USD)
) : PreferencesRepository {
    val themeMode = MutableStateFlow(initialThemeMode)
    val displayMode = MutableStateFlow(initialDisplayMode)
    val rates = MutableStateFlow(initialRates)
    val securityEnabled = MutableStateFlow(false)
    val onboardingCompleted = MutableStateFlow(true)
    val billRemindersEnabled = MutableStateFlow(true)
    val lastBillResetMonth = MutableStateFlow<String?>(null)

    override fun observeThemeMode(): Flow<ThemeMode> = themeMode
    override fun observeDisplayCurrencyMode(): Flow<DisplayCurrencyMode> = displayMode
    override fun observeExchangeRate(): Flow<ExchangeRateConfig> = rates
    override fun observeSecurityEnabled(): Flow<Boolean> = securityEnabled
    override fun observeOnboardingCompleted(): Flow<Boolean> = onboardingCompleted
    override fun observeBillRemindersEnabled(): Flow<Boolean> = billRemindersEnabled
    override fun observeLastBillResetMonth(): Flow<String?> = lastBillResetMonth

    override suspend fun setThemeMode(mode: ThemeMode) { themeMode.value = mode }
    override suspend fun setDisplayCurrencyMode(mode: DisplayCurrencyMode) { displayMode.value = mode }
    override suspend fun setExchangeRate(zwgPerUsd: String) {
        val bd = zwgPerUsd.toBigDecimalOrNull()?.takeIf { it > BigDecimal.ZERO } ?: return
        rates.value = rates.value.copy(ratesPerUsd = rates.value.ratesPerUsd + (CurrencyCode.ZWG to bd))
    }
    override suspend fun setExchangeRate(currency: CurrencyCode, perUsd: String) {
        val bd = perUsd.toBigDecimalOrNull()?.takeIf { it > BigDecimal.ZERO } ?: return
        rates.value = rates.value.copy(ratesPerUsd = rates.value.ratesPerUsd + (currency to bd))
    }
    override suspend fun setSecurityEnabled(enabled: Boolean) { securityEnabled.value = enabled }
    override suspend fun setOnboardingCompleted(completed: Boolean) { onboardingCompleted.value = completed }
    override suspend fun setBillRemindersEnabled(enabled: Boolean) { billRemindersEnabled.value = enabled }
    override suspend fun setLastBillResetMonth(yearMonth: String) { lastBillResetMonth.value = yearMonth }
}
