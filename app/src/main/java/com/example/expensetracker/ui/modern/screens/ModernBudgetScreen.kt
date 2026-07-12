package com.example.expensetracker.ui.modern.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.expensetracker.model.Expense
import com.example.expensetracker.ui.modern.components.*
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

// ── Budget edit dialog ────────────────────────────────────────────────────────

@Composable
fun ModernBudgetEditDialog(
    title: String,
    subtitle: String? = null,
    currentBudget: Double,
    onConfirm: (Double) -> Unit,
    onDismiss: () -> Unit
) {
    var budgetText by remember {
        mutableStateOf(if (currentBudget > 0) String.format("%.2f", currentBudget) else "")
    }

    Dialog(onDismissRequest = onDismiss) {
        AuroraCard(
            modifier = Modifier.fillMaxWidth(),
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
            shape = RoundedCornerShape(28.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.headlineSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (subtitle != null) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                AuroraTextField(
                    value = budgetText,
                    onValueChange = { budgetText = it },
                    label = "Budget amount (₹)",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    "Quick set",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(50, 100, 200, 500).forEach { amount ->
                        SuggestionChip(
                            onClick = { budgetText = amount.toString() },
                            label = { Text("₹$amount") },
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    TextButton(
                        onClick = { onConfirm(0.0) },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Clear")
                    }
                    Button(
                        onClick = {
                            val budgetValue = budgetText.toDoubleOrNull() ?: 0.0
                            onConfirm(budgetValue)
                        },
                        shape = RoundedCornerShape(50)
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}

// ── Budget screen ─────────────────────────────────────────────────────────────

@Composable
fun ModernBudgetScreen(
    expenses: List<Expense>,
    categories: List<String>,
    subcategoriesMap: Map<String, List<String>>,
    overallBudget: Double,
    categoryBudgets: Map<String, Double>,
    onCategoryBudgetChanged: (String, Double) -> Unit,
    subcategoryBudgets: Map<String, Double>,
    onSubcategoryBudgetChanged: (String, Double) -> Unit
) {
    var currentMonth by remember { mutableStateOf(YearMonth.now()) }

    val monthExpenses = remember(expenses, currentMonth) {
        expenses.filter { YearMonth.from(it.date) == currentMonth }
    }
    val totalSpent = remember(monthExpenses) { monthExpenses.sumOf { it.amount } }

    val categorySpending = remember(monthExpenses) {
        monthExpenses.groupBy { it.category }.mapValues { (_, list) -> list.sumOf { it.amount } }
    }
    val subcategorySpending = remember(monthExpenses) {
        monthExpenses.groupBy { "${it.category}/${it.subcategory}" }
            .mapValues { (_, list) -> list.sumOf { it.amount } }
    }

    var editingCategoryBudget by remember { mutableStateOf<String?>(null) }
    var editingSubcategoryBudget by remember { mutableStateOf<Pair<String, String>?>(null) }
    var selectedCategory by remember { mutableStateOf<String?>(categories.firstOrNull()) }

    LaunchedEffect(categories) {
        if (selectedCategory == null || !categories.contains(selectedCategory)) {
            selectedCategory = categories.firstOrNull()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Budget", style = MaterialTheme.typography.headlineLarge)
            AuroraMonthSwitcher(
                monthLabel = currentMonth.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                yearLabel = currentMonth.year.toString(),
                onPrevious = { currentMonth = currentMonth.minusMonths(1) },
                onNext = { currentMonth = currentMonth.plusMonths(1) }
            )
        }

        // Total allocation card
        val isOverBudget = totalSpent > overallBudget && overallBudget > 0
        AuroraCard(
            modifier = Modifier.fillMaxWidth(),
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Total budget allocation",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        "₹${String.format("%.0f", overallBudget)}",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        "₹${String.format("%.0f", totalSpent)} spent",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (isOverBudget) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                AuroraProgressBar(
                    spent = totalSpent,
                    budget = overallBudget,
                    barColor = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            "Categories",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            categories.forEach { category ->
                val isSelected = category == selectedCategory
                val catBudget = categoryBudgets[category] ?: 0.0
                val catSpent = categorySpending[category] ?: 0.0

                AuroraCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedCategory = category },
                    containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
                    border = if (isSelected)
                        BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                    else null
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = category,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { editingCategoryBudget = category },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "Edit budget",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Text(
                                "₹${String.format("%.0f", catBudget)}",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                "₹${String.format("%.0f", catSpent)} spent",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (catBudget > 0 && catSpent > catBudget)
                                    MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        AuroraProgressBar(spent = catSpent, budget = catBudget, height = 6.dp)
                    }
                }
            }
        }

        // Selected category: allocation summary + subcategories
        selectedCategory?.let { category ->
            Spacer(modifier = Modifier.height(24.dp))

            val categoryTotalBudget = categoryBudgets[category] ?: 0.0
            val categoryAllocated = remember(category, categoryBudgets, subcategoryBudgets, subcategoriesMap) {
                val subs = subcategoriesMap[category] ?: emptyList()
                subs.sumOf { sub -> subcategoryBudgets["$category/$sub"] ?: 0.0 }
            }
            val categoryUnallocated = categoryTotalBudget - categoryAllocated
            val isOverAllocated = categoryUnallocated < 0

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AuroraCard(
                    modifier = Modifier.weight(1f),
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "$category allocated",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.75f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "₹${String.format("%.0f", categoryAllocated)}",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
                AuroraCard(
                    modifier = Modifier.weight(1f),
                    containerColor = if (isOverAllocated) MaterialTheme.colorScheme.errorContainer
                    else MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            if (isOverAllocated) "Over-allocated" else "$category unallocated",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isOverAllocated)
                                MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.75f)
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "₹${String.format("%.0f", kotlin.math.abs(categoryUnallocated))}",
                            style = MaterialTheme.typography.titleLarge,
                            color = if (isOverAllocated) MaterialTheme.colorScheme.onErrorContainer
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Text(
                "Subcategories",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            val subs = subcategoriesMap[category] ?: emptyList()
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                subs.forEach { sub ->
                    val key = "$category/$sub"
                    val subBudget = subcategoryBudgets[key] ?: 0.0
                    val subSpent = subcategorySpending[key] ?: 0.0

                    AuroraCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    sub.ifEmpty { "Uncategorized" },
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { editingSubcategoryBudget = category to sub },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = "Edit budget",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                Text(
                                    "₹${String.format("%.0f", subBudget)}",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    "₹${String.format("%.0f", subSpent)} spent",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (subBudget > 0 && subSpent > subBudget)
                                        MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            AuroraProgressBar(spent = subSpent, budget = subBudget, height = 6.dp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    // ── Dialogs ─────────────────────────────────────────────────────────────
    editingCategoryBudget?.let { category ->
        ModernBudgetEditDialog(
            title = "$category budget",
            currentBudget = categoryBudgets[category] ?: 0.0,
            onConfirm = { newBudget ->
                onCategoryBudgetChanged(category, newBudget)
                editingCategoryBudget = null
            },
            onDismiss = { editingCategoryBudget = null }
        )
    }

    editingSubcategoryBudget?.let { (category, subcategory) ->
        val subKey = "$category/$subcategory"
        ModernBudgetEditDialog(
            title = "$subcategory budget",
            subtitle = "in $category",
            currentBudget = subcategoryBudgets[subKey] ?: 0.0,
            onConfirm = { newBudget ->
                onSubcategoryBudgetChanged(subKey, newBudget)
                editingSubcategoryBudget = null
            },
            onDismiss = { editingSubcategoryBudget = null }
        )
    }
}
