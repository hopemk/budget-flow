package com.budgetflow.app.domain.repository

interface BackupRepository {
    suspend fun exportBackupJson(): String
    suspend fun restoreBackupJson(json: String): Result<Unit>
}
