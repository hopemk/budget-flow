package com.budgetflow.app.domain.model

import java.time.YearMonth

/** Source classification for hypothetical or pre-loaded expected expenses in Budget Forecast. */
enum class ForecastExpenseSource(val displayName: String) {
    RECURRING_BILL("Bill"),
    SHOPPING_LIST("Shopping List"),
    ONE_OFF("One-Off")
}

/** Expected income entry (salary or additional expected income) in the in-memory forecast. */
data class ForecastIncomeItem(
    val id: String,
    val label: String,
    val amountCents: Long,
    val currency: CurrencyCode,
    val isIncluded: Boolean = true
)

/** Expected expense entry (bill, shopping list, or hypothetical one-off) in the in-memory forecast. */
data class ForecastExpenseItem(
    val id: String,
    val label: String,
    val amountCents: Long,
    val currency: CurrencyCode,
    val sourceType: ForecastExpenseSource,
    val categoryId: Long? = null,
    val isIncluded: Boolean = true
)

/** What-if monthly limit adjustment for an existing category budget. */
data class ForecastBudgetAdjustment(
    val categoryId: Long,
    val categoryName: String,
    val baseLimitCents: Long,
    val simulatedLimitCents: Long,
    val spentSoFarCents: Long,
    val currency: CurrencyCode,
    val rolloverMode: RolloverMode = RolloverMode.RESET_MONTHLY
) {
    val isModified: Boolean get() = simulatedLimitCents != baseLimitCents
}

/** Single month projection in a 1M / 3M / 6M forecast horizon. */
data class ForecastMonthlyProjection(
    val month: YearMonth,
    val expectedIncomeCents: Long,
    val expectedExpensesCents: Long,
    val netCashFlowCents: Long,
    val projectedEndBalanceCents: Long
)

/** Computed summary of a non-destructive Budget Forecast scenario. */
data class ForecastSummary(
    val targetCurrency: CurrencyCode,
    val currentWalletTotalCents: Long,
    val loggedIncomeThisMonthCents: Long,
    val loggedExpensesThisMonthCents: Long,
    val totalExpectedIncomeCents: Long,
    val totalExpectedExpensesCents: Long,
    val projectedNetCashFlowCents: Long,
    val projectedMonthEndBalanceCents: Long,
    val shortfallCents: Long,
    val atRiskCategories: List<ForecastBudgetAdjustment>,
    val monthlyProjections: List<ForecastMonthlyProjection>
)
