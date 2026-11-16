package com.budgetflow.app.ui.scanner

import com.budgetflow.app.domain.model.CurrencyCode
import com.budgetflow.app.domain.usecase.ReceiptTextParser
import org.junit.Assert.assertEquals
import org.junit.Test

class ReceiptScannerViewModelTest {

    @Test
    fun `initial status and setStatus update state`() {
        val vm = ReceiptScannerViewModel(ReceiptTextParser())
        assertEquals("Align receipt in frame and tap Capture", vm.statusText.value)

        vm.setStatus("Scanning receipt...")
        assertEquals("Scanning receipt...", vm.statusText.value)
    }

    @Test
    fun `parseReceiptText delegates to ReceiptTextParser`() {
        val vm = ReceiptScannerViewModel(ReceiptTextParser())
        val parsed = vm.parseReceiptText(
            """
            Fresh Mart
            Milk 2.50
            Bread 1.50
            TOTAL USD 4.00
            """.trimIndent()
        )

        assertEquals("Fresh Mart", parsed.merchant)
        assertEquals(CurrencyCode.USD, parsed.currency)
        assertEquals(400L, parsed.totalCents)
    }
}
