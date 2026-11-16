package com.budgetflow.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgetflow.app.domain.DomainConstants
import com.budgetflow.app.domain.model.Account
import com.budgetflow.app.domain.model.AccountType
import com.budgetflow.app.domain.model.CurrencyCode
import com.budgetflow.app.domain.model.ThemeMode
import com.budgetflow.app.domain.repository.AccountRepository
import com.budgetflow.app.domain.repository.BackupRepository
import com.budgetflow.app.domain.repository.BackupStorage
import com.budgetflow.app.domain.repository.CategoryRepository
import com.budgetflow.app.domain.repository.PreferencesRepository
import com.budgetflow.app.domain.repository.TransactionRepository
import com.budgetflow.app.domain.usecase.AccountUseCases
import com.budgetflow.app.domain.usecase.CsvExporter
import com.budgetflow.app.domain.util.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.LIGHT,
    val zwgPerUsd: String = DomainConstants.DEFAULT_ZWG_PER_USD,
    val ratesPerUsd: Map<CurrencyCode, String> = DomainConstants.DEFAULT_RATES_PER_USD
        .filterKeys { it != CurrencyCode.USD }
        .mapValues { it.value.toPlainString() },
    val securityEnabled: Boolean = false,
    val billRemindersEnabled: Boolean = false,
    val accounts: List<Account> = emptyList(),
    val archivedAccounts: List<Account> = emptyList()
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    accountRepository: AccountRepository,
    private val accountUseCases: AccountUseCases,
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val backupRepository: BackupRepository,
    private val backupStorage: BackupStorage,
    private val reminderScheduler: ReminderScheduler,
    private val csvExporter: CsvExporter
) : ViewModel() {

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    val uiState: StateFlow<SettingsUiState> = combine(
        preferencesRepository.observeThemeMode(),
        preferencesRepository.observeExchangeRate(),
        preferencesRepository.observeSecurityEnabled(),
        preferencesRepository.observeBillRemindersEnabled(),
        accountRepository.observeAllAccounts()
    ) { theme, rateConfig, security, reminders, allAccounts ->
        val rateStrings = CurrencyCode.entries
            .filter { it != CurrencyCode.USD }
            .associateWith { rateConfig.rateFor(it).toPlainString() }
        SettingsUiState(
            themeMode = theme,
            zwgPerUsd = rateConfig.zwgPerUsd.toPlainString(),
            ratesPerUsd = rateStrings,
            securityEnabled = security,
            billRemindersEnabled = reminders,
            accounts = allAccounts.filter { !it.isArchived },
            archivedAccounts = allAccounts.filter { it.isArchived }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferencesRepository.setThemeMode(mode) }
    }

    fun updateExchangeRate(zwgPerUsd: String) {
        updateExchangeRate(CurrencyCode.ZWG, zwgPerUsd)
    }

    fun updateExchangeRate(currency: CurrencyCode, perUsd: String) {
        val parsed = perUsd.trim().toBigDecimalOrNull()
        if (parsed == null || parsed <= BigDecimal.ZERO) {
            _messages.tryEmit("Enter a valid positive exchange rate.")
            return
        }
        viewModelScope.launch {
            preferencesRepository.setExchangeRate(currency, perUsd.trim())
            _messages.tryEmit("${currency.name} rate updated.")
        }
    }

    fun setSecurityEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setSecurityEnabled(enabled) }
    }

    fun setBillRemindersEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setBillRemindersEnabled(enabled)
            reminderScheduler.setBillRemindersEnabled(enabled)
        }
    }

    fun addAccount(name: String, type: AccountType, currency: CurrencyCode, balanceInput: String) {
        viewModelScope.launch {
            accountUseCases.addAccount(name, type, currency, balanceInput)
                .onFailure { err -> err.message?.let { _messages.tryEmit(it) } }
        }
    }

    fun updateAccount(account: Account, newName: String, newType: AccountType, balanceInput: String, showOnHome: Boolean = account.showOnHome) {
        viewModelScope.launch {
            accountUseCases.updateAccount(account, newName, newType, balanceInput, showOnHome)
                .onFailure { err -> err.message?.let { _messages.tryEmit(it) } }
        }
    }

    fun archiveAccount(accountId: Long) {
        viewModelScope.launch { accountUseCases.archiveAccount(accountId) }
    }

    fun restoreAccount(accountId: Long) {
        viewModelScope.launch { accountUseCases.restoreAccount(accountId) }
    }

    fun deleteAccount(accountId: Long) {
        viewModelScope.launch { accountUseCases.deleteAccount(accountId) }
    }

    fun buildCsvExport(onReady: (String) -> Unit) {
        viewModelScope.launch {
            val txs = transactionRepository.observeTransactions().first()
            val cats = categoryRepository.observeCategories(includeArchived = true).first()
            onReady(csvExporter.export(txs, cats))
        }
    }

    fun exportBackupToUri(uriString: String) {
        viewModelScope.launch {
            val json = backupRepository.exportBackupJson()
            backupStorage.writeText(uriString, json)
                .onSuccess { _messages.tryEmit("Backup exported") }
                .onFailure { _messages.tryEmit("Export failed: ${it.message}") }
        }
    }

    fun restoreBackupFromUri(uriString: String) {
        viewModelScope.launch {
            val readResult = backupStorage.readText(uriString)
            readResult.onFailure { err ->
                _messages.tryEmit("Could not read backup file: ${err.message ?: "unknown error"}")
                return@launch
            }
            val json = readResult.getOrNull()
            if (json.isNullOrBlank()) {
                _messages.tryEmit("Backup file is empty.")
                return@launch
            }
            backupRepository.restoreBackupJson(json)
                .onSuccess { _messages.tryEmit("Backup restored") }
                .onFailure { _messages.tryEmit("Restore failed: ${it.message}") }
        }
    }
}
