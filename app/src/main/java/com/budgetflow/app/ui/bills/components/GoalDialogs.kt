package com.budgetflow.app.ui.bills.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.budgetflow.app.domain.model.Account
import com.budgetflow.app.domain.model.AccountType
import com.budgetflow.app.domain.model.CurrencyCode
import com.budgetflow.app.domain.model.SavingsGoal
import com.budgetflow.app.domain.usecase.CurrencyConverter
import com.budgetflow.app.ui.components.AccountPickerRow
import com.budgetflow.app.ui.components.CurrencyChipRow

@Composable
fun GoalEditorDialog(
    existing: SavingsGoal?,
    onDismiss: () -> Unit,
    onSave: (name: String, targetInput: String, currentInput: String, currency: CurrencyCode) -> Unit
) {
    var name by remember(existing) { mutableStateOf(existing?.name ?: "") }
    var target by remember(existing) {
        mutableStateOf(existing?.let { CurrencyConverter.formatInput(it.targetCents) } ?: "")
    }
    var current by remember(existing) {
        mutableStateOf(existing?.let { CurrencyConverter.formatInput(it.currentCents) } ?: "0.00")
    }
    var currency by remember(existing) { mutableStateOf(existing?.currency ?: CurrencyCode.USD) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "New Savings Goal" else "Edit Savings Goal") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Goal Name") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = target,
                    onValueChange = { target = it },
                    label = { Text("Target Amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                if (existing != null) {
                    OutlinedTextField(
                        value = current,
                        onValueChange = { current = it },
                        label = { Text("Current Saved") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                }
                CurrencyChipRow(selected = currency, onSelect = { currency = it })
            }
        },
        confirmButton = {
            Button(onClick = { onSave(name, target, current, currency) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun ContributeGoalDialog(
    goal: SavingsGoal,
    accounts: List<Account>,
    onDismiss: () -> Unit,
    onConfirm: (amountInput: String, accountId: Long?) -> Unit
) {
    var amount by remember(goal) { mutableStateOf("10.00") }
    var selectedAccountId by remember(goal) {
        mutableStateOf(accounts.firstOrNull { it.currency == goal.currency }?.id ?: accounts.firstOrNull()?.id)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Contribute to ${goal.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount (${goal.currency.name})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                Text("Deduct from Account (optional):", style = MaterialTheme.typography.labelSmall)
                AccountPickerRow(
                    accounts = accounts,
                    selectedAccountId = selectedAccountId,
                    onSelect = { selectedAccountId = it },
                    nullOptionLabel = "Track only"
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(amount, selectedAccountId) }) { Text("Contribute") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun TransferSavingsDialog(
    initialSource: Account,
    allAccounts: List<Account>,
    savingsAccounts: List<Account>,
    onDismiss: () -> Unit,
    onConfirm: (source: Account, target: Account, amountInput: String, note: String) -> Unit
) {
    var selectedSource by remember(initialSource) { mutableStateOf(initialSource) }
    val availableTargets = allAccounts.filter { it.id != selectedSource.id }
    var targetId by remember(initialSource, selectedSource) {
        mutableStateOf(
            availableTargets.firstOrNull { it.type != AccountType.SAVINGS }?.id
                ?: availableTargets.firstOrNull()?.id
        )
    }
    var amount by remember(initialSource) { mutableStateOf("") }
    var note by remember(initialSource) { mutableStateOf("Transfer") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Transfer from Savings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("From Savings Account:", style = MaterialTheme.typography.labelSmall)
                AccountPickerRow(
                    accounts = savingsAccounts,
                    selectedAccountId = selectedSource.id,
                    onSelect = { id ->
                        savingsAccounts.firstOrNull { it.id == id }?.let { selectedSource = it }
                    },
                    showBalance = true
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount (${selectedSource.currency.name})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Purpose / Note") },
                    singleLine = true
                )
                Text("To Account (Operating or Another Savings):", style = MaterialTheme.typography.labelSmall)
                AccountPickerRow(
                    accounts = availableTargets,
                    selectedAccountId = targetId,
                    onSelect = { targetId = it },
                    chipLabel = { dest ->
                        val tag = if (dest.type == AccountType.SAVINGS) "Savings" else "Operating"
                        "${dest.name} ($tag • ${dest.currency.name})"
                    }
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val target = availableTargets.firstOrNull { it.id == targetId }
                if (target != null) {
                    onConfirm(selectedSource, target, amount, note)
                }
                onDismiss()
            }) { Text("Transfer") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
