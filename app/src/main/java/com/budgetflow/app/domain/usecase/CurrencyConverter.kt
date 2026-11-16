package com.budgetflow.app.domain.usecase

import com.budgetflow.app.domain.DomainConstants
import com.budgetflow.app.domain.model.CurrencyCode
import com.budgetflow.app.domain.model.ExchangeRateConfig
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

/** Exact minor-unit (cents) conversion and formatting across supported currencies (USD base). */
object CurrencyConverter {

    private val hundred = BigDecimal(100)

    /**
     * Converts [amountCents] from [from] to [to] using [rates] (1 USD = rates.rateFor(c)).
     */
    fun convert(
        amountCents: Long,
        from: CurrencyCode,
        to: CurrencyCode,
        rates: ExchangeRateConfig
    ): Long {
        if (from == to) return amountCents
        val fromRate = rates.rateFor(from)
        val toRate = rates.rateFor(to)
        return BigDecimal.valueOf(amountCents)
            .multiply(toRate)
            .divide(fromRate, 0, RoundingMode.HALF_UP)
            .toLong()
    }

    /**
     * Convenience overload for USD/ZWG conversion using a single [zwgPerUsd] rate.
     */
    fun convert(
        amountCents: Long,
        from: CurrencyCode,
        to: CurrencyCode,
        zwgPerUsd: BigDecimal
    ): Long {
        if (from == to) return amountCents
        val safeRate = if (zwgPerUsd > BigDecimal.ZERO) zwgPerUsd else BigDecimal.ONE
        val rates = ExchangeRateConfig(
            ratesPerUsd = DomainConstants.DEFAULT_RATES_PER_USD + (CurrencyCode.ZWG to safeRate)
        )
        return convert(amountCents, from, to, rates)
    }

    /** Formats minor units (cents) into a clean human-readable currency string. */
    fun format(amountCents: Long, currency: CurrencyCode, showPlus: Boolean = false): String {
        val absMajor = BigDecimal.valueOf(kotlin.math.abs(amountCents))
            .divide(hundred, 2, RoundingMode.HALF_UP)
        val formatter = NumberFormat.getNumberInstance(Locale.US).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
        val sign = when {
            amountCents < 0 -> "-"
            showPlus && amountCents > 0 -> "+"
            else -> ""
        }
        return "$sign${currency.symbol}${formatter.format(absMajor)}"
    }

    /** Formats minor units (cents) as a plain decimal string (e.g. "24.50") for text inputs and CSV. */
    fun formatInput(amountCents: Long): String =
        String.format(Locale.US, "%.2f", amountCents / 100.0)

    /** Parses a decimal string like "24.50" into integer cents (2450L), or null if invalid. */
    fun parseToCents(input: String): Long? {
        val cleaned = input.trim().replace(",", "")
        if (cleaned.isEmpty()) return null
        return runCatching {
            BigDecimal(cleaned)
                .multiply(hundred)
                .setScale(0, RoundingMode.HALF_UP)
                .toLong()
        }.getOrNull()
    }
}
