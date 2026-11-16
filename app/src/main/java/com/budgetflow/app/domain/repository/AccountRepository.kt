package com.budgetflow.app.domain.repository

import com.budgetflow.app.domain.model.Account
import kotlinx.coroutines.flow.Flow

interface AccountRepository {
    fun observeAccounts(): Flow<List<Account>>
    fun observeAllAccounts(): Flow<List<Account>>
    suspend fun saveAccount(account: Account)
    suspend fun setAccountArchived(accountId: Long, archived: Boolean)
    suspend fun setAccountShowOnHome(accountId: Long, showOnHome: Boolean)
    suspend fun deleteAccount(accountId: Long)
}
