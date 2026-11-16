package com.budgetflow.app.ui.spend

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.budgetflow.app.domain.model.CurrencyCode
import com.budgetflow.app.domain.model.TransactionType
import com.budgetflow.app.domain.usecase.CurrencyConverter
import com.budgetflow.app.ui.components.AccountPickerRow
import com.budgetflow.app.ui.components.BudgetFlowCard
import com.budgetflow.app.ui.components.CategoryHierarchySelector
import com.budgetflow.app.ui.components.DateFormats
import com.budgetflow.app.ui.components.MessageSnackbarBox
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpendScreen(
    onOpenScanner: () -> Unit,
    onSaved: () -> Unit,
    viewModel: SpendViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val form = state.form
    val activeAccount = state.accounts.firstOrNull { it.id == form.selectedAccountId }
    val activeCurrency = activeAccount?.currency ?: CurrencyCode.USD
    var showAddItemDialog by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    val dateLabel = remember(form.timestampMillis) {
        DateFormats.formatFullDayDate(form.timestampMillis)
    }

    MessageSnackbarBox(messages = viewModel.messages) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (form.editingTransactionId != null) "Edit Transaction" else "Log Purchase",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                OutlinedButton(onClick = onOpenScanner) {
                    Text("Scan Receipt")
                }
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TransactionType.entries.forEach { type ->
                    FilterChip(
                        selected = form.type == type,
                        onClick = { viewModel.setType(type) },
                        label = { Text(type.displayName) }
                    )
                }
            }
        }

        if (form.type != TransactionType.TRANSFER) {
            item {
                OutlinedTextField(
                    value = form.merchant,
                    onValueChange = viewModel::setMerchant,
                    label = { Text(if (form.type == TransactionType.INCOME) "Source / From whom?" else "Merchant / What did you buy?") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        }

        item {
            OutlinedTextField(
                value = form.amountInput,
                onValueChange = viewModel::setAmount,
                label = { Text("Total Amount (${activeCurrency.name})") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        // Itemized "What you bought" section (expenses only)
        if (form.type == TransactionType.EXPENSE) {
            item {
                BudgetFlowCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("WHAT YOU BOUGHT (ITEMS)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { showAddItemDialog = true }) { Text("+ Add item") }
                    }
                    if (form.items.isEmpty()) {
                        Text(
                            "Optional: break down individual items or scan a receipt.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        form.items.forEachIndexed { index, item ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(item.name, modifier = Modifier.weight(1f))
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(CurrencyConverter.format(item.amountCents, activeCurrency), fontWeight = FontWeight.Medium)
                                    TextButton(onClick = { viewModel.removeLineItem(index) }) { Text("×") }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (form.type == TransactionType.EXPENSE && state.categories.isNotEmpty()) {
            item {
                CategoryHierarchySelector(
                    categories = state.categories,
                    selectedCategoryId = form.selectedCategoryId,
                    onSelect = viewModel::setCategory,
                    showParentNameInAllChip = true
                )
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val accountLabel = when (form.type) {
                    TransactionType.EXPENSE -> "Pay From Account"
                    TransactionType.INCOME -> "Deposit Into Account"
                    TransactionType.TRANSFER -> "From Account"
                }
                Text(accountLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                AccountPickerRow(
                    accounts = state.accounts,
                    selectedAccountId = form.selectedAccountId,
                    onSelect = { it?.let(viewModel::setAccount) },
                    showBalance = true
                )
            }
        }

        if (form.type == TransactionType.TRANSFER) {
            val destinationAccounts = state.accounts.filter { it.id != form.selectedAccountId }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("To Account", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (destinationAccounts.isEmpty()) {
                        Text(
                            "Add another account first to move money between accounts.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        AccountPickerRow(
                            accounts = destinationAccounts,
                            selectedAccountId = form.targetAccountId,
                            onSelect = { it?.let(viewModel::setTargetAccount) },
                            showBalance = true
                        )
                    }
                }
            }
        }

        item {
            OutlinedButton(
                onClick = { showDatePicker = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Date: $dateLabel")
            }
        }

        item {
            OutlinedTextField(
                value = form.note,
                onValueChange = viewModel::setNote,
                label = { Text("Note (optional)") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            Button(
                onClick = { viewModel.savePurchase(onSaved) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                enabled = form.type != TransactionType.TRANSFER || state.accounts.size >= 2
            ) {
                val submitLabel = when (form.type) {
                    TransactionType.EXPENSE -> "Save Expense"
                    TransactionType.INCOME -> "Save Income"
                    TransactionType.TRANSFER -> "Move Money"
                }
                Text(submitLabel, modifier = Modifier.padding(vertical = 6.dp))
            }
        }
    }
    }

    if (showAddItemDialog) {
        var itemName by remember { mutableStateOf("") }
        var itemPrice by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddItemDialog = false },
            title = { Text("Add Purchased Item") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = itemName, onValueChange = { itemName = it }, label = { Text("Item Name") }, singleLine = true)
                    OutlinedTextField(
                        value = itemPrice,
                        onValueChange = { itemPrice = it },
                        label = { Text("Price (${activeCurrency.name})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.addLineItem(itemName, itemPrice)
                    showAddItemDialog = false
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showAddItemDialog = false }) { Text("Cancel") } }
        )
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = form.timestampMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { utcMillis ->
                        val localNoonMillis = Instant.ofEpochMilli(utcMillis)
                            .atZone(ZoneOffset.UTC)
                            .toLocalDate()
                            .atTime(12, 0)
                            .atZone(ZoneId.systemDefault())
                            .toInstant()
                            .toEpochMilli()
                        viewModel.setDateMillis(localNoonMillis)
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
