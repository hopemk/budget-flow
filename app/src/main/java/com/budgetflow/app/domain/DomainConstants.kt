package com.budgetflow.app.domain

import com.budgetflow.app.domain.model.CurrencyCode
import java.math.BigDecimal

/** Shared domain-level defaults and system-generated transaction prefixes/notes. */
object DomainConstants {
    const val DEFAULT_ZWG_PER_USD = "26.50"
    val DEFAULT_EXCHANGE_RATE: BigDecimal = BigDecimal(DEFAULT_ZWG_PER_USD)

    val DEFAULT_RATES_PER_USD: Map<CurrencyCode, BigDecimal> = mapOf(
        CurrencyCode.USD to BigDecimal.ONE,
        CurrencyCode.ZWG to BigDecimal("26.50"),
        CurrencyCode.GBP to BigDecimal("0.79"),
        CurrencyCode.ZAR to BigDecimal("18.20"),
        CurrencyCode.EUR to BigDecimal("0.92"),
        CurrencyCode.BWP to BigDecimal("13.50")
    )

    const val DEFAULT_CATEGORY_ICON = "label"
    const val DEFAULT_CATEGORY_COLOR_HEX = 0xFF64748BL
    const val DEFAULT_ALERT_THRESHOLD_PCT = 80

    const val AUTO_LOG_BILL_PREFIX = "Bill: "
    const val AUTO_LOG_BILL_NOTE = "Recurring bill payment"
    const val AUTO_LOG_GOAL_PREFIX = "Goal: "
    const val AUTO_LOG_GOAL_NOTE = "Savings goal contribution"
    const val AUTO_LOG_REPAYMENT_PREFIX = "Repayment: "
    const val AUTO_LOG_PAYABLE_PREFIX = "Paid: "
}
