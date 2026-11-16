package com.budgetflow.app.ui.forecast

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgetflow.app.domain.model.Account
import com.budgetflow.app.domain.model.Budget
import com.budgetflow.app.domain.model.Category
import com.budgetflow.app.domain.model.CurrencyCode
import com.budgetflow.app.domain.model.DisplayCurrencyMode
import com.budgetflow.app.domain.model.ExchangeRateConfig
import com.budgetflow.app.domain.model.ForecastBudgetAdjustment
import com.budgetflow.app.domain.model.ForecastExpenseItem
import com.budgetflow.app.domain.model.ForecastExpenseSource
import com.budgetflow.app.domain.model.ForecastIncomeItem
import com.budgetflow.app.domain.model.ForecastSummary
import com.budgetflow.app.domain.model.RecurringBill
import com.budgetflow.app.domain.model.ShoppingList
import com.budgetflow.app.domain.model.Transaction
import com.budgetflow.app.domain.repository.AccountRepository
import com.budgetflow.app.domain.repository.BillsGoalsRepository
import com.budgetflow.app.domain.repository.BudgetConfigRepository
import com.budgetflow.app.domain.repository.CategoryRepository
import com.budgetflow.app.domain.repository.PreferencesRepository
import com.budgetflow.app.domain.repository.ShoppingListRepository
import com.budgetflow.app.domain.repository.TransactionRepository
import com.budgetflow.app.domain.usecase.BudgetCalculator
import com.budgetflow.app.domain.usecase.BudgetForecastCalculator
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
import java.util.UUID
import javax.inject.Inject

private data class ForecastBaselineData(
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val transactions: List<Transaction> = emptyList(),
    val budgets: List<Budget> = emptyList(),
    val bills: List<RecurringBill> = emptyList(),
    val shoppingLists: List<ShoppingList> = emptyList(),
    val rates: ExchangeRateConfig = ExchangeRateConfig(),
    val displayMode: DisplayCurrencyMode = DisplayCurrencyMode.COMBINED_USD
)

data class ForecastScenarioState(
    val isSeeded: Boolean = false,
    val salaryInput: String = "",
    val salaryCurrency: CurrencyCode = CurrencyCode.USD,
    val incomeItems: List<ForecastIncomeItem> = emptyList(),
    val expenseItems: List<ForecastExpenseItem> = emptyList(),
    val budgetAdjustments: List<ForecastBudgetAdjustment> = emptyList(),
    val includeLoggedThisMonth: Boolean = true,
    val horizonMonths: Int = 1,
    val displayModeOverride: DisplayCurrencyMode? = null,
    val rateOverrides: Map<CurrencyCode, BigDecimal> = emptyMap()
)

data class ForecastUiState(
    val scenario: ForecastScenarioState = ForecastScenarioState(),
    val summary: ForecastSummary? = null,
    val categories: List<Category> = emptyList(),
    val displayMode: DisplayCurrencyMode = DisplayCurrencyMode.COMBINED_USD,
    val effectiveRates: ExchangeRateConfig = ExchangeRateConfig(),
    val hasModifiedBudgets: Boolean = false
)

@HiltViewModel
class ForecastViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository,
    private val budgetConfigRepository: BudgetConfigRepository,
    private val billsGoalsRepository: BillsGoalsRepository,
    private val shoppingListRepository: ShoppingListRepository,
    private val preferencesRepository: PreferencesRepository,
    private val budgetCalculator: BudgetCalculator,
    private val forecastCalculator: BudgetForecastCalculator,
    private val timeProvider: TimeProvider
) : ViewModel() {

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val scenarioState = MutableStateFlow(ForecastScenarioState())

    private val baselineFlow = combine(
        combine(
            accountRepository.observeAccounts(),
            categoryRepository.observeCategories(),
            transactionRepository.observeTransactions(),
            budgetConfigRepository.observeBudgets()
        ) { accounts, categories, transactions, budgets ->
            ForecastBaselineData(
                accounts = accounts,
                categories = categories,
                transactions = transactions,
                budgets = budgets
            )
        },
        combine(
            billsGoalsRepository.observeRecurringBills(),
            shoppingListRepository.observeShoppingLists(),
            preferencesRepository.observeExchangeRate(),
            preferencesRepository.observeDisplayCurrencyMode()
        ) { bills, lists, rates, mode ->
            Triple(bills, lists, rates to mode)
        }
    ) { part1, part2 ->
        part1.copy(
            bills = part2.first,
            shoppingLists = part2.second,
            rates = part2.third.first,
            displayMode = part2.third.second
        )
    }

    init {
        viewModelScope.launch {
            val initial = baselineFlow.first()
            if (!scenarioState.value.isSeeded) {
                scenarioState.value = buildBaselineScenario(initial)
            }
        }
    }

    val uiState: StateFlow<ForecastUiState> = combine(
        baselineFlow,
        scenarioState
    ) { baseline, scenario ->
        val activeScenario = if (scenario.isSeeded) scenario else buildBaselineScenario(baseline)
        val activeMode = activeScenario.displayModeOverride ?: baseline.displayMode
        val effectiveRates = activeScenario.rateOverrides.entries.fold(baseline.rates) { acc, (cur, rate) ->
            acc.withRate(cur, rate)
        }
        val salaryCents = CurrencyConverter.parseToCents(activeScenario.salaryInput) ?: 0L
        val allIncome = buildList {
            if (salaryCents > 0L) {
                add(
                    ForecastIncomeItem(
                        id = "salary",
                        label = "Expected Salary",
                        amountCents = salaryCents,
                        currency = activeScenario.salaryCurrency,
                        isIncluded = true
                    )
                )
            }
            addAll(activeScenario.incomeItems)
        }

        val summary = forecastCalculator.computeForecast(
            accounts = baseline.accounts,
            categories = baseline.categories,
            transactionsThisMonth = baseline.transactions,
            targetMonth = timeProvider.currentMonth(),
            mode = activeMode,
            rates = effectiveRates,
            expectedIncomeItems = allIncome,
            expectedExpenseItems = activeScenario.expenseItems,
            budgetAdjustments = activeScenario.budgetAdjustments,
            includeLoggedThisMonth = activeScenario.includeLoggedThisMonth,
            horizonMonths = activeScenario.horizonMonths
        )

        ForecastUiState(
            scenario = activeScenario,
            summary = summary,
            categories = baseline.categories,
            displayMode = activeMode,
            effectiveRates = effectiveRates,
            hasModifiedBudgets = activeScenario.budgetAdjustments.any { it.isModified }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ForecastUiState())

    private fun buildBaselineScenario(baseline: ForecastBaselineData): ForecastScenarioState {
        val targetMonth = timeProvider.currentMonth()
        val statuses = budgetCalculator.computeCategoryStatuses(
            budgets = baseline.budgets,
            categories = baseline.categories,
            transactions = baseline.transactions,
            targetMonth = targetMonth,
            mode = baseline.displayMode,
            rates = baseline.rates
        )
        val billExpenses = baseline.bills
            .filter { !it.isPaidThisMonth }
            .map { bill ->
                ForecastExpenseItem(
                    id = "bill_${bill.id}",
                    label = bill.name,
                    amountCents = bill.amountCents,
                    currency = bill.currency,
                    sourceType = ForecastExpenseSource.RECURRING_BILL,
                    categoryId = bill.categoryId,
                    isIncluded = true
                )
            }
        val listExpenses = baseline.shoppingLists.mapNotNull { list ->
            val uncheckedCents = list.items.filter { !it.isChecked }.sumOf { it.totalCents }
            if (uncheckedCents <= 0L) null
            else ForecastExpenseItem(
                id = "list_${list.id}",
                label = list.name,
                amountCents = uncheckedCents,
                currency = list.currency,
                sourceType = ForecastExpenseSource.SHOPPING_LIST,
                categoryId = list.categoryId,
                isIncluded = true
            )
        }
        val budgetAdjustments = statuses.map { status ->
            val spentInBudgetCurrency = CurrencyConverter.convert(
                amountCents = status.spentCents,
                from = baseline.displayMode.targetCurrency,
                to = status.budget.currency,
                rates = baseline.rates
            )
            ForecastBudgetAdjustment(
                categoryId = status.category.id,
                categoryName = status.category.name,
                baseLimitCents = status.budget.limitCents,
                simulatedLimitCents = status.budget.limitCents,
                spentSoFarCents = spentInBudgetCurrency,
                currency = status.budget.currency,
                rolloverMode = status.budget.rolloverMode
            )
        }
        return ForecastScenarioState(
            isSeeded = true,
            salaryInput = "",
            salaryCurrency = baseline.displayMode.targetCurrency,
            incomeItems = emptyList(),
            expenseItems = billExpenses + listExpenses,
            budgetAdjustments = budgetAdjustments,
            includeLoggedThisMonth = true,
            horizonMonths = 1,
            displayModeOverride = null
        )
    }

    fun setSalaryInput(value: String) = scenarioState.update { it.copy(salaryInput = value) }
    fun setSalaryCurrency(currency: CurrencyCode) = scenarioState.update { it.copy(salaryCurrency = currency) }
    fun setIncludeLoggedThisMonth(include: Boolean) = scenarioState.update { it.copy(includeLoggedThisMonth = include) }
    fun setHorizonMonths(months: Int) = scenarioState.update { it.copy(horizonMonths = months) }
    fun setDisplayCurrency(currency: CurrencyCode) = scenarioState.update {
        it.copy(displayModeOverride = DisplayCurrencyMode(currency, includeAllCurrencies = true))
    }

    fun setRateOverride(currency: CurrencyCode, rateInput: String) {
        if (currency == CurrencyCode.USD) return
        val parsed = rateInput.trim().toBigDecimalOrNull()
        if (parsed == null || parsed <= BigDecimal.ZERO) {
            _messages.tryEmit("Enter a valid positive rate for ${currency.name}.")
            return
        }
        scenarioState.update { state ->
            state.copy(rateOverrides = state.rateOverrides + (currency to parsed))
        }
    }

    fun clearRateOverride(currency: CurrencyCode) = scenarioState.update { state ->
        state.copy(rateOverrides = state.rateOverrides - currency)
    }

    fun addIncomeItem(label: String, amountInput: String, currency: CurrencyCode) {
        val trimmed = label.trim()
        if (trimmed.isEmpty()) {
            _messages.tryEmit("Enter an income description.")
            return
        }
        val cents = CurrencyConverter.parseToCents(amountInput)
        if (cents == null || cents <= 0L) {
            _messages.tryEmit("Enter a valid income amount.")
            return
        }
        scenarioState.update { state ->
            state.copy(
                incomeItems = state.incomeItems + ForecastIncomeItem(
                    id = "inc_${UUID.randomUUID()}",
                    label = trimmed,
                    amountCents = cents,
                    currency = currency
                )
            )
        }
    }

    fun toggleIncomeItem(id: String) = scenarioState.update { state ->
        state.copy(
            incomeItems = state.incomeItems.map { if (it.id == id) it.copy(isIncluded = !it.isIncluded) else it }
        )
    }

    fun removeIncomeItem(id: String) = scenarioState.update { state ->
        state.copy(incomeItems = state.incomeItems.filterNot { it.id == id })
    }

    fun addExpenseItem(label: String, amountInput: String, currency: CurrencyCode, categoryId: Long?) {
        val trimmed = label.trim()
        if (trimmed.isEmpty()) {
            _messages.tryEmit("Enter an expense description.")
            return
        }
        val cents = CurrencyConverter.parseToCents(amountInput)
        if (cents == null || cents <= 0L) {
            _messages.tryEmit("Enter a valid expense amount.")
            return
        }
        scenarioState.update { state ->
            state.copy(
                expenseItems = state.expenseItems + ForecastExpenseItem(
                    id = "exp_${UUID.randomUUID()}",
                    label = trimmed,
                    amountCents = cents,
                    currency = currency,
                    sourceType = ForecastExpenseSource.ONE_OFF,
                    categoryId = categoryId
                )
            )
        }
    }

    fun toggleExpenseItem(id: String) = scenarioState.update { state ->
        state.copy(
            expenseItems = state.expenseItems.map { if (it.id == id) it.copy(isIncluded = !it.isIncluded) else it }
        )
    }

    fun removeExpenseItem(id: String) = scenarioState.update { state ->
        state.copy(expenseItems = state.expenseItems.filterNot { it.id == id })
    }

    fun adjustBudgetLimit(categoryId: Long, deltaCents: Long) = scenarioState.update { state ->
        state.copy(
            budgetAdjustments = state.budgetAdjustments.map { adj ->
                if (adj.categoryId == categoryId) {
                    adj.copy(simulatedLimitCents = (adj.simulatedLimitCents + deltaCents).coerceAtLeast(0L))
                } else adj
            }
        )
    }

    fun setBudgetLimit(categoryId: Long, amountInput: String) {
        val cents = CurrencyConverter.parseToCents(amountInput)
        if (cents == null || cents < 0L) {
            _messages.tryEmit("Enter a valid budget limit.")
            return
        }
        scenarioState.update { state ->
            state.copy(
                budgetAdjustments = state.budgetAdjustments.map { adj ->
                    if (adj.categoryId == categoryId) adj.copy(simulatedLimitCents = cents) else adj
                }
            )
        }
    }

    fun resetToBaseline() {
        viewModelScope.launch {
            val current = baselineFlow.first()
            scenarioState.value = buildBaselineScenario(current)
            _messages.tryEmit("Forecast reset to current data.")
        }
    }

    fun applyBudgetChanges() {
        viewModelScope.launch {
            val modified = scenarioState.value.budgetAdjustments.filter { it.isModified }
            if (modified.isEmpty()) {
                _messages.tryEmit("No budget limit changes to apply.")
                return@launch
            }
            val existingByCategory = budgetConfigRepository.observeBudgets().first().associateBy { it.categoryId }
            modified.forEach { adj ->
                val existing = existingByCategory[adj.categoryId] ?: return@forEach
                budgetConfigRepository.saveBudget(existing.copy(limitCents = adj.simulatedLimitCents))
            }
            scenarioState.update { state ->
                state.copy(
                    budgetAdjustments = state.budgetAdjustments.map { it.copy(baseLimitCents = it.simulatedLimitCents) }
                )
            }
            _messages.tryEmit("Applied ${modified.size} budget limit update(s).")
        }
    }
}
