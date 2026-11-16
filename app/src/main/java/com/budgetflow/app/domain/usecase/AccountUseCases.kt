package com.budgetflow.app.domain.usecase

import com.budgetflow.app.domain.model.Account
import com.budgetflow.app.domain.model.AccountType
import com.budgetflow.app.domain.model.CurrencyCode
import com.budgetflow.app.domain.repository.AccountRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountUseCases @Inject constructor(
    private val accountRepository: AccountRepository
) {
    suspend fun addAccount(
        name: String,
        type: AccountType,
        currency: CurrencyCode,
        balanceInput: String,
        showOnHome: Boolean = true
    ): Result<Unit> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("Account name cannot be empty."))
        }
        val cents = if (balanceInput.isBlank()) {
            0L
        } else {
            CurrencyConverter.parseToCents(balanceInput)
                ?: return Result.failure(IllegalArgumentException("Enter a valid starting balance."))
        }
        accountRepository.saveAccount(
            Account(
                name = trimmed,
                type = type,
                currency = currency,
                balanceCents = cents,
                showOnHome = showOnHome
            )
        )
        return Result.success(Unit)
    }

    suspend fun updateAccount(
        account: Account,
        newName: String,
        newType: AccountType,
        balanceInput: String,
        showOnHome: Boolean = account.showOnHome
    ): Result<Unit> {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("Account name cannot be empty."))
        }
        val cents = if (balanceInput.isBlank()) {
            account.balanceCents
        } else {
            CurrencyConverter.parseToCents(balanceInput)
                ?: return Result.failure(IllegalArgumentException("Enter a valid balance."))
        }
        accountRepository.saveAccount(
            account.copy(
                name = trimmed,
                type = newType,
                balanceCents = cents,
                showOnHome = showOnHome
            )
        )
        return Result.success(Unit)
    }

    suspend fun setShowOnHome(accountId: Long, showOnHome: Boolean) {
        accountRepository.setAccountShowOnHome(accountId, showOnHome)
    }

    suspend fun archiveAccount(accountId: Long) {
        accountRepository.setAccountArchived(accountId, true)
    }

    suspend fun restoreAccount(accountId: Long) {
        accountRepository.setAccountArchived(accountId, false)
    }

    suspend fun deleteAccount(accountId: Long) {
        accountRepository.deleteAccount(accountId)
    }
}
