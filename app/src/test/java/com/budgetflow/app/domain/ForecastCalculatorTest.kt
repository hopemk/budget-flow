package com.budgetflow.app.domain

import com.budgetflow.app.domain.model.Account
import com.budgetflow.app.domain.model.AccountType
import com.budgetflow.app.domain.model.Category
import com.budgetflow.app.domain.model.CurrencyCode
import com.budgetflow.app.domain.model.DisplayCurrencyMode
import com.budgetflow.app.domain.model.ExchangeRateConfig
import com.budgetflow.app.domain.model.ForecastBudgetAdjustment
import com.budgetflow.app.domain.model.ForecastExpenseItem
import com.budgetflow.app.domain.model.ForecastExpenseSource
import com.budgetflow.app.domain.model.ForecastIncomeItem
import com.budgetflow.app.domain.model.RolloverMode
import com.budgetflow.app.domain.model.Transaction
import com.budgetflow.app.domain.model.TransactionType
import com.budgetflow.app.domain.usecase.BudgetCalculator
import com.budgetflow.app.domain.usecase.BudgetForecastCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

class ForecastCalculatorTest {

    private val budgetCalculator = BudgetCalculator(ZoneOffset.UTC)
    private val calculator = BudgetForecastCalculator(budgetCalculator, ZoneOffset.UTC)
    private val rates = ExchangeRateConfig(
        mapOf(
            CurrencyCode.ZWG to BigDecimal("26.50"),
            CurrencyCode.GBP to BigDecimal("0.80"),
            CurrencyCode.ZAR to BigDecimal("18.00")
        )
    )
    private val nov2026 = YearMonth.of(2026, 11)
    private val novMillis = LocalDate.of(2026, 11, 10).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    @Test
    fun `salary covers bills and budgets yields positive net cash flow and month end balance`() {
        val accounts = listOf(
            Account(id = 1L, name = "Bank", type = AccountType.BANK, currency = CurrencyCode.USD, balanceCents = 50000L)
        )
        val categories = listOf(
            Category(id = 1L, name = "Groceries", iconName = "cart", colorHex = 0xFF059669)
        )
        val income = listOf(
            ForecastIncomeItem("salary", "Salary", 150000L, CurrencyCode.USD, isIncluded = true)
        )
        val expenses = listOf(
            ForecastExpenseItem("bill_1", "Internet", 5000L, CurrencyCode.USD, ForecastExpenseSource.RECURRING_BILL, isIncluded = true),
            ForecastExpenseItem("list_1", "Weekly Shop", 10000L, CurrencyCode.USD, ForecastExpenseSource.SHOPPING_LIST, categoryId = 1L, isIncluded = true)
        )
        val budgets = listOf(
            ForecastBudgetAdjustment(1L, "Groceries", 20000L, 20000L, 5000L, CurrencyCode.USD)
        )

        val summary = calculator.computeForecast(
            accounts = accounts,
            categories = categories,
            transactionsThisMonth = emptyList(),
            targetMonth = nov2026,
            mode = DisplayCurrencyMode.COMBINED_USD,
            rates = rates,
            expectedIncomeItems = income,
            expectedExpenseItems = expenses,
            budgetAdjustments = budgets
        )

        assertEquals(150000L, summary.totalExpectedIncomeCents)
        assertEquals(15000L, summary.totalExpectedExpensesCents)
        assertEquals(135000L, summary.projectedNetCashFlowCents)
        assertEquals(185000L, summary.projectedMonthEndBalanceCents)
        assertEquals(0L, summary.shortfallCents)
        assertTrue(summary.atRiskCategories.isEmpty())
    }

    @Test
    fun `deficit scenario flags shortfall and at-risk categories`() {
        val accounts = listOf(
            Account(id = 1L, name = "Cash", type = AccountType.CASH, currency = CurrencyCode.USD, balanceCents = 10000L)
        )
        val categories = listOf(
            Category(id = 1L, name = "Transport", iconName = "bus", colorHex = 0xFF3B82F6),
            Category(id = 2L, name = "Fuel", iconName = "fuel", colorHex = 0xFF3B82F6, parentCategoryId = 1L)
        )
        val income = listOf(
            ForecastIncomeItem("salary", "Part-Time", 40000L, CurrencyCode.USD)
        )
        val expenses = listOf(
            ForecastExpenseItem("bill_1", "Rent", 45000L, CurrencyCode.USD, ForecastExpenseSource.RECURRING_BILL),
            ForecastExpenseItem("exp_1", "Car Service", 12000L, CurrencyCode.USD, ForecastExpenseSource.ONE_OFF, categoryId = 2L)
        )
        val budgets = listOf(
            ForecastBudgetAdjustment(1L, "Transport", 10000L, 10000L, 2000L, CurrencyCode.USD)
        )

        val summary = calculator.computeForecast(
            accounts = accounts,
            categories = categories,
            transactionsThisMonth = emptyList(),
            targetMonth = nov2026,
            mode = DisplayCurrencyMode.COMBINED_USD,
            rates = rates,
            expectedIncomeItems = income,
            expectedExpenseItems = expenses,
            budgetAdjustments = budgets
        )

        assertEquals(-17000L, summary.projectedNetCashFlowCents)
        assertEquals(-7000L, summary.projectedMonthEndBalanceCents)
        assertEquals(17000L, summary.shortfallCents)
        assertEquals(listOf(1L), summary.atRiskCategories.map { it.categoryId })
    }

    @Test
    fun `multi currency conversion handles USD salary with GBP and ZAR expenses`() {
        val income = listOf(
            ForecastIncomeItem("salary", "Salary", 100000L, CurrencyCode.USD) // $1000
        )
        val expenses = listOf(
            ForecastExpenseItem("gbp_exp", "UK Subscription", 8000L, CurrencyCode.GBP, ForecastExpenseSource.RECURRING_BILL), // £80 / 0.80 = $100
            ForecastExpenseItem("zar_exp", "SA Trip", 180000L, CurrencyCode.ZAR, ForecastExpenseSource.ONE_OFF) // R1800 / 18 = $100
        )

        val summary = calculator.computeForecast(
            accounts = emptyList(),
            categories = emptyList(),
            transactionsThisMonth = emptyList(),
            targetMonth = nov2026,
            mode = DisplayCurrencyMode.COMBINED_USD,
            rates = rates,
            expectedIncomeItems = income,
            expectedExpenseItems = expenses,
            budgetAdjustments = emptyList()
        )

        assertEquals(100000L, summary.totalExpectedIncomeCents)
        assertEquals(20000L, summary.totalExpectedExpensesCents)
        assertEquals(80000L, summary.projectedNetCashFlowCents)
    }

    @Test
    fun `toggling expenses and logged transactions updates totals accurately`() {
        val txs = listOf(
            Transaction(
                id = 1L,
                accountId = 1L,
                categoryId = 1L,
                type = TransactionType.EXPENSE,
                currency = CurrencyCode.USD,
                amountCents = 5000L,
                exchangeRateUsed = BigDecimal("1"),
                merchant = "Store",
                timestampMillis = novMillis
            )
        )
        val expenses = listOf(
            ForecastExpenseItem("bill_1", "Power", 3000L, CurrencyCode.USD, ForecastExpenseSource.RECURRING_BILL, isIncluded = true),
            ForecastExpenseItem("list_1", "Groceries List", 7000L, CurrencyCode.USD, ForecastExpenseSource.SHOPPING_LIST, isIncluded = false)
        )

        val withLogged = calculator.computeForecast(
            accounts = emptyList(),
            categories = emptyList(),
            transactionsThisMonth = txs,
            targetMonth = nov2026,
            mode = DisplayCurrencyMode.COMBINED_USD,
            rates = rates,
            expectedIncomeItems = emptyList(),
            expectedExpenseItems = expenses,
            budgetAdjustments = emptyList(),
            includeLoggedThisMonth = true
        )
        assertEquals(8000L, withLogged.totalExpectedExpensesCents)

        val withoutLogged = calculator.computeForecast(
            accounts = emptyList(),
            categories = emptyList(),
            transactionsThisMonth = txs,
            targetMonth = nov2026,
            mode = DisplayCurrencyMode.COMBINED_USD,
            rates = rates,
            expectedIncomeItems = emptyList(),
            expectedExpenseItems = expenses,
            budgetAdjustments = emptyList(),
            includeLoggedThisMonth = false
        )
        assertEquals(3000L, withoutLogged.totalExpectedExpensesCents)
    }

    @Test
    fun `multi month projection projects 3 months with rollover`() {
        val income = listOf(
            ForecastIncomeItem("salary", "Salary", 100000L, CurrencyCode.USD)
        )
        val expenses = listOf(
            ForecastExpenseItem("bill_1", "Rent", 40000L, CurrencyCode.USD, ForecastExpenseSource.RECURRING_BILL)
        )
        val budgets = listOf(
            ForecastBudgetAdjustment(1L, "Groceries", 20000L, 20000L, 15000L, CurrencyCode.USD, RolloverMode.ROLLOVER_SURPLUS)
        )

        val summary = calculator.computeForecast(
            accounts = listOf(Account(1L, "Bank", AccountType.BANK, CurrencyCode.USD, 10000L)),
            categories = emptyList(),
            transactionsThisMonth = emptyList(),
            targetMonth = nov2026,
            mode = DisplayCurrencyMode.COMBINED_USD,
            rates = rates,
            expectedIncomeItems = income,
            expectedExpenseItems = expenses,
            budgetAdjustments = budgets,
            horizonMonths = 3
        )

        assertEquals(3, summary.monthlyProjections.size)
        assertEquals(nov2026, summary.monthlyProjections[0].month)
        assertEquals(YearMonth.of(2026, 12), summary.monthlyProjections[1].month)
        assertEquals(YearMonth.of(2027, 1), summary.monthlyProjections[2].month)
        // Month 0 end: 10000 + 100000 - 40000 = 70000
        assertEquals(70000L, summary.monthlyProjections[0].projectedEndBalanceCents)
        // Month 1: recurring bills (40000) + Groceries effective budget (20000 + 5000 surplus) = 65000, net +35000 -> 105000
        assertEquals(35000L, summary.monthlyProjections[1].netCashFlowCents)
        assertEquals(105000L, summary.monthlyProjections[1].projectedEndBalanceCents)
    }
}
