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
import com.budgetflow.app.domain.model.CurrencyCode
import com.budgetflow.app.domain.usecase.CurrencyConverter
import com.budgetflow.app.ui.components.AccountPickerRow
import com.budgetflow.app.ui.components.CurrencyChipRow

@Composable
fun DebtEditorDialog(
    isReceivable: Boolean,
    initialPerson: String = "",
    initialDescription: String = "",
    initialAmountCents: Long? = null,
    initialCurrency: CurrencyCode = CurrencyCode.USD,
    onDismiss: () -> Unit,
    onSave: (person: String, description: String, amountInput: String, currency: CurrencyCode) -> Unit
) {
    val isEditing = initialAmountCents != null
    var person by remember(isReceivable, initialPerson) { mutableStateOf(initialPerson) }
    var desc by remember(isReceivable, initialDescription) { mutableStateOf(initialDescription) }
    var amount by remember(isReceivable, initialAmountCents) {
        mutableStateOf(initialAmountCents?.let { CurrencyConverter.formatInput(it) } ?: "")
    }
    var currency by remember(isReceivable, initialCurrency) { mutableStateOf(initialCurrency) }

    val title = when {
        isReceivable && isEditing -> "Edit What I'm Owed"
        isReceivable -> "What I'm Owed"
        isEditing -> "Edit What I Owe"
        else -> "What I Owe"
    }
    val personLabel = if (isReceivable) "Person / Client" else "Person / Lender"
    val amountLabel = if (isEditing) "Total Owed" else "Amount Owed"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = person,
                    onValueChange = { person = it },
                    label = { Text(personLabel) },
                    singleLine = true
                )
                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Reason (optional)") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text(amountLabel) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                CurrencyChipRow(selected = currency, onSelect = { currency = it })
            }
        },
        confirmButton = {
            Button(onClick = { onSave(person, desc, amount, currency) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun RecordDebtPaymentDialog(
    isReceivable: Boolean,
    personName: String,
    remainingCents: Long,
    currency: CurrencyCode,
    accounts: List<Account>,
    onDismiss: () -> Unit,
    onConfirm: (amountInput: String, accountId: Long?) -> Unit
) {
    var payAmount by remember(personName, remainingCents, currency) {
        mutableStateOf(CurrencyConverter.formatInput(remainingCents))
    }
    var selectedAccountId by remember(currency, accounts) {
        mutableStateOf(accounts.firstOrNull { it.currency == currency }?.id ?: accounts.firstOrNull()?.id)
    }

    val title = if (isReceivable) "Record Repayment" else "Pay Back"
    val formattedRemaining = CurrencyConverter.format(remainingCents, currency)
    val summaryText = if (isReceivable) {
        "$personName owes $formattedRemaining"
    } else {
        "You owe $personName $formattedRemaining"
    }
    val amountLabel = if (isReceivable) {
        "Amount Received (${currency.name})"
    } else {
        "Amount Paid (${currency.name})"
    }
    val walletPrompt = if (isReceivable) {
        "Credit to Wallet (optional):"
    } else {
        "Debit from Wallet (optional):"
    }
    val nullOptionLabel = if (isReceivable) "Don't credit" else "Don't debit"
    val confirmLabel = if (isReceivable) "Confirm" else "Pay"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(summaryText)
                OutlinedTextField(
                    value = payAmount,
                    onValueChange = { payAmount = it },
                    label = { Text(amountLabel) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                Text(walletPrompt, style = MaterialTheme.typography.labelSmall)
                AccountPickerRow(
                    accounts = accounts,
                    selectedAccountId = selectedAccountId,
                    onSelect = { selectedAccountId = it },
                    nullOptionLabel = nullOptionLabel
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(payAmount, selectedAccountId) }) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
