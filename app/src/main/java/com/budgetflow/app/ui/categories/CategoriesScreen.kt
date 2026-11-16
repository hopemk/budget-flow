package com.budgetflow.app.ui.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.budgetflow.app.domain.model.Category
import com.budgetflow.app.ui.components.BudgetFlowCard
import com.budgetflow.app.ui.components.EmptyStateCard
import com.budgetflow.app.ui.components.MessageSnackbarBox
import com.budgetflow.app.ui.theme.StatusError

@Composable
fun CategoriesScreen(viewModel: CategoriesViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Categories, 1 = Subcategories

    var showCategoryEditor by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<Category?>(null) }

    var showSubcategoryEditor by remember { mutableStateOf(false) }
    var editingSubcategory by remember { mutableStateOf<Category?>(null) }
    var defaultParentIdForSub by remember { mutableStateOf<Long?>(null) }

    var deletingTarget by remember { mutableStateOf<Category?>(null) }

    MessageSnackbarBox(messages = viewModel.messages) {
        Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Categories (${state.topLevelCategories.size})") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Subcategories (${state.activeSubcategories.size})") }
            )
        }

        if (selectedTab == 0) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Button(
                        onClick = {
                            editingCategory = null
                            showCategoryEditor = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("+ New Category")
                    }
                }

                if (state.topLevelCategories.isEmpty()) {
                    item {
                        EmptyStateCard(
                            title = "No categories yet",
                            subtitle = "Create a top-level category like Groceries or Transport."
                        )
                    }
                } else {
                    items(state.topLevelCategories, key = { it.id }) { category ->
                        val children = state.subcategoriesByParent[category.id].orEmpty()
                        BudgetFlowCard {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clip(CircleShape)
                                            .background(Color(category.colorHex))
                                    )
                                    Column {
                                        Text(
                                            text = category.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = if (children.isEmpty()) "No subcategories" else "${children.size} subcategories",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                OutlinedButton(
                                    onClick = {
                                        editingSubcategory = null
                                        defaultParentIdForSub = category.id
                                        showSubcategoryEditor = true
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text("+ Subcategory", style = MaterialTheme.typography.labelSmall)
                                }
                            }

                            if (children.isNotEmpty()) {
                                Divider(color = MaterialTheme.colorScheme.outlineVariant)
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    children.forEach { sub ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = 12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "↳ ${sub.name}",
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                TextButton(
                                                    onClick = {
                                                        editingSubcategory = sub
                                                        defaultParentIdForSub = sub.parentCategoryId
                                                        showSubcategoryEditor = true
                                                    },
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                                ) {
                                                    Text("Edit", style = MaterialTheme.typography.labelSmall)
                                                }
                                                TextButton(
                                                    onClick = { deletingTarget = sub },
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                                ) {
                                                    Text("Delete", style = MaterialTheme.typography.labelSmall, color = StatusError)
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Divider(color = MaterialTheme.colorScheme.outlineVariant)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = {
                                    editingCategory = category
                                    showCategoryEditor = true
                                }) {
                                    Text("Edit Category")
                                }
                                TextButton(onClick = { viewModel.archiveCategory(category.id) }) {
                                    Text("Archive")
                                }
                                TextButton(onClick = { deletingTarget = category }) {
                                    Text("Delete", color = StatusError)
                                }
                            }
                        }
                    }
                }

                if (state.archivedCategories.isNotEmpty()) {
                    item {
                        Text(
                            text = "Archived Categories",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    items(state.archivedCategories, key = { "arch_cat_${it.id}" }) { cat ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(cat.name, style = MaterialTheme.typography.bodyMedium)
                                Row {
                                    TextButton(onClick = { viewModel.restoreCategory(cat.id) }) {
                                        Text("Restore")
                                    }
                                    TextButton(onClick = { deletingTarget = cat }) {
                                        Text("Delete", color = StatusError)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Button(
                        onClick = {
                            editingSubcategory = null
                            defaultParentIdForSub = state.topLevelCategories.firstOrNull()?.id
                            showSubcategoryEditor = true
                        },
                        enabled = state.topLevelCategories.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("+ New Subcategory")
                    }
                }

                if (state.topLevelCategories.isEmpty()) {
                    item {
                        EmptyStateCard(
                            title = "Create a category first",
                            subtitle = "Subcategories belong to a top-level category. Switch to Categories to add one."
                        )
                    }
                } else if (state.activeSubcategories.isEmpty()) {
                    item {
                        EmptyStateCard(
                            title = "No subcategories yet",
                            subtitle = "Tap + New Subcategory to add items like Fuel under Transport or Tomatoes under Groceries."
                        )
                    }
                } else {
                    val parentsWithSubs = state.topLevelCategories.filter {
                        state.subcategoriesByParent[it.id].orEmpty().isNotEmpty()
                    }
                    items(parentsWithSubs, key = { "parent_group_${it.id}" }) { parent ->
                        val subs = state.subcategoriesByParent[parent.id].orEmpty()
                        BudgetFlowCard {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clip(CircleShape)
                                            .background(Color(parent.colorHex))
                                    )
                                    Text(
                                        text = parent.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                TextButton(
                                    onClick = {
                                        editingSubcategory = null
                                        defaultParentIdForSub = parent.id
                                        showSubcategoryEditor = true
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text("+ Add", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            Divider(color = MaterialTheme.colorScheme.outlineVariant)
                            subs.forEach { sub ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(sub.name, style = MaterialTheme.typography.bodyMedium)
                                    Row {
                                        TextButton(
                                            onClick = {
                                                editingSubcategory = sub
                                                defaultParentIdForSub = sub.parentCategoryId
                                                showSubcategoryEditor = true
                                            },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text("Edit", style = MaterialTheme.typography.labelSmall)
                                        }
                                        TextButton(
                                            onClick = { viewModel.archiveCategory(sub.id) },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text("Archive", style = MaterialTheme.typography.labelSmall)
                                        }
                                        TextButton(
                                            onClick = { deletingTarget = sub },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text("Delete", style = MaterialTheme.typography.labelSmall, color = StatusError)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (state.archivedSubcategories.isNotEmpty()) {
                    item {
                        Text(
                            text = "Archived Subcategories",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    items(state.archivedSubcategories, key = { "arch_sub_${it.id}" }) { sub ->
                        val parentName = sub.parentCategoryId?.let { state.categoryLookup[it]?.name }
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(sub.name, style = MaterialTheme.typography.bodyMedium)
                                    if (parentName != null) {
                                        Text(
                                            text = "Under $parentName",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Row {
                                    TextButton(onClick = { viewModel.restoreCategory(sub.id) }) {
                                        Text("Restore")
                                    }
                                    TextButton(onClick = { deletingTarget = sub }) {
                                        Text("Delete", color = StatusError)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    }

    if (showCategoryEditor) {
        CategoryEditorDialog(
            target = editingCategory,
            onDismiss = { showCategoryEditor = false },
            onSave = { name, colorHex ->
                viewModel.saveTopLevelCategory(editingCategory, name, colorHex)
                showCategoryEditor = false
            }
        )
    }

    if (showSubcategoryEditor && state.topLevelCategories.isNotEmpty()) {
        SubcategoryEditorDialog(
            target = editingSubcategory,
            topLevelCategories = state.topLevelCategories,
            categoryLookup = state.categoryLookup,
            defaultParentId = defaultParentIdForSub,
            onDismiss = { showSubcategoryEditor = false },
            onSave = { name, parentId, colorHex ->
                viewModel.saveSubcategory(editingSubcategory, name, parentId, colorHex)
                showSubcategoryEditor = false
            },
            onPromoteToTopLevel = { target ->
                viewModel.promoteToTopLevel(target)
                showSubcategoryEditor = false
            }
        )
    }

    deletingTarget?.let { target ->
        val isParentWithSubs = target.parentCategoryId == null &&
            state.subcategoriesByParent[target.id].orEmpty().isNotEmpty()
        DeleteCategoryDialog(
            target = target,
            hasSubcategories = isParentWithSubs,
            onDismiss = { deletingTarget = null },
            onConfirm = {
                viewModel.deleteCategory(target.id)
                deletingTarget = null
            }
        )
    }
}
