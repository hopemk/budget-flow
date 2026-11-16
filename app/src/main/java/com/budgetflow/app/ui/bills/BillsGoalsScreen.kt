package com.budgetflow.app.ui.bills

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.budgetflow.app.domain.model.Account
import com.budgetflow.app.domain.model.AccountType
import com.budgetflow.app.domain.model.CurrencyCode
import com.budgetflow.app.domain.model.Payable
import com.budgetflow.app.domain.model.Receivable
import com.budgetflow.app.domain.model.RecurringBill
import com.budgetflow.app.domain.model.SavingsGoal
import com.budgetflow.app.ui.bills.components.BillEditorDialog
import com.budgetflow.app.ui.bills.components.ContributeGoalDialog
import com.budgetflow.app.ui.bills.components.DebtEditorDialog
import com.budgetflow.app.ui.bills.components.DebtItemCard
import com.budgetflow.app.ui.bills.components.GoalEditorDialog
import com.budgetflow.app.ui.bills.components.PayBillDialog
import com.budgetflow.app.ui.bills.components.RecordDebtPaymentDialog
import com.budgetflow.app.ui.bills.components.RecurringBillCard
import com.budgetflow.app.ui.bills.components.SavingsAccountCard
import com.budgetflow.app.ui.bills.components.SavingsGoalCard
import com.budgetflow.app.ui.bills.components.TransferSavingsDialog
import com.budgetflow.app.ui.components.AddAccountDialog
import com.budgetflow.app.ui.components.EditAccountDialog
import com.budgetflow.app.ui.components.EmptyStateCard
import com.budgetflow.app.ui.components.MessageSnackbarBox
import java.time.LocalDate

private sealed interface BillsGoalsDialog {
    data class BillEditor(val existing: RecurringBill? = null) : BillsGoalsDialog
    data class PayBill(val bill: RecurringBill) : BillsGoalsDialog
    data class GoalEditor(val existing: SavingsGoal? = null) : BillsGoalsDialog
    data class ContributeGoal(val goal: SavingsGoal) : BillsGoalsDialog
    data object AddSavingsAccount : BillsGoalsDialog
    data class TransferSavings(val source: Account) : BillsGoalsDialog
    data class EditAccount(val account: Account) : BillsGoalsDialog
    data class ReceivableEditor(val existing: Receivable? = null) : BillsGoalsDialog
    data class PayableEditor(val existing: Payable? = null) : BillsGoalsDialog
    data class PayReceivable(val receivable: Receivable) : BillsGoalsDialog
    data class PayPayable(val payable: Payable) : BillsGoalsDialog
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BillsGoalsScreen(viewModel: BillsGoalsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var activeDialog by remember { mutableStateOf<BillsGoalsDialog?>(null) }

    val savingsAccounts = state.accounts.filter { it.type == AccountType.SAVINGS }
    val todayDay = remember { LocalDate.now().dayOfMonth }

    MessageSnackbarBox(messages = viewModel.messages) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
        item {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = { activeDialog = BillsGoalsDialog.BillEditor() }) { Text("+ Bill") }
                OutlinedButton(onClick = { activeDialog = BillsGoalsDialog.GoalEditor() }) { Text("+ Goal") }
                OutlinedButton(onClick = { activeDialog = BillsGoalsDialog.AddSavingsAccount }) { Text("+ Savings Account") }
                if (state.accounts.size >= 2) {
                    OutlinedButton(onClick = {
                        activeDialog = BillsGoalsDialog.TransferSavings(savingsAccounts.firstOrNull() ?: state.accounts.first())
                    }) {
                        Text("Transfer / Move Money")
                    }
                }
                OutlinedButton(onClick = { activeDialog = BillsGoalsDialog.ReceivableEditor() }) { Text("+ Owed to Me") }
                OutlinedButton(onClick = { activeDialog = BillsGoalsDialog.PayableEditor() }) { Text("+ I Owe") }
            }
        }

        item {
            Text("SAVINGS ACCOUNTS (RESERVES)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (savingsAccounts.isEmpty()) {
            item {
                EmptyStateCard(
                    "No custom savings accounts",
                    "Create a savings account to keep reserves separate and draw into operating wallets when needed."
                )
            }
        } else {
            items(savingsAccounts, key = { "sav_${it.id}" }) { acc ->
                SavingsAccountCard(
                    account = acc,
                    canTransfer = state.accounts.size >= 2,
                    onEdit = { activeDialog = BillsGoalsDialog.EditAccount(acc) },
                    onTransfer = { activeDialog = BillsGoalsDialog.TransferSavings(acc) }
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("RECURRING BILLS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (state.bills.any { it.isPaidThisMonth }) {
                    TextButton(onClick = { viewModel.resetAllBillsPaid() }) { Text("Reset Paid") }
                }
            }
        }
        if (state.bills.isEmpty()) {
            item { EmptyStateCard("No recurring bills", "Track rent, internet, or subscriptions here.") }
        } else {
            items(state.bills, key = { "bill_${it.id}" }) { bill ->
                RecurringBillCard(
                    bill = bill,
                    todayDay = todayDay,
                    onEdit = { activeDialog = BillsGoalsDialog.BillEditor(bill) },
                    onTogglePaid = { checked ->
                        if (checked) activeDialog = BillsGoalsDialog.PayBill(bill)
                        else viewModel.toggleBillPaid(bill.id, false)
                    },
                    onDelete = { viewModel.deleteBill(bill.id) }
                )
            }
        }

        item {
            Text("SAVINGS GOALS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (state.goals.isEmpty()) {
            item { EmptyStateCard("No savings goals", "Create a sinking fund or emergency target.") }
        } else {
            items(state.goals, key = { "goal_${it.id}" }) { goal ->
                SavingsGoalCard(
                    goal = goal,
                    onEdit = { activeDialog = BillsGoalsDialog.GoalEditor(goal) },
                    onContribute = { activeDialog = BillsGoalsDialog.ContributeGoal(goal) },
                    onDelete = { viewModel.deleteGoal(goal.id) }
                )
            }
        }

        item {
            Text("WHAT I'M OWED", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (state.receivables.isEmpty()) {
            item { EmptyStateCard("Nothing owed to you", "Track split bills, loans, or reimbursements here.") }
        } else {
            items(state.receivables, key = { "rec_${it.id}" }) { rec ->
                DebtItemCard(
                    isReceivable = true,
                    personName = rec.personName,
                    description = rec.description,
                    totalCents = rec.totalOwedCents,
                    remainingCents = rec.remainingCents,
                    currency = rec.currency,
                    isSettled = rec.isSettled,
                    onEdit = { activeDialog = BillsGoalsDialog.ReceivableEditor(rec) },
                    onPay = { activeDialog = BillsGoalsDialog.PayReceivable(rec) },
                    onDelete = { viewModel.deleteReceivable(rec.id) }
                )
            }
        }

        item {
            Text("WHAT I OWE", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (state.payables.isEmpty()) {
            item { EmptyStateCard("Nothing you owe", "Track debts, IOUs, or money you need to pay back here.") }
        } else {
            items(state.payables, key = { "pay_${it.id}" }) { payable ->
                DebtItemCard(
                    isReceivable = false,
                    personName = payable.personName,
                    description = payable.description,
                    totalCents = payable.totalOweCents,
                    remainingCents = payable.remainingCents,
                    currency = payable.currency,
                    isSettled = payable.isSettled,
                    onEdit = { activeDialog = BillsGoalsDialog.PayableEditor(payable) },
                    onPay = { activeDialog = BillsGoalsDialog.PayPayable(payable) },
                    onDelete = { viewModel.deletePayable(payable.id) }
                )
            }
        }
    }
    }

    when (val dialog = activeDialog) {
        is BillsGoalsDialog.BillEditor -> BillEditorDialog(
            existing = dialog.existing,
            onDismiss = { activeDialog = null },
            onSave = { name, amount, currency, dueDay ->
                if (dialog.existing == null) viewModel.addBill(name, amount, currency, dueDay)
                else viewModel.updateBill(dialog.existing, name, amount, currency, dueDay)
                activeDialog = null
            }
        )
        is BillsGoalsDialog.PayBill -> PayBillDialog(
            bill = dialog.bill,
            accounts = state.accounts,
            categories = state.categories,
            onDismiss = { activeDialog = null },
            onConfirm = { accountId, categoryId ->
                viewModel.payBill(dialog.bill.id, accountId, categoryId)
                activeDialog = null
            }
        )
        is BillsGoalsDialog.GoalEditor -> GoalEditorDialog(
            existing = dialog.existing,
            onDismiss = { activeDialog = null },
            onSave = { name, target, currentInput, currency ->
                if (dialog.existing == null) viewModel.addGoal(name, target, currency)
                else viewModel.updateGoal(dialog.existing, name, target, currentInput, currency)
                activeDialog = null
            }
        )
        is BillsGoalsDialog.ContributeGoal -> ContributeGoalDialog(
            goal = dialog.goal,
            accounts = state.accounts,
            onDismiss = { activeDialog = null },
            onConfirm = { amount, accountId ->
                viewModel.contributeToGoal(dialog.goal.id, amount, accountId)
                activeDialog = null
            }
        )
        BillsGoalsDialog.AddSavingsAccount -> AddAccountDialog(
            onDismiss = { activeDialog = null },
            onSave = { name, _, currency, balance ->
                viewModel.addSavingsAccount(name, currency, balance)
                activeDialog = null
            },
            title = "New Savings Account",
            nameLabel = "Account Name (e.g. Reserve)",
            confirmLabel = "Create",
            initialType = AccountType.SAVINGS,
            showTypeSelector = false
        )
        is BillsGoalsDialog.TransferSavings -> TransferSavingsDialog(
            initialSource = dialog.source,
            allAccounts = state.accounts,
            savingsAccounts = savingsAccounts,
            onDismiss = { activeDialog = null },
            onConfirm = { source, target, amount, note ->
                viewModel.transferBetweenAccounts(source, target, amount, note)
                activeDialog = null
            }
        )
        is BillsGoalsDialog.EditAccount -> EditAccountDialog(
            account = dialog.account,
            onDismiss = { activeDialog = null },
            onSave = { newName, newType, balanceInput, showOnHome ->
                viewModel.updateAccount(dialog.account, newName, newType, balanceInput, showOnHome)
                activeDialog = null
            },
            onArchive = {
                viewModel.archiveAccount(dialog.account.id)
                activeDialog = null
            },
            onDelete = {
                viewModel.deleteAccount(dialog.account.id)
                activeDialog = null
            }
        )
        is BillsGoalsDialog.ReceivableEditor -> DebtEditorDialog(
            isReceivable = true,
            initialPerson = dialog.existing?.personName ?: "",
            initialDescription = dialog.existing?.description ?: "",
            initialAmountCents = dialog.existing?.totalOwedCents,
            initialCurrency = dialog.existing?.currency ?: CurrencyCode.USD,
            onDismiss = { activeDialog = null },
            onSave = { person, desc, amount, currency ->
                if (dialog.existing == null) viewModel.addReceivable(person, desc, amount, currency)
                else viewModel.updateReceivable(dialog.existing, person, desc, amount, currency)
                activeDialog = null
            }
        )
        is BillsGoalsDialog.PayableEditor -> DebtEditorDialog(
            isReceivable = false,
            initialPerson = dialog.existing?.personName ?: "",
            initialDescription = dialog.existing?.description ?: "",
            initialAmountCents = dialog.existing?.totalOweCents,
            initialCurrency = dialog.existing?.currency ?: CurrencyCode.USD,
            onDismiss = { activeDialog = null },
            onSave = { person, desc, amount, currency ->
                if (dialog.existing == null) viewModel.addPayable(person, desc, amount, currency)
                else viewModel.updatePayable(dialog.existing, person, desc, amount, currency)
                activeDialog = null
            }
        )
        is BillsGoalsDialog.PayReceivable -> RecordDebtPaymentDialog(
            isReceivable = true,
            personName = dialog.receivable.personName,
            remainingCents = dialog.receivable.remainingCents,
            currency = dialog.receivable.currency,
            accounts = state.accounts,
            onDismiss = { activeDialog = null },
            onConfirm = { payAmount, selectedAccountId ->
                viewModel.recordPayment(dialog.receivable.id, payAmount, selectedAccountId)
                activeDialog = null
            }
        )
        is BillsGoalsDialog.PayPayable -> RecordDebtPaymentDialog(
            isReceivable = false,
            personName = dialog.payable.personName,
            remainingCents = dialog.payable.remainingCents,
            currency = dialog.payable.currency,
            accounts = state.accounts,
            onDismiss = { activeDialog = null },
            onConfirm = { payAmount, selectedAccountId ->
                viewModel.recordPayablePayment(dialog.payable.id, payAmount, selectedAccountId)
                activeDialog = null
            }
        )
        null -> Unit
    }
}
