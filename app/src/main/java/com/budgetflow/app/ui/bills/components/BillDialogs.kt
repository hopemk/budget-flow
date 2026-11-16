package com.budgetflow.app.ui.bills.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.budgetflow.app.domain.model.Account
import com.budgetflow.app.domain.model.Category
import com.budgetflow.app.domain.model.CurrencyCode
import com.budgetflow.app.domain.model.RecurringBill
import com.budgetflow.app.domain.usecase.CurrencyConverter
import com.budgetflow.app.ui.components.AccountPickerRow
import com.budgetflow.app.ui.components.CurrencyChipRow

@Composable
fun BillEditorDialog(
    existing: RecurringBill?,
    onDismiss: () -> Unit,
    onSave: (name: String, amountInput: String, currency: CurrencyCode, dueDay: Int) -> Unit
) {
    var name by remember(existing) { mutableStateOf(existing?.name ?: "") }
    var amount by remember(existing) {
        mutableStateOf(existing?.let { CurrencyConverter.formatInput(it.amountCents) } ?: "")
    }
    var day by remember(existing) { mutableStateOf((existing?.dueDayOfMonth ?: 1).toString()) }
    var currency by remember(existing) { mutableStateOf(existing?.currency ?: CurrencyCode.USD) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "New Recurring Bill" else "Edit Recurring Bill") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Bill Name") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                OutlinedTextField(
                    value = day,
                    onValueChange = { day = it },
                    label = { Text("Due Day (1–28)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                CurrencyChipRow(selected = currency, onSelect = { currency = it })
            }
        },
        confirmButton = {
            Button(onClick = {
                val fallbackDay = existing?.dueDayOfMonth ?: 1
                onSave(name, amount, currency, day.toIntOrNull() ?: fallbackDay)
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PayBillDialog(
    bill: RecurringBill,
    accounts: List<Account>,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onConfirm: (accountId: Long?, categoryId: Long?) -> Unit
) {
    var selectedAccountId by remember(bill) {
        mutableStateOf(accounts.firstOrNull { it.currency == bill.currency }?.id ?: accounts.firstOrNull()?.id)
    }
    var selectedCategoryId by remember(bill) {
        mutableStateOf(bill.categoryId ?: categories.firstOrNull()?.id)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pay Recurring Bill") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "${bill.name} • ${CurrencyConverter.format(bill.amountCents, bill.currency)}",
                    fontWeight = FontWeight.SemiBold
                )
                Text("Debit from Account (optional):", style = MaterialTheme.typography.labelSmall)
                AccountPickerRow(
                    accounts = accounts,
                    selectedAccountId = selectedAccountId,
                    onSelect = { selectedAccountId = it },
                    nullOptionLabel = "Mark paid only"
                )
                if (selectedAccountId != null && categories.isNotEmpty()) {
                    Text("Budget Category (optional):", style = MaterialTheme.typography.labelSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = selectedCategoryId == null,
                            onClick = { selectedCategoryId = null },
                            label = { Text("None") }
                        )
                        categories.forEach { cat ->
                            FilterChip(
                                selected = selectedCategoryId == cat.id,
                                onClick = { selectedCategoryId = cat.id },
                                label = { Text(cat.name) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selectedAccountId, selectedCategoryId) }) { Text("Confirm") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
