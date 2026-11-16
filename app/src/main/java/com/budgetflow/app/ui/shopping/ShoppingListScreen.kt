package com.budgetflow.app.ui.shopping

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.budgetflow.app.domain.model.CurrencyCode
import com.budgetflow.app.domain.model.LineItem
import com.budgetflow.app.domain.model.ShoppingList
import com.budgetflow.app.domain.model.ShoppingListItem
import com.budgetflow.app.domain.usecase.CurrencyConverter
import com.budgetflow.app.ui.components.BudgetFlowCard
import com.budgetflow.app.ui.components.CurrencyChipRow
import com.budgetflow.app.ui.components.EmptyStateCard
import com.budgetflow.app.ui.components.MessageSnackbarBox

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShoppingListScreen(
    onCheckoutToSpend: (listName: String, categoryId: Long?, currency: CurrencyCode, items: List<LineItem>) -> Unit,
    viewModel: ShoppingListViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val activeList = state.activeList

    var showListDialog by remember { mutableStateOf(false) }
    var editingList by remember { mutableStateOf<ShoppingList?>(null) }
    var showAddItemDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<ShoppingListItem?>(null) }
    var confirmingShopItem by remember { mutableStateOf<ShoppingListItem?>(null) }
    var showCheckoutDialog by remember { mutableStateOf(false) }

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
                Text("Shopping Lists", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Button(onClick = {
                    editingList = null
                    showListDialog = true
                }) { Text("+ New List") }
            }
        }

        if (state.lists.isNotEmpty()) {
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.lists.forEach { list ->
                        FilterChip(
                            selected = activeList?.id == list.id,
                            onClick = { viewModel.selectList(list.id) },
                            label = { Text("${list.name} (${list.items.size})") }
                        )
                    }
                }
            }
        }

        if (activeList == null) {
            item {
                EmptyStateCard(
                    title = "No shopping lists yet",
                    subtitle = "Create a list for groceries, hardware, or bulk shopping — prices are optional until you're in the shop."
                )
            }
        } else {
            val checkedItems = activeList.items.filter { it.isChecked }
            val estimatedTotal = activeList.items.sumOf { it.totalCents }
            val basketTotal = checkedItems.sumOf { it.totalCents }
            val remainingTotal = (estimatedTotal - basketTotal).coerceAtLeast(0L)

            item {
                BudgetFlowCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(activeList.name, fontWeight = FontWeight.Bold)
                            val catName = activeList.categoryId?.let { id ->
                                state.categories.firstOrNull { it.id == id }?.name
                            }
                            Text(
                                text = listOfNotNull(activeList.currency.name, catName).joinToString(" • "),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row {
                            TextButton(onClick = {
                                editingList = activeList
                                showListDialog = true
                            }) { Text("Edit") }
                            TextButton(onClick = { viewModel.deleteList(activeList.id) }) {
                                Text("Delete", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Planned", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(CurrencyConverter.format(estimatedTotal, activeList.currency), fontWeight = FontWeight.SemiBold)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("In Basket", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                CurrencyConverter.format(basketTotal, activeList.currency),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Remaining", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(CurrencyConverter.format(remainingTotal, activeList.currency), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            editingItem = null
                            showAddItemDialog = true
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("+ Add Item") }

                    Button(
                        onClick = { showCheckoutDialog = true },
                        enabled = checkedItems.isNotEmpty(),
                        modifier = Modifier.weight(1f)
                    ) { Text("Checkout (${checkedItems.size})") }
                }
            }

            if (activeList.items.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = "List is empty",
                        subtitle = "Tap + Add Item to plan what to buy. Prices are optional."
                    )
                }
            } else {
                items(activeList.items, key = { "sli_${it.id}" }) { item ->
                    BudgetFlowCard(
                        onClick = {
                            editingItem = item
                            showAddItemDialog = true
                        },
                        cornerRadius = 14.dp,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.Top
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Checkbox(
                                    checked = item.isChecked,
                                    onCheckedChange = { checked ->
                                        if (checked) {
                                            confirmingShopItem = item
                                        } else {
                                            viewModel.uncheckItem(item.id)
                                        }
                                    }
                                )
                                Column {
                                    Text(
                                        text = if (item.quantity > 1) "${item.name} ×${item.quantity}" else item.name,
                                        fontWeight = FontWeight.Medium,
                                        textDecoration = if (item.isChecked) TextDecoration.LineThrough else TextDecoration.None
                                    )
                                    val priceLabel = when {
                                        item.actualUnitCents != null ->
                                            "Confirmed: ${CurrencyConverter.format(item.totalCents, activeList.currency)}"
                                        item.estimatedUnitCents > 0L ->
                                            "Est: ${CurrencyConverter.format(item.totalCents, activeList.currency)}"
                                        else -> "No price set"
                                    }
                                    Text(
                                        text = priceLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (item.actualUnitCents != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            TextButton(onClick = { viewModel.deleteItem(item.id) }) {
                                Text("×", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
    }

    if (showListDialog) {
        val initial = editingList
        var name by remember(initial) { mutableStateOf(initial?.name ?: "") }
        var currency by remember(initial) { mutableStateOf(initial?.currency ?: CurrencyCode.USD) }
        var selectedCatId by remember(initial) { mutableStateOf(initial?.categoryId) }
        AlertDialog(
            onDismissRequest = { showListDialog = false },
            title = { Text(if (initial == null) "New Shopping List" else "Edit Shopping List") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("List Name (e.g. Groceries, Hardware)") },
                        singleLine = true
                    )
                    CurrencyChipRow(selected = currency, onSelect = { currency = it })
                    if (state.categories.isNotEmpty()) {
                        Text("Default Category (optional):", style = MaterialTheme.typography.labelSmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = selectedCatId == null,
                                onClick = { selectedCatId = null },
                                label = { Text("None") }
                            )
                            state.categories.forEach { cat ->
                                FilterChip(
                                    selected = selectedCatId == cat.id,
                                    onClick = { selectedCatId = cat.id },
                                    label = { Text(cat.name) }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.saveList(initial, name, currency, selectedCatId)
                    showListDialog = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showListDialog = false }) { Text("Cancel") } }
        )
    }

    if (showAddItemDialog && activeList != null) {
        val initial = editingItem
        var name by remember(initial) { mutableStateOf(initial?.name ?: "") }
        var qty by remember(initial) { mutableStateOf((initial?.quantity ?: 1).toString()) }
        var estPrice by remember(initial) {
            mutableStateOf(
                if (initial != null && initial.estimatedUnitCents > 0L) {
                    CurrencyConverter.formatInput(initial.estimatedUnitCents)
                } else ""
            )
        }
        AlertDialog(
            onDismissRequest = { showAddItemDialog = false },
            title = { Text(if (initial == null) "Add Item" else "Edit Item") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Item Name (e.g. Tomatoes)") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = qty,
                        onValueChange = { qty = it },
                        label = { Text("Quantity") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = estPrice,
                        onValueChange = { estPrice = it },
                        label = { Text("Estimated Unit Price (${activeList.currency.name}, optional)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.saveItem(activeList.id, initial, name, qty, estPrice)
                    showAddItemDialog = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showAddItemDialog = false }) { Text("Cancel") } }
        )
    }

    confirmingShopItem?.let { item ->
        val listCurrency = activeList?.currency ?: CurrencyCode.USD
        var qty by remember(item) { mutableStateOf(item.quantity.toString()) }
        var actualPrice by remember(item) {
            val prefill = item.actualUnitCents ?: item.estimatedUnitCents
            mutableStateOf(
                if (prefill > 0L) CurrencyConverter.formatInput(prefill) else ""
            )
        }
        AlertDialog(
            onDismissRequest = { confirmingShopItem = null },
            title = { Text("Confirm In-Shop Price") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(item.name, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = qty,
                        onValueChange = { qty = it },
                        label = { Text("Quantity") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = actualPrice,
                        onValueChange = { actualPrice = it },
                        label = { Text("Actual Unit Price (${listCurrency.name}, optional)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.confirmInShopItem(item, qty, actualPrice)
                    confirmingShopItem = null
                }) { Text("In Basket") }
            },
            dismissButton = {
                TextButton(onClick = { confirmingShopItem = null }) { Text("Cancel") }
            }
        )
    }

    if (showCheckoutDialog && activeList != null) {
        val checkedItems = activeList.items.filter { it.isChecked }
        val basketTotal = checkedItems.sumOf { it.totalCents }
        var reuseList by remember { mutableStateOf(true) }
        AlertDialog(
            onDismissRequest = { showCheckoutDialog = false },
            title = { Text("Checkout ${checkedItems.size} Item(s)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (basketTotal > 0L) {
                            "Basket Total: ${CurrencyConverter.format(basketTotal, activeList.currency)}"
                        } else {
                            "No item prices entered — you can enter the till total on the next screen."
                        },
                        fontWeight = FontWeight.SemiBold
                    )
                    checkedItems.take(6).forEach { item ->
                        val amt = if (item.totalCents > 0L) {
                            CurrencyConverter.format(item.totalCents, activeList.currency)
                        } else "—"
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${item.name}${if (item.quantity > 1) " ×${item.quantity}" else ""}")
                            Text(amt, fontWeight = FontWeight.Medium)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = reuseList,
                            onClick = { reuseList = true },
                            label = { Text("Keep & Uncheck") }
                        )
                        FilterChip(
                            selected = !reuseList,
                            onClick = { reuseList = false },
                            label = { Text("Clear Checked") }
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val items = viewModel.finishCheckout(activeList, reuseList)
                    showCheckoutDialog = false
                    onCheckoutToSpend(activeList.name, activeList.categoryId, activeList.currency, items)
                }) { Text("Log Expense") }
            },
            dismissButton = {
                TextButton(onClick = { showCheckoutDialog = false }) { Text("Cancel") }
            }
        )
    }
}
