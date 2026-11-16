package com.budgetflow.app.ui.budgets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgetflow.app.domain.DomainConstants
import com.budgetflow.app.domain.model.Budget
import com.budgetflow.app.domain.model.BudgetHealth
import com.budgetflow.app.domain.model.Category
import com.budgetflow.app.domain.model.CategoryBudgetStatus
import com.budgetflow.app.domain.model.CurrencyCode
import com.budgetflow.app.domain.model.DisplayCurrencyMode
import com.budgetflow.app.domain.model.MonthlySummary
import com.budgetflow.app.domain.model.RolloverMode
import com.budgetflow.app.domain.repository.BudgetConfigRepository
import com.budgetflow.app.domain.repository.CategoryRepository
import com.budgetflow.app.domain.repository.PreferencesRepository
import com.budgetflow.app.domain.repository.TransactionRepository
import com.budgetflow.app.domain.usecase.BudgetCalculator
import com.budgetflow.app.domain.usecase.CurrencyConverter
import com.budgetflow.app.domain.util.TimeProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth
import javax.inject.Inject

data class BudgetsUiState(
    val statuses: List<CategoryBudgetStatus> = emptyList(),
    val categories: List<Category> = emptyList(),
    val archivedCategories: List<Category> = emptyList(),
    val summary: MonthlySummary? = null,
    val onTrackCount: Int = 0,
    val warningCount: Int = 0,
    val overCount: Int = 0,
    val displayCurrency: CurrencyCode = CurrencyCode.USD,
    val displayMode: DisplayCurrencyMode = DisplayCurrencyMode.COMBINED_USD
)

@HiltViewModel
class BudgetsViewModel @Inject constructor(
    private val budgetConfigRepository: BudgetConfigRepository,
    private val categoryRepository: CategoryRepository,
    transactionRepository: TransactionRepository,
    preferencesRepository: PreferencesRepository,
    private val calculator: BudgetCalculator,
    private val timeProvider: TimeProvider
) : ViewModel() {

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    val uiState: StateFlow<BudgetsUiState> = combine(
        budgetConfigRepository.observeBudgets(),
        categoryRepository.observeCategories(includeArchived = true),
        transactionRepository.observeTransactions(),
        preferencesRepository.observeDisplayCurrencyMode(),
        preferencesRepository.observeExchangeRate()
    ) { budgets, allCategories, transactions, displayMode, rateConfig ->
        val categories = allCategories.filter { !it.isArchived }
        val archivedCategories = allCategories.filter { it.isArchived }
        val today = timeProvider.today()
        val month = YearMonth.from(today)
        val statuses = calculator.computeCategoryStatuses(
            budgets = budgets,
            categories = categories,
            transactions = transactions,
            targetMonth = month,
            mode = displayMode,
            rates = rateConfig
        )
        val summary = calculator.computeMonthlySummary(
            statuses = statuses,
            transactions = transactions,
            targetMonth = month,
            today = today,
            mode = displayMode,
            rates = rateConfig
        )
        BudgetsUiState(
            statuses = statuses,
            categories = categories,
            archivedCategories = archivedCategories,
            summary = summary,
            onTrackCount = statuses.count { it.health == BudgetHealth.ON_TRACK },
            warningCount = statuses.count { it.health == BudgetHealth.WARNING },
            overCount = statuses.count { it.health == BudgetHealth.OVER },
            displayCurrency = calculator.targetCurrencyFor(displayMode),
            displayMode = displayMode
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BudgetsUiState())

    fun saveBudget(
        existingBudgetId: Long,
        categoryId: Long,
        currency: CurrencyCode,
        limitInput: String,
        rolloverMode: RolloverMode,
        existingCreatedAtMillis: Long = 0L
    ) {
        val limitCents = CurrencyConverter.parseToCents(limitInput)
        if (limitCents == null || limitCents <= 0L) {
            _messages.tryEmit("Enter a valid budget limit greater than zero.")
            return
        }
        val createdAt = if (existingBudgetId == 0L) {
            timeProvider.currentTimeMillis()
        } else {
            existingCreatedAtMillis
        }
        viewModelScope.launch {
            budgetConfigRepository.saveBudget(
                Budget(
                    id = existingBudgetId,
                    categoryId = categoryId,
                    currency = currency,
                    limitCents = limitCents,
                    rolloverMode = rolloverMode,
                    createdAtMillis = createdAt
                )
            )
        }
    }

    fun deleteBudget(budgetId: Long) {
        viewModelScope.launch { budgetConfigRepository.deleteBudget(budgetId) }
    }

    fun archiveCategory(categoryId: Long) {
        viewModelScope.launch { categoryRepository.setCategoryArchived(categoryId, true) }
    }

    fun restoreCategory(categoryId: Long) {
        viewModelScope.launch { categoryRepository.setCategoryArchived(categoryId, false) }
    }

    fun renameCategory(category: Category, newName: String) {
        if (newName.isBlank()) {
            _messages.tryEmit("Category name cannot be empty.")
            return
        }
        viewModelScope.launch {
            categoryRepository.saveCategory(category.copy(name = newName.trim()))
        }
    }

    fun createCategory(name: String, colorHex: Long, parentCategoryId: Long? = null) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            _messages.tryEmit("Category name cannot be empty.")
            return
        }
        viewModelScope.launch {
            categoryRepository.saveCategory(
                Category(
                    name = trimmed,
                    iconName = DomainConstants.DEFAULT_CATEGORY_ICON,
                    colorHex = colorHex,
                    parentCategoryId = parentCategoryId
                )
            )
        }
    }
}
