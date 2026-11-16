package com.budgetflow.app.domain.usecase

import androidx.room.withTransaction
import com.budgetflow.app.data.local.BudgetDao
import com.budgetflow.app.data.local.BudgetDatabase
import com.budgetflow.app.data.local.TransactionEntity
import com.budgetflow.app.domain.DomainConstants
import com.budgetflow.app.domain.model.CurrencyCode
import com.budgetflow.app.domain.model.ExchangeRateConfig
import com.budgetflow.app.domain.model.TransactionType
import com.budgetflow.app.domain.util.TimeProvider
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Atomically records bill payments, savings goal contributions, and debt repayments
 * alongside their optional wallet ledger transactions inside a single Room transaction.
 */
@Singleton
class RecordScheduledPaymentUseCase @Inject constructor(
    private val db: BudgetDatabase,
    private val dao: BudgetDao,
    private val timeProvider: TimeProvider
) {

    suspend fun payRecurringBill(
        billId: Long,
        debitAccountId: Long?,
        categoryId: Long?,
        rates: ExchangeRateConfig
    ) {
        db.withTransaction {
            val bill = dao.getRecurringBillById(billId) ?: return@withTransaction
            dao.setBillPaid(billId, true)
            if (debitAccountId != null) {
                val acc = dao.getAccountById(debitAccountId) ?: return@withTransaction
                val fromCur = CurrencyCode.fromStringOrDefault(bill.currency)
                val toCur = CurrencyCode.fromStringOrDefault(acc.currency)
                val debited = CurrencyConverter.convert(bill.amountCents, fromCur, toCur, rates)
                val tx = TransactionEntity(
                    accountId = acc.id,
                    targetAccountId = null,
                    categoryId = categoryId ?: bill.categoryId,
                    type = TransactionType.EXPENSE.name,
                    currency = acc.currency,
                    amountCents = debited,
                    exchangeRateUsed = rates.rateFor(toCur).toPlainString(),
                    merchant = "${DomainConstants.AUTO_LOG_BILL_PREFIX}${bill.name}",
                    note = DomainConstants.AUTO_LOG_BILL_NOTE,
                    receiptUri = null,
                    timestampMillis = timeProvider.currentTimeMillis()
                )
                dao.insertTransaction(tx)
                dao.adjustAccountBalance(acc.id, -debited)
            }
        }
    }

    suspend fun contributeToGoal(
        goalId: Long,
        amountCents: Long,
        debitAccountId: Long?,
        rates: ExchangeRateConfig
    ) {
        if (amountCents <= 0L) return
        db.withTransaction {
            val goal = dao.getSavingsGoalById(goalId) ?: return@withTransaction
            dao.addGoalContribution(goalId, amountCents)
            if (debitAccountId != null) {
                val acc = dao.getAccountById(debitAccountId) ?: return@withTransaction
                val fromCur = CurrencyCode.fromStringOrDefault(goal.currency)
                val toCur = CurrencyCode.fromStringOrDefault(acc.currency)
                val debited = CurrencyConverter.convert(amountCents, fromCur, toCur, rates)
                val tx = TransactionEntity(
                    accountId = acc.id,
                    targetAccountId = null,
                    categoryId = null,
                    type = TransactionType.EXPENSE.name,
                    currency = acc.currency,
                    amountCents = debited,
                    exchangeRateUsed = rates.rateFor(toCur).toPlainString(),
                    merchant = "${DomainConstants.AUTO_LOG_GOAL_PREFIX}${goal.name}",
                    note = DomainConstants.AUTO_LOG_GOAL_NOTE,
                    receiptUri = null,
                    timestampMillis = timeProvider.currentTimeMillis()
                )
                dao.insertTransaction(tx)
                dao.adjustAccountBalance(acc.id, -debited)
            }
        }
    }

    suspend fun recordReceivablePayment(
        receivableId: Long,
        paymentCents: Long,
        creditAccountId: Long?,
        rates: ExchangeRateConfig
    ) {
        if (paymentCents <= 0L) return
        db.withTransaction {
            val existing = dao.getReceivableById(receivableId) ?: return@withTransaction
            val newPaid = (existing.paidBackCents + paymentCents).coerceAtMost(existing.totalOwedCents)
            dao.upsertReceivable(
                existing.copy(
                    paidBackCents = newPaid,
                    isSettled = newPaid >= existing.totalOwedCents
                )
            )
            if (creditAccountId != null) {
                val targetAcc = dao.getAccountById(creditAccountId) ?: return@withTransaction
                val fromCur = CurrencyCode.fromStringOrDefault(existing.currency)
                val toCur = CurrencyCode.fromStringOrDefault(targetAcc.currency)
                val credited = CurrencyConverter.convert(paymentCents, fromCur, toCur, rates)
                val tx = TransactionEntity(
                    accountId = targetAcc.id,
                    targetAccountId = null,
                    categoryId = null,
                    type = TransactionType.INCOME.name,
                    currency = targetAcc.currency,
                    amountCents = credited,
                    exchangeRateUsed = rates.rateFor(toCur).toPlainString(),
                    merchant = "${DomainConstants.AUTO_LOG_REPAYMENT_PREFIX}${existing.personName}",
                    note = existing.description,
                    receiptUri = null,
                    timestampMillis = timeProvider.currentTimeMillis()
                )
                dao.insertTransaction(tx)
                dao.adjustAccountBalance(targetAcc.id, credited)
            }
        }
    }

    suspend fun recordPayablePayment(
        payableId: Long,
        paymentCents: Long,
        debitAccountId: Long?,
        rates: ExchangeRateConfig
    ) {
        if (paymentCents <= 0L) return
        db.withTransaction {
            val existing = dao.getPayableById(payableId) ?: return@withTransaction
            val newPaid = (existing.paidCents + paymentCents).coerceAtMost(existing.totalOweCents)
            dao.upsertPayable(
                existing.copy(
                    paidCents = newPaid,
                    isSettled = newPaid >= existing.totalOweCents
                )
            )
            if (debitAccountId != null) {
                val sourceAcc = dao.getAccountById(debitAccountId) ?: return@withTransaction
                val fromCur = CurrencyCode.fromStringOrDefault(existing.currency)
                val toCur = CurrencyCode.fromStringOrDefault(sourceAcc.currency)
                val debited = CurrencyConverter.convert(paymentCents, fromCur, toCur, rates)
                val tx = TransactionEntity(
                    accountId = sourceAcc.id,
                    targetAccountId = null,
                    categoryId = null,
                    type = TransactionType.EXPENSE.name,
                    currency = sourceAcc.currency,
                    amountCents = debited,
                    exchangeRateUsed = rates.rateFor(toCur).toPlainString(),
                    merchant = "${DomainConstants.AUTO_LOG_PAYABLE_PREFIX}${existing.personName}",
                    note = existing.description,
                    receiptUri = null,
                    timestampMillis = timeProvider.currentTimeMillis()
                )
                dao.insertTransaction(tx)
                dao.adjustAccountBalance(sourceAcc.id, -debited)
            }
        }
    }
}
