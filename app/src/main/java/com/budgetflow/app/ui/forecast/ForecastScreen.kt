package com.budgetflow.app.ui.forecast

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import com.budgetflow.app.domain.model.ForecastBudgetAdjustment
import com.budgetflow.app.domain.model.ForecastExpenseSource
import com.budgetflow.app.domain.usecase.CurrencyConverter
import com.budgetflow.app.ui.components.BudgetFlowCard
import com.budgetflow.app.ui.components.CategoryHierarchySelector
import com.budgetflow.app.ui.components.CurrencyChipRow
import com.budgetflow.app.ui.components.DateFormats
import com.budgetflow.app.ui.components.MessageSnackbarBox
import com.budgetflow.app.ui.theme.EmeraldPrimary
import com.budgetflow.app.ui.theme.StatusError

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ForecastScreen(viewModel: ForecastViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val summary = state.summary

    var showAddIncomeDialog by remember { mutableStateOf(false) }
    var showAddExpenseDialog by remember { mutableStateOf(false) }
    var showApplyConfirmDialog by remember { mutableStateOf(false) }
    var editingBudget by remember { mutableStateOf<ForecastBudgetAdjustment?>(null) }
    var showRateOverrideDialog by remember { mutableStateOf(false) }

    MessageSnackbarBox(messages = viewModel.messages) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (summary != null) {
                item {
                    val cur = summary.targetCurrency
                    val isSurplus = summary.projectedNetCashFlowCents >= 0L
                    BudgetFlowCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Projected Cash Flow",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (isSurplus) "Surplus" else "Deficit",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isSurplus) EmeraldPrimary else StatusError
                            )
                        }
                        Text(
                            text = CurrencyConverter.format(
                                summary.projectedNetCashFlowCents,
                                cur,
                                showPlus = true
                            ),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isSurplus) EmeraldPrimary else StatusError
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Income: ${CurrencyConverter.format(summary.totalExpectedIncomeCents, cur)}",
                                style = MaterialTheme.typography.labelMedium
                            )
                            Text(
                                text = "Expenses: ${CurrencyConverter.format(summary.totalExpectedExpensesCents, cur)}",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                        Divider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Wallets Now: ${CurrencyConverter.format(summary.currentWalletTotalCents, cur)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Month-End: ${CurrencyConverter.format(summary.projectedMonthEndBalanceCents, cur)}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (summary.projectedMonthEndBalanceCents < 0L) StatusError else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        if (summary.shortfallCents > 0L) {
                            Text(
                                text = "Shortfall: ${CurrencyConverter.format(summary.shortfallCents, cur)}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = StatusError
                            )
                        }
                    }
                }
            }

            item {
                BudgetFlowCard {
                    Text("Forecast Settings", fontWeight = FontWeight.SemiBold)
                    Text("Projection Horizon", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1, 3, 6).forEach { months ->
                            FilterChip(
                                selected = state.scenario.horizonMonths == months,
                                onClick = { viewModel.setHorizonMonths(months) },
                                label = { Text("${months}M") }
                            )
                        }
                    }
                    Text("Display Currency", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    CurrencyChipRow(
                        selected = state.displayMode.targetCurrency,
                        onSelect = { viewModel.setDisplayCurrency(it) }
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val overrideCount = state.scenario.rateOverrides.size
                        Text(
                            text = if (overrideCount == 0) "What-If Exchange Rates: Using current" else "What-If Exchange Rates: $overrideCount overridden",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { showRateOverrideDialog = true }) {
                            Text("Edit Rates")
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Include logged income & expenses this month",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = state.scenario.includeLoggedThisMonth,
                            onCheckedChange = { viewModel.setIncludeLoggedThisMonth(it) }
                        )
                    }
                }
            }

            if (summary != null && summary.monthlyProjections.size > 1) {
                item {
                    val cur = summary.targetCurrency
                    BudgetFlowCard {
                        Text("${state.scenario.horizonMonths}-Month Projection", fontWeight = FontWeight.SemiBold)
                        summary.monthlyProjections.forEach { proj ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(proj.month.format(DateFormats.MONTH_YEAR), style = MaterialTheme.typography.bodyMedium)
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "Net ${CurrencyConverter.format(proj.netCashFlowCents, cur, showPlus = true)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (proj.netCashFlowCents >= 0L) EmeraldPrimary else StatusError
                                    )
                                    Text(
                                        text = "End ${CurrencyConverter.format(proj.projectedEndBalanceCents, cur)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
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
                        Text("Salary & Expected Income", fontWeight = FontWeight.SemiBold)
                        TextButton(onClick = { showAddIncomeDialog = true }) {
                            Text("+ Add Income")
                        }
                    }
                    OutlinedTextField(
                        value = state.scenario.salaryInput,
                        onValueChange = { viewModel.setSalaryInput(it) },
                        label = { Text("Expected Salary (${state.scenario.salaryCurrency.name})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    CurrencyChipRow(
                        selected = state.scenario.salaryCurrency,
                        onSelect = { viewModel.setSalaryCurrency(it) }
                    )
                    state.scenario.incomeItems.forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Checkbox(
                                    checked = item.isIncluded,
                                    onCheckedChange = { viewModel.toggleIncomeItem(item.id) }
                                )
                                Column {
                                    Text(item.label, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        text = CurrencyConverter.format(item.amountCents, item.currency),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = EmeraldPrimary
                                    )
                                }
                            }
                            TextButton(onClick = { viewModel.removeIncomeItem(item.id) }) {
                                Text("Remove", color = StatusError)
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
                        Text("Expected Expenses", fontWeight = FontWeight.SemiBold)
                        TextButton(onClick = { showAddExpenseDialog = true }) {
                            Text("+ Add Expense")
                        }
                    }
                    if (state.scenario.expenseItems.isEmpty()) {
                        Text(
                            text = "No unpaid bills, shopping lists, or one-offs yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        state.scenario.expenseItems.forEach { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Checkbox(
                                        checked = item.isIncluded,
                                        onCheckedChange = { viewModel.toggleExpenseItem(item.id) }
                                    )
                                    Column {
                                        Text(item.label, style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            text = "${item.sourceType.displayName} • ${CurrencyConverter.format(item.amountCents, item.currency)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                if (item.sourceType == ForecastExpenseSource.ONE_OFF) {
                                    TextButton(onClick = { viewModel.removeExpenseItem(item.id) }) {
                                        Text("Remove", color = StatusError)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (state.scenario.budgetAdjustments.isNotEmpty()) {
                val atRiskIds = summary?.atRiskCategories?.map { it.categoryId }?.toSet().orEmpty()
                item {
                    Text(
                        text = "What-If Category Budgets",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                items(state.scenario.budgetAdjustments, key = { it.categoryId }) { adj ->
                    val isAtRisk = adj.categoryId in atRiskIds
                    BudgetFlowCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(adj.categoryName, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = "Spent: ${CurrencyConverter.format(adj.spentSoFarCents, adj.currency)} / Limit: ${CurrencyConverter.format(adj.simulatedLimitCents, adj.currency)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isAtRisk) StatusError else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (isAtRisk) {
                                Text(
                                    text = "At Risk",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = StatusError
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.adjustBudgetLimit(adj.categoryId, -2500L) },
                                modifier = Modifier.weight(1f)
                            ) { Text("-25") }
                            OutlinedButton(
                                onClick = { editingBudget = adj },
                                modifier = Modifier.weight(1f)
                            ) { Text("Set") }
                            OutlinedButton(
                                onClick = { viewModel.adjustBudgetLimit(adj.categoryId, 2500L) },
                                modifier = Modifier.weight(1f)
                            ) { Text("+25") }
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.resetToBaseline() },
                        modifier = Modifier.weight(1f)
                    ) { Text("Reset") }
                    Button(
                        onClick = { showApplyConfirmDialog = true },
                        enabled = state.hasModifiedBudgets,
                        modifier = Modifier.weight(1f)
                    ) { Text("Apply Budgets") }
                }
            }
        }
    }

    if (showAddIncomeDialog) {
        var label by remember { mutableStateOf("") }
        var amount by remember { mutableStateOf("") }
        var currency by remember { mutableStateOf(state.displayMode.targetCurrency) }
        AlertDialog(
            onDismissRequest = { showAddIncomeDialog = false },
            title = { Text("Add Expected Income") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it },
                        label = { Text("Description (e.g. Bonus, Freelance)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it },
                        label = { Text("Amount") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    CurrencyChipRow(selected = currency, onSelect = { currency = it })
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.addIncomeItem(label, amount, currency)
                    showAddIncomeDialog = false
                }) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showAddIncomeDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showAddExpenseDialog) {
        var label by remember { mutableStateOf("") }
        var amount by remember { mutableStateOf("") }
        var currency by remember { mutableStateOf(state.displayMode.targetCurrency) }
        var categoryId by remember { mutableStateOf<Long?>(null) }
        AlertDialog(
            onDismissRequest = { showAddExpenseDialog = false },
            title = { Text("Add Expected Expense") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it },
                        label = { Text("Description (e.g. Car Service)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it },
                        label = { Text("Amount") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    CurrencyChipRow(selected = currency, onSelect = { currency = it })
                    if (state.categories.isNotEmpty()) {
                        CategoryHierarchySelector(
                            categories = state.categories,
                            selectedCategoryId = categoryId,
                            onSelect = { categoryId = it }
                        )
                        if (categoryId != null) {
                            TextButton(onClick = { categoryId = null }) {
                                Text("Clear Category")
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.addExpenseItem(label, amount, currency, categoryId)
                    showAddExpenseDialog = false
                }) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showAddExpenseDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showApplyConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showApplyConfirmDialog = false },
            title = { Text("Apply Budget Changes?") },
            text = {
                Text("This will update your saved category budget limits to match your forecast. Bills, transactions, and wallet balances will not be modified.")
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.applyBudgetChanges()
                    showApplyConfirmDialog = false
                }) { Text("Apply") }
            },
            dismissButton = {
                TextButton(onClick = { showApplyConfirmDialog = false }) { Text("Cancel") }
            }
        )
    }

    editingBudget?.let { adj ->
        var limitInput by remember(adj.categoryId) {
            mutableStateOf(CurrencyConverter.formatInput(adj.simulatedLimitCents))
        }
        AlertDialog(
            onDismissRequest = { editingBudget = null },
            title = { Text("Set ${adj.categoryName} Limit (${adj.currency.name})") },
            text = {
                OutlinedTextField(
                    value = limitInput,
                    onValueChange = { limitInput = it },
                    label = { Text("Monthly Limit") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.setBudgetLimit(adj.categoryId, limitInput)
                    editingBudget = null
                }) { Text("Set") }
            },
            dismissButton = {
                TextButton(onClick = { editingBudget = null }) { Text("Cancel") }
            }
        )
    }

    if (showRateOverrideDialog) {
        val nonUsd = remember { CurrencyCode.entries.filter { it != CurrencyCode.USD } }
        var selectedCur by remember {
            mutableStateOf(
                state.displayMode.targetCurrency.takeIf { it != CurrencyCode.USD } ?: nonUsd.first()
            )
        }
        var rateInput by remember(selectedCur, state.effectiveRates) {
            mutableStateOf(state.effectiveRates.rateFor(selectedCur).toPlainString())
        }
        AlertDialog(
            onDismissRequest = { showRateOverrideDialog = false },
            title = { Text("What-If Exchange Rate") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        nonUsd.forEach { cur ->
                            FilterChip(
                                selected = selectedCur == cur,
                                onClick = { selectedCur = cur },
                                label = { Text(cur.name) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = rateInput,
                        onValueChange = { rateInput = it },
                        label = { Text("1 USD = ? ${selectedCur.name}") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (selectedCur in state.scenario.rateOverrides) {
                        TextButton(onClick = {
                            viewModel.clearRateOverride(selectedCur)
                            showRateOverrideDialog = false
                        }) { Text("Reset ${selectedCur.name} Rate") }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.setRateOverride(selectedCur, rateInput)
                    showRateOverrideDialog = false
                }) { Text("Apply Rate") }
            },
            dismissButton = {
                TextButton(onClick = { showRateOverrideDialog = false }) { Text("Cancel") }
            }
        )
    }
}
