package com.budgetflow.app.domain.usecase

import com.budgetflow.app.domain.model.Category
import com.budgetflow.app.domain.model.Transaction
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Formats transactions into RFC-4180-safe CSV for user backup and spreadsheets. */
class CsvExporter(
    private val zoneId: ZoneId = ZoneId.systemDefault()
) {

    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    fun export(transactions: List<Transaction>, categories: List<Category>): String {
        val categoryMap = categories.associateBy { it.id }
        val sb = StringBuilder()
        sb.appendLine("Date,Type,Merchant,Category,Currency,Amount,ExchangeRateZwgPerUsd,Items,Note")

        for (tx in transactions) {
            val date = dateFormatter.format(Instant.ofEpochMilli(tx.timestampMillis).atZone(zoneId))
            val categoryName = tx.categoryId?.let { categoryMap[it]?.name } ?: "Uncategorized"
            val majorAmount = CurrencyConverter.formatInput(tx.amountCents)
            val itemsSummary = tx.items.joinToString("; ") { "${it.name}:${it.amountCents / 100.0}" }

            val row = listOf(
                date,
                tx.type.name,
                tx.merchant,
                categoryName,
                tx.currency.name,
                majorAmount,
                tx.exchangeRateUsed.toPlainString(),
                itemsSummary,
                tx.note
            ).joinToString(",") { escapeCsv(it) }

            sb.appendLine(row)
        }
        return sb.toString()
    }

    private fun escapeCsv(value: String): String {
        val needsQuotes = value.contains(',') || value.contains('"') || value.contains('\n')
        if (!needsQuotes) return value
        return "\"${value.replace("\"", "\"\"")}\""
    }
}
