package com.budgetflow.app.ui.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgetflow.app.domain.model.CurrencyCode
import com.budgetflow.app.domain.model.DisplayCurrencyMode
import com.budgetflow.app.domain.model.MonthlySummary
import com.budgetflow.app.domain.model.NetPosition
import com.budgetflow.app.domain.model.SpendingInsights
import com.budgetflow.app.domain.model.TransactionType
import com.budgetflow.app.domain.repository.AccountRepository
import com.budgetflow.app.domain.repository.BudgetConfigRepository
import com.budgetflow.app.domain.repository.CategoryRepository
import com.budgetflow.app.domain.repository.DebtRepository
import com.budgetflow.app.domain.repository.PreferencesRepository
import com.budgetflow.app.domain.repository.TransactionRepository
import com.budgetflow.app.domain.usecase.BudgetCalculator
import com.budgetflow.app.domain.util.TimeProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth
import javax.inject.Inject

data class CategoryShare(
    val name: String,
    val amountCents: Long,
    val percent: Int,
    val colorHex: Long
)

data class InsightsUiState(
    val summary: MonthlySummary? = null,
    val netPosition: NetPosition? = null,
    val insights: SpendingInsights? = null,
    val shares: List<CategoryShare> = emptyList(),
    val displayCurrency: CurrencyCode = CurrencyCode.USD,
    val displayMode: DisplayCurrencyMode = DisplayCurrencyMode.COMBINED_USD
)

@HiltViewModel
class InsightsViewModel @Inject constructor(
    budgetConfigRepository: BudgetConfigRepository,
    categoryRepository: CategoryRepository,
    transactionRepository: TransactionRepository,
    accountRepository: AccountRepository,
    debtRepository: DebtRepository,
    private val preferencesRepository: PreferencesRepository,
    calculator: BudgetCalculator,
    timeProvider: TimeProvider
) : ViewModel() {

    val uiState: StateFlow<InsightsUiState> = combine(
        combine(
            budgetConfigRepository.observeBudgets(),
            categoryRepository.observeCategories(),
            transactionRepository.observeTransactions()
        ) { budgets, categories, transactions -> Triple(budgets, categories, transactions) },
        combine(
            accountRepository.observeAccounts(),
            debtRepository.observeReceivables(),
            debtRepository.observePayables()
        ) { accounts, receivables, payables -> Triple(accounts, receivables, payables) },
        preferencesRepository.observeDisplayCurrencyMode(),
        preferencesRepository.observeExchangeRate()
    ) { (budgets, categories, transactions), (accounts, receivables, payables), displayMode, rateConfig ->
        val today = timeProvider.today()
        val month = YearMonth.from(today)
        val statuses = calculator.computeCategoryStatuses(
            budgets, categories, transactions, month, displayMode, rateConfig
        )
        val summary = calculator.computeMonthlySummary(
            statuses, transactions, month, today, displayMode, rateConfig
        )
        val netPosition = calculator.computeNetPosition(
            accounts = accounts,
            receivables = receivables,
            mode = displayMode,
            rates = rateConfig,
            payables = payables
        )
        val insights = calculator.computeSpendingInsights(
            statuses = statuses,
            summary = summary,
            transactions = transactions,
            targetMonth = month,
            today = today,
            mode = displayMode,
            rates = rateConfig
        )
        val catMap = categories.associateBy { it.id }
        val grouped = transactions
            .filter { it.type == TransactionType.EXPENSE && calculator.yearMonthOf(it.timestampMillis) == month }
            .groupBy { it.categoryId }

        val totalSpent = summary.totalSpentCents.coerceAtLeast(1L)
        val shares = grouped.mapNotNull { (catId, txs) ->
            val sum = txs.sumOf { calculator.amountInDisplayCurrency(it, displayMode, rateConfig) ?: 0L }
            if (sum <= 0L) return@mapNotNull null
            val cat = catId?.let { catMap[it] }
            CategoryShare(
                name = cat?.name ?: "Other",
                amountCents = sum,
                percent = ((sum * 100) / totalSpent).toInt(),
                colorHex = cat?.colorHex ?: 0xFF64748B
            )
        }.sortedByDescending { it.amountCents }

        InsightsUiState(
            summary = summary,
            netPosition = netPosition,
            insights = insights,
            shares = shares,
            displayCurrency = calculator.targetCurrencyFor(displayMode),
            displayMode = displayMode
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InsightsUiState())

    fun onDisplayModeChanged(mode: DisplayCurrencyMode) {
        viewModelScope.launch { preferencesRepository.setDisplayCurrencyMode(mode) }
    }
}
