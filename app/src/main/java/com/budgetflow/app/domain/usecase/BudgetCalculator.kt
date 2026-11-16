package com.budgetflow.app.domain.usecase

import com.budgetflow.app.domain.DomainConstants
import com.budgetflow.app.domain.model.Account
import com.budgetflow.app.domain.model.AccountType
import com.budgetflow.app.domain.model.Budget
import com.budgetflow.app.domain.model.BudgetHealth
import com.budgetflow.app.domain.model.BurnRateProjection
import com.budgetflow.app.domain.model.Category
import com.budgetflow.app.domain.model.CategoryBudgetStatus
import com.budgetflow.app.domain.model.CurrencyCode
import com.budgetflow.app.domain.model.CurrencySpendEntry
import com.budgetflow.app.domain.model.CurrencySpendSplit
import com.budgetflow.app.domain.model.DisplayCurrencyMode
import com.budgetflow.app.domain.model.ExchangeRateConfig
import com.budgetflow.app.domain.model.MonthOverMonthComparison
import com.budgetflow.app.domain.model.MonthlySummary
import com.budgetflow.app.domain.model.NetPosition
import com.budgetflow.app.domain.model.Payable
import com.budgetflow.app.domain.model.Receivable
import com.budgetflow.app.domain.model.RolloverMode
import com.budgetflow.app.domain.model.SpendingInsights
import com.budgetflow.app.domain.model.TopSpendEntry
import com.budgetflow.app.domain.model.Transaction
import com.budgetflow.app.domain.model.TransactionType
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.min

/** Pure business logic for budgets, flexible rollover, safe-to-spend, and pace checks. */
class BudgetCalculator(
    private val zoneId: ZoneId = ZoneId.systemDefault()
) {

    fun targetCurrencyFor(mode: DisplayCurrencyMode): CurrencyCode = mode.targetCurrency

    /** Filters any currency-tagged list according to [mode]. */
    fun <T> filterByDisplayMode(
        items: List<T>,
        mode: DisplayCurrencyMode,
        currencySelector: (T) -> CurrencyCode
    ): List<T> = if (mode.includeAllCurrencies) {
        items
    } else {
        items.filter { currencySelector(it) == mode.targetCurrency }
    }

    /** Filters transactions according to [mode] and converts amounts to the target currency. */
    fun amountInDisplayCurrency(
        tx: Transaction,
        mode: DisplayCurrencyMode,
        rates: ExchangeRateConfig
    ): Long? {
        if (!mode.includeAllCurrencies && tx.currency != mode.targetCurrency) return null
        if (tx.currency == mode.targetCurrency) return tx.amountCents
        val txRates = effectiveRatesForTransaction(tx, rates)
        return CurrencyConverter.convert(
            amountCents = tx.amountCents,
            from = tx.currency,
            to = mode.targetCurrency,
            rates = txRates
        )
    }

    fun amountInDisplayCurrency(
        tx: Transaction,
        mode: DisplayCurrencyMode,
        currentRate: BigDecimal
    ): Long? = amountInDisplayCurrency(tx, mode, ratesFromZwgRate(currentRate))

    /**
     * Computes each category's budget status for [targetMonth], applying its [RolloverMode]
     * from the immediately preceding month.
     */
    fun computeCategoryStatuses(
        budgets: List<Budget>,
        categories: List<Category>,
        transactions: List<Transaction>,
        targetMonth: YearMonth,
        mode: DisplayCurrencyMode,
        rates: ExchangeRateConfig
    ): List<CategoryBudgetStatus> {
        val categoryMap = categories.associateBy { it.id }
        val childIdsByParent = categories
            .filter { it.parentCategoryId != null && !it.isArchived }
            .groupBy { it.parentCategoryId!! }
        val targetCurrency = targetCurrencyFor(mode)
        val prevMonth = targetMonth.minusMonths(1)
        val hasPriorActivity = transactions.any { yearMonthOf(it.timestampMillis) < targetMonth }

        return filterByDisplayMode(budgets, mode) { it.currency }.mapNotNull { budget ->
            val category = categoryMap[budget.categoryId] ?: return@mapNotNull null
            if (category.isArchived) return@mapNotNull null

            val matchingCategoryIds = setOf(budget.categoryId) +
                childIdsByParent[budget.categoryId].orEmpty().map { it.id }.toSet()

            val limitInDisplay = CurrencyConverter.convert(
                amountCents = budget.limitCents,
                from = budget.currency,
                to = targetCurrency,
                rates = rates
            )

            val budgetStartMonth = if (budget.createdAtMillis > 0L) yearMonthOf(budget.createdAtMillis) else null
            val shouldApplyRollover = if (budgetStartMonth != null) {
                targetMonth.isAfter(budgetStartMonth)
            } else {
                hasPriorActivity
            }
            val carryover = if (shouldApplyRollover) {
                val prevSpent = sumCategoryExpenses(transactions, matchingCategoryIds, prevMonth, mode, rates)
                computeCarryover(limitInDisplay, prevSpent, budget.rolloverMode)
            } else {
                0L
            }
            val effectiveLimit = max(0L, limitInDisplay + carryover)
            val spent = sumCategoryExpenses(transactions, matchingCategoryIds, targetMonth, mode, rates)
            val remaining = effectiveLimit - spent
            val pct = if (effectiveLimit > 0L) ((spent * 100) / effectiveLimit).toInt() else 0
            val health = when {
                effectiveLimit > 0L && spent > effectiveLimit -> BudgetHealth.OVER
                pct >= budget.alertThresholdPct -> BudgetHealth.WARNING
                else -> BudgetHealth.ON_TRACK
            }

            CategoryBudgetStatus(
                budget = budget,
                category = category,
                effectiveLimitCents = effectiveLimit,
                spentCents = spent,
                carryoverCents = carryover,
                remainingCents = remaining,
                percentUsed = pct,
                health = health
            )
        }
    }

    fun computeCategoryStatuses(
        budgets: List<Budget>,
        categories: List<Category>,
        transactions: List<Transaction>,
        targetMonth: YearMonth,
        mode: DisplayCurrencyMode,
        currentRate: BigDecimal
    ): List<CategoryBudgetStatus> = computeCategoryStatuses(
        budgets = budgets,
        categories = categories,
        transactions = transactions,
        targetMonth = targetMonth,
        mode = mode,
        rates = ratesFromZwgRate(currentRate)
    )

    fun computeMonthlySummary(
        statuses: List<CategoryBudgetStatus>,
        transactions: List<Transaction>,
        targetMonth: YearMonth,
        today: LocalDate,
        mode: DisplayCurrencyMode,
        rates: ExchangeRateConfig
    ): MonthlySummary {
        val targetCurrency = targetCurrencyFor(mode)
        val monthTxs = transactions.filter { yearMonthOf(it.timestampMillis) == targetMonth }

        val totalSpent = monthTxs
            .filter { it.type == TransactionType.EXPENSE }
            .sumOf { amountInDisplayCurrency(it, mode, rates) ?: 0L }

        val totalIncome = monthTxs
            .filter { it.type == TransactionType.INCOME }
            .sumOf { amountInDisplayCurrency(it, mode, rates) ?: 0L }

        val totalBudget = statuses.sumOf { it.effectiveLimitCents }
        val remaining = totalBudget - totalSpent

        val daysInMonth = targetMonth.lengthOfMonth()
        val daysLeft = when {
            YearMonth.from(today) == targetMonth -> max(1, daysInMonth - today.dayOfMonth + 1)
            YearMonth.from(today).isBefore(targetMonth) -> daysInMonth
            else -> 1
        }
        val safePerDay = if (remaining > 0L) remaining / daysLeft else 0L

        val elapsedDays = when {
            YearMonth.from(today) == targetMonth -> today.dayOfMonth
            YearMonth.from(today).isAfter(targetMonth) -> daysInMonth
            else -> 0
        }
        val monthProgressPct = min(100, (elapsedDays * 100) / daysInMonth)
        val budgetUsedPct = if (totalBudget > 0L) ((totalSpent * 100) / totalBudget).toInt() else 0

        return MonthlySummary(
            month = targetMonth,
            displayCurrency = targetCurrency,
            totalBudgetCents = totalBudget,
            totalSpentCents = totalSpent,
            totalIncomeCents = totalIncome,
            remainingCents = remaining,
            safePerDayCents = safePerDay,
            monthProgressPct = monthProgressPct,
            budgetUsedPct = budgetUsedPct
        )
    }

    fun computeMonthlySummary(
        statuses: List<CategoryBudgetStatus>,
        transactions: List<Transaction>,
        targetMonth: YearMonth,
        today: LocalDate,
        mode: DisplayCurrencyMode,
        currentRate: BigDecimal
    ): MonthlySummary = computeMonthlySummary(
        statuses = statuses,
        transactions = transactions,
        targetMonth = targetMonth,
        today = today,
        mode = mode,
        rates = ratesFromZwgRate(currentRate)
    )

    /**
     * Combines all wallet balances, unsettled receivables, and unsettled payables into a unified
     * net position respecting [mode].
     */
    fun computeNetPosition(
        accounts: List<Account>,
        receivables: List<Receivable>,
        mode: DisplayCurrencyMode,
        rates: ExchangeRateConfig,
        payables: List<Payable> = emptyList()
    ): NetPosition {
        val targetCurrency = targetCurrencyFor(mode)
        val walletsByCurrency = accounts
            .groupBy { it.currency }
            .mapValues { (_, list) -> list.sumOf { it.balanceCents } }

        val filteredAccounts = filterByDisplayMode(accounts, mode) { it.currency }
        val operatingTotal = filteredAccounts
            .filter { it.type != AccountType.SAVINGS }
            .sumOf { CurrencyConverter.convert(it.balanceCents, it.currency, targetCurrency, rates) }
        val savingsTotal = filteredAccounts
            .filter { it.type == AccountType.SAVINGS }
            .sumOf { CurrencyConverter.convert(it.balanceCents, it.currency, targetCurrency, rates) }
        val walletsTotal = operatingTotal + savingsTotal

        val unsettledReceivables = receivables.filter { !it.isSettled && it.remainingCents > 0L }
        val filteredReceivables = filterByDisplayMode(unsettledReceivables, mode) { it.currency }
        val receivablesTotal = filteredReceivables.sumOf {
            CurrencyConverter.convert(it.remainingCents, it.currency, targetCurrency, rates)
        }

        val unsettledPayables = payables.filter { !it.isSettled && it.remainingCents > 0L }
        val filteredPayables = filterByDisplayMode(unsettledPayables, mode) { it.currency }
        val payablesTotal = filteredPayables.sumOf { payable ->
            CurrencyConverter.convert(payable.remainingCents, payable.currency, targetCurrency, rates)
        }

        return NetPosition(
            displayCurrency = targetCurrency,
            walletsTotalCents = walletsTotal,
            operatingTotalCents = operatingTotal,
            savingsTotalCents = savingsTotal,
            walletsByCurrency = walletsByCurrency,
            receivablesTotalCents = receivablesTotal,
            unsettledReceivablesCount = filteredReceivables.size,
            payablesTotalCents = payablesTotal,
            unsettledPayablesCount = filteredPayables.size,
            netPositionCents = walletsTotal + receivablesTotal - payablesTotal
        )
    }

    fun computeNetPosition(
        accounts: List<Account>,
        receivables: List<Receivable>,
        mode: DisplayCurrencyMode,
        currentRate: BigDecimal,
        payables: List<Payable> = emptyList()
    ): NetPosition = computeNetPosition(
        accounts = accounts,
        receivables = receivables,
        mode = mode,
        rates = ratesFromZwgRate(currentRate),
        payables = payables
    )

    /**
     * Computes month-over-month comparison, daily burn-rate projection, top merchants/items,
     * multi-currency spend split, and budget health counts.
     */
    fun computeSpendingInsights(
        statuses: List<CategoryBudgetStatus>,
        summary: MonthlySummary,
        transactions: List<Transaction>,
        targetMonth: YearMonth,
        today: LocalDate,
        mode: DisplayCurrencyMode,
        rates: ExchangeRateConfig
    ): SpendingInsights {
        val targetCurrency = targetCurrencyFor(mode)
        val prevMonth = targetMonth.minusMonths(1)

        val monthExpenses = transactions.filter {
            it.type == TransactionType.EXPENSE && yearMonthOf(it.timestampMillis) == targetMonth
        }
        val prevExpenses = transactions.filter {
            it.type == TransactionType.EXPENSE && yearMonthOf(it.timestampMillis) == prevMonth
        }

        val currentSpent = monthExpenses.sumOf { amountInDisplayCurrency(it, mode, rates) ?: 0L }
        val prevSpent = prevExpenses.sumOf { amountInDisplayCurrency(it, mode, rates) ?: 0L }
        val deltaCents = currentSpent - prevSpent
        val deltaPercent = if (prevSpent > 0L) ((deltaCents * 100) / prevSpent).toInt() else null

        val daysInMonth = targetMonth.lengthOfMonth()
        val todayMonth = YearMonth.from(today)
        val elapsedDays = when {
            todayMonth == targetMonth -> today.dayOfMonth.coerceAtLeast(1)
            todayMonth.isAfter(targetMonth) -> daysInMonth
            else -> 0
        }
        val dailyBurnCents = if (elapsedDays > 0) currentSpent / elapsedDays else 0L
        val projectedMonthSpendCents = if (elapsedDays > 0) {
            BigDecimal.valueOf(currentSpent)
                .multiply(BigDecimal.valueOf(daysInMonth.toLong()))
                .divide(BigDecimal.valueOf(elapsedDays.toLong()), 0, RoundingMode.HALF_UP)
                .toLong()
        } else 0L
        val projectedRemainingCents = summary.totalBudgetCents - projectedMonthSpendCents

        val topMerchants = aggregateTopEntries(
            monthExpenses.mapNotNull { tx ->
                val amount = amountInDisplayCurrency(tx, mode, rates) ?: return@mapNotNull null
                val name = tx.merchant.trim()
                if (name.isEmpty()) null else name to amount
            }
        )

        val topItems = aggregateTopEntries(
            monthExpenses.flatMap { tx ->
                if (amountInDisplayCurrency(tx, mode, rates) == null) return@flatMap emptyList()
                val txRates = effectiveRatesForTransaction(tx, rates)
                tx.items.mapNotNull { item ->
                    val name = item.name.trim()
                    if (name.isEmpty()) return@mapNotNull null
                    val converted = CurrencyConverter.convert(item.amountCents, tx.currency, targetCurrency, txRates)
                    name to converted
                }
            }
        )

        val currencySplit = computeCurrencySplit(monthExpenses, rates)

        return SpendingInsights(
            monthOverMonth = MonthOverMonthComparison(
                currentMonthSpentCents = currentSpent,
                previousMonthSpentCents = prevSpent,
                deltaCents = deltaCents,
                deltaPercent = deltaPercent
            ),
            projection = BurnRateProjection(
                dailyBurnCents = dailyBurnCents,
                projectedMonthSpendCents = projectedMonthSpendCents,
                projectedRemainingCents = projectedRemainingCents,
                elapsedDays = elapsedDays,
                daysInMonth = daysInMonth
            ),
            topMerchants = topMerchants,
            topItems = topItems,
            currencySplit = currencySplit,
            onTrackBudgets = statuses.count { it.health == BudgetHealth.ON_TRACK },
            warningBudgets = statuses.count { it.health == BudgetHealth.WARNING },
            overBudgets = statuses.count { it.health == BudgetHealth.OVER }
        )
    }

    fun computeSpendingInsights(
        statuses: List<CategoryBudgetStatus>,
        summary: MonthlySummary,
        transactions: List<Transaction>,
        targetMonth: YearMonth,
        today: LocalDate,
        mode: DisplayCurrencyMode,
        currentRate: BigDecimal
    ): SpendingInsights = computeSpendingInsights(
        statuses = statuses,
        summary = summary,
        transactions = transactions,
        targetMonth = targetMonth,
        today = today,
        mode = mode,
        rates = ratesFromZwgRate(currentRate)
    )

    private fun computeCurrencySplit(
        monthExpenses: List<Transaction>,
        rates: ExchangeRateConfig
    ): CurrencySpendSplit {
        if (monthExpenses.isEmpty()) return CurrencySpendSplit()

        val byCurrency = monthExpenses.groupBy { it.currency }
        val spentByCurrency = byCurrency.mapValues { (_, txs) -> txs.sumOf { it.amountCents } }
            .filterValues { it > 0L }
        if (spentByCurrency.isEmpty()) return CurrencySpendSplit()

        val normalizedUsdByCurrency = byCurrency
            .filterKeys { it in spentByCurrency }
            .mapValues { (currency, txs) ->
                txs.sumOf { tx ->
                    val txRates = effectiveRatesForTransaction(tx, rates)
                    CurrencyConverter.convert(tx.amountCents, currency, CurrencyCode.USD, txRates)
                }
            }
        val totalUsd = normalizedUsdByCurrency.values.sum()

        val entries = if (totalUsd > 0L) {
            var remainingPct = 100
            val sorted = spentByCurrency.entries.sortedByDescending { normalizedUsdByCurrency[it.key] ?: 0L }
            sorted.mapIndexed { index, (currency, spent) ->
                val pct = if (index == sorted.lastIndex) {
                    remainingPct.coerceIn(0, 100)
                } else {
                    val raw = (((normalizedUsdByCurrency[currency] ?: 0L) * 100) / totalUsd).toInt().coerceIn(0, remainingPct)
                    remainingPct -= raw
                    raw
                }
                CurrencySpendEntry(currency = currency, spentCents = spent, percent = pct)
            }
        } else {
            // Tiny non-USD amounts that round to 0 USD cents: split by raw minor units
            val totalRaw = spentByCurrency.values.sum().coerceAtLeast(1L)
            var remainingPct = 100
            val sorted = spentByCurrency.entries.sortedByDescending { it.value }
            sorted.mapIndexed { index, (currency, spent) ->
                val pct = if (index == sorted.lastIndex) {
                    remainingPct.coerceIn(0, 100)
                } else {
                    val raw = ((spent * 100) / totalRaw).toInt().coerceIn(0, remainingPct)
                    remainingPct -= raw
                    raw
                }
                CurrencySpendEntry(currency = currency, spentCents = spent, percent = pct)
            }
        }

        return CurrencySpendSplit(entries = entries)
    }

    private fun effectiveRatesForTransaction(
        tx: Transaction,
        currentRates: ExchangeRateConfig
    ): ExchangeRateConfig {
        if (tx.exchangeRateUsed <= BigDecimal.ZERO) return currentRates
        return if (tx.currency == CurrencyCode.USD) {
            if (tx.exchangeRateUsed > BigDecimal.ONE) {
                currentRates.withRate(CurrencyCode.ZWG, tx.exchangeRateUsed)
            } else {
                currentRates
            }
        } else {
            currentRates.withRate(tx.currency, tx.exchangeRateUsed)
        }
    }

    private fun ratesFromZwgRate(zwgPerUsd: BigDecimal): ExchangeRateConfig {
        val safeRate = if (zwgPerUsd > BigDecimal.ZERO) zwgPerUsd else DomainConstants.DEFAULT_EXCHANGE_RATE
        return ExchangeRateConfig(
            ratesPerUsd = DomainConstants.DEFAULT_RATES_PER_USD + (CurrencyCode.ZWG to safeRate)
        )
    }

    private fun aggregateTopEntries(pairs: List<Pair<String, Long>>, limit: Int = 5): List<TopSpendEntry> {
        data class Acc(val displayName: String, var amountCents: Long, var count: Int)
        val map = linkedMapOf<String, Acc>()
        for ((name, amount) in pairs) {
            val key = name.lowercase()
            val existing = map[key]
            if (existing == null) {
                map[key] = Acc(displayName = name, amountCents = amount, count = 1)
            } else {
                existing.amountCents += amount
                existing.count += 1
            }
        }
        return map.values
            .sortedByDescending { it.amountCents }
            .take(limit)
            .map { TopSpendEntry(name = it.displayName, amountCents = it.amountCents, count = it.count) }
    }

    private fun computeCarryover(limitCents: Long, prevSpentCents: Long, mode: RolloverMode): Long {
        val diff = limitCents - prevSpentCents
        return when (mode) {
            RolloverMode.RESET_MONTHLY -> 0L
            RolloverMode.ROLLOVER_SURPLUS -> max(0L, diff)
            RolloverMode.ROLLOVER_ALL -> diff
        }
    }

    private fun sumCategoryExpenses(
        transactions: List<Transaction>,
        categoryIds: Set<Long>,
        month: YearMonth,
        mode: DisplayCurrencyMode,
        rates: ExchangeRateConfig
    ): Long = transactions
        .asSequence()
        .filter { it.type == TransactionType.EXPENSE && it.categoryId != null && it.categoryId in categoryIds }
        .filter { yearMonthOf(it.timestampMillis) == month }
        .mapNotNull { amountInDisplayCurrency(it, mode, rates) }
        .sum()

    fun yearMonthOf(timestampMillis: Long): YearMonth =
        YearMonth.from(Instant.ofEpochMilli(timestampMillis).atZone(zoneId).toLocalDate())
}
