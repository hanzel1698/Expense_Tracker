package com.example.expensetracker.ui.modern.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.expensetracker.model.Expense
import com.example.expensetracker.ui.modern.components.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Aurora expense list — feature parity with the brutalist ExpenseListScreen:
 * search, date filter (presets/exact/range), attribute filters, by-store /
 * by-item views, filtered totals, group & single detail views, edit/delete.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModernExpenseListScreen(
    expenses: List<Expense>,
    viewingDate: LocalDate? = null,
    initialStartDate: LocalDate? = null,
    initialEndDate: LocalDate? = null,
    initialCategories: Set<String> = emptySet(),
    initialSubcategories: Set<String> = emptySet(),
    initialLabels: Set<String> = emptySet(),
    initialSelectedExpenseId: String? = null,
    initialSelectedGroupId: String? = null,
    showOnlyDrafts: Boolean = false,
    onClearFilter: () -> Unit = {},
    onAddExpense: () -> Unit = {},
    onEditExpense: (Expense) -> Unit = {},
    onDeleteExpense: (Expense) -> Unit = {}
) {
    var viewByStore by remember { mutableStateOf(true) }
    var selectedGroup by remember { mutableStateOf<List<Expense>?>(null) }
    var selectedExpense by remember { mutableStateOf<Expense?>(null) }
    var expenseToDelete by remember { mutableStateOf<Expense?>(null) }

    var searchQuery by remember { mutableStateOf("") }
    var filterCategories by remember { mutableStateOf(initialCategories) }
    var filterSubcategories by remember { mutableStateOf(initialSubcategories) }

    var exactDateFilter by remember { mutableStateOf<LocalDate?>(null) }
    var startDateFilter by remember { mutableStateOf<LocalDate?>(null) }
    var endDateFilter by remember { mutableStateOf<LocalDate?>(null) }

    var filterLabels by remember { mutableStateOf(initialLabels) }
    var filterPaymentModes by remember { mutableStateOf(emptySet<String>()) }
    var filterPaidVia by remember { mutableStateOf(emptySet<String>()) }
    var filtersExpanded by remember { mutableStateOf(false) }

    // Sync filters when external navigation changes the initial state
    LaunchedEffect(
        viewingDate, initialStartDate, initialEndDate, initialCategories,
        initialSubcategories, initialLabels, initialSelectedExpenseId, initialSelectedGroupId
    ) {
        filterCategories = initialCategories
        filterSubcategories = initialSubcategories
        filterLabels = initialLabels
        filterPaymentModes = emptySet()
        filterPaidVia = emptySet()
        if (initialStartDate != null) {
            startDateFilter = initialStartDate
            exactDateFilter = null
        }
        if (initialEndDate != null) {
            endDateFilter = initialEndDate
        }

        if (initialSelectedExpenseId != null) {
            val found = expenses.find { it.id == initialSelectedExpenseId }
            if (found != null) {
                startDateFilter = null
                endDateFilter = null
                exactDateFilter = null
                filterCategories = emptySet()
                filterSubcategories = emptySet()
                filterLabels = emptySet()
                filterPaymentModes = emptySet()
                filterPaidVia = emptySet()
                viewByStore = false
                selectedExpense = found
            }
        } else {
            selectedExpense = null
        }

        if (initialSelectedGroupId != null) {
            val group = expenses.filter { it.groupId == initialSelectedGroupId }
            if (group.isNotEmpty()) {
                startDateFilter = null
                endDateFilter = null
                exactDateFilter = null
                filterCategories = emptySet()
                filterSubcategories = emptySet()
                filterLabels = emptySet()
                filterPaymentModes = emptySet()
                filterPaidVia = emptySet()
                viewByStore = true
                selectedGroup = group
            }
        } else {
            selectedGroup = null
        }
    }

    var showExactDatePicker by remember { mutableStateOf(false) }
    var showRangeDatePicker by remember { mutableStateOf(false) }
    var showDateFilterDialog by remember { mutableStateOf(false) }

    val dateFormatter = java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy", java.util.Locale.getDefault())
    val context = androidx.compose.ui.platform.LocalContext.current
    val timeFormatter = remember {
        java.time.format.DateTimeFormatter.ofPattern(
            if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm" else "hh:mm a"
        )
    }
    // Date, plus the optional time when one was recorded.
    fun dateTimeText(expense: Expense): String =
        expense.date.format(dateFormatter) + (expense.time?.let { " · ${it.format(timeFormatter)}" } ?: "")
    // Newest first; within a day, timed entries by time and untimed ones after them.
    val newestFirst = compareByDescending<Expense> { it.date }
        .thenByDescending(nullsFirst<java.time.LocalTime>()) { it.time }

    val filteredExpenses = expenses.filter { expense ->
        val matchesDate = when {
            viewingDate != null -> expense.date == viewingDate
            exactDateFilter != null -> expense.date == exactDateFilter
            startDateFilter != null && endDateFilter != null ->
                !expense.date.isBefore(startDateFilter) && !expense.date.isAfter(endDateFilter)
            else -> true
        }
        if (!matchesDate) return@filter false

        if (filterCategories.isNotEmpty() && !filterCategories.contains(expense.category)) return@filter false
        if (filterSubcategories.isNotEmpty() && !filterSubcategories.contains(expense.subcategory)) return@filter false
        if (filterLabels.isNotEmpty() && expense.labels.none { filterLabels.contains(it) }) return@filter false
        if (filterPaymentModes.isNotEmpty() && !filterPaymentModes.contains(expense.paymentMode)) return@filter false
        if (filterPaidVia.isNotEmpty() && !filterPaidVia.contains(expense.paidVia)) return@filter false

        val q = searchQuery.lowercase()
        if (q.isNotBlank()) {
            val matchesSearch = expense.storeName.lowercase().contains(q) ||
                expense.itemDescription.lowercase().contains(q) ||
                expense.category.lowercase().contains(q) ||
                expense.subcategory.lowercase().contains(q) ||
                expense.amount.toString().contains(q) ||
                expense.labels.any { it.lowercase().contains(q) } ||
                expense.notes.lowercase().contains(q)
            if (!matchesSearch) return@filter false
        }
        true
    }

    val displayExpenses = filteredExpenses.filter { expense ->
        if (filterLabels.isNotEmpty() && filterLabels.intersect(expense.labels.toSet()).isEmpty()) return@filter false
        true
    }.sortedWith(newestFirst)

    val groupedExpenses = displayExpenses
        .groupBy { it.groupId }
        .values
        .sortedWith(compareBy(newestFirst) { it.first() })

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        if (selectedGroup != null || selectedExpense != null) {
            // ── Detail view ──────────────────────────────────────────────────
            Column(modifier = Modifier.fillMaxSize()) {
                FilledTonalButton(
                    onClick = {
                        selectedGroup = null
                        selectedExpense = null
                    },
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Back to list")
                }

                if (selectedGroup != null) {
                    val group = selectedGroup!!
                    AuroraCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(16.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        dateTimeText(group.first()),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        group.first().storeName,
                                        style = MaterialTheme.typography.headlineSmall,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                val total = group.sumOf { it.amount }
                                AuroraStat(
                                    label = "Total",
                                    value = "₹${String.format("%.0f", total)}",
                                    valueColor = MaterialTheme.colorScheme.primary,
                                    alignEnd = true
                                )
                            }

                            if (group.first().location.isNotBlank()) {
                                AuroraChip(
                                    text = group.first().location,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }

                            if (group.first().paymentMode.isNotBlank() || group.first().paidVia.isNotBlank()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                                ) {
                                    if (group.first().paymentMode.isNotBlank()) {
                                        Column {
                                            Text(
                                                "Payment",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(group.first().paymentMode, style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                    if (group.first().paidVia.isNotBlank()) {
                                        Column {
                                            Text(
                                                "Paid via",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(group.first().paidVia, style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Text(
                                "Items (${group.size})",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )

                            group.forEachIndexed { i, subTx ->
                                AuroraCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 8.dp),
                                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            val desc = if (subTx.itemDescription.isNotEmpty()) subTx.itemDescription
                                            else subTx.category
                                            Text(
                                                "${i + 1}.  $desc",
                                                style = MaterialTheme.typography.titleSmall,
                                                modifier = Modifier.weight(1f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                "₹${String.format("%.2f", subTx.amount)}",
                                                style = MaterialTheme.typography.titleSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 2.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                "${subTx.category} / ${subTx.subcategory}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            if (subTx.quantity != null && subTx.quantity > 0) {
                                                val quantityText = if (subTx.unit != null)
                                                    "${subTx.quantity} ${subTx.unit}" else subTx.quantity.toString()
                                                val unitRate = subTx.amount / subTx.quantity
                                                val rateText = if (subTx.unit != null)
                                                    " • ₹${String.format("%.2f", unitRate)}/${subTx.unit}" else ""
                                                Text(
                                                    quantityText + rateText,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        if (subTx.labels.isNotEmpty()) {
                                            Row(
                                                modifier = Modifier.padding(top = 6.dp),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                subTx.labels.take(3).forEach { AuroraChip(text = it) }
                                                if (subTx.labels.size > 3) {
                                                    AuroraChip(text = "+${subTx.labels.size - 3}")
                                                }
                                            }
                                        }

                                        if (subTx.notes.isNotBlank()) {
                                            Text(
                                                subTx.notes,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(top = 4.dp),
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 8.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            FilledTonalButton(
                                                onClick = { onEditExpense(subTx) },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(50),
                                                contentPadding = PaddingValues(vertical = 4.dp)
                                            ) {
                                                Text("Edit", style = MaterialTheme.typography.labelMedium)
                                            }
                                            OutlinedButton(
                                                onClick = { expenseToDelete = subTx },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(50),
                                                contentPadding = PaddingValues(vertical = 4.dp),
                                                colors = ButtonDefaults.outlinedButtonColors(
                                                    contentColor = MaterialTheme.colorScheme.error
                                                )
                                            ) {
                                                Text("Delete", style = MaterialTheme.typography.labelMedium)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else if (selectedExpense != null) {
                    val expense = selectedExpense!!
                    AuroraCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(16.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            val title = if (expense.itemDescription.isNotEmpty()) expense.itemDescription
                            else expense.storeName
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Item",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        title.ifEmpty { "No description" },
                                        style = MaterialTheme.typography.titleLarge,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                AuroraStat(
                                    label = "Amount",
                                    value = "₹${String.format("%.2f", expense.amount)}",
                                    valueColor = MaterialTheme.colorScheme.primary,
                                    alignEnd = true
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(24.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Store",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(expense.storeName, style = MaterialTheme.typography.bodyMedium)
                                }
                                Column {
                                    Text(
                                        "Date",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(dateTimeText(expense), style = MaterialTheme.typography.bodyMedium)
                                }
                            }

                            if (expense.location.isNotBlank()) {
                                AuroraChip(text = expense.location, modifier = Modifier.padding(bottom = 8.dp))
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(24.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Category",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        "${expense.category} / ${expense.subcategory}",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                if (expense.quantity != null && expense.quantity > 0) {
                                    Column {
                                        Text(
                                            "Qty",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        val quantityText = if (expense.unit != null)
                                            "${expense.quantity} ${expense.unit}" else expense.quantity.toString()
                                        Text(quantityText, style = MaterialTheme.typography.bodyMedium)
                                    }
                                    Column {
                                        Text(
                                            "Rate",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        val unitRate = expense.amount / expense.quantity
                                        val rateText = if (expense.unit != null)
                                            "₹${String.format("%.2f", unitRate)}/${expense.unit}"
                                        else "₹${String.format("%.2f", unitRate)}/unit"
                                        Text(rateText, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }

                            if (expense.paymentMode.isNotBlank() || expense.paidVia.isNotBlank()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                                ) {
                                    if (expense.paymentMode.isNotBlank()) {
                                        Column {
                                            Text(
                                                "Payment",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(expense.paymentMode, style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                    if (expense.paidVia.isNotBlank()) {
                                        Column {
                                            Text(
                                                "Paid via",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(expense.paidVia, style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                }
                            }

                            if (expense.labels.isNotEmpty()) {
                                Text(
                                    "Labels",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    expense.labels.take(4).forEach { AuroraChip(text = it) }
                                    if (expense.labels.size > 4) {
                                        AuroraChip(text = "+${expense.labels.size - 4}")
                                    }
                                }
                            }

                            if (expense.notes.isNotBlank()) {
                                Text(
                                    "Notes",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    expense.notes,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilledTonalButton(
                                    onClick = { onEditExpense(expense) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(50)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Edit")
                                }
                                OutlinedButton(
                                    onClick = { expenseToDelete = expense },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(50),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Delete")
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // ── List view ────────────────────────────────────────────────────
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    val titleText = when {
                        showOnlyDrafts -> "Drafts"
                        viewingDate != null -> "Expenses · ${viewingDate.format(dateFormatter)}"
                        initialSubcategories.size == 1 -> "Expenses · ${initialSubcategories.first()}"
                        initialSubcategories.size > 1 -> "Expenses · multiple"
                        initialCategories.size == 1 -> "Expenses · ${initialCategories.first()}"
                        initialCategories.size > 1 -> "Expenses · multiple"
                        else -> "Expenses"
                    }
                    Text(
                        text = titleText,
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(bottom = 12.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (viewingDate != null || initialCategories.isNotEmpty() ||
                            initialSubcategories.isNotEmpty() || initialLabels.isNotEmpty()
                        ) {
                            AssistChip(
                                onClick = onClearFilter,
                                label = { Text("Clear") },
                                shape = RoundedCornerShape(50)
                            )
                        }
                        AuroraSearchField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = "Search…",
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        )
                        FilledIconButton(onClick = onAddExpense) {
                            Icon(Icons.Default.Add, contentDescription = "Add expense")
                        }
                    }
                }

                item {
                    val dateFilterActive = exactDateFilter != null || startDateFilter != null
                    val dateFilterText = when {
                        exactDateFilter != null -> exactDateFilter!!.format(dateFormatter)
                        startDateFilter != null && endDateFilter != null ->
                            "${startDateFilter!!.format(dateFormatter)} – ${endDateFilter!!.format(dateFormatter)}"
                        else -> "Date filter"
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = dateFilterActive,
                            onClick = { showDateFilterDialog = true },
                            label = { Text(dateFilterText, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.DateRange,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        FilterChip(
                            selected = filtersExpanded,
                            onClick = { filtersExpanded = !filtersExpanded },
                            label = { Text(if (filtersExpanded) "Hide filters" else "Filters") },
                            shape = RoundedCornerShape(50)
                        )
                    }
                }

                if (filtersExpanded) {
                    item {
                        val categoriesInData = expenses.map { it.category }.distinct().sorted()
                        val subcats = expenses.map { it.subcategory }.distinct().sorted()
                        val availableLabels = expenses.flatMap { it.labels }.distinct().sorted()
                        val paymentModesInData =
                            expenses.map { it.paymentMode }.filter { it.isNotBlank() }.distinct().sorted()
                        val paidViasInData =
                            expenses.map { it.paidVia }.filter { it.isNotBlank() }.distinct().sorted()

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AuroraMultiSelectDropdown(
                                label = "Categories",
                                options = categoriesInData,
                                selectedOptions = filterCategories,
                                onOptionToggled = {
                                    filterCategories =
                                        if (filterCategories.contains(it)) filterCategories - it
                                        else filterCategories + it
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                            AuroraMultiSelectDropdown(
                                label = "Subcategories",
                                options = subcats,
                                selectedOptions = filterSubcategories,
                                onOptionToggled = {
                                    filterSubcategories =
                                        if (filterSubcategories.contains(it)) filterSubcategories - it
                                        else filterSubcategories + it
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                            AuroraMultiSelectDropdown(
                                label = "Labels",
                                options = availableLabels,
                                selectedOptions = filterLabels,
                                onOptionToggled = {
                                    filterLabels =
                                        if (filterLabels.contains(it)) filterLabels - it else filterLabels + it
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                            AuroraMultiSelectDropdown(
                                label = "Payment modes",
                                options = paymentModesInData,
                                selectedOptions = filterPaymentModes,
                                onOptionToggled = {
                                    filterPaymentModes =
                                        if (filterPaymentModes.contains(it)) filterPaymentModes - it
                                        else filterPaymentModes + it
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                            AuroraMultiSelectDropdown(
                                label = "Paid via",
                                options = paidViasInData,
                                selectedOptions = filterPaidVia,
                                onOptionToggled = {
                                    filterPaidVia =
                                        if (filterPaidVia.contains(it)) filterPaidVia - it else filterPaidVia + it
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (filterCategories.isNotEmpty() || filterSubcategories.isNotEmpty() ||
                                filterLabels.isNotEmpty() || filterPaymentModes.isNotEmpty() ||
                                filterPaidVia.isNotEmpty() || searchQuery.isNotBlank()
                            ) {
                                TextButton(
                                    onClick = {
                                        filterCategories = emptySet()
                                        filterSubcategories = emptySet()
                                        filterLabels = emptySet()
                                        filterPaymentModes = emptySet()
                                        filterPaidVia = emptySet()
                                        searchQuery = ""
                                        onClearFilter()
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Clear all filters")
                                }
                            }
                        }
                    }
                }

                item {
                    // View mode toggle
                    val options = listOf("By store", "By item")
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        options.forEachIndexed { index, option ->
                            SegmentedButton(
                                selected = (index == 0) == viewByStore,
                                onClick = { viewByStore = index == 0 },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size)
                            ) {
                                Text(option)
                            }
                        }
                    }
                }

                item {
                    val filteredTotal = displayExpenses.sumOf { it.amount }
                    val filteredCount = if (viewByStore) groupedExpenses.size else displayExpenses.size
                    val itemLabel = if (viewByStore) "groups" else "items"

                    AuroraCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        containerColor = MaterialTheme.colorScheme.primary
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "$filteredCount $itemLabel",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                            )
                            Text(
                                "Total ₹${String.format("%.2f", filteredTotal)}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }

                if (viewByStore) {
                    items(groupedExpenses) { group ->
                        val groupTotal = group.sumOf { it.amount }
                        val storeName = group.first().storeName
                        AuroraCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedGroup = group }
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(end = 8.dp)
                                    ) {
                                        Text(
                                            storeName,
                                            style = MaterialTheme.typography.titleMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            dateTimeText(group.first()),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        "₹${String.format("%.0f", groupTotal)}",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                group.forEach { expense ->
                                    val title = if (expense.itemDescription.isNotEmpty()) expense.itemDescription
                                    else expense.storeName
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            "₹${String.format("%.0f", expense.amount)}",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    items(displayExpenses) { expense ->
                        val title = if (expense.itemDescription.isNotEmpty()) expense.itemDescription
                        else expense.storeName
                        AuroraCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedExpense = expense }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 8.dp)
                                ) {
                                    Text(
                                        title,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        dateTimeText(expense) +
                                            if (expense.storeName.isNotEmpty() && expense.itemDescription.isNotEmpty())
                                                "  ·  ${expense.storeName}" else "",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    "₹${String.format("%.0f", expense.amount)}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(8.dp)) }
            }
        }
    }

    // ── Date pickers ────────────────────────────────────────────────────────
    if (showExactDatePicker) {
        AuroraDatePickerDialog(
            onDismissRequest = { showExactDatePicker = false },
            onDateSelected = { millis ->
                if (millis != null) {
                    exactDateFilter = Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate()
                    startDateFilter = null
                    endDateFilter = null
                }
            }
        )
    }

    if (showRangeDatePicker) {
        AuroraDateRangePickerDialog(
            onDismissRequest = { showRangeDatePicker = false },
            onDateRangeSelected = { start, end ->
                if (start != null && end != null) {
                    startDateFilter = Instant.ofEpochMilli(start).atZone(ZoneId.of("UTC")).toLocalDate()
                    endDateFilter = Instant.ofEpochMilli(end).atZone(ZoneId.of("UTC")).toLocalDate()
                    exactDateFilter = null
                }
            }
        )
    }

    if (showDateFilterDialog) {
        Dialog(onDismissRequest = { showDateFilterDialog = false }) {
            AuroraCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
                shape = RoundedCornerShape(28.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Date filter", style = MaterialTheme.typography.headlineSmall)

                    Text(
                        "Presets",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    FilledTonalButton(
                        onClick = {
                            val now = LocalDate.now()
                            startDateFilter = now.minusMonths(6)
                            endDateFilter = now
                            exactDateFilter = null
                            showDateFilterDialog = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(50)
                    ) { Text("Last 6 months") }
                    FilledTonalButton(
                        onClick = {
                            val now = LocalDate.now()
                            startDateFilter = now.withDayOfYear(1)
                            endDateFilter = now
                            exactDateFilter = null
                            showDateFilterDialog = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(50)
                    ) { Text("This year") }
                    FilledTonalButton(
                        onClick = {
                            val now = LocalDate.now()
                            startDateFilter = now.minusYears(1)
                            endDateFilter = now
                            exactDateFilter = null
                            showDateFilterDialog = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(50)
                    ) { Text("Last 12 months") }

                    Text(
                        "Custom",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    OutlinedButton(
                        onClick = {
                            showDateFilterDialog = false
                            showExactDatePicker = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(50)
                    ) { Text("Exact date") }
                    OutlinedButton(
                        onClick = {
                            showDateFilterDialog = false
                            showRangeDatePicker = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(50)
                    ) { Text("Custom range") }

                    if (exactDateFilter != null || startDateFilter != null) {
                        TextButton(
                            onClick = {
                                exactDateFilter = null
                                startDateFilter = null
                                endDateFilter = null
                                showDateFilterDialog = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) { Text("Clear date filter") }
                    }
                }
            }
        }
    }

    // ── Delete confirmation ─────────────────────────────────────────────────
    if (expenseToDelete != null) {
        AuroraConfirmDialog(
            title = "Delete expense?",
            message = "Store: ${expenseToDelete!!.storeName}\n" +
                "Item: ${
                    if (expenseToDelete!!.itemDescription.isNotEmpty()) expenseToDelete!!.itemDescription
                    else expenseToDelete!!.category
                }\n" +
                "Amount: ₹${String.format("%.2f", expenseToDelete!!.amount)}\n\n" +
                "This cannot be undone.",
            confirmText = "Delete",
            destructive = true,
            onConfirm = {
                onDeleteExpense(expenseToDelete!!)
                expenseToDelete = null
                selectedGroup = null
                selectedExpense = null
            },
            onDismiss = { expenseToDelete = null }
        )
    }
}
