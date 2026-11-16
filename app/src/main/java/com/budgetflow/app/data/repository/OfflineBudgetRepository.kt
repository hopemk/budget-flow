package com.budgetflow.app.data.repository

import androidx.room.withTransaction
import com.budgetflow.app.data.local.BackupSerializer
import com.budgetflow.app.data.local.BackupSnapshot
import com.budgetflow.app.data.local.BudgetDao
import com.budgetflow.app.data.local.BudgetDatabase
import com.budgetflow.app.data.local.LineItemEntity
import com.budgetflow.app.data.local.SettingsDataStore
import com.budgetflow.app.data.local.TransactionEntity
import com.budgetflow.app.data.local.toDomain
import com.budgetflow.app.data.local.toEntity
import com.budgetflow.app.domain.model.Account
import com.budgetflow.app.domain.model.Budget
import com.budgetflow.app.domain.model.Category
import com.budgetflow.app.domain.model.CurrencyCode
import com.budgetflow.app.domain.model.DisplayCurrencyMode
import com.budgetflow.app.domain.model.ExchangeRateConfig
import com.budgetflow.app.domain.model.Payable
import com.budgetflow.app.domain.model.Receivable
import com.budgetflow.app.domain.model.RecurringBill
import com.budgetflow.app.domain.model.SavingsGoal
import com.budgetflow.app.domain.model.ShoppingList
import com.budgetflow.app.domain.model.ShoppingListItem
import com.budgetflow.app.domain.model.ThemeMode
import com.budgetflow.app.domain.model.Transaction
import com.budgetflow.app.domain.model.TransactionType
import com.budgetflow.app.domain.repository.AccountRepository
import com.budgetflow.app.domain.repository.BackupRepository
import com.budgetflow.app.domain.repository.BillsGoalsRepository
import com.budgetflow.app.domain.repository.BudgetConfigRepository
import com.budgetflow.app.domain.repository.CategoryRepository
import com.budgetflow.app.domain.repository.DebtRepository
import com.budgetflow.app.domain.repository.PreferencesRepository
import com.budgetflow.app.domain.repository.ShoppingListRepository
import com.budgetflow.app.domain.repository.TransactionRepository
import com.budgetflow.app.domain.usecase.CurrencyConverter
import com.budgetflow.app.domain.usecase.RecordScheduledPaymentUseCase
import com.budgetflow.app.domain.util.TimeProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflineBudgetRepository @Inject constructor(
    private val db: BudgetDatabase,
    private val dao: BudgetDao,
    private val settings: SettingsDataStore,
    private val recordScheduledPayment: RecordScheduledPaymentUseCase,
    private val timeProvider: TimeProvider
) : AccountRepository,
    CategoryRepository,
    TransactionRepository,
    BudgetConfigRepository,
    BillsGoalsRepository,
    DebtRepository,
    ShoppingListRepository,
    PreferencesRepository,
    BackupRepository {

    override fun observeAccounts(): Flow<List<Account>> =
        dao.observeAccounts().map { list -> list.map { it.toDomain() } }

    override fun observeAllAccounts(): Flow<List<Account>> =
        dao.observeAllAccounts().map { list -> list.map { it.toDomain() } }

    override fun observeCategories(includeArchived: Boolean): Flow<List<Category>> =
        dao.observeCategories(includeArchived).map { list -> list.map { it.toDomain() } }

    override fun observeTransactions(): Flow<List<Transaction>> =
        dao.observeTransactionsWithItems().map { list -> list.map { it.toDomain() } }

    override fun observeBudgets(): Flow<List<Budget>> =
        dao.observeBudgets().map { list -> list.map { it.toDomain() } }

    override fun observeRecurringBills(): Flow<List<RecurringBill>> =
        dao.observeRecurringBills().map { list -> list.map { it.toDomain() } }

    override fun observeSavingsGoals(): Flow<List<SavingsGoal>> =
        dao.observeSavingsGoals().map { list -> list.map { it.toDomain() } }

    override fun observeReceivables(): Flow<List<Receivable>> =
        dao.observeReceivables().map { list -> list.map { it.toDomain() } }

    override fun observePayables(): Flow<List<Payable>> =
        dao.observePayables().map { list -> list.map { it.toDomain() } }

    override fun observeShoppingLists(): Flow<List<ShoppingList>> =
        dao.observeShoppingListsWithItems().map { list -> list.map { it.toDomain() } }

    override fun observeThemeMode(): Flow<ThemeMode> = settings.themeMode
    override fun observeDisplayCurrencyMode(): Flow<DisplayCurrencyMode> = settings.displayCurrencyMode
    override fun observeExchangeRate(): Flow<ExchangeRateConfig> = settings.exchangeRate
    override fun observeSecurityEnabled(): Flow<Boolean> = settings.securityEnabled
    override fun observeOnboardingCompleted(): Flow<Boolean> = settings.onboardingCompleted
    override fun observeBillRemindersEnabled(): Flow<Boolean> = settings.billRemindersEnabled
    override fun observeLastBillResetMonth(): Flow<String?> = settings.lastBillResetMonth

    override suspend fun getTransactionById(transactionId: Long): Transaction? =
        dao.getTransactionWithItems(transactionId)?.toDomain()

    override suspend fun saveTransaction(transaction: Transaction) {
        val txEntity = transaction.toEntity()
        val items = transaction.items.map {
            LineItemEntity(id = it.id, transactionId = transaction.id, name = it.name, amountCents = it.amountCents)
        }
        val rates = settings.exchangeRate.first()
        db.withTransaction {
            if (txEntity.id != 0L) {
                val existing = dao.getTransactionWithItems(txEntity.id)
                if (existing != null) {
                    applyBalanceEffect(existing.transaction, rates, reverse = true)
                    dao.deleteLineItemsForTransaction(txEntity.id)
                }
            }
            val savedId = dao.insertTransaction(txEntity)
            if (items.isNotEmpty()) {
                dao.insertLineItems(items.map { it.copy(transactionId = savedId) })
            }
            applyBalanceEffect(txEntity, rates, reverse = false)
        }
    }

    override suspend fun deleteTransaction(transactionId: Long) {
        val rates = settings.exchangeRate.first()
        db.withTransaction {
            val existing = dao.getTransactionWithItems(transactionId) ?: return@withTransaction
            applyBalanceEffect(existing.transaction, rates, reverse = true)
            dao.deleteTransactionById(transactionId)
        }
    }

    private suspend fun applyBalanceEffect(
        tx: TransactionEntity,
        rates: ExchangeRateConfig,
        reverse: Boolean
    ) {
        val sign = if (reverse) -1L else 1L
        val type = runCatching { TransactionType.valueOf(tx.type) }.getOrNull() ?: return
        when (type) {
            TransactionType.EXPENSE -> dao.adjustAccountBalance(tx.accountId, -tx.amountCents * sign)
            TransactionType.INCOME -> dao.adjustAccountBalance(tx.accountId, tx.amountCents * sign)
            TransactionType.TRANSFER -> {
                dao.adjustAccountBalance(tx.accountId, -tx.amountCents * sign)
                val targetId = tx.targetAccountId ?: return
                val targetAccount = dao.getAccountById(targetId) ?: return
                val fromCurrency = CurrencyCode.fromStringOrDefault(tx.currency)
                val targetCurrency = CurrencyCode.fromStringOrDefault(targetAccount.currency)
                val storedRate = runCatching { BigDecimal(tx.exchangeRateUsed) }
                    .getOrNull()
                    ?.takeIf { it > BigDecimal.ZERO }
                val effectiveRates = rates.withTransactionRate(fromCurrency, targetCurrency, storedRate)
                val credited = CurrencyConverter.convert(tx.amountCents, fromCurrency, targetCurrency, effectiveRates)
                dao.adjustAccountBalance(targetId, credited * sign)
            }
        }
    }

    override suspend fun saveCategory(category: Category): Long =
        dao.upsertCategory(category.toEntity())

    override suspend fun setCategoryArchived(categoryId: Long, archived: Boolean) {
        dao.setCategoryArchived(categoryId, archived)
    }

    override suspend fun deleteCategory(categoryId: Long) {
        dao.deleteCategoryAtomically(categoryId)
    }

    override suspend fun saveBudget(budget: Budget) {
        dao.upsertBudget(budget.toEntity())
    }

    override suspend fun deleteBudget(budgetId: Long) {
        dao.deleteBudgetById(budgetId)
    }

    override suspend fun saveAccount(account: Account) {
        dao.upsertAccount(account.toEntity())
    }

    override suspend fun setAccountArchived(accountId: Long, archived: Boolean) {
        dao.setAccountArchived(accountId, archived)
    }

    override suspend fun setAccountShowOnHome(accountId: Long, showOnHome: Boolean) {
        dao.setAccountShowOnHome(accountId, showOnHome)
    }

    /** Soft-deletes the account by archiving it so historical transactions remain intact. */
    override suspend fun deleteAccount(accountId: Long) {
        dao.setAccountArchived(accountId, true)
    }

    override suspend fun saveRecurringBill(bill: RecurringBill) {
        dao.upsertRecurringBill(bill.toEntity())
    }

    override suspend fun toggleBillPaid(billId: Long, paid: Boolean) {
        dao.setBillPaid(billId, paid)
    }

    override suspend fun payRecurringBill(billId: Long, debitAccountId: Long?, categoryId: Long?) {
        val rates = settings.exchangeRate.first()
        recordScheduledPayment.payRecurringBill(billId, debitAccountId, categoryId, rates)
    }

    override suspend fun resetAllBillsPaid() {
        dao.resetAllBillsPaid()
    }

    override suspend fun deleteRecurringBill(billId: Long) {
        dao.deleteRecurringBillById(billId)
    }

    override suspend fun saveSavingsGoal(goal: SavingsGoal) {
        dao.upsertSavingsGoal(goal.toEntity())
    }

    override suspend fun addGoalContribution(goalId: Long, deltaCents: Long) {
        dao.addGoalContribution(goalId, deltaCents)
    }

    override suspend fun contributeToGoal(goalId: Long, amountCents: Long, debitAccountId: Long?) {
        val rates = settings.exchangeRate.first()
        recordScheduledPayment.contributeToGoal(goalId, amountCents, debitAccountId, rates)
    }

    override suspend fun deleteSavingsGoal(goalId: Long) {
        dao.deleteSavingsGoalById(goalId)
    }

    override suspend fun saveReceivable(receivable: Receivable) {
        dao.upsertReceivable(receivable.toEntity())
    }

    override suspend fun recordReceivablePayment(receivableId: Long, paymentCents: Long, creditAccountId: Long?) {
        val rates = settings.exchangeRate.first()
        recordScheduledPayment.recordReceivablePayment(receivableId, paymentCents, creditAccountId, rates)
    }

    override suspend fun deleteReceivable(receivableId: Long) {
        dao.deleteReceivableById(receivableId)
    }

    override suspend fun savePayable(payable: Payable) {
        dao.upsertPayable(payable.toEntity())
    }

    override suspend fun recordPayablePayment(payableId: Long, paymentCents: Long, debitAccountId: Long?) {
        val rates = settings.exchangeRate.first()
        recordScheduledPayment.recordPayablePayment(payableId, paymentCents, debitAccountId, rates)
    }

    override suspend fun deletePayable(payableId: Long) {
        dao.deletePayableById(payableId)
    }

    override suspend fun saveShoppingList(list: ShoppingList): Long =
        dao.upsertShoppingList(list.toEntity())

    override suspend fun deleteShoppingList(listId: Long) {
        dao.deleteShoppingListAtomically(listId)
    }

    override suspend fun saveShoppingListItem(item: ShoppingListItem): Long =
        dao.upsertShoppingListItem(item.toEntity())

    override suspend fun deleteShoppingListItem(itemId: Long) {
        dao.deleteShoppingListItemById(itemId)
    }

    override suspend fun setShoppingListItemChecked(itemId: Long, checked: Boolean) {
        dao.setShoppingListItemChecked(itemId, checked)
    }

    override suspend fun uncheckAllShoppingListItems(listId: Long) {
        dao.uncheckAllShoppingListItems(listId)
    }

    override suspend fun deleteCheckedShoppingListItems(listId: Long) {
        dao.deleteCheckedShoppingListItems(listId)
    }

    override suspend fun setThemeMode(mode: ThemeMode) = settings.setThemeMode(mode)
    override suspend fun setDisplayCurrencyMode(mode: DisplayCurrencyMode) = settings.setDisplayCurrencyMode(mode)
    override suspend fun setExchangeRate(zwgPerUsd: String) = settings.setExchangeRate(zwgPerUsd)
    override suspend fun setExchangeRate(currency: CurrencyCode, perUsd: String) = settings.setExchangeRate(currency, perUsd)
    override suspend fun setSecurityEnabled(enabled: Boolean) = settings.setSecurityEnabled(enabled)
    override suspend fun setOnboardingCompleted(completed: Boolean) = settings.setOnboardingCompleted(completed)
    override suspend fun setBillRemindersEnabled(enabled: Boolean) = settings.setBillRemindersEnabled(enabled)
    override suspend fun setLastBillResetMonth(yearMonth: String) = settings.setLastBillResetMonth(yearMonth)

    override suspend fun exportBackupJson(): String {
        val txWithItems = dao.getAllTransactionsWithItems()
        val snapshot = BackupSnapshot(
            accounts = dao.getAllAccounts(),
            categories = dao.getAllCategories(),
            transactions = txWithItems.map { it.transaction },
            lineItems = txWithItems.flatMap { it.items },
            budgets = dao.getAllBudgets(),
            bills = dao.getAllRecurringBills(),
            goals = dao.getAllSavingsGoals(),
            receivables = dao.getAllReceivables(),
            payables = dao.getAllPayables(),
            shoppingLists = dao.getAllShoppingLists(),
            shoppingListItems = dao.getAllShoppingListItems()
        )
        return BackupSerializer.toJson(snapshot, nowMillis = timeProvider.currentTimeMillis())
    }

    override suspend fun restoreBackupJson(json: String): Result<Unit> = runCatching {
        val snapshot = BackupSerializer.fromJson(json, nowMillis = timeProvider.currentTimeMillis())
        dao.restoreBackupAtomically(
            accounts = snapshot.accounts,
            categories = snapshot.categories,
            transactions = snapshot.transactions,
            lineItems = snapshot.lineItems,
            budgets = snapshot.budgets,
            bills = snapshot.bills,
            goals = snapshot.goals,
            receivables = snapshot.receivables,
            payables = snapshot.payables,
            shoppingLists = snapshot.shoppingLists,
            shoppingListItems = snapshot.shoppingListItems
        )
    }
}
