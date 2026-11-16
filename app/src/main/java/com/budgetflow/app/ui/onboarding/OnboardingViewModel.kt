package com.budgetflow.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgetflow.app.domain.model.Account
import com.budgetflow.app.domain.model.AccountType
import com.budgetflow.app.domain.model.CurrencyCode
import com.budgetflow.app.domain.repository.AccountRepository
import com.budgetflow.app.domain.repository.PreferencesRepository
import com.budgetflow.app.domain.usecase.CurrencyConverter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    private val accountRepository: AccountRepository
) : ViewModel() {

    fun completeOnboarding(
        walletName: String,
        walletType: AccountType,
        walletCurrency: CurrencyCode,
        startingBalanceInput: String,
        exchangeRateInput: String
    ) {
        viewModelScope.launch {
            if (exchangeRateInput.isNotBlank()) {
                preferencesRepository.setExchangeRate(exchangeRateInput)
            }
            if (walletName.isNotBlank()) {
                val cents = CurrencyConverter.parseToCents(startingBalanceInput) ?: 0L
                accountRepository.saveAccount(
                    Account(
                        name = walletName.trim(),
                        type = walletType,
                        currency = walletCurrency,
                        balanceCents = cents,
                        showOnHome = true
                    )
                )
            }
            preferencesRepository.setOnboardingCompleted(true)
        }
    }

    fun skipOnboarding() {
        viewModelScope.launch {
            preferencesRepository.setOnboardingCompleted(true)
        }
    }
}
