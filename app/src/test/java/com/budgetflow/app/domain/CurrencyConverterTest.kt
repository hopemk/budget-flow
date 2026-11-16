package com.budgetflow.app.domain

import com.budgetflow.app.domain.model.CurrencyCode
import com.budgetflow.app.domain.model.ExchangeRateConfig
import com.budgetflow.app.domain.usecase.CurrencyConverter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class CurrencyConverterTest {

    private val customRates = ExchangeRateConfig(
        ratesPerUsd = mapOf(
            CurrencyCode.USD to BigDecimal.ONE,
            CurrencyCode.ZWG to BigDecimal("26.50"),
            CurrencyCode.GBP to BigDecimal("0.80"),
            CurrencyCode.ZAR to BigDecimal("18.00"),
            CurrencyCode.EUR to BigDecimal("0.90"),
            CurrencyCode.BWP to BigDecimal("13.50")
        )
    )

    @Test
    fun `convert returns exact amount when source and target currencies match`() {
        CurrencyCode.entries.forEach { currency ->
            assertEquals(12345L, CurrencyConverter.convert(12345L, currency, currency, customRates))
            assertEquals(0L, CurrencyConverter.convert(0L, currency, currency, customRates))
            assertEquals(-500L, CurrencyConverter.convert(-500L, currency, currency, customRates))
        }
    }

    @Test
    fun `convert converts across all supported currencies via USD base`() {
        // 100.00 USD -> 2650.00 ZWG
        assertEquals(265000L, CurrencyConverter.convert(10000L, CurrencyCode.USD, CurrencyCode.ZWG, customRates))
        // 2650.00 ZWG -> 100.00 USD
        assertEquals(10000L, CurrencyConverter.convert(265000L, CurrencyCode.ZWG, CurrencyCode.USD, customRates))
        // 80.00 GBP (100 USD) -> 1800.00 ZAR
        assertEquals(180000L, CurrencyConverter.convert(8000L, CurrencyCode.GBP, CurrencyCode.ZAR, customRates))
        // 90.00 EUR (100 USD) -> 1350.00 BWP
        assertEquals(135000L, CurrencyConverter.convert(9000L, CurrencyCode.EUR, CurrencyCode.BWP, customRates))
        // 1350.00 BWP (100 USD) -> 80.00 GBP
        assertEquals(8000L, CurrencyConverter.convert(135000L, CurrencyCode.BWP, CurrencyCode.GBP, customRates))
        // Negative amount preserves sign
        assertEquals(-26500L, CurrencyConverter.convert(-1000L, CurrencyCode.USD, CurrencyCode.ZWG, customRates))
    }

    @Test
    fun `convert applies HALF_UP rounding on fractional cents`() {
        val rates = ExchangeRateConfig(
            ratesPerUsd = mapOf(
                CurrencyCode.USD to BigDecimal.ONE,
                CurrencyCode.GBP to BigDecimal("0.50"),
                CurrencyCode.EUR to BigDecimal("0.49")
            )
        )
        // 1 cent * 0.50 = 0.50 -> rounds HALF_UP to 1
        assertEquals(1L, CurrencyConverter.convert(1L, CurrencyCode.USD, CurrencyCode.GBP, rates))
        // 1 cent * 0.49 = 0.49 -> rounds HALF_UP to 0
        assertEquals(0L, CurrencyConverter.convert(1L, CurrencyCode.USD, CurrencyCode.EUR, rates))
    }

    @Test
    fun `convert with zwgPerUsd overload handles valid and non-positive rates`() {
        assertEquals(30000L, CurrencyConverter.convert(1000L, CurrencyCode.USD, CurrencyCode.ZWG, BigDecimal("30.00")))
        assertEquals(1000L, CurrencyConverter.convert(30000L, CurrencyCode.ZWG, CurrencyCode.USD, BigDecimal("30.00")))
        assertEquals(500L, CurrencyConverter.convert(500L, CurrencyCode.ZWG, CurrencyCode.ZWG, BigDecimal("30.00")))

        // Non-positive zwgPerUsd safely falls back to 1.0
        assertEquals(1000L, CurrencyConverter.convert(1000L, CurrencyCode.USD, CurrencyCode.ZWG, BigDecimal.ZERO))
        assertEquals(1000L, CurrencyConverter.convert(1000L, CurrencyCode.USD, CurrencyCode.ZWG, BigDecimal("-5.0")))
    }

    @Test
    fun `format formats all supported currencies, signs, and thousands separators`() {
        assertEquals("$12.34", CurrencyConverter.format(1234L, CurrencyCode.USD))
        assertEquals("ZWG 12.34", CurrencyConverter.format(1234L, CurrencyCode.ZWG))
        assertEquals("£12.34", CurrencyConverter.format(1234L, CurrencyCode.GBP))
        assertEquals("R12.34", CurrencyConverter.format(1234L, CurrencyCode.ZAR))
        assertEquals("€12.34", CurrencyConverter.format(1234L, CurrencyCode.EUR))
        assertEquals("P12.34", CurrencyConverter.format(1234L, CurrencyCode.BWP))

        assertEquals("$1,234.56", CurrencyConverter.format(123456L, CurrencyCode.USD))
        assertEquals("$0.05", CurrencyConverter.format(5L, CurrencyCode.USD))
        assertEquals("$0.00", CurrencyConverter.format(0L, CurrencyCode.USD))
        assertEquals("$0.00", CurrencyConverter.format(0L, CurrencyCode.USD, showPlus = true))
        assertEquals("+$12.34", CurrencyConverter.format(1234L, CurrencyCode.USD, showPlus = true))
        assertEquals("-$12.34", CurrencyConverter.format(-1234L, CurrencyCode.USD))
        assertEquals("-ZWG 12.34", CurrencyConverter.format(-1234L, CurrencyCode.ZWG, showPlus = true))
    }

    @Test
    fun `formatInput formats cents as plain decimal without grouping separators`() {
        assertEquals("0.00", CurrencyConverter.formatInput(0L))
        assertEquals("0.05", CurrencyConverter.formatInput(5L))
        assertEquals("24.50", CurrencyConverter.formatInput(2450L))
        assertEquals("10000.00", CurrencyConverter.formatInput(1000000L))
        assertEquals("-12.34", CurrencyConverter.formatInput(-1234L))
    }

    @Test
    fun `parseToCents parses valid decimals and rejects invalid strings`() {
        assertEquals(2450L, CurrencyConverter.parseToCents("24.50"))
        assertEquals(123456L, CurrencyConverter.parseToCents("  1,234.56  "))
        assertEquals(0L, CurrencyConverter.parseToCents("0"))
        assertEquals(50L, CurrencyConverter.parseToCents(".50"))
        assertEquals(1235L, CurrencyConverter.parseToCents("12.345"))
        assertEquals(-1025L, CurrencyConverter.parseToCents("-10.25"))

        assertNull(CurrencyConverter.parseToCents(""))
        assertNull(CurrencyConverter.parseToCents("   "))
        assertNull(CurrencyConverter.parseToCents("abc"))
        assertNull(CurrencyConverter.parseToCents("12.34.56"))
        assertNull(CurrencyConverter.parseToCents("$10.00"))
    }
}
