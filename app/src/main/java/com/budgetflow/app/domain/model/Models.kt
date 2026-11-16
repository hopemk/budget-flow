package com.budgetflow.app.domain.model

import com.budgetflow.app.domain.DomainConstants
import java.math.BigDecimal
import java.time.YearMonth

/** Supported currencies in BudgetFlow. */
enum class CurrencyCode(val symbol: String, val displayName: String) {
    USD("$", "US Dollar"),
    ZWG("ZWG ", "Zimbabwe Gold"),
    GBP("£", "British Pound"),
    ZAR("R", "South African Rand"),
    EUR("€", "Euro"),
    BWP("P", "Botswana Pula");

    companion object {
        fun fromStringOrDefault(value: String?, default: CurrencyCode = USD): CurrencyCode =
            value?.let { runCatching { valueOf(it) }.getOrNull() } ?: default
    }
}

/** How Home, Budgets, and Insights aggregate multi-currency totals. */
data class DisplayCurrencyMode(
    val targetCurrency: CurrencyCode = CurrencyCode.USD,
    val includeAllCurrencies: Boolean = true
) {
    val displayName: String
        get() = if (includeAllCurrencies) "All (${targetCurrency.name})" else "${targetCurrency.name} Only"

    fun serialize(): String = "${targetCurrency.name}|$includeAllCurrencies"

    companion object {
        val COMBINED_USD = DisplayCurrencyMode(CurrencyCode.USD, true)
        val COMBINED_ZWG = DisplayCurrencyMode(CurrencyCode.ZWG, true)
        val USD_ONLY = DisplayCurrencyMode(CurrencyCode.USD, false)
        val ZWG_ONLY = DisplayCurrencyMode(CurrencyCode.ZWG, false)

        fun deserialize(raw: String?): DisplayCurrencyMode {
            if (raw.isNullOrBlank()) return COMBINED_USD
            return when (raw) {
                "COMBINED_USD" -> COMBINED_USD
                "COMBINED_ZWG" -> COMBINED_ZWG
                "USD_ONLY" -> USD_ONLY
                "ZWG_ONLY" -> ZWG_ONLY
                else -> {
                    val parts = raw.split("|")
                    val currency = CurrencyCode.fromStringOrDefault(parts.getOrNull(0), CurrencyCode.USD)
                    val includeAll = parts.getOrNull(1)?.toBooleanStrictOrNull() ?: true
                    DisplayCurrencyMode(currency, includeAll)
                }
            }
        }
    }
}

/** Account / wallet type. */
enum class AccountType(val displayName: String) {
    CASH("Cash"),
    BANK("Bank"),
    MOBILE_MONEY("Mobile Money"),
    SAVINGS("Savings"),
    CREDIT_CARD("Credit Card")
}

/** Transaction direction. */
enum class TransactionType(val displayName: String) {
    EXPENSE("Expense"),
    INCOME("Income"),
    TRANSFER("Transfer")
}

/** Per-category budget rollover policy. */
enum class RolloverMode(val label: String) {
    RESET_MONTHLY("Reset Monthly"),
    ROLLOVER_SURPLUS("Rollover Surplus"),
    ROLLOVER_ALL("Rollover Surplus & Deficit")
}

/** App appearance preference (Light-first by default). */
enum class ThemeMode(val displayName: String) {
    LIGHT("Light"),
    DARK("Dark"),
    SYSTEM("System")
}

/** Budget health thresholds. */
enum class BudgetHealth {
    ON_TRACK,
    WARNING,
    OVER
}

/** Wallet or bank/mobile-money account denominated in a single currency. */
data class Account(
    val id: Long = 0L,
    val name: String,
    val type: AccountType,
    val currency: CurrencyCode,
    val balanceCents: Long,
    val isArchived: Boolean = false,
    val showOnHome: Boolean = true
)

/** User-customizable spending/income category. */
data class Category(
    val id: Long = 0L,
    val name: String,
    val iconName: String,
    val colorHex: Long,
    val isArchived: Boolean = false,
    val parentCategoryId: Long? = null
)

/** Individual purchased item inside a transaction ("what you bought"). */
data class LineItem(
    val id: Long = 0L,
    val transactionId: Long = 0L,
    val name: String,
    val amountCents: Long
)

/** Financial transaction with historical exchange rate snapshot. */
data class Transaction(
    val id: Long = 0L,
    val accountId: Long,
    val targetAccountId: Long? = null,
    val categoryId: Long?,
    val type: TransactionType,
    val currency: CurrencyCode,
    val amountCents: Long,
    val exchangeRateUsed: BigDecimal,
    val merchant: String,
    val note: String = "",
    val receiptUri: String? = null,
    val timestampMillis: Long,
    val items: List<LineItem> = emptyList()
)

/** Monthly budget limit for a category with selectable rollover behavior. */
data class Budget(
    val id: Long = 0L,
    val categoryId: Long,
    val currency: CurrencyCode,
    val limitCents: Long,
    val rolloverMode: RolloverMode = RolloverMode.RESET_MONTHLY,
    val alertThresholdPct: Int = DomainConstants.DEFAULT_ALERT_THRESHOLD_PCT,
    val createdAtMillis: Long = 0L
)

/** Recurring bill or subscription. */
data class RecurringBill(
    val id: Long = 0L,
    val name: String,
    val amountCents: Long,
    val currency: CurrencyCode,
    val categoryId: Long?,
    val dueDayOfMonth: Int,
    val isPaidThisMonth: Boolean = false,
    val autoLog: Boolean = false
)

/** Dedicated savings goal / sinking fund. */
data class SavingsGoal(
    val id: Long = 0L,
    val name: String,
    val currency: CurrencyCode,
    val targetCents: Long,
    val currentCents: Long
)

/** Money owed to the user (IOUs, split bills, or reimbursements). */
data class Receivable(
    val id: Long = 0L,
    val personName: String,
    val description: String = "",
    val currency: CurrencyCode,
    val totalOwedCents: Long,
    val paidBackCents: Long = 0L,
    val isSettled: Boolean = false,
    val createdAtMillis: Long = System.currentTimeMillis()
) {
    val remainingCents: Long get() = (totalOwedCents - paidBackCents).coerceAtLeast(0L)
}

/** Money the user owes to someone else (debts, split bills, or loans). */
data class Payable(
    val id: Long = 0L,
    val personName: String,
    val description: String = "",
    val currency: CurrencyCode,
    val totalOweCents: Long,
    val paidCents: Long = 0L,
    val isSettled: Boolean = false,
    val createdAtMillis: Long = System.currentTimeMillis()
) {
    val remainingCents: Long get() = (totalOweCents - paidCents).coerceAtLeast(0L)
}

/** Single item in a reusable or one-off shopping list. */
data class ShoppingListItem(
    val id: Long = 0L,
    val listId: Long = 0L,
    val name: String,
    val quantity: Int = 1,
    val estimatedUnitCents: Long = 0L,
    val actualUnitCents: Long? = null,
    val isChecked: Boolean = false
) {
    val effectiveUnitCents: Long get() = actualUnitCents ?: estimatedUnitCents
    val totalCents: Long get() = effectiveUnitCents * quantity.coerceAtLeast(1)
}

/** Shopping list tied optionally to a currency and budget category. */
data class ShoppingList(
    val id: Long = 0L,
    val name: String,
    val currency: CurrencyCode,
    val categoryId: Long? = null,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val items: List<ShoppingListItem> = emptyList()
)

/** Configured currency-to-USD exchange rates (1 USD = ratesPerUsd[c] units of c). */
data class ExchangeRateConfig(
    val ratesPerUsd: Map<CurrencyCode, BigDecimal> = DomainConstants.DEFAULT_RATES_PER_USD,
    val updatedAtMillis: Long = 0L
) {
    val zwgPerUsd: BigDecimal
        get() = rateFor(CurrencyCode.ZWG)

    fun rateFor(currency: CurrencyCode): BigDecimal =
        if (currency == CurrencyCode.USD) {
            BigDecimal.ONE
        } else {
            ratesPerUsd[currency]?.takeIf { it > BigDecimal.ZERO }
                ?: DomainConstants.DEFAULT_RATES_PER_USD[currency]
                ?: BigDecimal.ONE
        }

    fun withRate(currency: CurrencyCode, ratePerUsd: BigDecimal): ExchangeRateConfig {
        if (currency == CurrencyCode.USD || ratePerUsd <= BigDecimal.ZERO) return this
        return copy(ratesPerUsd = ratesPerUsd + (currency to ratePerUsd))
    }

    /** Returns the rate snapshot to persist on a transaction (uses target currency for USD -> non-USD transfers). */
    fun rateForTransaction(from: CurrencyCode, targetCurrency: CurrencyCode? = null): BigDecimal =
        if (from == CurrencyCode.USD && targetCurrency != null && targetCurrency != CurrencyCode.USD) {
            rateFor(targetCurrency)
        } else {
            rateFor(from)
        }

    /** Applies a persisted transaction [storedRate] for a transfer between [from] and [targetCurrency]. */
    fun withTransactionRate(
        from: CurrencyCode,
        targetCurrency: CurrencyCode,
        storedRate: BigDecimal?
    ): ExchangeRateConfig {
        if (storedRate == null || storedRate <= BigDecimal.ZERO || from == targetCurrency) return this
        return when {
            from == CurrencyCode.USD && targetCurrency != CurrencyCode.USD ->
                if (storedRate.compareTo(BigDecimal.ONE) != 0) withRate(targetCurrency, storedRate) else this
            from != CurrencyCode.USD -> withRate(from, storedRate)
            else -> this
        }
    }
}

/** Computed budget status for a single category in a given month. */
data class CategoryBudgetStatus(
    val budget: Budget,
    val category: Category,
    val effectiveLimitCents: Long,
    val spentCents: Long,
    val carryoverCents: Long,
    val remainingCents: Long,
    val percentUsed: Int,
    val health: BudgetHealth
)

/** Aggregated monthly financial summary in the active display currency. */
data class MonthlySummary(
    val month: YearMonth,
    val displayCurrency: CurrencyCode,
    val totalBudgetCents: Long,
    val totalSpentCents: Long,
    val totalIncomeCents: Long,
    val remainingCents: Long,
    val safePerDayCents: Long,
    val monthProgressPct: Int,
    val budgetUsedPct: Int
)

/** Output from on-device receipt OCR parsing. */
data class ParsedReceipt(
    val merchant: String = "",
    val currency: CurrencyCode = CurrencyCode.USD,
    val totalCents: Long? = null,
    val items: List<LineItem> = emptyList(),
    val rawText: String = ""
)

/** Unified net position combining wallets and unsettled receivables in the active display currency. */
data class NetPosition(
    val displayCurrency: CurrencyCode,
    val walletsTotalCents: Long,
    val operatingTotalCents: Long,
    val savingsTotalCents: Long,
    val walletsByCurrency: Map<CurrencyCode, Long> = emptyMap(),
    val receivablesTotalCents: Long,
    val unsettledReceivablesCount: Int,
    val payablesTotalCents: Long = 0L,
    val unsettledPayablesCount: Int = 0,
    val netPositionCents: Long
) {
    val usdWalletsCents: Long get() = walletsByCurrency[CurrencyCode.USD] ?: 0L
    val zwgWalletsCents: Long get() = walletsByCurrency[CurrencyCode.ZWG] ?: 0L
}

/** Month-over-month spending comparison in the active display currency. */
data class MonthOverMonthComparison(
    val currentMonthSpentCents: Long,
    val previousMonthSpentCents: Long,
    val deltaCents: Long,
    val deltaPercent: Int?
)

/** Projected end-of-month spend and budget surplus/deficit at current daily burn rate. */
data class BurnRateProjection(
    val dailyBurnCents: Long,
    val projectedMonthSpendCents: Long,
    val projectedRemainingCents: Long,
    val elapsedDays: Int,
    val daysInMonth: Int
)

/** Top merchant or purchased line-item by total spend in the active display currency. */
data class TopSpendEntry(
    val name: String,
    val amountCents: Long,
    val count: Int
)

/** Single currency's share of monthly expenses. */
data class CurrencySpendEntry(
    val currency: CurrencyCode,
    val spentCents: Long,
    val percent: Int
)

/** Breakdown of expenses by original transaction currency. */
data class CurrencySpendSplit(
    val entries: List<CurrencySpendEntry> = emptyList()
) {
    val usdSpentCents: Long get() = entries.firstOrNull { it.currency == CurrencyCode.USD }?.spentCents ?: 0L
    val zwgSpentCents: Long get() = entries.firstOrNull { it.currency == CurrencyCode.ZWG }?.spentCents ?: 0L
    val usdPercent: Int get() = entries.firstOrNull { it.currency == CurrencyCode.USD }?.percent ?: 0
    val zwgPercent: Int get() = entries.firstOrNull { it.currency == CurrencyCode.ZWG }?.percent ?: 0
}

/** Complete monthly spending insights in the active display currency. */
data class SpendingInsights(
    val monthOverMonth: MonthOverMonthComparison,
    val projection: BurnRateProjection,
    val topMerchants: List<TopSpendEntry>,
    val topItems: List<TopSpendEntry>,
    val currencySplit: CurrencySpendSplit,
    val onTrackBudgets: Int,
    val warningBudgets: Int,
    val overBudgets: Int
)
