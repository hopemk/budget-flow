package com.budgetflow.app.domain

import androidx.room.DatabaseConfiguration
import androidx.room.InvalidationTracker
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.budgetflow.app.FakeTimeProvider
import com.budgetflow.app.data.local.AccountEntity
import com.budgetflow.app.data.local.BudgetDao
import com.budgetflow.app.data.local.BudgetDatabase
import com.budgetflow.app.data.local.PayableEntity
import com.budgetflow.app.data.local.ReceivableEntity
import com.budgetflow.app.data.local.RecurringBillEntity
import com.budgetflow.app.data.local.SavingsGoalEntity
import com.budgetflow.app.data.local.TransactionEntity
import com.budgetflow.app.domain.model.AccountType
import com.budgetflow.app.domain.model.CurrencyCode
import com.budgetflow.app.domain.model.ExchangeRateConfig
import com.budgetflow.app.domain.model.TransactionType
import com.budgetflow.app.domain.usecase.RecordScheduledPaymentUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy
import java.math.BigDecimal
import java.util.concurrent.Executor

class RecordScheduledPaymentUseCaseTest {

    private class FakeBudgetDao(
        private val base: BudgetDao = Proxy.newProxyInstance(
            BudgetDao::class.java.classLoader,
            arrayOf(BudgetDao::class.java)
        ) { _, method, _ ->
            throw UnsupportedOperationException("Unexpected DAO call: ${method.name}")
        } as BudgetDao
    ) : BudgetDao by base {

        val accounts = mutableMapOf<Long, AccountEntity>()
        val bills = mutableMapOf<Long, RecurringBillEntity>()
        val goals = mutableMapOf<Long, SavingsGoalEntity>()
        val receivables = mutableMapOf<Long, ReceivableEntity>()
        val payables = mutableMapOf<Long, PayableEntity>()
        val transactions = mutableListOf<TransactionEntity>()
        private var nextTxId = 1L

        override suspend fun getAccountById(id: Long): AccountEntity? = accounts[id]

        override suspend fun adjustAccountBalance(accountId: Long, deltaCents: Long) {
            val acc = accounts[accountId] ?: return
            accounts[accountId] = acc.copy(balanceCents = acc.balanceCents + deltaCents)
        }

        override suspend fun insertTransaction(tx: TransactionEntity): Long {
            val id = if (tx.id == 0L) nextTxId++ else tx.id
            transactions.add(tx.copy(id = id))
            return id
        }

        override suspend fun getRecurringBillById(id: Long): RecurringBillEntity? = bills[id]

        override suspend fun setBillPaid(billId: Long, paid: Boolean) {
            val bill = bills[billId] ?: return
            bills[billId] = bill.copy(isPaidThisMonth = paid)
        }

        override suspend fun getSavingsGoalById(id: Long): SavingsGoalEntity? = goals[id]

        override suspend fun addGoalContribution(goalId: Long, deltaCents: Long) {
            val goal = goals[goalId] ?: return
            goals[goalId] = goal.copy(currentCents = maxOf(0L, goal.currentCents + deltaCents))
        }

        override suspend fun getReceivableById(id: Long): ReceivableEntity? = receivables[id]

        override suspend fun upsertReceivable(receivable: ReceivableEntity): Long {
            receivables[receivable.id] = receivable
            return receivable.id
        }

        override suspend fun getPayableById(id: Long): PayableEntity? = payables[id]

        override suspend fun upsertPayable(payable: PayableEntity): Long {
            payables[payable.id] = payable
            return payable.id
        }
    }

    private class FakeBudgetDatabase(private val dao: BudgetDao) : BudgetDatabase() {
        private var inTx = false

        override fun budgetDao(): BudgetDao = dao

        override val transactionExecutor: Executor = Executor { command -> command.run() }

        @Suppress("OVERRIDE_DEPRECATION")
        override fun beginTransaction() {
            inTx = true
        }

        @Suppress("OVERRIDE_DEPRECATION")
        override fun setTransactionSuccessful() {}

        @Suppress("OVERRIDE_DEPRECATION")
        override fun endTransaction() {
            inTx = false
        }

        override fun inTransaction(): Boolean = inTx

        private val fakeDb = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "beginTransaction", "beginTransactionNonExclusive", "setTransactionSuccessful", "endTransaction", "close" -> Unit
                "inTransaction" -> inTx
                "isOpen" -> true
                else -> null
            }
        } as SupportSQLiteDatabase

        private val fakeOpenHelper = Proxy.newProxyInstance(
            SupportSQLiteOpenHelper::class.java.classLoader,
            arrayOf(SupportSQLiteOpenHelper::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "getWritableDatabase", "getReadableDatabase" -> fakeDb
                "close" -> Unit
                else -> null
            }
        } as SupportSQLiteOpenHelper

        override val openHelper: SupportSQLiteOpenHelper
            get() = fakeOpenHelper

        override fun createOpenHelper(config: DatabaseConfiguration): SupportSQLiteOpenHelper = fakeOpenHelper

        @Suppress("RestrictedApi")
        override fun createInvalidationTracker(): InvalidationTracker =
            InvalidationTracker(this, emptyMap(), emptyMap(), "accounts")

        override fun clearAllTables() {}
    }

    private val rates = ExchangeRateConfig(
        ratesPerUsd = mapOf(
            CurrencyCode.USD to BigDecimal.ONE,
            CurrencyCode.ZWG to BigDecimal("25.00"),
            CurrencyCode.GBP to BigDecimal("0.80")
        )
    )

    private val timeProvider = FakeTimeProvider(millis = 1_700_000_000_000L)

    @Test
    fun `payRecurringBill marks bill paid without debiting when debitAccountId is null`() = runTest {
        val dao = FakeBudgetDao()
        dao.bills[1L] = RecurringBillEntity(1L, "Internet", 5000L, CurrencyCode.USD.name, 10L, 5, isPaidThisMonth = false, autoLog = true)
        val useCase = RecordScheduledPaymentUseCase(FakeBudgetDatabase(dao), dao, timeProvider)

        useCase.payRecurringBill(billId = 1L, debitAccountId = null, categoryId = null, rates = rates)

        assertTrue(dao.bills[1L]!!.isPaidThisMonth)
        assertTrue(dao.transactions.isEmpty())
    }

    @Test
    fun `payRecurringBill converts currency, logs expense transaction, and debits account`() = runTest {
        val dao = FakeBudgetDao()
        dao.bills[1L] = RecurringBillEntity(1L, "Cloud", 1000L, CurrencyCode.USD.name, 10L, 1, isPaidThisMonth = false, autoLog = true)
        dao.accounts[2L] = AccountEntity(2L, "ZWG Wallet", AccountType.MOBILE_MONEY.name, CurrencyCode.ZWG.name, 50000L)
        val useCase = RecordScheduledPaymentUseCase(FakeBudgetDatabase(dao), dao, timeProvider)

        useCase.payRecurringBill(billId = 1L, debitAccountId = 2L, categoryId = 99L, rates = rates)

        assertTrue(dao.bills[1L]!!.isPaidThisMonth)
        // $10.00 USD * 25.00 = 250.00 ZWG (25000 cents)
        assertEquals(25000L, dao.accounts[2L]!!.balanceCents)
        val tx = dao.transactions.single()
        assertEquals(TransactionType.EXPENSE.name, tx.type)
        assertEquals(CurrencyCode.ZWG.name, tx.currency)
        assertEquals(25000L, tx.amountCents)
        assertEquals(99L, tx.categoryId)
        assertEquals("${DomainConstants.AUTO_LOG_BILL_PREFIX}Cloud", tx.merchant)
        assertEquals(DomainConstants.AUTO_LOG_BILL_NOTE, tx.note)
        assertEquals(timeProvider.millis, tx.timestampMillis)
    }

    @Test
    fun `contributeToGoal ignores non-positive amounts and supports optional cross-currency debit`() = runTest {
        val dao = FakeBudgetDao()
        dao.goals[1L] = SavingsGoalEntity(1L, "Laptop", CurrencyCode.USD.name, 100000L, 10000L)
        dao.accounts[2L] = AccountEntity(2L, "GBP Bank", AccountType.BANK.name, CurrencyCode.GBP.name, 50000L)
        val useCase = RecordScheduledPaymentUseCase(FakeBudgetDatabase(dao), dao, timeProvider)

        useCase.contributeToGoal(goalId = 1L, amountCents = 0L, debitAccountId = 2L, rates = rates)
        useCase.contributeToGoal(goalId = 1L, amountCents = -500L, debitAccountId = 2L, rates = rates)
        assertEquals(10000L, dao.goals[1L]!!.currentCents)
        assertTrue(dao.transactions.isEmpty())

        // Contribute without debit account
        useCase.contributeToGoal(goalId = 1L, amountCents = 5000L, debitAccountId = null, rates = rates)
        assertEquals(15000L, dao.goals[1L]!!.currentCents)
        assertTrue(dao.transactions.isEmpty())

        // Contribute $100.00 USD from GBP account (0.80 rate -> £80.00 = 8000 cents)
        useCase.contributeToGoal(goalId = 1L, amountCents = 10000L, debitAccountId = 2L, rates = rates)
        assertEquals(25000L, dao.goals[1L]!!.currentCents)
        assertEquals(42000L, dao.accounts[2L]!!.balanceCents)
        val tx = dao.transactions.single()
        assertEquals(TransactionType.EXPENSE.name, tx.type)
        assertEquals(8000L, tx.amountCents)
        assertEquals("${DomainConstants.AUTO_LOG_GOAL_PREFIX}Laptop", tx.merchant)
    }

    @Test
    fun `recordReceivablePayment handles partial, capped settlement, and cross-currency credit`() = runTest {
        val dao = FakeBudgetDao()
        dao.receivables[1L] = ReceivableEntity(1L, "Alice", "Dinner", CurrencyCode.USD.name, 10000L, 0L, isSettled = false, createdAtMillis = 100L)
        dao.accounts[2L] = AccountEntity(2L, "ZWG Cash", AccountType.CASH.name, CurrencyCode.ZWG.name, 0L)
        val useCase = RecordScheduledPaymentUseCase(FakeBudgetDatabase(dao), dao, timeProvider)

        // Partial payment without credit account
        useCase.recordReceivablePayment(receivableId = 1L, paymentCents = 4000L, creditAccountId = null, rates = rates)
        assertEquals(4000L, dao.receivables[1L]!!.paidBackCents)
        assertFalse(dao.receivables[1L]!!.isSettled)

        // Overpayment caps at totalOwedCents, marks settled, and credits ZWG account
        useCase.recordReceivablePayment(receivableId = 1L, paymentCents = 8000L, creditAccountId = 2L, rates = rates)
        assertEquals(10000L, dao.receivables[1L]!!.paidBackCents)
        assertTrue(dao.receivables[1L]!!.isSettled)
        // 80.00 USD * 25.00 = 2000.00 ZWG (200000 cents)
        assertEquals(200000L, dao.accounts[2L]!!.balanceCents)
        val tx = dao.transactions.single()
        assertEquals(TransactionType.INCOME.name, tx.type)
        assertEquals(200000L, tx.amountCents)
        assertEquals("${DomainConstants.AUTO_LOG_REPAYMENT_PREFIX}Alice", tx.merchant)
        assertEquals("Dinner", tx.note)
    }

    @Test
    fun `recordPayablePayment handles partial, capped settlement, and cross-currency debit`() = runTest {
        val dao = FakeBudgetDao()
        dao.payables[1L] = PayableEntity(1L, "Bob", "Tickets", CurrencyCode.GBP.name, 8000L, 0L, isSettled = false, createdAtMillis = 100L)
        dao.accounts[2L] = AccountEntity(2L, "USD Bank", AccountType.BANK.name, CurrencyCode.USD.name, 20000L)
        val useCase = RecordScheduledPaymentUseCase(FakeBudgetDatabase(dao), dao, timeProvider)

        // Partial payment without debit account
        useCase.recordPayablePayment(payableId = 1L, paymentCents = 3000L, debitAccountId = null, rates = rates)
        assertEquals(3000L, dao.payables[1L]!!.paidCents)
        assertFalse(dao.payables[1L]!!.isSettled)

        // Overpayment (£60.00 when £50.00 remaining) caps at totalOweCents, marks settled, and debits USD account
        // £60.00 / 0.80 = $75.00 USD = 7500 cents
        useCase.recordPayablePayment(payableId = 1L, paymentCents = 6000L, debitAccountId = 2L, rates = rates)
        assertEquals(8000L, dao.payables[1L]!!.paidCents)
        assertTrue(dao.payables[1L]!!.isSettled)
        assertEquals(12500L, dao.accounts[2L]!!.balanceCents)
        val tx = dao.transactions.single()
        assertEquals(TransactionType.EXPENSE.name, tx.type)
        assertEquals(7500L, tx.amountCents)
        assertEquals("${DomainConstants.AUTO_LOG_PAYABLE_PREFIX}Bob", tx.merchant)
        assertEquals("Tickets", tx.note)
    }

    @Test
    fun `payRecurringBill falls back to bill categoryId and handles missing debit account`() = runTest {
        val dao = FakeBudgetDao()
        dao.bills[1L] = RecurringBillEntity(1L, "Water", 2000L, CurrencyCode.USD.name, 42L, 10, isPaidThisMonth = false, autoLog = true)
        dao.accounts[2L] = AccountEntity(2L, "Cash", AccountType.CASH.name, CurrencyCode.USD.name, 10000L)
        val useCase = RecordScheduledPaymentUseCase(FakeBudgetDatabase(dao), dao, timeProvider)

        // Missing debit account still marks bill paid without logging transaction
        useCase.payRecurringBill(billId = 1L, debitAccountId = 999L, categoryId = null, rates = rates)
        assertTrue(dao.bills[1L]!!.isPaidThisMonth)
        assertTrue(dao.transactions.isEmpty())

        // With valid account and null categoryId param, uses bill.categoryId (42L)
        useCase.payRecurringBill(billId = 1L, debitAccountId = 2L, categoryId = null, rates = rates)
        assertEquals(42L, dao.transactions.single().categoryId)
        assertEquals(8000L, dao.accounts[2L]!!.balanceCents)
    }

    @Test
    fun `recordReceivablePayment and recordPayablePayment ignore non-positive amounts`() = runTest {
        val dao = FakeBudgetDao()
        dao.receivables[1L] = ReceivableEntity(1L, "Alice", "Loan", CurrencyCode.USD.name, 5000L, 0L, isSettled = false, createdAtMillis = 100L)
        dao.payables[1L] = PayableEntity(1L, "Bob", "Loan", CurrencyCode.USD.name, 5000L, 0L, isSettled = false, createdAtMillis = 100L)
        val useCase = RecordScheduledPaymentUseCase(FakeBudgetDatabase(dao), dao, timeProvider)

        useCase.recordReceivablePayment(1L, 0L, creditAccountId = null, rates = rates)
        useCase.recordReceivablePayment(1L, -100L, creditAccountId = null, rates = rates)
        useCase.recordPayablePayment(1L, 0L, debitAccountId = null, rates = rates)
        useCase.recordPayablePayment(1L, -100L, debitAccountId = null, rates = rates)

        assertEquals(0L, dao.receivables[1L]!!.paidBackCents)
        assertEquals(0L, dao.payables[1L]!!.paidCents)
        assertTrue(dao.transactions.isEmpty())
    }
}
