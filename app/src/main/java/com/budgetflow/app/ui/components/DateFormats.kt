package com.budgetflow.app.ui.components

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Shared date formatters and formatting helpers for UI presentation. */
object DateFormats {
    val SHORT_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM dd")
    val MONTH_YEAR: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM yyyy")
    val FULL_DAY_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM dd, yyyy")

    fun formatShortDate(timestampMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(timestampMillis)
            .atZone(zoneId)
            .toLocalDate()
            .format(SHORT_DATE)

    fun formatFullDayDate(timestampMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(timestampMillis)
            .atZone(zoneId)
            .toLocalDate()
            .format(FULL_DAY_DATE)
}
