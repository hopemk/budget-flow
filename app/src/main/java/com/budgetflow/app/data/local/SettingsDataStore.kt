package com.budgetflow.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.budgetflow.app.domain.DomainConstants
import com.budgetflow.app.domain.model.CurrencyCode
import com.budgetflow.app.domain.model.DisplayCurrencyMode
import com.budgetflow.app.domain.model.ExchangeRateConfig
import com.budgetflow.app.domain.model.ThemeMode
import com.budgetflow.app.domain.util.SystemTimeProvider
import com.budgetflow.app.domain.util.TimeProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.math.BigDecimal

private val Context.dataStore by preferencesDataStore(name = "budgetflow_settings")

/** Persists theme, multi-currency display mode, currency->USD rates, and security lock preference. */
class SettingsDataStore(
    private val context: Context,
    private val timeProvider: TimeProvider = SystemTimeProvider()
) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DISPLAY_CURRENCY_MODE = stringPreferencesKey("display_currency_mode")
        val ZWG_PER_USD = stringPreferencesKey("zwg_per_usd")
        val RATE_UPDATED_AT = longPreferencesKey("rate_updated_at")
        val SECURITY_ENABLED = booleanPreferencesKey("security_enabled")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val BILL_REMINDERS_ENABLED = booleanPreferencesKey("bill_reminders_enabled")
        val LAST_BILL_RESET_MONTH = stringPreferencesKey("last_bill_reset_month")

        fun rateKey(currency: CurrencyCode) = stringPreferencesKey("rate_per_usd_${currency.name}")
    }

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        prefs[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            ?: ThemeMode.LIGHT
    }

    val displayCurrencyMode: Flow<DisplayCurrencyMode> = context.dataStore.data.map { prefs ->
        DisplayCurrencyMode.deserialize(prefs[Keys.DISPLAY_CURRENCY_MODE])
    }

    val exchangeRate: Flow<ExchangeRateConfig> = context.dataStore.data.map { prefs ->
        val rates = buildMap {
            put(CurrencyCode.USD, BigDecimal.ONE)
            CurrencyCode.entries.filter { it != CurrencyCode.USD }.forEach { currency ->
                val raw = prefs[Keys.rateKey(currency)]
                    ?: if (currency == CurrencyCode.ZWG) prefs[Keys.ZWG_PER_USD] else null
                val parsed = raw?.let { runCatching { BigDecimal(it) }.getOrNull() }
                    ?.takeIf { it > BigDecimal.ZERO }
                    ?: DomainConstants.DEFAULT_RATES_PER_USD[currency]
                    ?: BigDecimal.ONE
                put(currency, parsed)
            }
        }
        val updated = prefs[Keys.RATE_UPDATED_AT] ?: 0L
        ExchangeRateConfig(ratesPerUsd = rates, updatedAtMillis = updated)
    }

    val securityEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.SECURITY_ENABLED] ?: false
    }

    val onboardingCompleted: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.ONBOARDING_COMPLETED] ?: false
    }

    val billRemindersEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.BILL_REMINDERS_ENABLED] ?: false
    }

    val lastBillResetMonth: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[Keys.LAST_BILL_RESET_MONTH]
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setDisplayCurrencyMode(mode: DisplayCurrencyMode) {
        context.dataStore.edit { it[Keys.DISPLAY_CURRENCY_MODE] = mode.serialize() }
    }

    suspend fun setExchangeRate(zwgPerUsd: String) {
        setExchangeRate(CurrencyCode.ZWG, zwgPerUsd)
    }

    suspend fun setExchangeRate(currency: CurrencyCode, perUsd: String) {
        if (currency == CurrencyCode.USD) return
        val parsed = runCatching { BigDecimal(perUsd.trim()) }.getOrNull() ?: return
        if (parsed <= BigDecimal.ZERO) return
        val plain = parsed.toPlainString()
        context.dataStore.edit { prefs ->
            prefs[Keys.rateKey(currency)] = plain
            if (currency == CurrencyCode.ZWG) {
                prefs[Keys.ZWG_PER_USD] = plain
            }
            prefs[Keys.RATE_UPDATED_AT] = timeProvider.currentTimeMillis()
        }
    }

    suspend fun setSecurityEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SECURITY_ENABLED] = enabled }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { it[Keys.ONBOARDING_COMPLETED] = completed }
    }

    suspend fun setBillRemindersEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.BILL_REMINDERS_ENABLED] = enabled }
    }

    suspend fun setLastBillResetMonth(yearMonth: String) {
        context.dataStore.edit { it[Keys.LAST_BILL_RESET_MONTH] = yearMonth }
    }
}
