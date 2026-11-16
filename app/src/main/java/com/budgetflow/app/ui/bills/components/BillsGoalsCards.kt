package com.budgetflow.app.ui.bills.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.budgetflow.app.domain.model.Account
import com.budgetflow.app.domain.model.CurrencyCode
import com.budgetflow.app.domain.model.RecurringBill
import com.budgetflow.app.domain.model.SavingsGoal
import com.budgetflow.app.domain.usecase.CurrencyConverter
import com.budgetflow.app.ui.components.BudgetFlowCard

@Composable
fun SavingsAccountCard(
    account: Account,
    canTransfer: Boolean,
    onEdit: () -> Unit,
    onTransfer: () -> Unit
) {
    BudgetFlowCard(onClick = onEdit, verticalArrangement = Arrangement.Top) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(account.name, fontWeight = FontWeight.SemiBold)
                Text(
                    text = CurrencyConverter.format(account.balanceCents, account.currency),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (canTransfer) {
                TextButton(onClick = onTransfer) { Text("Draw / Transfer") }
            }
        }
    }
}

@Composable
fun RecurringBillCard(
    bill: RecurringBill,
    todayDay: Int,
    onEdit: () -> Unit,
    onTogglePaid: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    BudgetFlowCard(
        onClick = onEdit,
        cornerRadius = 14.dp,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.Top
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = bill.isPaidThisMonth,
                    onCheckedChange = onTogglePaid
                )
                Column {
                    Text(bill.name, fontWeight = FontWeight.Medium)
                    val statusText = when {
                        bill.isPaidThisMonth -> "Paid this month"
                        todayDay > bill.dueDayOfMonth -> "Due day ${bill.dueDayOfMonth} • Overdue"
                        bill.dueDayOfMonth - todayDay in 0..3 -> "Due day ${bill.dueDayOfMonth} • Due soon"
                        else -> "Due day ${bill.dueDayOfMonth}"
                    }
                    val statusColor = when {
                        bill.isPaidThisMonth -> MaterialTheme.colorScheme.primary
                        todayDay > bill.dueDayOfMonth -> MaterialTheme.colorScheme.error
                        bill.dueDayOfMonth - todayDay in 0..3 -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Text(statusText, style = MaterialTheme.typography.labelSmall, color = statusColor)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(CurrencyConverter.format(bill.amountCents, bill.currency), fontWeight = FontWeight.SemiBold)
                TextButton(onClick = onDelete) {
                    Text("×", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
fun SavingsGoalCard(
    goal: SavingsGoal,
    onEdit: () -> Unit,
    onContribute: () -> Unit,
    onDelete: () -> Unit
) {
    val pct = if (goal.targetCents > 0) ((goal.currentCents * 100) / goal.targetCents).toInt().coerceIn(0, 100) else 0
    BudgetFlowCard(onClick = onEdit) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(goal.name, fontWeight = FontWeight.SemiBold)
            Text("$pct%", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        }
        LinearProgressIndicator(
            progress = pct / 100f,
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "${CurrencyConverter.format(goal.currentCents, goal.currency)} / ${CurrencyConverter.format(goal.targetCents, goal.currency)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onContribute) { Text("Contribute") }
                TextButton(onClick = onDelete) {
                    Text("×", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
fun DebtItemCard(
    isReceivable: Boolean,
    personName: String,
    description: String,
    totalCents: Long,
    remainingCents: Long,
    currency: CurrencyCode,
    isSettled: Boolean,
    onEdit: () -> Unit,
    onPay: () -> Unit,
    onDelete: () -> Unit
) {
    BudgetFlowCard(onClick = onEdit, verticalArrangement = Arrangement.Top) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(personName, fontWeight = FontWeight.SemiBold)
                if (description.isNotBlank()) {
                    Text(description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val settledWord = if (isReceivable) "Settled" else "Paid"
                val statusColor = when {
                    isSettled -> MaterialTheme.colorScheme.primary
                    isReceivable -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> MaterialTheme.colorScheme.error
                }
                Text(
                    text = if (isSettled) {
                        "$settledWord (${CurrencyConverter.format(totalCents, currency)})"
                    } else {
                        "Remaining: ${CurrencyConverter.format(remainingCents, currency)} of ${CurrencyConverter.format(totalCents, currency)}"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!isSettled) {
                    TextButton(onClick = onPay) {
                        Text(if (isReceivable) "Pay / Settle" else "Pay")
                    }
                }
                TextButton(onClick = onDelete) {
                    Text("×", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
