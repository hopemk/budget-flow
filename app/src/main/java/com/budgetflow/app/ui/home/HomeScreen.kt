package com.budgetflow.app.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.budgetflow.app.domain.model.Account
import com.budgetflow.app.domain.usecase.CurrencyConverter
import com.budgetflow.app.ui.components.AddAccountDialog
import com.budgetflow.app.ui.components.BudgetFlowCard
import com.budgetflow.app.ui.components.EditAccountDialog
import com.budgetflow.app.ui.components.EmptyStateCard
import com.budgetflow.app.ui.components.MessageSnackbarBox
import com.budgetflow.app.ui.components.TransactionRow

@Composable
fun HomeScreen(
    onNavigateToSpend: () -> Unit,
    onNavigateToScanner: () -> Unit,
    onNavigateToGoals: () -> Unit,
    onNavigateToShoppingLists: () -> Unit = {},
    onNavigateToTransactions: () -> Unit = {},
    onEditTransaction: (Long) -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showAddWalletDialog by remember { mutableStateOf(false) }
    var showChooseAccountsDialog by remember { mutableStateOf(false) }
    var editingWallet by remember { mutableStateOf<Account?>(null) }

    MessageSnackbarBox(messages = viewModel.messages) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "MY ACCOUNTS",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (state.allAccounts.isNotEmpty()) {
                        TextButton(onClick = { showChooseAccountsDialog = true }) {
                            Text("Choose")
                        }
                    }
                    TextButton(onClick = { showAddWalletDialog = true }) {
                        Text("+ Add")
                    }
                }
            }
            if (state.homeAccounts.isEmpty()) {
                EmptyStateCard(
                    title = "No accounts pinned here",
                    subtitle = if (state.allAccounts.isEmpty()) {
                        "Tap + Add to create your first wallet or account."
                    } else {
                        "Tap Choose to pick which accounts appear on your opening page."
                    }
                )
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(state.homeAccounts, key = { "home_acc_${it.id}" }) { account ->
                        BudgetFlowCard(
                            onClick = { editingWallet = account },
                            fillMaxWidth = false,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.Top
                        ) {
                            Text(
                                text = "${account.name} (${account.type.displayName})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = CurrencyConverter.format(account.balanceCents, account.currency),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onNavigateToScanner,
                    modifier = Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp)
                ) {
                    Text("Scan", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                }
                OutlinedButton(
                    onClick = onNavigateToSpend,
                    modifier = Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp)
                ) {
                    Text("Spend", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                }
                OutlinedButton(
                    onClick = onNavigateToShoppingLists,
                    modifier = Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp)
                ) {
                    Text("Lists", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                }
                OutlinedButton(
                    onClick = onNavigateToGoals,
                    modifier = Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp)
                ) {
                    Text("Goals", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "RECENT TRANSACTIONS",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = onNavigateToTransactions) {
                    Text("See All")
                }
            }
        }

        if (state.recentTransactions.isEmpty()) {
            item {
                EmptyStateCard(
                    title = "No transactions yet",
                    subtitle = "Tap + to log what you bought or scan a receipt."
                )
            }
        } else {
            items(state.recentTransactions, key = { it.id }) { tx ->
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

    if (showChooseAccountsDialog) {
        AlertDialog(
            onDismissRequest = { showChooseAccountsDialog = false },
            title = { Text("Choose Home Accounts") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Pick which accounts appear on your opening page:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    state.allAccounts.forEach { acc ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.toggleAccountOnHome(acc.id, !acc.showOnHome) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Checkbox(
                                    checked = acc.showOnHome,
                                    onCheckedChange = { checked ->
                                        viewModel.toggleAccountOnHome(acc.id, checked)
                                    }
                                )
                                Column {
                                    Text(acc.name, fontWeight = FontWeight.Medium)
                                    Text(
                                        acc.type.displayName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Text(
                                CurrencyConverter.format(acc.balanceCents, acc.currency),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showChooseAccountsDialog = false }) { Text("Done") }
            }
        )
    }

    if (showAddWalletDialog) {
        AddAccountDialog(
            onDismiss = { showAddWalletDialog = false },
            onSave = { name, type, currency, balance ->
                viewModel.addAccount(name, type, currency, balance)
                showAddWalletDialog = false
            }
        )
    }

    editingWallet?.let { wallet ->
        EditAccountDialog(
            account = wallet,
            onDismiss = { editingWallet = null },
            onSave = { newName, newType, balanceInput, showOnHome ->
                viewModel.updateAccount(wallet, newName, newType, balanceInput, showOnHome)
                editingWallet = null
            },
            onArchive = {
                viewModel.archiveAccount(wallet.id)
                editingWallet = null
            },
            onDelete = {
                viewModel.deleteAccount(wallet.id)
                editingWallet = null
            }
        )
    }
}
