package com.budgetflow.app.domain.repository

import com.budgetflow.app.domain.model.CurrencyCode
import com.budgetflow.app.domain.model.DisplayCurrencyMode
import com.budgetflow.app.domain.model.ExchangeRateConfig
import com.budgetflow.app.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

interface PreferencesRepository {
    fun observeThemeMode(): Flow<ThemeMode>
    fun observeDisplayCurrencyMode(): Flow<DisplayCurrencyMode>
    fun observeExchangeRate(): Flow<ExchangeRateConfig>
    fun observeSecurityEnabled(): Flow<Boolean>
    fun observeOnboardingCompleted(): Flow<Boolean>
    fun observeBillRemindersEnabled(): Flow<Boolean>
    fun observeLastBillResetMonth(): Flow<String?>

    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setDisplayCurrencyMode(mode: DisplayCurrencyMode)
    suspend fun setExchangeRate(zwgPerUsd: String)
    suspend fun setExchangeRate(currency: CurrencyCode, perUsd: String)
    suspend fun setSecurityEnabled(enabled: Boolean)
    suspend fun setOnboardingCompleted(completed: Boolean)
    suspend fun setBillRemindersEnabled(enabled: Boolean)
    suspend fun setLastBillResetMonth(yearMonth: String)
}
