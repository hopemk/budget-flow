package com.budgetflow.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {

    @Query("SELECT * FROM accounts WHERE isArchived = 0 ORDER BY id ASC")
    fun observeAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY id ASC")
    fun observeAllAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY id ASC")
    suspend fun getAllAccounts(): List<AccountEntity>

    @Query("UPDATE accounts SET isArchived = :archived WHERE id = :accountId")
    suspend fun setAccountArchived(accountId: Long, archived: Boolean)

    @Query("UPDATE accounts SET showOnHome = :showOnHome WHERE id = :accountId")
    suspend fun setAccountShowOnHome(accountId: Long, showOnHome: Boolean)

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getAccountById(id: Long): AccountEntity?

    @Upsert
    suspend fun upsertAccount(account: AccountEntity): Long

    @Query("UPDATE accounts SET balanceCents = balanceCents + :deltaCents WHERE id = :accountId")
    suspend fun adjustAccountBalance(accountId: Long, deltaCents: Long)

    @Query("SELECT * FROM categories WHERE (:includeArchived = 1 OR isArchived = 0) ORDER BY id ASC")
    fun observeCategories(includeArchived: Boolean): Flow<List<CategoryEntity>>

    @Upsert
    suspend fun upsertCategory(category: CategoryEntity): Long

    @Query("UPDATE categories SET isArchived = :archived WHERE id = :categoryId")
    suspend fun setCategoryArchived(categoryId: Long, archived: Boolean)

    @Query("UPDATE transactions SET categoryId = NULL WHERE categoryId = :categoryId")
    suspend fun clearTransactionCategory(categoryId: Long)

    @Query("UPDATE shopping_lists SET categoryId = NULL WHERE categoryId = :categoryId")
    suspend fun clearShoppingListCategory(categoryId: Long)

    @Query("DELETE FROM budgets WHERE categoryId = :categoryId")
    suspend fun deleteBudgetsForCategory(categoryId: Long)

    @Query("UPDATE categories SET parentCategoryId = NULL WHERE parentCategoryId = :categoryId")
    suspend fun detachChildCategories(categoryId: Long)

    @Query("DELETE FROM categories WHERE id = :categoryId")
    suspend fun deleteCategoryById(categoryId: Long)

    @Transaction
    suspend fun deleteCategoryAtomically(categoryId: Long) {
        clearTransactionCategory(categoryId)
        clearShoppingListCategory(categoryId)
        deleteBudgetsForCategory(categoryId)
        detachChildCategories(categoryId)
        deleteCategoryById(categoryId)
    }

    @Transaction
    @Query("SELECT * FROM transactions ORDER BY timestampMillis DESC")
    fun observeTransactionsWithItems(): Flow<List<TransactionWithItems>>

    @Transaction
    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getTransactionWithItems(id: Long): TransactionWithItems?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(tx: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLineItems(items: List<LineItemEntity>)

    @Query("DELETE FROM line_items WHERE transactionId = :transactionId")
    suspend fun deleteLineItemsForTransaction(transactionId: Long)

    @Query("DELETE FROM transactions WHERE id = :transactionId")
    suspend fun deleteTransactionById(transactionId: Long)

    @Query("SELECT * FROM budgets ORDER BY id ASC")
    fun observeBudgets(): Flow<List<BudgetEntity>>

    @Upsert
    suspend fun upsertBudget(budget: BudgetEntity): Long

    @Query("DELETE FROM budgets WHERE id = :budgetId")
    suspend fun deleteBudgetById(budgetId: Long)

    @Query("SELECT * FROM recurring_bills ORDER BY dueDayOfMonth ASC")
    fun observeRecurringBills(): Flow<List<RecurringBillEntity>>

    @Upsert
    suspend fun upsertRecurringBill(bill: RecurringBillEntity): Long

    @Query("SELECT * FROM recurring_bills WHERE id = :id")
    suspend fun getRecurringBillById(id: Long): RecurringBillEntity?

    @Query("UPDATE recurring_bills SET isPaidThisMonth = :paid WHERE id = :billId")
    suspend fun setBillPaid(billId: Long, paid: Boolean)

    @Query("UPDATE recurring_bills SET isPaidThisMonth = 0")
    suspend fun resetAllBillsPaid()

    @Query("DELETE FROM recurring_bills WHERE id = :billId")
    suspend fun deleteRecurringBillById(billId: Long)

    @Query("DELETE FROM savings_goals WHERE id = :goalId")
    suspend fun deleteSavingsGoalById(goalId: Long)

    @Query("DELETE FROM receivables WHERE id = :receivableId")
    suspend fun deleteReceivableById(receivableId: Long)

    @Query("DELETE FROM payables WHERE id = :payableId")
    suspend fun deletePayableById(payableId: Long)

    @Query("SELECT * FROM savings_goals ORDER BY id ASC")
    fun observeSavingsGoals(): Flow<List<SavingsGoalEntity>>

    @Query("SELECT * FROM savings_goals WHERE id = :id")
    suspend fun getSavingsGoalById(id: Long): SavingsGoalEntity?

    @Upsert
    suspend fun upsertSavingsGoal(goal: SavingsGoalEntity): Long

    @Query("UPDATE savings_goals SET currentCents = MAX(0, currentCents + :deltaCents) WHERE id = :goalId")
    suspend fun addGoalContribution(goalId: Long, deltaCents: Long)

    @Query("SELECT * FROM receivables ORDER BY isSettled ASC, createdAtMillis DESC")
    fun observeReceivables(): Flow<List<ReceivableEntity>>

    @Query("SELECT * FROM receivables WHERE id = :id")
    suspend fun getReceivableById(id: Long): ReceivableEntity?

    @Upsert
    suspend fun upsertReceivable(receivable: ReceivableEntity): Long

    @Query("SELECT * FROM payables ORDER BY isSettled ASC, createdAtMillis DESC")
    fun observePayables(): Flow<List<PayableEntity>>

    @Query("SELECT * FROM payables WHERE id = :id")
    suspend fun getPayableById(id: Long): PayableEntity?

    @Upsert
    suspend fun upsertPayable(payable: PayableEntity): Long

    @Query("SELECT * FROM categories ORDER BY id ASC")
    suspend fun getAllCategories(): List<CategoryEntity>

    @Transaction
    @Query("SELECT * FROM transactions ORDER BY id ASC")
    suspend fun getAllTransactionsWithItems(): List<TransactionWithItems>

    @Query("SELECT * FROM budgets ORDER BY id ASC")
    suspend fun getAllBudgets(): List<BudgetEntity>

    @Query("SELECT * FROM recurring_bills ORDER BY id ASC")
    suspend fun getAllRecurringBills(): List<RecurringBillEntity>

    @Query("SELECT * FROM savings_goals ORDER BY id ASC")
    suspend fun getAllSavingsGoals(): List<SavingsGoalEntity>

    @Query("SELECT * FROM receivables ORDER BY id ASC")
    suspend fun getAllReceivables(): List<ReceivableEntity>

    @Query("SELECT * FROM payables ORDER BY id ASC")
    suspend fun getAllPayables(): List<PayableEntity>

    @Transaction
    @Query("SELECT * FROM shopping_lists ORDER BY createdAtMillis DESC")
    fun observeShoppingListsWithItems(): Flow<List<ShoppingListWithItems>>

    @Upsert
    suspend fun upsertShoppingList(list: ShoppingListEntity): Long

    @Query("DELETE FROM shopping_list_items WHERE listId = :listId")
    suspend fun deleteItemsForShoppingList(listId: Long)

    @Query("DELETE FROM shopping_lists WHERE id = :listId")
    suspend fun deleteShoppingListById(listId: Long)

    @Transaction
    suspend fun deleteShoppingListAtomically(listId: Long) {
        deleteItemsForShoppingList(listId)
        deleteShoppingListById(listId)
    }

    @Upsert
    suspend fun upsertShoppingListItem(item: ShoppingListItemEntity): Long

    @Query("DELETE FROM shopping_list_items WHERE id = :itemId")
    suspend fun deleteShoppingListItemById(itemId: Long)

    @Query("UPDATE shopping_list_items SET isChecked = :checked WHERE id = :itemId")
    suspend fun setShoppingListItemChecked(itemId: Long, checked: Boolean)

    @Query("UPDATE shopping_list_items SET isChecked = 0 WHERE listId = :listId")
    suspend fun uncheckAllShoppingListItems(listId: Long)

    @Query("DELETE FROM shopping_list_items WHERE listId = :listId AND isChecked = 1")
    suspend fun deleteCheckedShoppingListItems(listId: Long)

    @Query("SELECT * FROM shopping_lists ORDER BY id ASC")
    suspend fun getAllShoppingLists(): List<ShoppingListEntity>

    @Query("SELECT * FROM shopping_list_items ORDER BY id ASC")
    suspend fun getAllShoppingListItems(): List<ShoppingListItemEntity>

    @Query("DELETE FROM shopping_list_items")
    suspend fun clearShoppingListItems()

    @Query("DELETE FROM shopping_lists")
    suspend fun clearShoppingLists()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShoppingLists(items: List<ShoppingListEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShoppingListItems(items: List<ShoppingListItemEntity>)

    @Query("DELETE FROM line_items")
    suspend fun clearLineItems()

    @Query("DELETE FROM transactions")
    suspend fun clearTransactions()

    @Query("DELETE FROM budgets")
    suspend fun clearBudgets()

    @Query("DELETE FROM recurring_bills")
    suspend fun clearRecurringBills()

    @Query("DELETE FROM savings_goals")
    suspend fun clearSavingsGoals()

    @Query("DELETE FROM receivables")
    suspend fun clearReceivables()

    @Query("DELETE FROM payables")
    suspend fun clearPayables()

    @Query("DELETE FROM categories")
    suspend fun clearCategories()

    @Query("DELETE FROM accounts")
    suspend fun clearAccounts()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccounts(items: List<AccountEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(items: List<CategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactions(items: List<TransactionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBudgets(items: List<BudgetEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecurringBills(items: List<RecurringBillEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavingsGoals(items: List<SavingsGoalEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReceivables(items: List<ReceivableEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayables(items: List<PayableEntity>)

    @Transaction
    suspend fun restoreBackupAtomically(
        accounts: List<AccountEntity>,
        categories: List<CategoryEntity>,
        transactions: List<TransactionEntity>,
        lineItems: List<LineItemEntity>,
        budgets: List<BudgetEntity>,
        bills: List<RecurringBillEntity>,
        goals: List<SavingsGoalEntity>,
        receivables: List<ReceivableEntity>,
        payables: List<PayableEntity>,
        shoppingLists: List<ShoppingListEntity> = emptyList(),
        shoppingListItems: List<ShoppingListItemEntity> = emptyList()
    ) {
        clearLineItems()
        clearTransactions()
        clearBudgets()
        clearRecurringBills()
        clearSavingsGoals()
        clearReceivables()
        clearPayables()
        clearShoppingListItems()
        clearShoppingLists()
        clearCategories()
        clearAccounts()

        if (accounts.isNotEmpty()) insertAccounts(accounts)
        if (categories.isNotEmpty()) insertCategories(categories)
        if (transactions.isNotEmpty()) insertTransactions(transactions)
        if (lineItems.isNotEmpty()) insertLineItems(lineItems)
        if (budgets.isNotEmpty()) insertBudgets(budgets)
        if (bills.isNotEmpty()) insertRecurringBills(bills)
        if (goals.isNotEmpty()) insertSavingsGoals(goals)
        if (receivables.isNotEmpty()) insertReceivables(receivables)
        if (payables.isNotEmpty()) insertPayables(payables)
        if (shoppingLists.isNotEmpty()) insertShoppingLists(shoppingLists)
        if (shoppingListItems.isNotEmpty()) insertShoppingListItems(shoppingListItems)
    }
}
