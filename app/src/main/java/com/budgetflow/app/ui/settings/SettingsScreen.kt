package com.budgetflow.app.ui.settings

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.budgetflow.app.domain.model.Account
import com.budgetflow.app.domain.model.CurrencyCode
import com.budgetflow.app.domain.model.ThemeMode
import com.budgetflow.app.domain.usecase.CurrencyConverter
import com.budgetflow.app.ui.components.AddAccountDialog
import com.budgetflow.app.ui.components.BudgetFlowCard
import com.budgetflow.app.ui.components.EditAccountDialog
import com.budgetflow.app.ui.components.MessageSnackbarBox

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var selectedRateCurrency by remember { mutableStateOf(CurrencyCode.ZWG) }
    var rateInput by remember { mutableStateOf(state.ratesPerUsd[selectedRateCurrency] ?: state.zwgPerUsd) }
    var showAddAccount by remember { mutableStateOf(false) }
    var editingAccount by remember { mutableStateOf<Account?>(null) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    val createBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            viewModel.exportBackupToUri(uri.toString())
        }
    }

    val restoreBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.restoreBackupFromUri(uri.toString())
        }
    }

    LaunchedEffect(state.ratesPerUsd, selectedRateCurrency) {
        rateInput = state.ratesPerUsd[selectedRateCurrency] ?: state.zwgPerUsd
    }

    MessageSnackbarBox(messages = viewModel.messages) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
        item {
            BudgetFlowCard {
                Text("APPEARANCE", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = state.themeMode == mode,
                            onClick = { viewModel.setThemeMode(mode) },
                            label = { Text(mode.displayName) }
                        )
                    }
                }
            }
        }

        item {
            BudgetFlowCard {
                Text("EXCHANGE RATES (USD BASE)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CurrencyCode.entries.filter { it != CurrencyCode.USD }.forEach { currency ->
                        FilterChip(
                            selected = selectedRateCurrency == currency,
                            onClick = { selectedRateCurrency = currency },
                            label = { Text(currency.name) }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = rateInput,
                        onValueChange = { rateInput = it },
                        label = { Text("1 USD = ? ${selectedRateCurrency.name}") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Button(onClick = { viewModel.updateExchangeRate(selectedRateCurrency, rateInput) }) {
                        Text("Update")
                    }
                }
            }
        }

        item {
            BudgetFlowCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("WALLETS & ACCOUNTS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { showAddAccount = true }) { Text("+ Add") }
                }
                state.accounts.forEach { acc ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { editingAccount = acc }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("${acc.name} (${acc.currency.name})")
                            Text(
                                text = "${acc.type.displayName} • Tap to edit",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(CurrencyConverter.format(acc.balanceCents, acc.currency), fontWeight = FontWeight.SemiBold)
                    }
                }
                if (state.archivedAccounts.isNotEmpty()) {
                    Text(
                        text = "ARCHIVED ACCOUNTS",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    state.archivedAccounts.forEach { acc ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${acc.name} (${CurrencyConverter.format(acc.balanceCents, acc.currency)})",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(onClick = { viewModel.restoreAccount(acc.id) }) {
                                Text("Restore")
                            }
                        }
                    }
                }
            }
        }

        item {
            BudgetFlowCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("App Lock (Biometric / PIN)", fontWeight = FontWeight.Medium)
                        Text(
                            "Optional security when opening BudgetFlow",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = state.securityEnabled,
                        onCheckedChange = viewModel::setSecurityEnabled
                    )
                }
            }
        }

        item {
            BudgetFlowCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Bill Due Reminders", fontWeight = FontWeight.Medium)
                        Text(
                            "Daily check for unpaid bills due within 2 days",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = state.billRemindersEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            viewModel.setBillRemindersEnabled(enabled)
                        }
                    )
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { createBackupLauncher.launch("budgetflow-backup.json") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Export Full Backup (JSON)")
                }
                OutlinedButton(
                    onClick = { restoreBackupLauncher.launch(arrayOf("application/json", "*/*")) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Restore Backup (JSON)")
                }
                OutlinedButton(
                    onClick = {
                        viewModel.buildCsvExport { csv ->
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/csv"
                                putExtra(Intent.EXTRA_SUBJECT, "BudgetFlow Export")
                                putExtra(Intent.EXTRA_TEXT, csv)
                            }
                            context.startActivity(Intent.createChooser(intent, "Export Transactions CSV"))
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Export Transactions (CSV)")
                }
            }
        }
    }
    }

    if (showAddAccount) {
        AddAccountDialog(
            onDismiss = { showAddAccount = false },
            onSave = { name, type, currency, balance ->
                viewModel.addAccount(name, type, currency, balance)
                showAddAccount = false
            },
            title = "New Wallet / Account",
            nameLabel = "Account Name"
        )
    }

    editingAccount?.let { acc ->
        EditAccountDialog(
            account = acc,
            onDismiss = { editingAccount = null },
            onSave = { newName, newType, balanceInput, showOnHome ->
                viewModel.updateAccount(acc, newName, newType, balanceInput, showOnHome)
                editingAccount = null
            },
            onArchive = {
                viewModel.archiveAccount(acc.id)
                editingAccount = null
            },
            onDelete = {
                viewModel.deleteAccount(acc.id)
                editingAccount = null
            }
        )
    }
}
