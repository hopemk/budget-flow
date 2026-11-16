package com.budgetflow.app.ui.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.budgetflow.app.domain.model.Category

val CATEGORY_COLORS = listOf(
    0xFF059669L,
    0xFF0284C7L,
    0xFF7C3AEDL,
    0xFFD97706L,
    0xFFDC2626L,
    0xFF0D9488L,
    0xFFDB2777L,
    0xFF4F46E5L
)

@Composable
fun CategoryEditorDialog(
    target: Category?,
    onDismiss: () -> Unit,
    onSave: (name: String, colorHex: Long) -> Unit
) {
    var name by remember(target) { mutableStateOf(target?.name ?: "") }
    var colorHex by remember(target) { mutableLongStateOf(target?.colorHex ?: CATEGORY_COLORS.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (target == null) "New Category" else "Edit Category") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Category Name (e.g. Transport)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Color", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CATEGORY_COLORS.forEach { hex ->
                        val selected = colorHex == hex
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(hex))
                                .then(
                                    if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                    else Modifier
                                )
                                .clickable { colorHex = hex }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, colorHex) },
                enabled = name.isNotBlank()
            ) {
                Text(if (target == null) "Create" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SubcategoryEditorDialog(
    target: Category?,
    topLevelCategories: List<Category>,
    categoryLookup: Map<Long, Category>,
    defaultParentId: Long?,
    onDismiss: () -> Unit,
    onSave: (name: String, parentId: Long, colorHex: Long) -> Unit,
    onPromoteToTopLevel: (Category) -> Unit
) {
    var name by remember(target) { mutableStateOf(target?.name ?: "") }
    var parentId by remember(target, defaultParentId) {
        mutableLongStateOf(
            target?.parentCategoryId
                ?: defaultParentId
                ?: topLevelCategories.first().id
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (target == null) "New Subcategory" else "Edit Subcategory") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Subcategory Name (e.g. Fuel)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Parent Category", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    topLevelCategories.forEach { parent ->
                        FilterChip(
                            selected = parentId == parent.id,
                            onClick = { parentId = parent.id },
                            label = { Text(parent.name) }
                        )
                    }
                }
                if (target != null) {
                    TextButton(onClick = { onPromoteToTopLevel(target) }) {
                        Text("Promote to Top-Level Category")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parentColor = categoryLookup[parentId]?.colorHex ?: CATEGORY_COLORS.first()
                    onSave(name, parentId, target?.colorHex ?: parentColor)
                },
                enabled = name.isNotBlank()
            ) {
                Text(if (target == null) "Create" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun DeleteCategoryDialog(
    target: Category,
    hasSubcategories: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete '${target.name}'?") },
        text = {
            Text(
                if (hasSubcategories) {
                    "This removes the category and any budget on it. Existing transactions stay intact, and its subcategories will become top-level categories."
                } else {
                    "This removes '${target.name}' and any budget on it. Existing transactions will remain in your history."
                }
            )
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
