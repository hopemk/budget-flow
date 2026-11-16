package com.budgetflow.app.domain.repository

interface BackupStorage {
    suspend fun writeText(uriString: String, content: String): Result<Unit>
    suspend fun readText(uriString: String): Result<String>
}
