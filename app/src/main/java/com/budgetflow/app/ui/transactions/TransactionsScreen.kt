package com.budgetflow.app.ui.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.budgetflow.app.domain.model.TransactionType
import com.budgetflow.app.ui.components.EmptyStateCard
import com.budgetflow.app.ui.components.TransactionRow

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TransactionsScreen(
    onEditTransaction: (Long) -> Unit,
    viewModel: TransactionsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val filter = state.filter

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                text = "All Transactions",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            OutlinedTextField(
                value = filter.query,
                onValueChange = viewModel::setQuery,
                label = { Text("Search merchant, item, or note") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = filter.typeFilter == null,
                    onClick = { viewModel.setTypeFilter(null) },
                    label = { Text("All") }
                )
                TransactionType.entries.forEach { t ->
                    FilterChip(
                        selected = filter.typeFilter == t,
                        onClick = { viewModel.setTypeFilter(if (filter.typeFilter == t) null else t) },
                        label = { Text(t.displayName) }
                    )
                }
            }
        }

        if (state.accounts.size > 1) {
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = filter.accountIdFilter == null,
                        onClick = { viewModel.setAccountFilter(null) },
                        label = { Text("All Accounts") }
                    )
                    state.accounts.forEach { acc ->
                        FilterChip(
                            selected = filter.accountIdFilter == acc.id,
                            onClick = {
                                viewModel.setAccountFilter(if (filter.accountIdFilter == acc.id) null else acc.id)
                            },
                            label = { Text(acc.name) }
                        )
                    }
                }
            }
        }

        if (state.transactions.isEmpty()) {
            item {
                EmptyStateCard(
                    title = "No matching transactions",
                    subtitle = "Try clearing your search or filters."
                )
            }
        } else {
            items(state.transactions, key = { it.id }) { tx ->
                TransactionRow(
                    transaction = tx,
                    category = tx.categoryId?.let { state.categoriesById[it] },
                    onClick = { onEditTransaction(tx.id) },
                    onDelete = { viewModel.deleteTransaction(tx.id) }
                )
            }
        }
    }
}
