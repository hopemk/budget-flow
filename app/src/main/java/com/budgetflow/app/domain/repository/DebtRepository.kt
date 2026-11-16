package com.budgetflow.app.domain.repository

import com.budgetflow.app.domain.model.Payable
import com.budgetflow.app.domain.model.Receivable
import kotlinx.coroutines.flow.Flow

interface DebtRepository {
    fun observeReceivables(): Flow<List<Receivable>>
    fun observePayables(): Flow<List<Payable>>
    suspend fun saveReceivable(receivable: Receivable)
    suspend fun recordReceivablePayment(receivableId: Long, paymentCents: Long, creditAccountId: Long?)
    suspend fun deleteReceivable(receivableId: Long)
    suspend fun savePayable(payable: Payable)
    suspend fun recordPayablePayment(payableId: Long, paymentCents: Long, debitAccountId: Long?)
    suspend fun deletePayable(payableId: Long)
}
