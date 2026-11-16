package com.budgetflow.app.data.local

import android.content.Context
import android.net.Uri
import com.budgetflow.app.domain.repository.BackupStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidBackupStorage @Inject constructor(
    @ApplicationContext private val context: Context
) : BackupStorage {

    override suspend fun writeText(uriString: String, content: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val uri = Uri.parse(uriString)
                val stream = context.contentResolver.openOutputStream(uri)
                    ?: throw IOException("Unable to open output stream for $uriString")
                stream.use { out ->
                    out.write(content.toByteArray(Charsets.UTF_8))
                }
            }
        }

    override suspend fun readText(uriString: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val uri = Uri.parse(uriString)
                val stream = context.contentResolver.openInputStream(uri)
                    ?: throw IOException("Unable to open input stream for $uriString")
                stream.bufferedReader().use { it.readText() }
            }
        }
}
