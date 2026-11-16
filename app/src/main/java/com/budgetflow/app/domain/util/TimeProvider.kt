package com.budgetflow.app.domain.util

import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

interface TimeProvider {
    fun currentTimeMillis(): Long
    fun today(): LocalDate
    fun currentMonth(): YearMonth
}

@Singleton
class SystemTimeProvider @Inject constructor() : TimeProvider {
    override fun currentTimeMillis(): Long = System.currentTimeMillis()
    override fun today(): LocalDate = LocalDate.now()
    override fun currentMonth(): YearMonth = YearMonth.now()
}
