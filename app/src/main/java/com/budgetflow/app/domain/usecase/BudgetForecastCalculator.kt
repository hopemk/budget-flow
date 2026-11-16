package com.budgetflow.app.domain.usecase

import com.budgetflow.app.domain.model.Account
import com.budgetflow.app.domain.model.Category
import com.budgetflow.app.domain.model.CurrencyCode
import com.budgetflow.app.domain.model.DisplayCurrencyMode
import com.budgetflow.app.domain.model.ExchangeRateConfig
import com.budgetflow.app.domain.model.ForecastBudgetAdjustment
import com.budgetflow.app.domain.model.ForecastExpenseItem
import com.budgetflow.app.domain.model.ForecastExpenseSource
import com.budgetflow.app.domain.model.ForecastIncomeItem
import com.budgetflow.app.domain.model.ForecastMonthlyProjection
import com.budgetflow.app.domain.model.ForecastSummary
import com.budgetflow.app.domain.model.RolloverMode
import com.budgetflow.app.domain.model.Transaction
import com.budgetflow.app.domain.model.TransactionType
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.max

/**
 * Pure, non-destructive calculator for the Budget Forecast cash-flow & what-if budget simulator.
 */
class BudgetForecastCalculator(
    private val budgetCalculator: BudgetCalculator = BudgetCalculator(),
    private val zoneId: ZoneId = ZoneId.systemDefault()
) {

    fun computeForecast(
        accounts: List<Account>,
        categories: List<Category>,
        transactionsThisMonth: List<Transaction>,
        targetMonth: YearMonth,
        mode: DisplayCurrencyMode,
        rates: ExchangeRateConfig,
        expectedIncomeItems: List<ForecastIncomeItem>,
        expectedExpenseItems: List<ForecastExpenseItem>,
        budgetAdjustments: List<ForecastBudgetAdjustment>,
        includeLoggedThisMonth: Boolean = true,
        horizonMonths: Int = 1
    ): ForecastSummary {
        val targetCurrency = mode.targetCurrency
        val childIdsByParent = categories
            .filter { it.parentCategoryId != null && !it.isArchived }
            .groupBy { it.parentCategoryId!! }

        val currentWalletTotalCents = budgetCalculator
            .filterByDisplayMode(accounts.filter { !it.isArchived }, mode) { it.currency }
            .sumOf { convertToTarget(it.balanceCents, it.currency, targetCurrency, rates) }

        val monthTxs = transactionsThisMonth.filter {
            Instant.ofEpochMilli(it.timestampMillis).atZone(zoneId).let { zdt ->
                YearMonth.of(zdt.year, zdt.month) == targetMonth
            }
        }
        val loggedIncomeCents = monthTxs
            .filter { it.type == TransactionType.INCOME }
            .sumOf { budgetCalculator.amountInDisplayCurrency(it, mode, rates) ?: 0L }
        val loggedExpensesCents = monthTxs
            .filter { it.type == TransactionType.EXPENSE }
            .sumOf { budgetCalculator.amountInDisplayCurrency(it, mode, rates) ?: 0L }

        val remainingIncomeCents = budgetCalculator
            .filterByDisplayMode(expectedIncomeItems.filter { it.isIncluded && it.amountCents > 0L }, mode) { it.currency }
            .sumOf { convertToTarget(it.amountCents, it.currency, targetCurrency, rates) }

        val includedExpenses = budgetCalculator
            .filterByDisplayMode(expectedExpenseItems.filter { it.isIncluded && it.amountCents > 0L }, mode) { it.currency }
        val remainingExpensesCents = includedExpenses
            .sumOf { convertToTarget(it.amountCents, it.currency, targetCurrency, rates) }

        val totalExpectedIncomeCents = (if (includeLoggedThisMonth) loggedIncomeCents else 0L) + remainingIncomeCents
        val totalExpectedExpensesCents = (if (includeLoggedThisMonth) loggedExpensesCents else 0L) + remainingExpensesCents
        val projectedNetCashFlowCents = totalExpectedIncomeCents - totalExpectedExpensesCents
        val projectedMonthEndBalanceCents = currentWalletTotalCents + remainingIncomeCents - remainingExpensesCents

        val shortfallCents = maxOf(
            0L,
            totalExpectedExpensesCents - totalExpectedIncomeCents,
            -projectedMonthEndBalanceCents
        )

        val filteredBudgets = budgetCalculator.filterByDisplayMode(budgetAdjustments, mode) { it.currency }
        val atRiskCategories = filteredBudgets.filter { adj ->
            val matchingIds = setOf(adj.categoryId) +
                childIdsByParent[adj.categoryId].orEmpty().map { it.id }.toSet()
            val expectedCatSpend = includedExpenses
                .filter { it.categoryId != null && it.categoryId in matchingIds }
                .sumOf { convertToTarget(it.amountCents, it.currency, adj.currency, rates) }
            val projectedTotalCatSpend = adj.spentSoFarCents + expectedCatSpend
            adj.simulatedLimitCents >= 0L && projectedTotalCatSpend > adj.simulatedLimitCents
        }

        val safeHorizon = horizonMonths.coerceIn(1, 12)
        val projections = buildMonthlyProjections(
            startMonth = targetMonth,
            horizonMonths = safeHorizon,
            currentMonthIncomeCents = totalExpectedIncomeCents,
            currentMonthExpensesCents = totalExpectedExpensesCents,
            currentMonthEndBalanceCents = projectedMonthEndBalanceCents,
            recurringIncomeCents = remainingIncomeCents,
            includedExpenses = includedExpenses,
            budgetAdjustments = filteredBudgets,
            targetCurrency = targetCurrency,
            rates = rates
        )

        return ForecastSummary(
            targetCurrency = targetCurrency,
            currentWalletTotalCents = currentWalletTotalCents,
            loggedIncomeThisMonthCents = loggedIncomeCents,
            loggedExpensesThisMonthCents = loggedExpensesCents,
            totalExpectedIncomeCents = totalExpectedIncomeCents,
            totalExpectedExpensesCents = totalExpectedExpensesCents,
            projectedNetCashFlowCents = projectedNetCashFlowCents,
            projectedMonthEndBalanceCents = projectedMonthEndBalanceCents,
            shortfallCents = shortfallCents,
            atRiskCategories = atRiskCategories,
            monthlyProjections = projections
        )
    }

    private fun buildMonthlyProjections(
        startMonth: YearMonth,
        horizonMonths: Int,
        currentMonthIncomeCents: Long,
        currentMonthExpensesCents: Long,
        currentMonthEndBalanceCents: Long,
        recurringIncomeCents: Long,
        includedExpenses: List<ForecastExpenseItem>,
        budgetAdjustments: List<ForecastBudgetAdjustment>,
        targetCurrency: CurrencyCode,
        rates: ExchangeRateConfig
    ): List<ForecastMonthlyProjection> {
        val result = mutableListOf(
            ForecastMonthlyProjection(
                month = startMonth,
                expectedIncomeCents = currentMonthIncomeCents,
                expectedExpensesCents = currentMonthExpensesCents,
                netCashFlowCents = currentMonthIncomeCents - currentMonthExpensesCents,
                projectedEndBalanceCents = currentMonthEndBalanceCents
            )
        )
        if (horizonMonths <= 1) return result

        val recurringBillsCents = includedExpenses
            .filter { it.sourceType == ForecastExpenseSource.RECURRING_BILL }
            .sumOf { convertToTarget(it.amountCents, it.currency, targetCurrency, rates) }

        var runningBalance = currentMonthEndBalanceCents
        // Track rollover carryover per category across projected future months
        val carryovers = budgetAdjustments.associate { adj ->
            val baseLimit = convertToTarget(adj.simulatedLimitCents, adj.currency, targetCurrency, rates)
            val spent = convertToTarget(adj.spentSoFarCents, adj.currency, targetCurrency, rates)
            adj.categoryId to computeCarryover(baseLimit, spent, adj.rolloverMode)
        }.toMutableMap()

        for (offset in 1 until horizonMonths) {
            val month = startMonth.plusMonths(offset.toLong())
            val effectiveBudgetSpendCents = budgetAdjustments.sumOf { adj ->
                val baseLimit = convertToTarget(adj.simulatedLimitCents, adj.currency, targetCurrency, rates)
                val carryover = carryovers[adj.categoryId] ?: 0L
                max(0L, baseLimit + carryover)
            }
            val monthExpenses = if (effectiveBudgetSpendCents > 0L) {
                recurringBillsCents + effectiveBudgetSpendCents
            } else {
                recurringBillsCents
            }
            val net = recurringIncomeCents - monthExpenses
            runningBalance += net

            // Assuming future months spend their base simulated limit, rollover resets carryover to 0 unless adjusted
            budgetAdjustments.forEach { adj ->
                val baseLimit = convertToTarget(adj.simulatedLimitCents, adj.currency, targetCurrency, rates)
                val prevCarry = carryovers[adj.categoryId] ?: 0L
                val effective = max(0L, baseLimit + prevCarry)
                carryovers[adj.categoryId] = computeCarryover(effective, baseLimit, adj.rolloverMode)
            }

            result += ForecastMonthlyProjection(
                month = month,
                expectedIncomeCents = recurringIncomeCents,
                expectedExpensesCents = monthExpenses,
                netCashFlowCents = net,
                projectedEndBalanceCents = runningBalance
            )
        }
        return result
    }

    private fun computeCarryover(limitCents: Long, spentCents: Long, mode: RolloverMode): Long {
        val diff = limitCents - spentCents
        return when (mode) {
            RolloverMode.RESET_MONTHLY -> 0L
            RolloverMode.ROLLOVER_SURPLUS -> max(0L, diff)
            RolloverMode.ROLLOVER_ALL -> diff
        }
    }

    private fun convertToTarget(
        amountCents: Long,
        from: CurrencyCode,
        to: CurrencyCode,
        rates: ExchangeRateConfig
    ): Long = CurrencyConverter.convert(amountCents, from, to, rates)
}
