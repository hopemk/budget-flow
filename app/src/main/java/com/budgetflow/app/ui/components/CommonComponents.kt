package com.budgetflow.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.budgetflow.app.domain.model.Account
import com.budgetflow.app.domain.model.AccountType
import com.budgetflow.app.domain.model.BudgetHealth
import com.budgetflow.app.domain.model.Category
import com.budgetflow.app.domain.model.CategoryBudgetStatus
import com.budgetflow.app.domain.model.CurrencyCode
import com.budgetflow.app.domain.model.DisplayCurrencyMode
import com.budgetflow.app.domain.model.Transaction
import com.budgetflow.app.domain.model.TransactionType
import com.budgetflow.app.domain.usecase.CurrencyConverter
import com.budgetflow.app.ui.theme.StatusError
import com.budgetflow.app.ui.theme.StatusWarning
import kotlinx.coroutines.flow.Flow

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DisplayCurrencySelector(
    selected: DisplayCurrencyMode,
    onSelect: (DisplayCurrencyMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = selected.includeAllCurrencies,
                onClick = { onSelect(selected.copy(includeAllCurrencies = true)) },
                label = { Text("All (${selected.targetCurrency.name})", style = MaterialTheme.typography.labelSmall) }
            )
            FilterChip(
                selected = !selected.includeAllCurrencies,
                onClick = { onSelect(selected.copy(includeAllCurrencies = false)) },
                label = { Text("${selected.targetCurrency.name} Only", style = MaterialTheme.typography.labelSmall) }
            )
            CurrencyCode.entries.forEach { currency ->
                FilterChip(
                    selected = selected.targetCurrency == currency,
                    onClick = { onSelect(selected.copy(targetCurrency = currency)) },
                    label = { Text(currency.name, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }
    }
}

@Composable
fun BudgetProgressCard(
    status: CategoryBudgetStatus,
    displayCurrency: CurrencyCode,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val barColor = when (status.health) {
        BudgetHealth.ON_TRACK -> MaterialTheme.colorScheme.primary
        BudgetHealth.WARNING -> StatusWarning
        BudgetHealth.OVER -> StatusError
    }
    val progress = (status.percentUsed.coerceIn(0, 100)) / 100f

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(status.category.name, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = status.budget.rolloverMode.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = if (status.remainingCents < 0) {
                        "Over by ${CurrencyConverter.format(-status.remainingCents, displayCurrency)}"
                    } else {
                        "${CurrencyConverter.format(status.remainingCents, displayCurrency)} left"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (status.remainingCents < 0) StatusError else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            }
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = barColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${CurrencyConverter.format(status.spentCents, displayCurrency)} spent",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val carryoverText = if (status.carryoverCents != 0L) {
                    " (incl. ${CurrencyConverter.format(status.carryoverCents, displayCurrency, showPlus = true)} rollover)"
                } else ""
                Text(
                    text = "${CurrencyConverter.format(status.effectiveLimitCents, displayCurrency)} limit$carryoverText",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun TransactionRow(
    transaction: Transaction,
    category: Category?,
    onDelete: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val dateText = DateFormats.formatShortDate(transaction.timestampMillis)

    val signedAmount = when (transaction.type) {
        TransactionType.EXPENSE -> -transaction.amountCents
        TransactionType.INCOME -> transaction.amountCents
        TransactionType.TRANSFER -> transaction.amountCents
    }
    val amountColor = when (transaction.type) {
        TransactionType.INCOME -> MaterialTheme.colorScheme.primary
        TransactionType.EXPENSE -> MaterialTheme.colorScheme.onSurface
        TransactionType.TRANSFER -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = transaction.merchant.ifBlank { category?.name ?: transaction.type.name },
                    fontWeight = FontWeight.Medium
                )
                val itemSuffix = if (transaction.items.isNotEmpty()) " • ${transaction.items.size} items" else ""
                Text(
                    text = "${category?.name ?: transaction.type.name} • $dateText$itemSuffix",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = CurrencyConverter.format(
                        signedAmount,
                        transaction.currency,
                        showPlus = transaction.type == TransactionType.INCOME
                    ),
                    fontWeight = FontWeight.SemiBold,
                    color = amountColor
                )
                if (onDelete != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Delete",
                        style = MaterialTheme.typography.labelSmall,
                        color = StatusError,
                        modifier = Modifier.clickable(onClick = onDelete)
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyStateCard(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun EditAccountDialog(
    account: Account,
    onDismiss: () -> Unit,
    onSave: (newName: String, newType: AccountType, balanceInput: String, showOnHome: Boolean) -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit
) {
    var name by remember(account) { mutableStateOf(account.name) }
    var balance by remember(account) {
        mutableStateOf(CurrencyConverter.formatInput(account.balanceCents))
    }
    var accountType by remember(account) { mutableStateOf(account.type) }
    var showOnHome by remember(account) { mutableStateOf(account.showOnHome) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit / Reconcile Account") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Account Name") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = balance,
                    onValueChange = { balance = it },
                    label = { Text("Reconciled Balance (${account.currency.name})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                AccountTypeChipRow(selected = accountType, onSelect = { accountType = it })
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Show on Home page", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = showOnHome, onCheckedChange = { showOnHome = it })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onArchive) {
                        Text("Archive")
                    }
                    TextButton(onClick = onDelete) {
                        Text("Delete", color = StatusError)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(name, accountType, balance, showOnHome) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun BudgetFlowCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    cornerRadius: Dp = 16.dp,
    contentPadding: PaddingValues = PaddingValues(14.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(8.dp),
    fillMaxWidth: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val cardModifier = modifier.then(if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier)
    val shape = RoundedCornerShape(cornerRadius)
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = cardModifier,
            shape = shape,
            colors = colors,
            border = border
        ) {
            Column(
                modifier = Modifier.padding(contentPadding),
                verticalArrangement = verticalArrangement,
                content = content
            )
        }
    } else {
        Card(
            modifier = cardModifier,
            shape = shape,
            colors = colors,
            border = border
        ) {
            Column(
                modifier = Modifier.padding(contentPadding),
                verticalArrangement = verticalArrangement,
                content = content
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CurrencyChipRow(
    selected: CurrencyCode,
    onSelect: (CurrencyCode) -> Unit,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CurrencyCode.entries.forEach { currency ->
            FilterChip(
                selected = selected == currency,
                onClick = { onSelect(currency) },
                label = { Text(currency.name) }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccountTypeChipRow(
    selected: AccountType,
    onSelect: (AccountType) -> Unit,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        AccountType.entries.forEach { type ->
            FilterChip(
                selected = selected == type,
                onClick = { onSelect(type) },
                label = { Text(type.displayName) }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccountPickerRow(
    accounts: List<Account>,
    selectedAccountId: Long?,
    onSelect: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    nullOptionLabel: String? = null,
    showBalance: Boolean = false,
    chipLabel: (Account) -> String = { acc ->
        if (showBalance) "${acc.name} (${CurrencyConverter.format(acc.balanceCents, acc.currency)})"
        else "${acc.name} (${acc.currency.name})"
    }
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (nullOptionLabel != null) {
            FilterChip(
                selected = selectedAccountId == null,
                onClick = { onSelect(null) },
                label = { Text(nullOptionLabel) }
            )
        }
        accounts.forEach { acc ->
            FilterChip(
                selected = selectedAccountId == acc.id,
                onClick = { onSelect(acc.id) },
                label = { Text(chipLabel(acc)) }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryHierarchySelector(
    categories: List<Category>,
    selectedCategoryId: Long?,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
    showParentNameInAllChip: Boolean = false
) {
    if (categories.isEmpty()) return
    val topCategories = remember(categories) {
        categories.filter { it.parentCategoryId == null }
    }
    val selectedCat = categories.firstOrNull { it.id == selectedCategoryId }
    val selectedTopId = selectedCat?.parentCategoryId ?: selectedCat?.id
    val subcategories = remember(categories, selectedTopId) {
        selectedTopId?.let { parentId ->
            categories.filter { it.parentCategoryId == parentId }
        }.orEmpty()
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "Category",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            topCategories.forEach { cat ->
                FilterChip(
                    selected = selectedTopId == cat.id,
                    onClick = { onSelect(cat.id) },
                    label = { Text(cat.name) }
                )
            }
        }
        if (subcategories.isNotEmpty() && selectedTopId != null) {
            val allLabel = if (showParentNameInAllChip) {
                val parentName = topCategories.firstOrNull { it.id == selectedTopId }?.name ?: "General"
                "All ($parentName)"
            } else {
                "All"
            }
            Text(
                "Subcategory (optional)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = selectedCategoryId == selectedTopId,
                    onClick = { onSelect(selectedTopId) },
                    label = { Text(allLabel) }
                )
                subcategories.forEach { sub ->
                    FilterChip(
                        selected = selectedCategoryId == sub.id,
                        onClick = { onSelect(sub.id) },
                        label = { Text(sub.name) }
                    )
                }
            }
        }
    }
}

@Composable
fun AddAccountDialog(
    onDismiss: () -> Unit,
    onSave: (name: String, type: AccountType, currency: CurrencyCode, balanceInput: String) -> Unit,
    title: String = "New Account / Wallet",
    nameLabel: String = "Account Name (e.g. Cash, Bank)",
    confirmLabel: String = "Save",
    initialType: AccountType = AccountType.CASH,
    showTypeSelector: Boolean = true
) {
    var name by remember { mutableStateOf("") }
    var balance by remember { mutableStateOf("0.00") }
    var currency by remember { mutableStateOf(CurrencyCode.USD) }
    var accountType by remember { mutableStateOf(initialType) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(nameLabel) },
                    singleLine = true
                )
                OutlinedTextField(
                    value = balance,
                    onValueChange = { balance = it },
                    label = { Text("Starting Balance (${currency.name})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                CurrencyChipRow(selected = currency, onSelect = { currency = it })
                if (showTypeSelector) {
                    AccountTypeChipRow(selected = accountType, onSelect = { accountType = it })
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(name, accountType, currency, balance) }) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun MessageSnackbarBox(
    messages: Flow<String>,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(messages) {
        messages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }
    Box(modifier = modifier.fillMaxSize()) {
        content()
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
