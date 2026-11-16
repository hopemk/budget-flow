package com.budgetflow.app.domain

import com.budgetflow.app.data.local.AccountEntity
import com.budgetflow.app.data.local.BudgetEntity
import com.budgetflow.app.data.local.TransactionEntity
import com.budgetflow.app.data.local.TransactionWithItems
import com.budgetflow.app.data.local.toDomain
import com.budgetflow.app.domain.model.Account
import com.budgetflow.app.domain.model.AccountType
import com.budgetflow.app.domain.model.CurrencyCode
import com.budgetflow.app.domain.model.DisplayCurrencyMode
import com.budgetflow.app.domain.model.ExchangeRateConfig
import com.budgetflow.app.domain.model.RolloverMode
import com.budgetflow.app.domain.model.Transaction
import com.budgetflow.app.domain.model.TransactionType
import com.budgetflow.app.domain.usecase.BudgetCalculator
import com.budgetflow.app.domain.usecase.CurrencyConverter
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

class MultiCurrencyAndMappingTest {

    private val calculator = BudgetCalculator(ZoneOffset.UTC)

    private val rates = ExchangeRateConfig(
        ratesPerUsd = mapOf(
            CurrencyCode.USD to BigDecimal.ONE,
            CurrencyCode.ZWG to BigDecimal("25.00"),
            CurrencyCode.GBP to BigDecimal("0.80"),
            CurrencyCode.ZAR to BigDecimal("18.00"),
            CurrencyCode.EUR to BigDecimal("0.90"),
            CurrencyCode.BWP to BigDecimal("13.50")
        )
    )

    @Test
    fun `currency converter converts between non-USD currencies via USD base`() {
        // 80.00 GBP = 100.00 USD = 1800.00 ZAR
        val zarCents = CurrencyConverter.convert(8000L, CurrencyCode.GBP, CurrencyCode.ZAR, rates)
        assertEquals(180000L, zarCents)

        // 1800.00 ZAR = 90.00 EUR
        val eurCents = CurrencyConverter.convert(180000L, CurrencyCode.ZAR, CurrencyCode.EUR, rates)
        assertEquals(9000L, eurCents)

        // Same currency returns exact amount
        assertEquals(12345L, CurrencyConverter.convert(12345L, CurrencyCode.BWP, CurrencyCode.BWP, rates))
    }

    @Test
    fun `display currency mode serializes and deserializes legacy and new modes`() {
        assertEquals(DisplayCurrencyMode.COMBINED_USD, DisplayCurrencyMode.deserialize("COMBINED_USD"))
        assertEquals(DisplayCurrencyMode.COMBINED_ZWG, DisplayCurrencyMode.deserialize("COMBINED_ZWG"))
        assertEquals(DisplayCurrencyMode.USD_ONLY, DisplayCurrencyMode.deserialize("USD_ONLY"))
        assertEquals(DisplayCurrencyMode.ZWG_ONLY, DisplayCurrencyMode.deserialize("ZWG_ONLY"))

        val gbpOnly = DisplayCurrencyMode(CurrencyCode.GBP, includeAllCurrencies = false)
        assertEquals(gbpOnly, DisplayCurrencyMode.deserialize(gbpOnly.serialize()))

        val zarAll = DisplayCurrencyMode(CurrencyCode.ZAR, includeAllCurrencies = true)
        assertEquals(zarAll, DisplayCurrencyMode.deserialize(zarAll.serialize()))

        // Invalid input falls back safely to COMBINED_USD
        assertEquals(DisplayCurrencyMode.COMBINED_USD, DisplayCurrencyMode.deserialize("UNKNOWN_LEGACY"))
    }

    @Test
    fun `net position and insights aggregate multi-currency wallets and expenses`() {
        val accounts = listOf(
            Account(id = 1L, name = "USD", type = AccountType.CASH, currency = CurrencyCode.USD, balanceCents = 10000L), // $100
            Account(id = 2L, name = "GBP", type = AccountType.BANK, currency = CurrencyCode.GBP, balanceCents = 8000L),  // £80 = $100
            Account(id = 3L, name = "ZAR", type = AccountType.BANK, currency = CurrencyCode.ZAR, balanceCents = 180000L) // R1800 = $100
        )

        val netAllGbp = calculator.computeNetPosition(
            accounts = accounts,
            receivables = emptyList(),
            mode = DisplayCurrencyMode(CurrencyCode.GBP, includeAllCurrencies = true),
            rates = rates
        )
        assertEquals(CurrencyCode.GBP, netAllGbp.displayCurrency)
        // Total = $300 = £240 (24000 cents)
        assertEquals(24000L, netAllGbp.walletsTotalCents)
        assertEquals(10000L, netAllGbp.walletsByCurrency[CurrencyCode.USD])
        assertEquals(8000L, netAllGbp.walletsByCurrency[CurrencyCode.GBP])
        assertEquals(180000L, netAllGbp.walletsByCurrency[CurrencyCode.ZAR])

        val netZarOnly = calculator.computeNetPosition(
            accounts = accounts,
            receivables = emptyList(),
            mode = DisplayCurrencyMode(CurrencyCode.ZAR, includeAllCurrencies = false),
            rates = rates
        )
        assertEquals(180000L, netZarOnly.walletsTotalCents)

        val month = YearMonth.of(2026, 11)
        val today = LocalDate.of(2026, 11, 10)
        val ts = LocalDate.of(2026, 11, 5).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val txs = listOf(
            Transaction(
                id = 1L, accountId = 1L, categoryId = 1L, type = TransactionType.EXPENSE,
                currency = CurrencyCode.USD, amountCents = 10000L, exchangeRateUsed = BigDecimal.ONE,
                merchant = "US Store", timestampMillis = ts
            ),
            Transaction(
                id = 2L, accountId = 2L, categoryId = 1L, type = TransactionType.EXPENSE,
                currency = CurrencyCode.GBP, amountCents = 8000L, exchangeRateUsed = BigDecimal("0.80"),
                merchant = "UK Store", timestampMillis = ts
            )
        )
        val summary = calculator.computeMonthlySummary(
            statuses = emptyList(),
            transactions = txs,
            targetMonth = month,
            today = today,
            mode = DisplayCurrencyMode(CurrencyCode.USD, includeAllCurrencies = true),
            rates = rates
        )
        val insights = calculator.computeSpendingInsights(
            statuses = emptyList(),
            summary = summary,
            transactions = txs,
            targetMonth = month,
            today = today,
            mode = DisplayCurrencyMode(CurrencyCode.USD, includeAllCurrencies = true),
            rates = rates
        )
        assertEquals(20000L, summary.totalSpentCents)
        assertEquals(2, insights.currencySplit.entries.size)
        assertEquals(100, insights.currencySplit.entries.sumOf { it.percent })
    }

    @Test
    fun `entities guard invalid enum names and malformed exchange rates`() {
        val acc = AccountEntity(
            id = 1L,
            name = "Legacy",
            type = "UNKNOWN_TYPE",
            currency = "XYZ",
            balanceCents = 500L
        ).toDomain()
        assertEquals(AccountType.CASH, acc.type)
        assertEquals(CurrencyCode.USD, acc.currency)

        val tx = TransactionWithItems(
            transaction = TransactionEntity(
                id = 1L,
                accountId = 1L,
                targetAccountId = null,
                categoryId = null,
                type = "WEIRD",
                currency = "BAD_CUR",
                amountCents = 1000L,
                exchangeRateUsed = "not-a-number",
                merchant = "Shop",
                note = "",
                receiptUri = null,
                timestampMillis = 1000L
            ),
            items = emptyList()
        ).toDomain()
        assertEquals(TransactionType.EXPENSE, tx.type)
        assertEquals(CurrencyCode.USD, tx.currency)
        assertEquals(BigDecimal.ONE, tx.exchangeRateUsed)

        val budget = BudgetEntity(
            id = 1L,
            categoryId = 1L,
            currency = "BAD",
            limitCents = 1000L,
            rolloverMode = "UNKNOWN_MODE",
            alertThresholdPct = 80
        ).toDomain()
        assertEquals(CurrencyCode.USD, budget.currency)
        assertEquals(RolloverMode.RESET_MONTHLY, budget.rolloverMode)
    }

    @Test
    fun `transfer rate snapshot and balance conversion handle USD to non-USD and cross-currency transfers`() {
        // USD -> GBP ($100.00 -> £80.00 at 0.80)
        val usdToGbpRate = rates.rateForTransaction(CurrencyCode.USD, CurrencyCode.GBP)
        assertEquals(BigDecimal("0.80"), usdToGbpRate)
        val gbpEffective = rates.withTransactionRate(CurrencyCode.USD, CurrencyCode.GBP, usdToGbpRate)
        assertEquals(8000L, CurrencyConverter.convert(10000L, CurrencyCode.USD, CurrencyCode.GBP, gbpEffective))

        // USD -> ZAR ($100.00 -> R1800.00 at 18.00)
        val usdToZarRate = rates.rateForTransaction(CurrencyCode.USD, CurrencyCode.ZAR)
        assertEquals(BigDecimal("18.00"), usdToZarRate)
        val zarEffective = rates.withTransactionRate(CurrencyCode.USD, CurrencyCode.ZAR, usdToZarRate)
        assertEquals(180000L, CurrencyConverter.convert(10000L, CurrencyCode.USD, CurrencyCode.ZAR, zarEffective))

        // Legacy USD -> GBP transfer that stored 1.00 falls back to current GBP rate instead of corrupting to 1:1
        val legacyEffective = rates.withTransactionRate(CurrencyCode.USD, CurrencyCode.GBP, BigDecimal.ONE)
        assertEquals(8000L, CurrencyConverter.convert(10000L, CurrencyCode.USD, CurrencyCode.GBP, legacyEffective))

        // GBP -> ZAR (£80.00 -> R1800.00) using persisted GBP rate
        val gbpToZarRate = rates.rateForTransaction(CurrencyCode.GBP, CurrencyCode.ZAR)
        assertEquals(BigDecimal("0.80"), gbpToZarRate)
        val crossEffective = rates.withTransactionRate(CurrencyCode.GBP, CurrencyCode.ZAR, gbpToZarRate)
        assertEquals(180000L, CurrencyConverter.convert(8000L, CurrencyCode.GBP, CurrencyCode.ZAR, crossEffective))
    }
}
