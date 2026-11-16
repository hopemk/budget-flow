package com.budgetflow.app.domain

import com.budgetflow.app.domain.model.Category
import com.budgetflow.app.domain.model.CurrencyCode
import com.budgetflow.app.domain.model.LineItem
import com.budgetflow.app.domain.model.Transaction
import com.budgetflow.app.domain.model.TransactionType
import com.budgetflow.app.domain.usecase.CsvExporter
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.ZoneOffset

class CsvExporterTest {

    @Test
    fun `exports header and escaped transaction row`() {
        val exporter = CsvExporter(ZoneOffset.UTC)
        val cat = Category(id = 1L, name = "Groceries", iconName = "cart", colorHex = 0xFF059669)
        val tx = Transaction(
            id = 1L,
            accountId = 1L,
            categoryId = 1L,
            type = TransactionType.EXPENSE,
            currency = CurrencyCode.USD,
            amountCents = 1250L,
            exchangeRateUsed = BigDecimal("26.50"),
            merchant = "Pick n Pay, Harare",
            note = "Weekly run",
            timestampMillis = 0L,
            items = listOf(LineItem(name = "Bread", amountCents = 250L))
        )

        val csv = exporter.export(listOf(tx), listOf(cat))
        assertTrue(csv.contains("Date,Type,Merchant,Category,Currency,Amount,ExchangeRateZwgPerUsd,Items,Note"))
        assertTrue(csv.contains("\"Pick n Pay, Harare\""))
        assertTrue(csv.contains("12.50"))
    }
}
