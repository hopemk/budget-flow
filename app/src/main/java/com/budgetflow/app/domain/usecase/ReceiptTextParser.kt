package com.budgetflow.app.domain.usecase

import com.budgetflow.app.domain.model.CurrencyCode
import com.budgetflow.app.domain.model.LineItem
import com.budgetflow.app.domain.model.ParsedReceipt

/**
 * Extracts merchant name, currency (USD vs ZWG/ZiG), total, and line items
 * from raw OCR text produced by ML Kit Text Recognition.
 */
class ReceiptTextParser {

    private val priceRegex = Regex("""(\d{1,3}(?:,\d{3})*(?:\.\d{2})|\d+\.\d{2})\s*$""")
    private val zwgCueRegex = Regex("""\b(ZWG|ZIG)\b""", RegexOption.IGNORE_CASE)
    private val totalCueRegex = Regex("""\b(TOTAL|AMOUNT\s+DUE|BALANCE\s+DUE|GRAND\s+TOTAL)\b""", RegexOption.IGNORE_CASE)
    private val ignoreLineRegex = Regex("""\b(SUBTOTAL|TAX|VAT|CHANGE|CASH|CARD|VISA|MASTERCARD|THANK\s+YOU)\b""", RegexOption.IGNORE_CASE)

    fun parse(rawText: String): ParsedReceipt {
        val lines = rawText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return ParsedReceipt(rawText = rawText)

        val currency = if (zwgCueRegex.containsMatchIn(rawText)) CurrencyCode.ZWG else CurrencyCode.USD
        val merchant = lines.firstOrNull { line ->
            line.any { it.isLetter() } && !priceRegex.containsMatchIn(line)
        }.orEmpty()

        var totalCents: Long? = null
        val items = mutableListOf<LineItem>()

        for (line in lines) {
            val match = priceRegex.find(line) ?: continue
            val amountCents = CurrencyConverter.parseToCents(match.groupValues[1]) ?: continue
            val label = line.substring(0, match.range.first).trim().trimEnd('-', ':')

            if (totalCueRegex.containsMatchIn(line)) {
                totalCents = amountCents
            } else if (label.isNotBlank() && !ignoreLineRegex.containsMatchIn(line)) {
                items += LineItem(name = label, amountCents = amountCents)
            }
        }

        if (totalCents == null && items.isNotEmpty()) {
            totalCents = items.sumOf { it.amountCents }
        }

        return ParsedReceipt(
            merchant = merchant,
            currency = currency,
            totalCents = totalCents,
            items = items,
            rawText = rawText
        )
    }
}
