package com.example.expensetracker.ui.modern.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.expensetracker.model.RecurrenceFrequency
import com.example.expensetracker.model.RecurringExpense
import com.example.expensetracker.ui.modern.components.*
import com.example.expensetracker.ui.modern.theme.LocalAuroraExtras
import java.time.LocalDate

/**
 * Aurora settings — feature parity with the brutalist SettingsScreen:
 * Google Drive sync, CSV import/export, categories /
 * subcategories / labels / payment modes / paid-via management, recurring
 * expenses, dev mode (long-press version), sample data, and clear-all.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModernSettingsScreen(
    categories: List<String>,
    onAddCategory: (String) -> Unit,
    onEditCategory: (Int, String) -> Unit,
    onDeleteCategory: (Int) -> Unit,
    subcategoriesMap: Map<String, List<String>>,
    onAddSubcategory: (String, String) -> Unit,
    onEditSubcategory: (String, Int, String) -> Unit,
    onDeleteSubcategory: (String, Int) -> Unit,
    labels: List<String>,
    onAddLabel: (String) -> Unit,
    onEditLabel: (Int, String) -> Unit,
    onDeleteLabel: (Int) -> Unit,
    paymentModes: List<String>,
    onAddPaymentMode: (String) -> Unit,
    onEditPaymentMode: (Int, String) -> Unit,
    onDeletePaymentMode: (Int) -> Unit,
    paidVia: List<String>,
    onAddPaidVia: (String) -> Unit,
    onEditPaidVia: (Int, String) -> Unit,
    onDeletePaidVia: (Int) -> Unit,
    recurringExpenses: List<RecurringExpense>,
    onAddRecurringExpense: (RecurringExpense) -> Unit,
    onEditRecurringExpense: (Int, RecurringExpense) -> Unit,
    onDeleteRecurringExpense: (Int) -> Unit,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onUploadBackup: () -> Unit,
    onViewBackups: () -> Unit,
    isSignedIn: Boolean,
    hasBackupFolder: Boolean,
    onChooseBackupFolder: () -> Unit,
    isDeveloperMode: Boolean = false,
    onToggleDevMode: () -> Unit = {},
    onDevModePressStart: () -> Unit = {},
    onDevModePressEnd: () -> Unit = {},
    onPopulateSampleData: () -> Unit = {},
    onClearAllData: () -> Unit = {},
    onExportTemplate: () -> Unit = {},
    onImportCsv: () -> Unit = {},
    onRestoreFromFile: () -> Unit = {}
) {
    var selectedCategory by remember { mutableStateOf(categories.firstOrNull()) }
    val validSelection =
        if (selectedCategory != null && categories.contains(selectedCategory)) selectedCategory
        else categories.firstOrNull()
    LaunchedEffect(validSelection) { selectedCategory = validSelection }

    val currentSubcategories = selectedCategory?.let { subcategoriesMap[it] } ?: emptyList()

    var showPopulateConfirm by remember { mutableStateOf(false) }
    var showSignInDialog by remember { mutableStateOf(false) }
    var showBackupsDialog by remember { mutableStateOf(false) }
    var showRestoreFileDialog by remember { mutableStateOf(false) }

    val extras = LocalAuroraExtras.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            "Settings",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // ── Google Drive sync ────────────────────────────────────────────────
        AuroraCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Google Drive sync", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (isSignedIn) "Connected" else "Not connected",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isSignedIn) extras.success else MaterialTheme.colorScheme.error
                        )
                    }
                    if (isSignedIn) {
                        OutlinedButton(
                            onClick = onSignOut,
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) { Text("Sign out") }
                    } else {
                        Button(
                            onClick = { showSignInDialog = true },
                            shape = RoundedCornerShape(50)
                        ) { Text("Sign in") }
                    }
                }

                if (isSignedIn) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (hasBackupFolder) "Backup folder selected" else "No backup folder selected",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (hasBackupFolder) extras.success else MaterialTheme.colorScheme.error
                        )
                        OutlinedButton(
                            onClick = onChooseBackupFolder,
                            shape = RoundedCornerShape(50)
                        ) { Text(if (hasBackupFolder) "Change folder" else "Choose folder…") }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Pick the folder in your Drive (or other storage) where backups are read from and written to — this lets the app use a folder you already have.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = onUploadBackup,
                            enabled = hasBackupFolder,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(50)
                        ) { Text("Upload") }
                        OutlinedButton(
                            onClick = { showBackupsDialog = true },
                            enabled = hasBackupFolder,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(50)
                        ) { Text("Download") }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "• Upload saves your data to the selected folder\n• Download retrieves backups from it",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Spacer(modifier = Modifier.height(12.dp))
                Text("Restore from a backup file", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Pick a backup JSON file from your device (e.g. one downloaded from Drive manually or shared to you) and merge it in — no sign-in required.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showRestoreFileDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(50)
                ) { Text("Restore from file…") }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── CSV import/export ────────────────────────────────────────────────
        AuroraCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Data import / export (CSV)", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onExportTemplate,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(50)
                    ) { Text("Export template") }
                    FilledTonalButton(
                        onClick = onImportCsv,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(50)
                    ) { Text("Import CSV") }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "• Export a blank template with sample entries\n• Import CSV to add multiple expenses and settings",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ── Taxonomy management ──────────────────────────────────────────────
        ModernManageableList(
            title = "Categories",
            items = categories,
            onAdd = onAddCategory,
            onEdit = onEditCategory,
            onDelete = onDeleteCategory,
            selectedIndex = categories.indexOf(selectedCategory),
            onItemSelected = { index -> selectedCategory = categories[index] }
        )

        Spacer(modifier = Modifier.height(16.dp))

        ModernManageableList(
            title = "Subcategories",
            items = currentSubcategories,
            onAdd = { name -> selectedCategory?.let { cat -> onAddSubcategory(cat, name) } },
            onEdit = { index, newValue -> selectedCategory?.let { cat -> onEditSubcategory(cat, index, newValue) } },
            onDelete = { index -> selectedCategory?.let { cat -> onDeleteSubcategory(cat, index) } },
            parentLabel = selectedCategory
        )

        Spacer(modifier = Modifier.height(16.dp))

        ModernManageableList(
            title = "Labels",
            items = labels,
            onAdd = onAddLabel,
            onEdit = onEditLabel,
            onDelete = onDeleteLabel
        )

        Spacer(modifier = Modifier.height(16.dp))

        ModernManageableList(
            title = "Payment modes",
            items = paymentModes,
            onAdd = onAddPaymentMode,
            onEdit = onEditPaymentMode,
            onDelete = onDeletePaymentMode
        )

        Spacer(modifier = Modifier.height(16.dp))

        ModernManageableList(
            title = "Paid via",
            items = paidVia,
            onAdd = onAddPaidVia,
            onEdit = onEditPaidVia,
            onDelete = onDeletePaidVia
        )

        Spacer(modifier = Modifier.height(16.dp))

        // ── Recurring expenses ───────────────────────────────────────────────
        ModernRecurringExpensesSection(
            recurringExpenses = recurringExpenses,
            categories = categories,
            subcategoriesMap = subcategoriesMap,
            labels = labels,
            paymentModes = paymentModes,
            paidVia = paidVia,
            onAdd = onAddRecurringExpense,
            onEdit = onEditRecurringExpense,
            onDelete = onDeleteRecurringExpense,
            onAddCategory = onAddCategory,
            onAddSubcategory = onAddSubcategory,
            onAddLabel = onAddLabel
        )

        Spacer(modifier = Modifier.height(20.dp))

        // ── Version (long-press for dev mode) ────────────────────────────────
        Text(
            "Version 1.0.0 · Aurora",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .pointerInput(Unit) {
                    // Hidden dev mode: hold the version label for 3+ seconds.
                    detectTapGestures(
                        onPress = {
                            onDevModePressStart()
                            tryAwaitRelease()
                            onToggleDevMode()
                            onDevModePressEnd()
                        }
                    )
                }
                .padding(8.dp)
        )

        if (isDeveloperMode) {
            Spacer(modifier = Modifier.height(8.dp))
            AuroraCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.colorScheme.errorContainer
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Dev mode active",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = { showPopulateConfirm = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(50)
                        ) { Text("Populate data", style = MaterialTheme.typography.labelMedium) }
                        Button(
                            onClick = onClearAllData,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            )
                        ) { Text("Clear all", style = MaterialTheme.typography.labelMedium) }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    // ── Dialogs ─────────────────────────────────────────────────────────────
    if (showPopulateConfirm) {
        AuroraConfirmDialog(
            title = "Populate sample data",
            message = "This will generate approximately 500 sample expenses spanning 18 months. Existing data will be replaced. Continue?",
            onConfirm = {
                onPopulateSampleData()
                showPopulateConfirm = false
            },
            onDismiss = { showPopulateConfirm = false }
        )
    }

    if (showSignInDialog) {
        AuroraConfirmDialog(
            title = "Sign in",
            message = "Sign in to enable backup and sync functionality for your expense data across devices.",
            confirmText = "Sign in",
            onConfirm = {
                showSignInDialog = false
                onSignIn()
            },
            onDismiss = { showSignInDialog = false }
        )
    }

    if (showBackupsDialog) {
        AuroraConfirmDialog(
            title = "Download backup",
            message = "This will restore the most recent backup from Google Drive.",
            confirmText = "Download",
            onConfirm = {
                showBackupsDialog = false
                onViewBackups()
            },
            onDismiss = { showBackupsDialog = false }
        )
    }

    if (showRestoreFileDialog) {
        AuroraConfirmDialog(
            title = "Restore from file",
            message = "Pick a backup JSON file to merge into your data. Existing categories, labels, and expenses are kept — only missing items are added.",
            confirmText = "Choose file",
            onConfirm = {
                showRestoreFileDialog = false
                onRestoreFromFile()
            },
            onDismiss = { showRestoreFileDialog = false }
        )
    }
}

// ── Manageable list (categories / labels / etc.) ─────────────────────────────

@Composable
fun ModernManageableList(
    title: String,
    items: List<String>,
    onAdd: (String) -> Unit,
    onEdit: (Int, String) -> Unit,
    onDelete: (Int) -> Unit,
    modifier: Modifier = Modifier,
    selectedIndex: Int = -1,
    onItemSelected: ((Int) -> Unit)? = null,
    parentLabel: String? = null
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var deleteConfirmIndex by remember { mutableStateOf<Int?>(null) }

    AuroraCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    if (parentLabel != null) {
                        Text(
                            "in $parentLabel",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                FilledTonalIconButton(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add", modifier = Modifier.size(20.dp))
                }
            }

            if (parentLabel != null && items.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No items",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            } else {
                Column(modifier = Modifier.padding(bottom = 8.dp)) {
                    items.forEachIndexed { index, item ->
                        val isSelected = index == selectedIndex
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (isSelected) Modifier.background(
                                        MaterialTheme.colorScheme.secondaryContainer
                                    ) else Modifier
                                )
                                .then(
                                    if (onItemSelected != null)
                                        Modifier.clickable { onItemSelected(index) }
                                    else Modifier
                                )
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = item,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer
                                else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            IconButton(
                                onClick = { editingIndex = index },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "Edit",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = { deleteConfirmIndex = index },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AuroraEditDialog(
            title = "Add ${title.lowercase()}",
            initialValue = "",
            onConfirm = { value ->
                if (value.isNotBlank()) onAdd(value.trim())
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }

    editingIndex?.let { index ->
        if (index in items.indices) {
            AuroraEditDialog(
                title = "Edit ${title.lowercase()}",
                initialValue = items[index],
                onConfirm = { value ->
                    if (value.isNotBlank()) onEdit(index, value.trim())
                    editingIndex = null
                },
                onDismiss = { editingIndex = null }
            )
        }
    }

    deleteConfirmIndex?.let { index ->
        if (index in items.indices) {
            AuroraConfirmDialog(
                title = "Delete",
                message = "Remove \"${items[index]}\"?",
                confirmText = "Delete",
                destructive = true,
                onConfirm = {
                    onDelete(index)
                    deleteConfirmIndex = null
                },
                onDismiss = { deleteConfirmIndex = null }
            )
        }
    }
}

// ── Recurring expenses section ────────────────────────────────────────────────

@Composable
fun ModernRecurringExpensesSection(
    recurringExpenses: List<RecurringExpense>,
    categories: List<String>,
    subcategoriesMap: Map<String, List<String>>,
    labels: List<String>,
    paymentModes: List<String>,
    paidVia: List<String>,
    onAdd: (RecurringExpense) -> Unit,
    onEdit: (Int, RecurringExpense) -> Unit,
    onDelete: (Int) -> Unit,
    onAddCategory: (String) -> Unit = {},
    onAddSubcategory: (String, String) -> Unit = { _, _ -> },
    onAddLabel: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var deleteConfirmIndex by remember { mutableStateOf<Int?>(null) }
    var detailIndex by remember { mutableStateOf<Int?>(null) }

    val extras = LocalAuroraExtras.current

    val activeRecurring = recurringExpenses.filter { it.isActive }
    val monthlySum = activeRecurring.filter { it.frequency == RecurrenceFrequency.MONTHLY }.sumOf { it.amount }
    val yearlySum = activeRecurring.filter { it.frequency == RecurrenceFrequency.YEARLY }.sumOf { it.amount }
    val otherSum = activeRecurring
        .filter { it.frequency == RecurrenceFrequency.DAILY || it.frequency == RecurrenceFrequency.WEEKLY }
        .sumOf { it.amount }

    AuroraCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Recurring expenses", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${recurringExpenses.count { it.isActive }} active · ${recurringExpenses.size} total",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        buildString {
                            append("Monthly ₹${String.format("%.2f", monthlySum)}")
                            append(" · Yearly ₹${String.format("%.2f", yearlySum)}")
                            if (otherSum > 0) append(" · Other ₹${String.format("%.2f", otherSum)}")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
                FilledTonalIconButton(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add recurring expense", modifier = Modifier.size(20.dp))
                }
            }

            if (recurringExpenses.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No recurring expenses",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            } else {
                Column(modifier = Modifier.padding(bottom = 8.dp)) {
                    recurringExpenses.forEachIndexed { index, re ->
                        val scheduleLabel = buildString {
                            when (re.frequency) {
                                RecurrenceFrequency.DAILY -> append("Every day")
                                RecurrenceFrequency.WEEKLY -> {
                                    val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                                    append("Every ${dayNames.getOrElse(re.dayOfPeriod - 1) { "?" }}")
                                }
                                RecurrenceFrequency.MONTHLY -> append("Day ${re.dayOfPeriod} monthly")
                                RecurrenceFrequency.YEARLY -> {
                                    val mVal = if (re.monthOfPeriod > 0) re.monthOfPeriod else re.startDate.monthValue
                                    val monthName = java.time.Month.of(mVal.coerceIn(1, 12)).name.take(3)
                                    append("Yearly on $monthName ${re.dayOfPeriod}")
                                }
                            }
                            val fmt = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yy")
                            append(" · from ${re.startDate.format(fmt)}")
                            if (re.endDate != null) append(" to ${re.endDate.format(fmt)}")
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { detailIndex = index }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        if (re.isActive) extras.success
                                        else MaterialTheme.colorScheme.outlineVariant,
                                        CircleShape
                                    )
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    re.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (re.isActive) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "₹${String.format("%.2f", re.amount)} · $scheduleLabel",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (re.category.isNotEmpty()) {
                                    Text(
                                        buildString {
                                            append(re.category)
                                            if (re.subcategory.isNotEmpty()) append(" › ${re.subcategory}")
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Switch(
                                checked = re.isActive,
                                onCheckedChange = { onEdit(index, re.copy(isActive = !re.isActive)) },
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                            IconButton(onClick = { editingId = re.id }, modifier = Modifier.size(32.dp)) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "Edit",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { deleteConfirmIndex = index }, modifier = Modifier.size(32.dp)) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                                )
                            }
                        }
                        if (index < recurringExpenses.lastIndex) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        ModernRecurringExpenseDialog(
            title = "Add recurring expense",
            initial = null,
            categories = categories,
            subcategoriesMap = subcategoriesMap,
            labels = labels,
            paymentModes = paymentModes,
            paidVia = paidVia,
            onAddCategory = onAddCategory,
            onAddSubcategory = onAddSubcategory,
            onAddLabel = onAddLabel,
            onConfirm = { re ->
                onAdd(re)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }

    editingId?.let { id ->
        val idx = recurringExpenses.indexOfFirst { it.id == id }
        if (idx in recurringExpenses.indices) {
            key(id) {
                ModernRecurringExpenseDialog(
                    title = "Edit recurring expense",
                    initial = recurringExpenses[idx],
                    categories = categories,
                    subcategoriesMap = subcategoriesMap,
                    labels = labels,
                    paymentModes = paymentModes,
                    paidVia = paidVia,
                    onAddCategory = onAddCategory,
                    onAddSubcategory = onAddSubcategory,
                    onAddLabel = onAddLabel,
                    onConfirm = { re ->
                        onEdit(idx, re)
                        editingId = null
                    },
                    onDismiss = { editingId = null }
                )
            }
        }
    }

    LaunchedEffect(editingId, recurringExpenses.size) {
        editingId?.let { id ->
            if (recurringExpenses.none { it.id == id }) editingId = null
        }
    }

    deleteConfirmIndex?.let { idx ->
        if (idx in recurringExpenses.indices) {
            AuroraConfirmDialog(
                title = "Delete recurring expense",
                message = "Remove \"${recurringExpenses[idx].name}\"? This will not delete any expenses already generated.",
                confirmText = "Delete",
                destructive = true,
                onConfirm = {
                    onDelete(idx)
                    deleteConfirmIndex = null
                },
                onDismiss = { deleteConfirmIndex = null }
            )
        }
    }

    detailIndex?.let { idx ->
        if (idx in recurringExpenses.indices) {
            val re = recurringExpenses[idx]
            val dateFormatter = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")
            val scheduleStr = buildString {
                when (re.frequency) {
                    RecurrenceFrequency.DAILY -> append("Every day")
                    RecurrenceFrequency.WEEKLY -> {
                        val dayNames = listOf(
                            "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"
                        )
                        append("Every ${dayNames.getOrElse(re.dayOfPeriod - 1) { "?" }}")
                    }
                    RecurrenceFrequency.MONTHLY -> append("Day ${re.dayOfPeriod} of each month")
                    RecurrenceFrequency.YEARLY -> {
                        val mVal = if (re.monthOfPeriod > 0) re.monthOfPeriod else re.startDate.monthValue
                        val monthName = java.time.Month.of(mVal.coerceIn(1, 12)).name.take(3)
                        append("Yearly on $monthName ${re.dayOfPeriod}")
                    }
                }
            }
            AlertDialog(
                onDismissRequest = { detailIndex = null },
                shape = RoundedCornerShape(28.dp),
                title = { Text("Recurring expense", style = MaterialTheme.typography.headlineSmall) },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("Name: ${re.name}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        if (re.storeName.isNotEmpty()) {
                            Text("Store: ${re.storeName}", style = MaterialTheme.typography.bodyMedium)
                        }
                        Text(
                            "Amount: ₹${String.format("%.2f", re.amount)}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (re.category.isNotEmpty()) {
                            Text(
                                "Category: ${re.category}${if (re.subcategory.isNotEmpty()) " › ${re.subcategory}" else ""}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        if (re.labels.isNotEmpty()) {
                            Text("Labels: ${re.labels.joinToString()}", style = MaterialTheme.typography.bodyMedium)
                        }
                        if (re.paymentMode.isNotEmpty()) {
                            Text("Payment mode: ${re.paymentMode}", style = MaterialTheme.typography.bodyMedium)
                        }
                        if (re.paidVia.isNotEmpty()) {
                            Text("Paid via: ${re.paidVia}", style = MaterialTheme.typography.bodyMedium)
                        }
                        Text("Frequency: ${re.frequency.name.lowercase().replaceFirstChar { it.uppercase() }}", style = MaterialTheme.typography.bodyMedium)
                        Text("Schedule: $scheduleStr", style = MaterialTheme.typography.bodyMedium)
                        Text("Start date: ${re.startDate.format(dateFormatter)}", style = MaterialTheme.typography.bodyMedium)
                        Text("End date: ${re.endDate?.format(dateFormatter) ?: "None"}", style = MaterialTheme.typography.bodyMedium)
                        if (re.notes.isNotEmpty()) {
                            Text("Notes: ${re.notes}", style = MaterialTheme.typography.bodyMedium)
                        }
                        Text(
                            "Status: ${if (re.isActive) "Active" else "Inactive"}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (re.isActive) extras.success
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (re.lastGeneratedDate != null) {
                            Text(
                                "Last generated: ${re.lastGeneratedDate.format(dateFormatter)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { detailIndex = null }) { Text("Close") }
                }
            )
        }
    }
}

// ── Recurring expense add/edit dialog ─────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModernRecurringExpenseDialog(
    title: String,
    initial: RecurringExpense?,
    categories: List<String>,
    subcategoriesMap: Map<String, List<String>>,
    labels: List<String>,
    paymentModes: List<String>,
    paidVia: List<String>,
    onAddCategory: (String) -> Unit = {},
    onAddSubcategory: (String, String) -> Unit = { _, _ -> },
    onAddLabel: (String) -> Unit = {},
    onConfirm: (RecurringExpense) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var storeName by remember { mutableStateOf(initial?.storeName ?: "") }
    var amountStr by remember {
        mutableStateOf(if (initial != null) String.format("%.2f", initial.amount) else "")
    }
    var selectedCategory by remember { mutableStateOf(initial?.category ?: categories.firstOrNull() ?: "") }
    var selectedSubcategory by remember { mutableStateOf(initial?.subcategory ?: "") }
    var itemDescription by remember { mutableStateOf(initial?.itemDescription ?: "") }
    var selectedPaymentMode by remember { mutableStateOf(initial?.paymentMode ?: "") }
    var selectedPaidVia by remember { mutableStateOf(initial?.paidVia ?: "") }
    var selectedFrequency by remember { mutableStateOf(initial?.frequency ?: RecurrenceFrequency.MONTHLY) }
    var dayOfPeriod by remember { mutableStateOf(initial?.dayOfPeriod ?: 1) }
    var monthOfPeriod by remember { mutableStateOf(initial?.monthOfPeriod ?: 0) }
    var startDate by remember { mutableStateOf(initial?.startDate ?: LocalDate.now()) }
    var endDate by remember { mutableStateOf<LocalDate?>(initial?.endDate) }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var isActive by remember { mutableStateOf(initial?.isActive ?: true) }
    var nameError by remember { mutableStateOf(false) }
    var amountError by remember { mutableStateOf(false) }
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    val dateFormatter = remember { java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy") }

    val selectedLabels = remember {
        mutableStateListOf<String>().apply { addAll(initial?.labels ?: emptyList()) }
    }

    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var showAddSubcategoryDialog by remember { mutableStateOf(false) }
    var showAddLabelDialog by remember { mutableStateOf(false) }

    val currentSubcategories = subcategoriesMap[selectedCategory] ?: emptyList()

    val frequencyOptions = listOf(
        RecurrenceFrequency.DAILY to "Daily",
        RecurrenceFrequency.WEEKLY to "Weekly",
        RecurrenceFrequency.MONTHLY to "Monthly",
        RecurrenceFrequency.YEARLY to "Yearly"
    )

    // Inline add dialogs
    if (showAddCategoryDialog) {
        AuroraEditDialog(
            title = "New category",
            initialValue = "",
            fieldLabel = "Category name",
            onConfirm = { value ->
                if (value.isNotBlank()) {
                    onAddCategory(value)
                    selectedCategory = value
                    selectedSubcategory = ""
                }
                showAddCategoryDialog = false
            },
            onDismiss = { showAddCategoryDialog = false }
        )
    }
    if (showAddSubcategoryDialog) {
        AuroraEditDialog(
            title = "New subcategory in $selectedCategory",
            initialValue = "",
            fieldLabel = "Subcategory name",
            onConfirm = { value ->
                if (value.isNotBlank() && selectedCategory.isNotBlank()) {
                    onAddSubcategory(selectedCategory, value)
                    selectedSubcategory = value
                }
                showAddSubcategoryDialog = false
            },
            onDismiss = { showAddSubcategoryDialog = false }
        )
    }
    if (showAddLabelDialog) {
        AuroraEditDialog(
            title = "New label",
            initialValue = "",
            fieldLabel = "Label name",
            onConfirm = { value ->
                if (value.isNotBlank()) {
                    onAddLabel(value)
                    selectedLabels.add(value)
                }
                showAddLabelDialog = false
            },
            onDismiss = { showAddLabelDialog = false }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        AuroraCard(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(vertical = 24.dp),
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
            shape = RoundedCornerShape(28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(title, style = MaterialTheme.typography.headlineSmall)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                AuroraTextField(
                    value = name,
                    onValueChange = { name = it; nameError = false },
                    label = "Name *",
                    isError = nameError,
                    supportingText = if (nameError) "Required" else null
                )
                Spacer(modifier = Modifier.height(10.dp))

                AuroraTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it; amountError = false },
                    label = "Amount (₹) *",
                    isError = amountError,
                    supportingText = if (amountError) "Enter a valid amount" else null
                )
                Spacer(modifier = Modifier.height(10.dp))

                AuroraTextField(
                    value = storeName,
                    onValueChange = { storeName = it },
                    label = "Store / merchant"
                )
                Spacer(modifier = Modifier.height(10.dp))

                AuroraTextField(
                    value = itemDescription,
                    onValueChange = { itemDescription = it },
                    label = "Item description"
                )
                Spacer(modifier = Modifier.height(10.dp))

                AuroraDropdown(
                    label = "Category",
                    options = categories + "+ Add New",
                    selectedOption = selectedCategory,
                    onOptionSelected = {
                        if (it == "+ Add New") {
                            showAddCategoryDialog = true
                        } else {
                            selectedCategory = it
                            selectedSubcategory = ""
                        }
                    }
                )
                Spacer(modifier = Modifier.height(10.dp))

                if (selectedCategory.isNotEmpty()) {
                    AuroraDropdown(
                        label = "Subcategory",
                        options = listOf("") + currentSubcategories + "+ Add New",
                        selectedOption = selectedSubcategory,
                        onOptionSelected = {
                            if (it == "+ Add New") {
                                showAddSubcategoryDialog = true
                            } else {
                                selectedSubcategory = it
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                AuroraMultiSelectDropdown(
                    label = "Labels",
                    options = labels + "+ Add New",
                    selectedOptions = selectedLabels.toSet(),
                    onOptionToggled = { lbl ->
                        if (lbl == "+ Add New") {
                            showAddLabelDialog = true
                        } else {
                            if (selectedLabels.contains(lbl)) selectedLabels.remove(lbl)
                            else selectedLabels.add(lbl)
                        }
                    }
                )
                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    "Frequency",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    frequencyOptions.forEachIndexed { index, (freq, label) ->
                        SegmentedButton(
                            selected = selectedFrequency == freq,
                            onClick = { selectedFrequency = freq },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index, count = frequencyOptions.size
                            )
                        ) {
                            Text(label, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))

                when (selectedFrequency) {
                    RecurrenceFrequency.MONTHLY -> {
                        AuroraDropdown(
                            label = "Day of month",
                            options = (1..31).map { it.toString() },
                            selectedOption = dayOfPeriod.toString(),
                            onOptionSelected = { dayOfPeriod = it.toIntOrNull() ?: 1 }
                        )
                    }
                    RecurrenceFrequency.WEEKLY -> {
                        val weekDays = listOf(
                            "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"
                        )
                        AuroraDropdown(
                            label = "Day of week",
                            options = weekDays,
                            selectedOption = weekDays.getOrElse(dayOfPeriod - 1) { "Monday" },
                            onOptionSelected = { dayOfPeriod = weekDays.indexOf(it) + 1 }
                        )
                    }
                    RecurrenceFrequency.YEARLY -> {
                        val months = listOf(
                            "Current Month (Default)", "January", "February", "March", "April",
                            "May", "June", "July", "August", "September", "October", "November", "December"
                        )
                        AuroraDropdown(
                            label = "Month of year",
                            options = months,
                            selectedOption = if (monthOfPeriod in 1..12) months[monthOfPeriod]
                            else "Current Month (Default)",
                            onOptionSelected = { monthOfPeriod = months.indexOf(it) }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        AuroraDropdown(
                            label = "Day of month (yearly)",
                            options = (1..31).map { it.toString() },
                            selectedOption = dayOfPeriod.toString(),
                            onOptionSelected = { dayOfPeriod = it.toIntOrNull() ?: 1 }
                        )
                    }
                    RecurrenceFrequency.DAILY -> { /* no extra picker */ }
                }
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(modifier = Modifier
                        .weight(1f)
                        .clickable { showStartDatePicker = true }) {
                        AuroraTextField(
                            value = startDate.format(dateFormatter),
                            onValueChange = {},
                            label = "Start date",
                            readOnly = true,
                            enabled = false
                        )
                    }
                    Box(modifier = Modifier
                        .weight(1f)
                        .clickable { showEndDatePicker = true }) {
                        AuroraTextField(
                            value = endDate?.format(dateFormatter) ?: "None",
                            onValueChange = {},
                            label = "End date",
                            readOnly = true,
                            enabled = false
                        )
                    }
                }
                if (endDate != null) {
                    TextButton(
                        onClick = { endDate = null },
                        modifier = Modifier.align(Alignment.End),
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Clear end date", style = MaterialTheme.typography.labelMedium)
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))

                if (paymentModes.isNotEmpty()) {
                    AuroraDropdown(
                        label = "Payment mode",
                        options = listOf("") + paymentModes,
                        selectedOption = selectedPaymentMode,
                        onOptionSelected = { selectedPaymentMode = it }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                if (paidVia.isNotEmpty()) {
                    AuroraDropdown(
                        label = "Paid via",
                        options = listOf("") + paidVia,
                        selectedOption = selectedPaidVia,
                        onOptionSelected = { selectedPaidVia = it }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                AuroraTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = "Notes",
                    singleLine = false
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Active", style = MaterialTheme.typography.titleSmall)
                    Switch(checked = isActive, onCheckedChange = { isActive = it })
                }
                Spacer(modifier = Modifier.height(16.dp))

                if (showStartDatePicker) {
                    AuroraDatePickerDialog(
                        onDismissRequest = { showStartDatePicker = false },
                        onDateSelected = { millis ->
                            millis?.let {
                                startDate = java.time.Instant.ofEpochMilli(it)
                                    .atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                            }
                        }
                    )
                }
                if (showEndDatePicker) {
                    AuroraDatePickerDialog(
                        onDismissRequest = { showEndDatePicker = false },
                        onDateSelected = { millis ->
                            millis?.let {
                                endDate = java.time.Instant.ofEpochMilli(it)
                                    .atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                            }
                        }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    TextButton(onClick = onDismiss, shape = RoundedCornerShape(50)) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            val amount = amountStr.trim().toDoubleOrNull()
                            nameError = name.isBlank()
                            amountError = amount == null || amount <= 0.0
                            if (!nameError && !amountError) {
                                onConfirm(
                                    RecurringExpense(
                                        id = initial?.id ?: java.util.UUID.randomUUID().toString(),
                                        name = name.trim(),
                                        storeName = storeName.trim(),
                                        amount = amount!!,
                                        category = selectedCategory,
                                        subcategory = selectedSubcategory,
                                        itemDescription = itemDescription.trim(),
                                        labels = selectedLabels.toList(),
                                        paymentMode = selectedPaymentMode,
                                        paidVia = selectedPaidVia,
                                        frequency = selectedFrequency,
                                        dayOfPeriod = dayOfPeriod,
                                        monthOfPeriod = monthOfPeriod,
                                        startDate = startDate,
                                        endDate = endDate,
                                        notes = notes.trim(),
                                        isActive = isActive,
                                        lastGeneratedDate = initial?.lastGeneratedDate
                                    )
                                )
                            }
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
