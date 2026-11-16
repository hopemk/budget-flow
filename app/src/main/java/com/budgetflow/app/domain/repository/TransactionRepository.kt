package com.budgetflow.app.domain.repository

import com.budgetflow.app.domain.model.Transaction
import kotlinx.coroutines.flow.Flow

interface TransactionRepository {
    fun observeTransactions(): Flow<List<Transaction>>
    suspend fun getTransactionById(transactionId: Long): Transaction?
    suspend fun saveTransaction(transaction: Transaction)
    suspend fun deleteTransaction(transactionId: Long)
}
