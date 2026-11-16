package com.budgetflow.app.domain

import com.budgetflow.app.domain.model.CurrencyCode
import com.budgetflow.app.domain.usecase.ReceiptTextParser
import org.junit.Assert.assertEquals
import org.junit.Test

class ReceiptTextParserTest {

    private val parser = ReceiptTextParser()

    @Test
    fun `parses USD receipt merchant items and total`() {
        val raw = """
            TRADER JOE'S
            Organic Milk 4.50
            Sourdough Bread 3.50
            SUBTOTAL 8.00
            TOTAL 8.00
        """.trimIndent()

        val parsed = parser.parse(raw)
        assertEquals("TRADER JOE'S", parsed.merchant)
        assertEquals(CurrencyCode.USD, parsed.currency)
        assertEquals(800L, parsed.totalCents)
        assertEquals(2, parsed.items.size)
    }

    @Test
    fun `detects ZWG currency cue on receipt`() {
        val raw = """
            OK SUPERMARKET
            Maize Meal ZWG 120.00
            TOTAL ZWG 120.00
        """.trimIndent()

        val parsed = parser.parse(raw)
        assertEquals(CurrencyCode.ZWG, parsed.currency)
        assertEquals(12000L, parsed.totalCents)
    }
}
