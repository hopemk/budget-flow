package com.budgetflow.app.ui.budgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.budgetflow.app.domain.model.CategoryBudgetStatus
import com.budgetflow.app.domain.model.CurrencyCode
import com.budgetflow.app.domain.model.RolloverMode
import com.budgetflow.app.domain.usecase.CurrencyConverter
import com.budgetflow.app.ui.components.BudgetFlowCard
import com.budgetflow.app.ui.components.BudgetProgressCard
import com.budgetflow.app.ui.components.CategoryHierarchySelector
import com.budgetflow.app.ui.components.CurrencyChipRow
import com.budgetflow.app.ui.components.EmptyStateCard
import com.budgetflow.app.ui.components.MessageSnackbarBox
import com.budgetflow.app.ui.theme.StatusError
import com.budgetflow.app.ui.theme.StatusWarning

@Composable
fun BudgetsScreen(
    onNavigateToCategories: () -> Unit = {},
    onNavigateToForecast: () -> Unit = {},
    viewModel: BudgetsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showBudgetDialog by remember { mutableStateOf(false) }
    var editingStatus by remember { mutableStateOf<CategoryBudgetStatus?>(null) }

    MessageSnackbarBox(messages = viewModel.messages) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        editingStatus = null
                        showBudgetDialog = true
                    },
                    modifier = Modifier.weight(1.2f),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) { Text("+ Budget", maxLines = 1) }

                OutlinedButton(
                    onClick = onNavigateToCategories,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) { Text("Categories", maxLines = 1) }

                OutlinedButton(
                    onClick = onNavigateToForecast,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) { Text("Forecast", maxLines = 1) }
            }
        }

        if (state.statuses.isNotEmpty()) {
            state.summary?.let { summary ->
                item {
                    BudgetFlowCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Budget Overview", fontWeight = FontWeight.SemiBold)
                            Text(
                                text = "${summary.budgetUsedPct}% used • ${summary.monthProgressPct}% month",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Total: ${CurrencyConverter.format(summary.totalBudgetCents, state.displayCurrency)}",
                                style = MaterialTheme.typography.labelMedium
                            )
                            Text(
                                text = "Left: ${CurrencyConverter.format(summary.remainingCents, state.displayCurrency)}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (summary.remainingCents < 0L) StatusError else MaterialTheme.colorScheme.primary
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = "${state.onTrackCount} On Track",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "${state.warningCount} Warning",
                                style = MaterialTheme.typography.labelSmall,
                                color = StatusWarning
                            )
                            Text(
                                text = "${state.overCount} Over",
                                style = MaterialTheme.typography.labelSmall,
                                color = StatusError
                            )
                        }
                    }
                }
            }
        }

        if (state.statuses.isEmpty()) {
            item {
                EmptyStateCard(
                    title = "No category budgets",
                    subtitle = "Tap + Set Budget to assign a monthly limit and rollover rule."
                )
            }
        } else {
            items(state.statuses, key = { it.budget.id }) { status ->
                BudgetProgressCard(
                    status = status,
                    displayCurrency = state.displayCurrency,
                    onClick = {
                        editingStatus = status
                        showBudgetDialog = true
                    }
                )
            }
        }
    }
    }

    if (showBudgetDialog && state.categories.isNotEmpty()) {
        val initial = editingStatus
        var selectedCatId by remember(initial) {
            mutableStateOf(initial?.category?.id ?: state.categories.first().id)
        }
        var selectedCurrency by remember(initial) {
            mutableStateOf(initial?.budget?.currency ?: CurrencyCode.USD)
        }
        var limitText by remember(initial) {
            mutableStateOf(initial?.let { CurrencyConverter.formatInput(it.budget.limitCents) } ?: "200.00")
        }
        var rollover by remember(initial) {
            mutableStateOf(initial?.budget?.rolloverMode ?: RolloverMode.RESET_MONTHLY)
        }

        AlertDialog(
            onDismissRequest = { showBudgetDialog = false },
            title = { Text(if (initial == null) "Set Category Budget" else "Edit Budget") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CategoryHierarchySelector(
                        categories = state.categories,
                        selectedCategoryId = selectedCatId,
                        onSelect = { selectedCatId = it }
                    )
                    CurrencyChipRow(
                        selected = selectedCurrency,
                        onSelect = { selectedCurrency = it }
                    )
                    OutlinedTextField(
                        value = limitText,
                        onValueChange = { limitText = it },
                        label = { Text("Monthly Limit") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        RolloverMode.entries.forEach { mode ->
                            FilterChip(
                                selected = rollover == mode,
                                onClick = { rollover = mode },
                                label = { Text(mode.label) }
                            )
                        }
                    }
                    if (initial != null) {
                        TextButton(onClick = {
                            viewModel.deleteBudget(initial.budget.id)
                            showBudgetDialog = false
                        }) {
                            Text("Delete Budget", color = StatusError)
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.saveBudget(
                        existingBudgetId = initial?.budget?.id ?: 0L,
                        categoryId = selectedCatId,
                        currency = selectedCurrency,
                        limitInput = limitText,
                        rolloverMode = rollover,
                        existingCreatedAtMillis = initial?.budget?.createdAtMillis ?: 0L
                    )
                    showBudgetDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showBudgetDialog = false }) { Text("Cancel") }
            }
        )
    }
}
