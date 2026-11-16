package com.budgetflow.app.domain

import com.budgetflow.app.domain.model.Account
import com.budgetflow.app.domain.model.AccountType
import com.budgetflow.app.domain.model.Budget
import com.budgetflow.app.domain.model.Category
import com.budgetflow.app.domain.model.CurrencyCode
import com.budgetflow.app.domain.model.DisplayCurrencyMode
import com.budgetflow.app.domain.model.LineItem
import com.budgetflow.app.domain.model.Receivable
import com.budgetflow.app.domain.model.RolloverMode
import com.budgetflow.app.domain.model.Transaction
import com.budgetflow.app.domain.model.TransactionType
import com.budgetflow.app.domain.usecase.BudgetCalculator
import com.budgetflow.app.domain.usecase.CurrencyConverter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

class BudgetCalculatorTest {

    private val calculator = BudgetCalculator(ZoneOffset.UTC)

    @Test
    fun `currency converter converts USD to ZWG and back`() {
        val rate = BigDecimal("26.50")
        val zwg = CurrencyConverter.convert(1000L, CurrencyCode.USD, CurrencyCode.ZWG, rate)
        assertEquals(26500L, zwg)

        val usd = CurrencyConverter.convert(26500L, CurrencyCode.ZWG, CurrencyCode.USD, rate)
        assertEquals(1000L, usd)
    }

    @Test
    fun `rollover surplus carries unspent balance forward`() {
        val category = Category(id = 1L, name = "Groceries", iconName = "cart", colorHex = 0xFF059669)
        val budget = Budget(
            id = 1L,
            categoryId = 1L,
            currency = CurrencyCode.USD,
            limitCents = 20000L,
            rolloverMode = RolloverMode.ROLLOVER_SURPLUS
        )
        val octTx = Transaction(
            id = 1L,
            accountId = 1L,
            categoryId = 1L,
            type = TransactionType.EXPENSE,
            currency = CurrencyCode.USD,
            amountCents = 15000L,
            exchangeRateUsed = BigDecimal("26.50"),
            merchant = "Store",
            timestampMillis = LocalDate.of(2026, 10, 15).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )

        val statuses = calculator.computeCategoryStatuses(
            budgets = listOf(budget),
            categories = listOf(category),
            transactions = listOf(octTx),
            targetMonth = YearMonth.of(2026, 11),
            mode = DisplayCurrencyMode.COMBINED_USD,
            currentRate = BigDecimal("26.50")
        )

        assertEquals(1, statuses.size)
        assertEquals(5000L, statuses.first().carryoverCents)
        assertEquals(25000L, statuses.first().effectiveLimitCents)
    }

    @Test
    fun `rollover all carries deficit forward`() {
        val category = Category(id = 1L, name = "Dining", iconName = "food", colorHex = 0xFFF59E0B)
        val budget = Budget(
            id = 1L,
            categoryId = 1L,
            currency = CurrencyCode.USD,
            limitCents = 10000L,
            rolloverMode = RolloverMode.ROLLOVER_ALL
        )
        val octTx = Transaction(
            id = 1L,
            accountId = 1L,
            categoryId = 1L,
            type = TransactionType.EXPENSE,
            currency = CurrencyCode.USD,
            amountCents = 13000L,
            exchangeRateUsed = BigDecimal("26.50"),
            merchant = "Cafe",
            timestampMillis = LocalDate.of(2026, 10, 20).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )

        val statuses = calculator.computeCategoryStatuses(
            budgets = listOf(budget),
            categories = listOf(category),
            transactions = listOf(octTx),
            targetMonth = YearMonth.of(2026, 11),
            mode = DisplayCurrencyMode.COMBINED_USD,
            currentRate = BigDecimal("26.50")
        )

        assertEquals(-3000L, statuses.first().carryoverCents)
        assertEquals(7000L, statuses.first().effectiveLimitCents)
    }

    @Test
    fun `newly created rollover budget does not invent prior month surplus`() {
        val category = Category(id = 1L, name = "Fuel", iconName = "local_gas_station", colorHex = 0xFF3B82F6)
        val createdInNov = LocalDate.of(2026, 11, 15).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val budget = Budget(
            id = 1L,
            categoryId = 1L,
            currency = CurrencyCode.USD,
            limitCents = 10000L,
            rolloverMode = RolloverMode.ROLLOVER_ALL,
            createdAtMillis = createdInNov
        )

        val statuses = calculator.computeCategoryStatuses(
            budgets = listOf(budget),
            categories = listOf(category),
            transactions = emptyList(),
            targetMonth = YearMonth.of(2026, 11),
            mode = DisplayCurrencyMode.COMBINED_USD,
            currentRate = BigDecimal("26.50")
        )

        assertEquals(0L, statuses.first().carryoverCents)
        assertEquals(10000L, statuses.first().effectiveLimitCents)
    }

    @Test
    fun `net position combines dual currency wallets and unsettled receivables`() {
        val rate = BigDecimal("25.00")
        val accounts = listOf(
            Account(id = 1L, name = "USD Cash", type = AccountType.CASH, currency = CurrencyCode.USD, balanceCents = 10000L),
            Account(id = 2L, name = "EcoCash ZWG", type = AccountType.MOBILE_MONEY, currency = CurrencyCode.ZWG, balanceCents = 250000L) // $100
        )
        val receivables = listOf(
            Receivable(id = 1L, personName = "Alex", currency = CurrencyCode.USD, totalOwedCents = 5000L, paidBackCents = 2000L, isSettled = false), // $30 left
            Receivable(id = 2L, personName = "Sam", currency = CurrencyCode.ZWG, totalOwedCents = 50000L, paidBackCents = 0L, isSettled = false), // $20
            Receivable(id = 3L, personName = "Settled", currency = CurrencyCode.USD, totalOwedCents = 9900L, paidBackCents = 9900L, isSettled = true)
        )

        val combinedUsd = calculator.computeNetPosition(accounts, receivables, DisplayCurrencyMode.COMBINED_USD, rate)
        assertEquals(CurrencyCode.USD, combinedUsd.displayCurrency)
        assertEquals(20000L, combinedUsd.walletsTotalCents)
        assertEquals(10000L, combinedUsd.usdWalletsCents)
        assertEquals(250000L, combinedUsd.zwgWalletsCents)
        assertEquals(5000L, combinedUsd.receivablesTotalCents)
        assertEquals(2, combinedUsd.unsettledReceivablesCount)
        assertEquals(25000L, combinedUsd.netPositionCents)

        val combinedZwg = calculator.computeNetPosition(accounts, receivables, DisplayCurrencyMode.COMBINED_ZWG, rate)
        assertEquals(CurrencyCode.ZWG, combinedZwg.displayCurrency)
        assertEquals(500000L, combinedZwg.walletsTotalCents)
        assertEquals(125000L, combinedZwg.receivablesTotalCents)
        assertEquals(625000L, combinedZwg.netPositionCents)

        val usdOnly = calculator.computeNetPosition(accounts, receivables, DisplayCurrencyMode.USD_ONLY, rate)
        assertEquals(10000L, usdOnly.walletsTotalCents)
        assertEquals(3000L, usdOnly.receivablesTotalCents)
        assertEquals(1, usdOnly.unsettledReceivablesCount)
        assertEquals(13000L, usdOnly.netPositionCents)

        val zwgOnly = calculator.computeNetPosition(accounts, receivables, DisplayCurrencyMode.ZWG_ONLY, rate)
        assertEquals(250000L, zwgOnly.walletsTotalCents)
        assertEquals(50000L, zwgOnly.receivablesTotalCents)
        assertEquals(1, zwgOnly.unsettledReceivablesCount)
        assertEquals(300000L, zwgOnly.netPositionCents)
    }

    @Test
    fun `net position handles empty wallets and receivables`() {
        val empty = calculator.computeNetPosition(emptyList(), emptyList(), DisplayCurrencyMode.COMBINED_USD, BigDecimal("26.50"))
        assertEquals(0L, empty.walletsTotalCents)
        assertEquals(0L, empty.receivablesTotalCents)
        assertEquals(0L, empty.netPositionCents)
        assertEquals(0, empty.unsettledReceivablesCount)
    }

    @Test
    fun `spending insights computes MoM, burn rate projection, top merchants and items, and currency split`() {
        val rate = BigDecimal("20.00")
        val groceries = Category(id = 1L, name = "Groceries", iconName = "cart", colorHex = 0xFF059669)
        val dining = Category(id = 2L, name = "Dining", iconName = "food", colorHex = 0xFFF59E0B)
        val budgets = listOf(
            Budget(id = 1L, categoryId = 1L, currency = CurrencyCode.USD, limitCents = 40000L), // $400
            Budget(id = 2L, categoryId = 2L, currency = CurrencyCode.USD, limitCents = 10000L)  // $100
        )
        val nov10 = LocalDate.of(2026, 11, 10)
        val nov5Millis = LocalDate.of(2026, 11, 5).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val nov8Millis = LocalDate.of(2026, 11, 8).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val oct12Millis = LocalDate.of(2026, 10, 12).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

        val transactions = listOf(
            // Oct: $100
            Transaction(
                id = 1L, accountId = 1L, categoryId = 1L, type = TransactionType.EXPENSE,
                currency = CurrencyCode.USD, amountCents = 10000L, exchangeRateUsed = rate,
                merchant = "Pick n Pay", timestampMillis = oct12Millis
            ),
            // Nov: $100 USD at Pick n Pay with items
            Transaction(
                id = 2L, accountId = 1L, categoryId = 1L, type = TransactionType.EXPENSE,
                currency = CurrencyCode.USD, amountCents = 10000L, exchangeRateUsed = rate,
                merchant = "Pick n Pay", timestampMillis = nov5Millis,
                items = listOf(
                    LineItem(id = 1L, transactionId = 2L, name = "Milk", amountCents = 4000L),
                    LineItem(id = 2L, transactionId = 2L, name = "Bread", amountCents = 6000L)
                )
            ),
            // Nov: ZWG 2000 (= $100 at rate 20) at pick n pay + Milk item
            Transaction(
                id = 3L, accountId = 2L, categoryId = 2L, type = TransactionType.EXPENSE,
                currency = CurrencyCode.ZWG, amountCents = 200000L, exchangeRateUsed = rate,
                merchant = "pick n pay", timestampMillis = nov8Millis,
                items = listOf(
                    LineItem(id = 3L, transactionId = 3L, name = "milk", amountCents = 100000L), // $50
                    LineItem(id = 4L, transactionId = 3L, name = "   ", amountCents = 100000L) // blank ignored
                )
            ),
            // Nov Income: should not affect expense insights
            Transaction(
                id = 4L, accountId = 1L, categoryId = null, type = TransactionType.INCOME,
                currency = CurrencyCode.USD, amountCents = 50000L, exchangeRateUsed = rate,
                merchant = "Salary", timestampMillis = nov5Millis
            )
        )

        val month = YearMonth.of(2026, 11)
        val statuses = calculator.computeCategoryStatuses(budgets, listOf(groceries, dining), transactions, month, DisplayCurrencyMode.COMBINED_USD, rate)
        val summary = calculator.computeMonthlySummary(statuses, transactions, month, nov10, DisplayCurrencyMode.COMBINED_USD, rate)
        val insights = calculator.computeSpendingInsights(statuses, summary, transactions, month, nov10, DisplayCurrencyMode.COMBINED_USD, rate)

        // MoM: Nov = $200, Oct = $100 => +$100 (+100%)
        assertEquals(20000L, insights.monthOverMonth.currentMonthSpentCents)
        assertEquals(10000L, insights.monthOverMonth.previousMonthSpentCents)
        assertEquals(10000L, insights.monthOverMonth.deltaCents)
        assertEquals(100, insights.monthOverMonth.deltaPercent)

        // Burn rate: $200 over 10 days = $20/day; 30-day month => $600 projected; budget = $500 => -$100 deficit
        assertEquals(2000L, insights.projection.dailyBurnCents)
        assertEquals(60000L, insights.projection.projectedMonthSpendCents)
        assertEquals(-10000L, insights.projection.projectedRemainingCents)
        assertEquals(10, insights.projection.elapsedDays)
        assertEquals(30, insights.projection.daysInMonth)

        // Top merchants: "Pick n Pay" merged case-insensitively => $200, 2x
        assertEquals(1, insights.topMerchants.size)
        assertEquals("Pick n Pay", insights.topMerchants.first().name)
        assertEquals(20000L, insights.topMerchants.first().amountCents)
        assertEquals(2, insights.topMerchants.first().count)

        // Top items: Milk ($40 + $50 = $90, 2x), Bread ($60, 1x)
        assertEquals(2, insights.topItems.size)
        assertEquals("Milk", insights.topItems[0].name)
        assertEquals(9000L, insights.topItems[0].amountCents)
        assertEquals(2, insights.topItems[0].count)
        assertEquals("Bread", insights.topItems[1].name)
        assertEquals(6000L, insights.topItems[1].amountCents)

        // Currency split: $100 USD vs ZWG 2000 ($100) => 50% / 50%
        assertEquals(10000L, insights.currencySplit.usdSpentCents)
        assertEquals(200000L, insights.currencySplit.zwgSpentCents)
        assertEquals(50, insights.currencySplit.usdPercent)
        assertEquals(50, insights.currencySplit.zwgPercent)

        // Budget health: Groceries $100/$400 (ON_TRACK), Dining $100/$100 (WARNING at 100%)
        assertEquals(1, insights.onTrackBudgets)
        assertEquals(1, insights.warningBudgets)
        assertEquals(0, insights.overBudgets)
    }

    @Test
    fun `spending insights handles zero prior month spend and zero current spend`() {
        val rate = BigDecimal("26.50")
        val month = YearMonth.of(2026, 11)
        val today = LocalDate.of(2026, 11, 15)
        val summary = calculator.computeMonthlySummary(emptyList(), emptyList(), month, today, DisplayCurrencyMode.COMBINED_USD, rate)
        val insights = calculator.computeSpendingInsights(emptyList(), summary, emptyList(), month, today, DisplayCurrencyMode.COMBINED_USD, rate)

        assertNull(insights.monthOverMonth.deltaPercent)
        assertEquals(0L, insights.projection.dailyBurnCents)
        assertEquals(0L, insights.projection.projectedMonthSpendCents)
        assertEquals(0, insights.currencySplit.usdPercent)
        assertEquals(0, insights.currencySplit.zwgPercent)
        assertEquals(0, insights.topMerchants.size)
        assertEquals(0, insights.topItems.size)
    }

    @Test
    fun `spending insights filters mixed-currency transactions and items in USD_ONLY and ZWG_ONLY modes`() {
        val rate = BigDecimal("25.00")
        val month = YearMonth.of(2026, 11)
        val today = LocalDate.of(2026, 11, 10)
        val nov5Millis = LocalDate.of(2026, 11, 5).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

        val transactions = listOf(
            Transaction(
                id = 1L, accountId = 1L, categoryId = 1L, type = TransactionType.EXPENSE,
                currency = CurrencyCode.USD, amountCents = 5000L, exchangeRateUsed = rate,
                merchant = "USD Cafe", timestampMillis = nov5Millis,
                items = listOf(LineItem(id = 1L, transactionId = 1L, name = "Coffee", amountCents = 5000L))
            ),
            Transaction(
                id = 2L, accountId = 2L, categoryId = 1L, type = TransactionType.EXPENSE,
                currency = CurrencyCode.ZWG, amountCents = 100000L, exchangeRateUsed = rate,
                merchant = "ZWG Market", timestampMillis = nov5Millis,
                items = listOf(LineItem(id = 2L, transactionId = 2L, name = "Sugar", amountCents = 100000L))
            )
        )

        val usdSummary = calculator.computeMonthlySummary(emptyList(), transactions, month, today, DisplayCurrencyMode.USD_ONLY, rate)
        val usdInsights = calculator.computeSpendingInsights(emptyList(), usdSummary, transactions, month, today, DisplayCurrencyMode.USD_ONLY, rate)
        assertEquals(5000L, usdInsights.monthOverMonth.currentMonthSpentCents)
        assertEquals(listOf("USD Cafe"), usdInsights.topMerchants.map { it.name })
        assertEquals(listOf("Coffee"), usdInsights.topItems.map { it.name })
        assertEquals(5000L, usdInsights.topItems.first().amountCents)

        val zwgSummary = calculator.computeMonthlySummary(emptyList(), transactions, month, today, DisplayCurrencyMode.ZWG_ONLY, rate)
        val zwgInsights = calculator.computeSpendingInsights(emptyList(), zwgSummary, transactions, month, today, DisplayCurrencyMode.ZWG_ONLY, rate)
        assertEquals(100000L, zwgInsights.monthOverMonth.currentMonthSpentCents)
        assertEquals(listOf("ZWG Market"), zwgInsights.topMerchants.map { it.name })
        assertEquals(listOf("Sugar"), zwgInsights.topItems.map { it.name })
        assertEquals(100000L, zwgInsights.topItems.first().amountCents)
    }

    @Test
    fun `spending insights caps top merchants at 5 and handles tiny ZWG-only currency split`() {
        val rate = BigDecimal("26.50")
        val month = YearMonth.of(2026, 11)
        val today = LocalDate.of(2026, 11, 10)
        val nov5Millis = LocalDate.of(2026, 11, 5).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

        val transactions = (1..7).map { idx ->
            Transaction(
                id = idx.toLong(),
                accountId = 2L,
                categoryId = 1L,
                type = TransactionType.EXPENSE,
                currency = CurrencyCode.ZWG,
                amountCents = idx.toLong(), // tiny ZWG cents (< 0.5 USD cents each)
                exchangeRateUsed = rate,
                merchant = "Merchant $idx",
                timestampMillis = nov5Millis
            )
        }

        val summary = calculator.computeMonthlySummary(emptyList(), transactions, month, today, DisplayCurrencyMode.COMBINED_ZWG, rate)
        val insights = calculator.computeSpendingInsights(emptyList(), summary, transactions, month, today, DisplayCurrencyMode.COMBINED_ZWG, rate)

        assertEquals(5, insights.topMerchants.size)
        assertEquals("Merchant 7", insights.topMerchants.first().name)
        assertEquals("Merchant 3", insights.topMerchants.last().name)
        assertEquals(0, insights.currencySplit.usdPercent)
        assertEquals(100, insights.currencySplit.zwgPercent)
    }
}
