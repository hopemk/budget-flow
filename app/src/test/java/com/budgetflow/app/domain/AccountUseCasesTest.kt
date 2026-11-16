package com.budgetflow.app.domain

import com.budgetflow.app.domain.model.Account
import com.budgetflow.app.domain.model.AccountType
import com.budgetflow.app.domain.model.CurrencyCode
import com.budgetflow.app.domain.repository.AccountRepository
import com.budgetflow.app.domain.usecase.AccountUseCases
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountUseCasesTest {

    private class FakeAccountRepository : AccountRepository {
        private val accounts = MutableStateFlow<List<Account>>(emptyList())
        private var nextId = 1L

        override fun observeAccounts(): Flow<List<Account>> =
            accounts.map { list -> list.filter { !it.isArchived } }

        override fun observeAllAccounts(): Flow<List<Account>> = accounts

        override suspend fun saveAccount(account: Account) {
            val saved = if (account.id == 0L) account.copy(id = nextId++) else account
            accounts.value = accounts.value.filterNot { it.id == saved.id } + saved
        }

        override suspend fun setAccountArchived(accountId: Long, archived: Boolean) {
            accounts.value = accounts.value.map {
                if (it.id == accountId) it.copy(isArchived = archived) else it
            }
        }

        override suspend fun setAccountShowOnHome(accountId: Long, showOnHome: Boolean) {
            accounts.value = accounts.value.map {
                if (it.id == accountId) it.copy(showOnHome = showOnHome) else it
            }
        }

        override suspend fun deleteAccount(accountId: Long) {
            setAccountArchived(accountId, true)
        }

        fun all(): List<Account> = accounts.value
    }

    @Test
    fun `addAccount trims name, parses balance, and saves`() = runBlocking {
        val repo = FakeAccountRepository()
        val useCases = AccountUseCases(repo)

        val result = useCases.addAccount(
            name = "  Main Wallet  ",
            type = AccountType.CASH,
            currency = CurrencyCode.USD,
            balanceInput = "125.50",
            showOnHome = true
        )

        assertTrue(result.isSuccess)
        val saved = repo.all().single()
        assertEquals("Main Wallet", saved.name)
        assertEquals(12550L, saved.balanceCents)
        assertEquals(AccountType.CASH, saved.type)
        assertTrue(saved.showOnHome)
    }

    @Test
    fun `addAccount defaults blank balance to zero`() = runBlocking {
        val repo = FakeAccountRepository()
        val useCases = AccountUseCases(repo)

        val result = useCases.addAccount("Reserve", AccountType.SAVINGS, CurrencyCode.ZWG, "   ")

        assertTrue(result.isSuccess)
        assertEquals(0L, repo.all().single().balanceCents)
    }

    @Test
    fun `addAccount fails on blank name or invalid balance`() = runBlocking {
        val repo = FakeAccountRepository()
        val useCases = AccountUseCases(repo)

        val blankName = useCases.addAccount("  ", AccountType.CASH, CurrencyCode.USD, "10.00")
        assertTrue(blankName.isFailure)

        val badBalance = useCases.addAccount("Cash", AccountType.CASH, CurrencyCode.USD, "not-a-number")
        assertTrue(badBalance.isFailure)
        assertTrue(repo.all().isEmpty())
    }

    @Test
    fun `updateAccount validates name and balance and preserves soft-delete archive behavior`() = runBlocking {
        val repo = FakeAccountRepository()
        val useCases = AccountUseCases(repo)
        useCases.addAccount("Cash", AccountType.CASH, CurrencyCode.USD, "50.00")
        val initial = repo.all().single()

        val badUpdate = useCases.updateAccount(initial, "", AccountType.BANK, "75.00", false)
        assertTrue(badUpdate.isFailure)

        val goodUpdate = useCases.updateAccount(initial, " Bank ", AccountType.BANK, "75.00", false)
        assertTrue(goodUpdate.isSuccess)
        val updated = repo.all().single()
        assertEquals("Bank", updated.name)
        assertEquals(7500L, updated.balanceCents)
        assertEquals(false, updated.showOnHome)

        useCases.deleteAccount(updated.id)
        assertTrue(repo.all().single().isArchived)

        useCases.restoreAccount(updated.id)
        assertEquals(false, repo.all().single().isArchived)
    }
}
