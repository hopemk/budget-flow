package com.budgetflow.app.domain.repository

import com.budgetflow.app.domain.model.RecurringBill
import com.budgetflow.app.domain.model.SavingsGoal
import kotlinx.coroutines.flow.Flow

interface BillsGoalsRepository {
    fun observeRecurringBills(): Flow<List<RecurringBill>>
    fun observeSavingsGoals(): Flow<List<SavingsGoal>>
    suspend fun saveRecurringBill(bill: RecurringBill)
    suspend fun toggleBillPaid(billId: Long, paid: Boolean)
    suspend fun payRecurringBill(billId: Long, debitAccountId: Long?, categoryId: Long?)
    suspend fun resetAllBillsPaid()
    suspend fun deleteRecurringBill(billId: Long)
    suspend fun saveSavingsGoal(goal: SavingsGoal)
    suspend fun addGoalContribution(goalId: Long, deltaCents: Long)
    suspend fun contributeToGoal(goalId: Long, amountCents: Long, debitAccountId: Long?)
    suspend fun deleteSavingsGoal(goalId: Long)
}
