package com.budgetflow.app.domain.repository

import com.budgetflow.app.domain.model.Budget
import kotlinx.coroutines.flow.Flow

interface BudgetConfigRepository {
    fun observeBudgets(): Flow<List<Budget>>
    suspend fun saveBudget(budget: Budget)
    suspend fun deleteBudget(budgetId: Long)
}
