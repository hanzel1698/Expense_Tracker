package com.example.expensetracker.ui.modern

import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.example.expensetracker.CSV_TEMPLATE_CONTENT
import com.example.expensetracker.Screen
import com.example.expensetracker.TrendDimension
import com.example.expensetracker.data.AppData
import com.example.expensetracker.data.DataRepository
import com.example.expensetracker.data.RecurringExpenseEngine
import com.example.expensetracker.data.SampleDataManager
import com.example.expensetracker.model.Expense
import com.example.expensetracker.model.RecurrenceFrequency
import com.example.expensetracker.model.RecurringExpense
import com.example.expensetracker.parseCsvLine
import com.example.expensetracker.parseFlexibleDate
import com.example.expensetracker.releasenotes.WhatsNewGate
import com.example.expensetracker.sync.SyncService
import com.example.expensetracker.ui.modern.components.AuroraConfirmDialog
import com.example.expensetracker.ui.modern.screens.*
import com.example.expensetracker.ui.modern.theme.AuroraTheme
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

/**
 * Sole entry point for the app — the Aurora (Material 3) UI. Owns navigation,
 * the in-memory data state, auto-save, Google Drive sync, CSV
 * import/export, and the recurring-expense engine.
 */
class ModernMainActivity : ComponentActivity() {
    private lateinit var syncService: SyncService
    private var signInRefreshTrigger by mutableStateOf(0)
    private var signInErrorMessage by mutableStateOf<String?>(null)

    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        handleSignInResult(task)
    }

    private fun handleSignInResult(completedTask: Task<GoogleSignInAccount>) {
        try {
            val account = completedTask.getResult(ApiException::class.java)
            if (account != null) {
                syncService.initializeDriveService(account)
            }
            refreshSignInStatus()
        } catch (e: ApiException) {
            Log.e("ModernMainActivity", "Sign-in failed with ApiException: ${e.statusCode}", e)
            signInErrorMessage = "Google sign-in failed (code ${e.statusCode}). " +
                "Check that this app's SHA-1 is registered in Google Cloud Console."
            refreshSignInStatus()
        } catch (e: Exception) {
            Log.e("ModernMainActivity", "Sign-in failed with unexpected error", e)
            signInErrorMessage = "Google sign-in failed: ${e.message}"
            refreshSignInStatus()
        }
    }

    private fun refreshSignInStatus() {
        signInRefreshTrigger++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        syncService = SyncService(applicationContext)

        setContent {
            val context = applicationContext
            val initialData = remember { DataRepository.load(context) }

            var isDarkTheme by remember { mutableStateOf(initialData.isDarkTheme) }
            AuroraTheme(darkTheme = isDarkTheme) {
                WhatsNewGate {
                    val globalExpenses = remember { mutableStateListOf(*initialData.expenses.toTypedArray()) }
                    var currentScreen by remember { mutableStateOf(Screen.Dashboard) }

                    var viewingDateFilter by remember { mutableStateOf<LocalDate?>(null) }
                    var viewingStartDateFilter by remember { mutableStateOf<LocalDate?>(null) }
                    var viewingEndDateFilter by remember { mutableStateOf<LocalDate?>(null) }
                    var viewingCategoryFilter by remember { mutableStateOf<Set<String>>(emptySet()) }
                    var viewingSubcategoryFilter by remember { mutableStateOf<Set<String>>(emptySet()) }
                    var viewingExpenseIdFilter by remember { mutableStateOf<String?>(null) }
                    var viewingGroupIdFilter by remember { mutableStateOf<String?>(null) }
                    var viewingLabelFilter by remember { mutableStateOf<Set<String>>(emptySet()) }

                    var expenseToEdit by remember { mutableStateOf<Expense?>(null) }
                    var groupToEdit by remember { mutableStateOf<List<Expense>?>(null) }
                    var initialDateForNewExpense by remember { mutableStateOf<LocalDate?>(null) }

                    val categories = remember { mutableStateListOf(*initialData.categories.toTypedArray()) }
                    val subcategoriesMap = remember {
                        val map = mutableStateMapOf<String, androidx.compose.runtime.snapshots.SnapshotStateList<String>>()
                        initialData.subcategoriesMap.forEach { (cat, subs) ->
                            map[cat] = mutableStateListOf(*subs.toTypedArray())
                        }
                        map
                    }
                    val labels = remember { mutableStateListOf(*initialData.labels.toTypedArray()) }
                    val paymentModes = remember { mutableStateListOf(*initialData.paymentModes.toTypedArray()) }
                    val paidVia = remember { mutableStateListOf(*initialData.paidVia.toTypedArray()) }
                    val recurringExpenses = remember { mutableStateListOf(*initialData.recurringExpenses.toTypedArray()) }
                    val storeHistory = remember { mutableStateListOf(*initialData.storeHistory.toTypedArray()) }
                    val categoryBudgets = remember { mutableStateMapOf(*initialData.categoryBudgets.toList().toTypedArray()) }
                    val subcategoryBudgets = remember { mutableStateMapOf(*initialData.subcategoryBudgets.toList().toTypedArray()) }

                    // ── CSV import/export (same parsing as the brutalist app) ─────
                    val importLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.GetContent()
                    ) { uri: Uri? ->
                        if (uri != null) {
                            try {
                                val inputStream = context.contentResolver.openInputStream(uri)
                                val reader = java.io.BufferedReader(java.io.InputStreamReader(inputStream))
                                val lines = reader.readLines()
                                if (lines.isEmpty()) {
                                    android.widget.Toast.makeText(context, "CSV file is empty", android.widget.Toast.LENGTH_SHORT).show()
                                    return@rememberLauncherForActivityResult
                                }

                                val rows = lines.drop(1)
                                var importCount = 0
                                var recurringCount = 0

                                val newExpenses = mutableListOf<Expense>()
                                val newRecurringConfigs = mutableListOf<RecurringExpense>()
                                val splitGroupMap = mutableMapOf<String, String>()

                                for (line in rows) {
                                    if (line.isBlank()) continue
                                    val fields = parseCsvLine(line)
                                    if (fields.size < 3) continue

                                    val dateStr = fields[0]
                                    val storeName = fields[1]
                                    val amountStr = fields[2]

                                    if (dateStr.isBlank() || storeName.isBlank() || amountStr.isBlank()) continue

                                    val date = parseFlexibleDate(dateStr)
                                    val amount = amountStr.toDoubleOrNull() ?: 0.0

                                    val category = if (fields.size > 3) fields[3] else ""
                                    val subcategory = if (fields.size > 4) fields[4] else ""
                                    val itemDesc = if (fields.size > 5) fields[5] else ""
                                    val labelsStr = if (fields.size > 6) fields[6] else ""
                                    val quantityStr = if (fields.size > 7) fields[7] else ""
                                    val unitStr = if (fields.size > 8) fields[8] else ""
                                    val notesStr = if (fields.size > 9) fields[9] else ""
                                    val paymentMode = if (fields.size > 10) fields[10] else ""
                                    val paidViaStr = if (fields.size > 11) fields[11] else ""
                                    val splitId = if (fields.size > 12) fields[12] else ""
                                    val isRecurringStr = if (fields.size > 13) fields[13] else ""
                                    val recurringFreq = if (fields.size > 14) fields[14] else ""
                                    val recurringEndDateStr = if (fields.size > 15) fields[15] else ""

                                    val rowLabels = if (labelsStr.isNotBlank()) {
                                        labelsStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                    } else {
                                        emptyList()
                                    }

                                    val quantity = quantityStr.toDoubleOrNull()
                                    val unit = if (unitStr.isBlank()) null else unitStr

                                    if (category.isNotBlank() && !categories.contains(category)) {
                                        categories.add(category)
                                        subcategoriesMap[category] = mutableStateListOf()
                                    }
                                    if (category.isNotBlank() && subcategory.isNotBlank()) {
                                        val subs = subcategoriesMap[category]
                                        if (subs != null && !subs.contains(subcategory)) {
                                            subs.add(subcategory)
                                        }
                                    }
                                    rowLabels.forEach { label ->
                                        if (label.isNotBlank() && !labels.contains(label)) {
                                            labels.add(label)
                                        }
                                    }
                                    if (paymentMode.isNotBlank() && !paymentModes.contains(paymentMode)) {
                                        paymentModes.add(paymentMode)
                                    }
                                    if (paidViaStr.isNotBlank() && !paidVia.contains(paidViaStr)) {
                                        paidVia.add(paidViaStr)
                                    }

                                    val isRecurring = isRecurringStr.trim().lowercase() == "yes" ||
                                        isRecurringStr.trim().lowercase() == "true"
                                    if (isRecurring) {
                                        val frequency = when (recurringFreq.trim().lowercase()) {
                                            "daily" -> RecurrenceFrequency.DAILY
                                            "weekly" -> RecurrenceFrequency.WEEKLY
                                            "monthly" -> RecurrenceFrequency.MONTHLY
                                            "yearly" -> RecurrenceFrequency.YEARLY
                                            else -> RecurrenceFrequency.MONTHLY
                                        }
                                        val dayOfPeriod = if (frequency == RecurrenceFrequency.WEEKLY) {
                                            date.dayOfWeek.value
                                        } else {
                                            date.dayOfMonth
                                        }
                                        val endDate = if (recurringEndDateStr.isNotBlank())
                                            parseFlexibleDate(recurringEndDateStr) else null

                                        newRecurringConfigs.add(
                                            RecurringExpense(
                                                name = storeName,
                                                storeName = storeName,
                                                amount = amount,
                                                category = category,
                                                subcategory = subcategory,
                                                itemDescription = itemDesc,
                                                labels = rowLabels,
                                                notes = notesStr,
                                                paymentMode = paymentMode,
                                                paidVia = paidViaStr,
                                                frequency = frequency,
                                                dayOfPeriod = dayOfPeriod,
                                                startDate = date,
                                                endDate = endDate
                                            )
                                        )
                                        recurringCount++
                                    } else {
                                        val groupId = if (splitId.isNotBlank()) {
                                            val key = "${splitId.trim()}_${dateStr.trim()}_${storeName.trim()}"
                                            splitGroupMap.getOrPut(key) { UUID.randomUUID().toString() }
                                        } else {
                                            UUID.randomUUID().toString()
                                        }

                                        newExpenses.add(
                                            Expense(
                                                groupId = groupId,
                                                date = date,
                                                storeName = storeName,
                                                amount = amount,
                                                category = category,
                                                subcategory = subcategory,
                                                itemDescription = itemDesc,
                                                labels = rowLabels,
                                                quantity = quantity,
                                                unit = unit,
                                                notes = notesStr,
                                                paymentMode = paymentMode,
                                                paidVia = paidViaStr
                                            )
                                        )
                                        importCount++
                                    }
                                }

                                if (newExpenses.isNotEmpty()) {
                                    globalExpenses.addAll(newExpenses)
                                }

                                val (occurrences, updatedConfigs) =
                                    RecurringExpenseEngine.generate(newRecurringConfigs, LocalDate.now())
                                if (occurrences.isNotEmpty()) {
                                    globalExpenses.addAll(occurrences)
                                }
                                recurringExpenses.addAll(updatedConfigs)

                                android.widget.Toast.makeText(
                                    context,
                                    "Import complete! Added $importCount expenses and $recurringCount recurring rules.",
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            } catch (e: Exception) {
                                Log.e("ModernMainActivity", "Failed to import CSV", e)
                                android.widget.Toast.makeText(
                                    context, "Import failed: ${e.message}", android.widget.Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }

                    val exportLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.CreateDocument("text/csv")
                    ) { uri: Uri? ->
                        if (uri != null) {
                            try {
                                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                                    outputStream.write(CSV_TEMPLATE_CONTENT.toByteArray())
                                }
                                android.widget.Toast.makeText(
                                    context, "Template exported successfully!", android.widget.Toast.LENGTH_SHORT
                                ).show()
                            } catch (e: Exception) {
                                Log.e("ModernMainActivity", "Failed to export CSV template", e)
                                android.widget.Toast.makeText(
                                    context, "Export failed: ${e.message}", android.widget.Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }

                    // ── Auto-save ──────────────────────────────────────────────────
                    LaunchedEffect(Unit) {
                        snapshotFlow {
                            AppData(
                                expenses = globalExpenses.toList(),
                                categories = categories.toList(),
                                subcategoriesMap = subcategoriesMap.mapValues { it.value.toList() },
                                labels = labels.toList(),
                                paymentModes = paymentModes.toList(),
                                paidVia = paidVia.toList(),
                                categoryBudgets = categoryBudgets.toMap(),
                                subcategoryBudgets = subcategoryBudgets.toMap(),
                                storeHistory = storeHistory.toList(),
                                isDarkTheme = isDarkTheme,
                                recurringExpenses = recurringExpenses.toList()
                            )
                        }.collect { data ->
                            DataRepository.save(context, data)
                        }
                    }

                    val overallBudget by remember(categoryBudgets, categories) {
                        derivedStateOf {
                            categories.sumOf { categoryBudgets[it] ?: 0.0 }
                        }
                    }

                    // ── Sync state ─────────────────────────────────────────────────
                    var isSignedIn by remember { mutableStateOf(false) }
                    var isSyncing by remember { mutableStateOf(false) }
                    var showSyncMessage by remember { mutableStateOf("") }
                    var showSyncError by remember { mutableStateOf(false) }
                    val coroutineScope = rememberCoroutineScope()

                    fun applyRecurringEngineResult(
                        newExpenses: List<Expense>,
                        updatedTemplates: List<RecurringExpense>
                    ) {
                        if (newExpenses.isNotEmpty()) {
                            globalExpenses.addAll(newExpenses)
                        }
                        updatedTemplates.forEach { updated ->
                            val index = recurringExpenses.indexOfFirst { it.id == updated.id }
                            if (index >= 0 && recurringExpenses[index].lastGeneratedDate != updated.lastGeneratedDate) {
                                recurringExpenses[index] = updated
                            }
                        }
                    }

                    fun updateRecurringExpenseAt(index: Int, re: RecurringExpense) {
                        val resolvedIndex = recurringExpenses.indexOfFirst { it.id == re.id }
                            .takeIf { it >= 0 } ?: index
                        if (resolvedIndex in recurringExpenses.indices) {
                            recurringExpenses[resolvedIndex] = re
                        }
                    }

                    // Developer mode (hidden: long-press the version label)
                    var isDeveloperMode by remember { mutableStateOf(false) }
                    var devModeLongPressStart by remember { mutableStateOf(0L) }
                    var showClearDataDialog by remember { mutableStateOf(false) }
                    var showExitConfirmationDialog by remember { mutableStateOf(false) }

                    LaunchedEffect(Unit) {
                        isSignedIn = syncService.isSignedIn()
                        val (newOccurrences, updatedRe) =
                            RecurringExpenseEngine.generate(recurringExpenses.toList(), LocalDate.now())
                        applyRecurringEngineResult(newOccurrences, updatedRe)
                    }

                    LaunchedEffect(signInRefreshTrigger) {
                        if (signInRefreshTrigger > 0) {
                            isSignedIn = syncService.isSignedIn()
                        }
                    }

                    LaunchedEffect(signInErrorMessage) {
                        signInErrorMessage?.let {
                            showSyncMessage = it
                            showSyncError = true
                            signInErrorMessage = null
                        }
                    }

                    fun reloadAllFromRepository() {
                        val newData = DataRepository.load(context)
                        globalExpenses.clear()
                        globalExpenses.addAll(newData.expenses)
                        categories.clear()
                        categories.addAll(newData.categories)
                        labels.clear()
                        labels.addAll(newData.labels)
                        storeHistory.clear()
                        storeHistory.addAll(newData.storeHistory)
                        subcategoriesMap.clear()
                        newData.subcategoriesMap.forEach { (cat, subs) ->
                            subcategoriesMap[cat] = mutableStateListOf(*subs.toTypedArray())
                        }
                        paymentModes.clear()
                        paymentModes.addAll(newData.paymentModes)
                        paidVia.clear()
                        paidVia.addAll(newData.paidVia)
                        recurringExpenses.clear()
                        recurringExpenses.addAll(newData.recurringExpenses)
                        categoryBudgets.clear()
                        categoryBudgets.putAll(newData.categoryBudgets)
                        subcategoryBudgets.clear()
                        subcategoryBudgets.putAll(newData.subcategoryBudgets)
                    }

                    // ── Restore from a backup JSON file (no Drive sign-in needed) ───
                    val restoreFileLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.GetContent()
                    ) { uri: Uri? ->
                        if (uri != null) {
                            coroutineScope.launch {
                                isSyncing = true
                                try {
                                    val jsonData = context.contentResolver.openInputStream(uri)
                                        ?.bufferedReader()
                                        ?.use { it.readText() }
                                    if (jsonData.isNullOrBlank()) {
                                        showSyncMessage = "Restore failed: could not read file"
                                        showSyncError = true
                                    } else {
                                        val result = syncService.restoreFromFile(jsonData)
                                        result.fold(
                                            onSuccess = { syncResult ->
                                                if (syncResult.success) {
                                                    reloadAllFromRepository()
                                                    val removedMsg =
                                                        if (syncResult.expensesRemoved > 0)
                                                            ", ${syncResult.expensesRemoved} removed"
                                                        else ""
                                                    showSyncMessage =
                                                        "Backup restored (${syncResult.expensesAdded} added$removedMsg)"
                                                    showSyncError = false
                                                } else {
                                                    showSyncMessage = "Restore failed: ${syncResult.message}"
                                                    showSyncError = true
                                                }
                                            },
                                            onFailure = {
                                                showSyncMessage = "Restore failed: ${it.message}"
                                                showSyncError = true
                                            }
                                        )
                                    }
                                } catch (e: Exception) {
                                    Log.e("ModernMainActivity", "Failed to restore from file", e)
                                    showSyncMessage = "Restore failed: ${e.message}"
                                    showSyncError = true
                                }
                                isSyncing = false
                            }
                        }
                    }

                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        containerColor = MaterialTheme.colorScheme.background,
                        bottomBar = {
                            ModernBottomBar(
                                currentScreen = currentScreen,
                                onScreenSelected = {
                                    currentScreen = it
                                    if (it != Screen.ExpenseList && it != Screen.DraftList) {
                                        viewingDateFilter = null
                                        viewingStartDateFilter = null
                                        viewingEndDateFilter = null
                                        viewingCategoryFilter = emptySet()
                                        viewingSubcategoryFilter = emptySet()
                                        viewingLabelFilter = emptySet()
                                        viewingExpenseIdFilter = null
                                        viewingGroupIdFilter = null
                                    }
                                    if (it != Screen.AddExpense) {
                                        expenseToEdit = null
                                        groupToEdit = null
                                    }
                                }
                            )
                        }
                    ) { innerPadding ->
                        BackHandler(enabled = true) {
                            when (currentScreen) {
                                Screen.Dashboard -> {
                                    showExitConfirmationDialog = true
                                }
                                Screen.ExpenseList -> {
                                    if (viewingExpenseIdFilter != null || viewingGroupIdFilter != null) {
                                        viewingExpenseIdFilter = null
                                        viewingGroupIdFilter = null
                                        viewingDateFilter = null
                                        viewingStartDateFilter = null
                                        viewingEndDateFilter = null
                                        viewingCategoryFilter = emptySet()
                                        viewingSubcategoryFilter = emptySet()
                                        viewingLabelFilter = emptySet()
                                    } else {
                                        currentScreen = Screen.Dashboard
                                        viewingDateFilter = null
                                        viewingStartDateFilter = null
                                        viewingEndDateFilter = null
                                        viewingCategoryFilter = emptySet()
                                        viewingSubcategoryFilter = emptySet()
                                        viewingLabelFilter = emptySet()
                                    }
                                }
                                Screen.Budget, Screen.Settings, Screen.AddExpense, Screen.DraftList -> {
                                    currentScreen = Screen.Dashboard
                                    viewingDateFilter = null
                                    viewingStartDateFilter = null
                                    viewingEndDateFilter = null
                                    viewingCategoryFilter = emptySet()
                                    viewingSubcategoryFilter = emptySet()
                                    viewingLabelFilter = emptySet()
                                    viewingExpenseIdFilter = null
                                    viewingGroupIdFilter = null
                                    expenseToEdit = null
                                    groupToEdit = null
                                    initialDateForNewExpense = null
                                }
                            }
                        }

                        Box(modifier = Modifier.padding(innerPadding)) {
                            when (currentScreen) {
                                Screen.Dashboard -> ModernDashboardScreen(
                                    expenses = globalExpenses,
                                    budget = overallBudget,
                                    categories = categories,
                                    subcategoriesMap = subcategoriesMap,
                                    labels = labels,
                                    onNavigateToExpenses = { date ->
                                        viewingDateFilter = date
                                        viewingStartDateFilter = null
                                        viewingEndDateFilter = null
                                        viewingCategoryFilter = emptySet()
                                        viewingSubcategoryFilter = emptySet()
                                        viewingLabelFilter = emptySet()
                                        currentScreen = Screen.ExpenseList
                                    },
                                    onNavigateToMonthExpenses = { ym, dimension, item ->
                                        viewingStartDateFilter = ym.atDay(1)
                                        viewingEndDateFilter = ym.atEndOfMonth()
                                        viewingDateFilter = null
                                        viewingCategoryFilter =
                                            if (dimension == TrendDimension.CATEGORY && item != null) setOf(item) else emptySet()
                                        viewingSubcategoryFilter =
                                            if (dimension == TrendDimension.SUBCATEGORY && item != null) setOf(item) else emptySet()
                                        viewingLabelFilter =
                                            if (dimension == TrendDimension.LABEL && item != null) setOf(item) else emptySet()
                                        currentScreen = Screen.ExpenseList
                                    },
                                    onNavigateToFilteredExpenses = { startDate, endDate, cats, subcats, lbls ->
                                        viewingStartDateFilter = startDate
                                        viewingEndDateFilter = endDate
                                        viewingDateFilter = null
                                        viewingCategoryFilter = cats
                                        viewingSubcategoryFilter = subcats
                                        viewingLabelFilter = lbls
                                        viewingExpenseIdFilter = null
                                        currentScreen = Screen.ExpenseList
                                    },
                                    onNewExpense = { date ->
                                        expenseToEdit = null
                                        groupToEdit = null
                                        initialDateForNewExpense = date
                                        currentScreen = Screen.AddExpense
                                    },
                                    isDarkTheme = isDarkTheme,
                                    onThemeToggle = { isDarkTheme = !isDarkTheme },
                                    onNavigateToDrafts = { currentScreen = Screen.DraftList }
                                )
                                Screen.ExpenseList -> {
                                    val currentMonthStart = LocalDate.now().withDayOfMonth(1)
                                    val currentMonthEnd =
                                        LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth())
                                    if (viewingDateFilter == null && viewingStartDateFilter == null &&
                                        viewingEndDateFilter == null
                                    ) {
                                        viewingStartDateFilter = currentMonthStart
                                        viewingEndDateFilter = currentMonthEnd
                                    }
                                    ModernExpenseListScreen(
                                        expenses = globalExpenses.filter { !it.isDraft },
                                        viewingDate = viewingDateFilter,
                                        initialStartDate = viewingStartDateFilter,
                                        initialEndDate = viewingEndDateFilter,
                                        initialCategories = viewingCategoryFilter,
                                        initialSubcategories = viewingSubcategoryFilter,
                                        initialLabels = viewingLabelFilter,
                                        initialSelectedExpenseId = viewingExpenseIdFilter,
                                        initialSelectedGroupId = viewingGroupIdFilter,
                                        onClearFilter = {
                                            viewingDateFilter = null
                                            viewingStartDateFilter = null
                                            viewingEndDateFilter = null
                                            viewingCategoryFilter = emptySet()
                                            viewingSubcategoryFilter = emptySet()
                                            viewingLabelFilter = emptySet()
                                            viewingExpenseIdFilter = null
                                            viewingGroupIdFilter = null
                                        },
                                        onAddExpense = {
                                            expenseToEdit = null
                                            groupToEdit = null
                                            currentScreen = Screen.AddExpense
                                        },
                                        onEditExpense = { expense ->
                                            val group = globalExpenses.filter { it.groupId == expense.groupId }
                                            if (group.size > 1) {
                                                groupToEdit = group
                                                expenseToEdit = null
                                            } else {
                                                expenseToEdit = expense
                                                groupToEdit = null
                                            }
                                            currentScreen = Screen.AddExpense
                                        },
                                        onDeleteExpense = { expense ->
                                            globalExpenses.removeAll { it.id == expense.id }
                                        }
                                    )
                                }
                                Screen.DraftList -> ModernExpenseListScreen(
                                    expenses = globalExpenses.filter { it.isDraft },
                                    viewingDate = viewingDateFilter,
                                    initialStartDate = viewingStartDateFilter,
                                    initialEndDate = viewingEndDateFilter,
                                    initialCategories = viewingCategoryFilter,
                                    initialSubcategories = viewingSubcategoryFilter,
                                    initialLabels = viewingLabelFilter,
                                    initialSelectedExpenseId = viewingExpenseIdFilter,
                                    initialSelectedGroupId = viewingGroupIdFilter,
                                    showOnlyDrafts = true,
                                    onClearFilter = {
                                        viewingDateFilter = null
                                        viewingStartDateFilter = null
                                        viewingEndDateFilter = null
                                        viewingCategoryFilter = emptySet()
                                        viewingSubcategoryFilter = emptySet()
                                        viewingLabelFilter = emptySet()
                                        viewingExpenseIdFilter = null
                                        viewingGroupIdFilter = null
                                    },
                                    onAddExpense = {
                                        expenseToEdit = null
                                        groupToEdit = null
                                        currentScreen = Screen.AddExpense
                                    },
                                    onEditExpense = { expense ->
                                        val group = globalExpenses.filter { it.groupId == expense.groupId }
                                        if (group.size > 1) {
                                            groupToEdit = group
                                            expenseToEdit = null
                                        } else {
                                            expenseToEdit = expense
                                            groupToEdit = null
                                        }
                                        currentScreen = Screen.AddExpense
                                    },
                                    onDeleteExpense = { expense ->
                                        globalExpenses.removeAll { it.id == expense.id }
                                    }
                                )
                                Screen.Settings -> ModernSettingsScreen(
                                    categories = categories,
                                    onAddCategory = { name ->
                                        categories.add(name)
                                        subcategoriesMap[name] = mutableStateListOf()
                                    },
                                    onEditCategory = { index, newValue ->
                                        val oldName = categories[index]
                                        val subs = subcategoriesMap.remove(oldName)
                                        categories[index] = newValue
                                        if (subs != null) subcategoriesMap[newValue] = subs

                                        categoryBudgets.remove(oldName)?.let { budget ->
                                            categoryBudgets[newValue] = budget
                                        }

                                        val keysToMigrate =
                                            subcategoryBudgets.keys.filter { it.startsWith("$oldName/") }
                                        keysToMigrate.forEach { oldKey ->
                                            val newKey = oldKey.replaceFirst("$oldName/", "$newValue/")
                                            subcategoryBudgets.remove(oldKey)?.let { budget ->
                                                subcategoryBudgets[newKey] = budget
                                            }
                                        }
                                    },
                                    onDeleteCategory = { index ->
                                        val name = categories[index]
                                        subcategoriesMap.remove(name)
                                        categories.removeAt(index)
                                        categoryBudgets.remove(name)
                                        val keysToRemove =
                                            subcategoryBudgets.keys.filter { it.startsWith("$name/") }
                                        keysToRemove.forEach { subcategoryBudgets.remove(it) }
                                    },
                                    subcategoriesMap = subcategoriesMap,
                                    onAddSubcategory = { category, name ->
                                        subcategoriesMap.getOrPut(category) { mutableStateListOf() }.add(name)
                                    },
                                    onEditSubcategory = { category, index, newValue ->
                                        val list = subcategoriesMap[category]
                                        if (list != null) {
                                            val oldSubName = list[index]
                                            list[index] = newValue

                                            val oldKey = "$category/$oldSubName"
                                            val newKey = "$category/$newValue"
                                            subcategoryBudgets.remove(oldKey)?.let { budget ->
                                                subcategoryBudgets[newKey] = budget
                                            }
                                        }
                                    },
                                    onDeleteSubcategory = { category, index ->
                                        val list = subcategoriesMap[category]
                                        if (list != null) {
                                            val subName = list[index]
                                            list.removeAt(index)
                                            subcategoryBudgets.remove("$category/$subName")
                                        }
                                    },
                                    labels = labels,
                                    onAddLabel = { label -> labels.add(label) },
                                    onEditLabel = { index, newValue -> labels[index] = newValue },
                                    onDeleteLabel = { index -> labels.removeAt(index) },
                                    paymentModes = paymentModes,
                                    onAddPaymentMode = { mode -> paymentModes.add(mode) },
                                    onEditPaymentMode = { index, newValue -> paymentModes[index] = newValue },
                                    onDeletePaymentMode = { index -> paymentModes.removeAt(index) },
                                    paidVia = paidVia,
                                    onAddPaidVia = { method -> paidVia.add(method) },
                                    onEditPaidVia = { index, newValue -> paidVia[index] = newValue },
                                    onDeletePaidVia = { index -> paidVia.removeAt(index) },
                                    recurringExpenses = recurringExpenses,
                                    onAddRecurringExpense = { re ->
                                        val (newExp, updated) = RecurringExpenseEngine.generate(listOf(re))
                                        if (newExp.isNotEmpty()) {
                                            globalExpenses.addAll(newExp)
                                            recurringExpenses.add(updated.first())
                                        } else {
                                            recurringExpenses.add(re)
                                        }
                                    },
                                    onEditRecurringExpense = { index, re ->
                                        updateRecurringExpenseAt(index, re)
                                    },
                                    onDeleteRecurringExpense = { index ->
                                        if (index in recurringExpenses.indices) {
                                            recurringExpenses.removeAt(index)
                                        }
                                    },
                                    onSignIn = {
                                        val signInIntent = syncService.getGoogleSignInClient().signInIntent
                                        signInLauncher.launch(signInIntent)
                                    },
                                    onSignOut = {
                                        coroutineScope.launch {
                                            syncService.signOut()
                                            isSignedIn = false
                                        }
                                    },
                                    onUploadBackup = {
                                        coroutineScope.launch {
                                            isSyncing = true
                                            val result = syncService.uploadToDrive()
                                            result.fold(
                                                onSuccess = {
                                                    showSyncMessage = it.message
                                                    showSyncError = false
                                                },
                                                onFailure = {
                                                    showSyncMessage = "Upload failed: ${it.message}"
                                                    showSyncError = true
                                                }
                                            )
                                            isSyncing = false
                                        }
                                    },
                                    onViewBackups = {
                                        coroutineScope.launch {
                                            isSyncing = true
                                            val result = syncService.listAvailableBackups()
                                            result.fold(
                                                onSuccess = { backups ->
                                                    if (backups.isNotEmpty()) {
                                                        val downloadResult =
                                                            syncService.downloadFromDrive(backups.first().fileId)
                                                        downloadResult.fold(
                                                            onSuccess = { syncResult ->
                                                                if (syncResult.success) {
                                                                    reloadAllFromRepository()
                                                                    val removedMsg =
                                                                        if (syncResult.expensesRemoved > 0)
                                                                            ", ${syncResult.expensesRemoved} removed"
                                                                        else ""
                                                                    showSyncMessage =
                                                                        "Backup restored (${syncResult.expensesAdded} added$removedMsg)"
                                                                    showSyncError = false
                                                                } else {
                                                                    showSyncMessage =
                                                                        "Restore failed: ${syncResult.message}"
                                                                    showSyncError = true
                                                                }
                                                            },
                                                            onFailure = {
                                                                showSyncMessage = "Download failed: ${it.message}"
                                                                showSyncError = true
                                                            }
                                                        )
                                                    } else {
                                                        showSyncMessage = "No backups found"
                                                        showSyncError = true
                                                    }
                                                },
                                                onFailure = {
                                                    showSyncMessage = "Failed to list backups: ${it.message}"
                                                    showSyncError = true
                                                }
                                            )
                                            isSyncing = false
                                        }
                                    },
                                    isSignedIn = isSignedIn,
                                    onRestoreFromFile = { restoreFileLauncher.launch("*/*") },
                                    isDeveloperMode = isDeveloperMode,
                                    onToggleDevMode = {
                                        val currentTime = System.currentTimeMillis()
                                        if (currentTime - devModeLongPressStart >= 3000) {
                                            isDeveloperMode = !isDeveloperMode
                                        }
                                    },
                                    onDevModePressStart = { devModeLongPressStart = System.currentTimeMillis() },
                                    onDevModePressEnd = { devModeLongPressStart = 0L },
                                    onPopulateSampleData = {
                                        val sampleData = SampleDataManager.populateSampleData()
                                        globalExpenses.clear()
                                        globalExpenses.addAll(sampleData.expenses)
                                        categories.clear()
                                        categories.addAll(sampleData.categories)
                                        subcategoriesMap.clear()
                                        sampleData.subcategoriesMap.forEach { (cat, subs) ->
                                            subcategoriesMap[cat] = mutableStateListOf(*subs.toTypedArray())
                                        }
                                        labels.clear()
                                        labels.addAll(sampleData.labels)
                                        categoryBudgets.clear()
                                        categoryBudgets.putAll(sampleData.categoryBudgets)
                                        showSyncMessage = "Sample data populated: ${sampleData.expenses.size} expenses"
                                        showSyncError = false
                                    },
                                    onClearAllData = {
                                        showClearDataDialog = true
                                    },
                                    onExportTemplate = { exportLauncher.launch("expense_import_template.csv") },
                                    onImportCsv = { importLauncher.launch("*/*") }
                                )
                                Screen.Budget -> ModernBudgetScreen(
                                    expenses = globalExpenses,
                                    categories = categories,
                                    subcategoriesMap = subcategoriesMap,
                                    overallBudget = overallBudget,
                                    categoryBudgets = categoryBudgets,
                                    onCategoryBudgetChanged = { cat, amount -> categoryBudgets[cat] = amount },
                                    subcategoryBudgets = subcategoryBudgets,
                                    onSubcategoryBudgetChanged = { key, amount ->
                                        subcategoryBudgets[key] = amount
                                    }
                                )
                                Screen.AddExpense -> ModernExpenseEntryScreen(
                                    categories = categories,
                                    subcategoriesMap = subcategoriesMap,
                                    labels = labels,
                                    paymentModes = paymentModes,
                                    paidVia = paidVia,
                                    storeHistory = storeHistory.toList(),
                                    expenseToEdit = expenseToEdit,
                                    groupToEdit = groupToEdit,
                                    initialDate = initialDateForNewExpense,
                                    onSave = { newExpenses ->
                                        val gid = expenseToEdit?.groupId ?: groupToEdit?.firstOrNull()?.groupId
                                        if (gid != null) {
                                            globalExpenses.removeIf { it.groupId == gid }
                                        }
                                        globalExpenses.addAll(newExpenses)
                                        expenseToEdit = null
                                        groupToEdit = null
                                        initialDateForNewExpense = null
                                        currentScreen = Screen.Dashboard
                                    },
                                    onBack = {
                                        expenseToEdit = null
                                        groupToEdit = null
                                        initialDateForNewExpense = null
                                        currentScreen = Screen.Dashboard
                                    },
                                    onAddCategory = { name ->
                                        categories.add(name)
                                        subcategoriesMap[name] = mutableStateListOf()
                                    },
                                    onAddSubcategory = { category, name ->
                                        subcategoriesMap.getOrPut(category) { mutableStateListOf() }.add(name)
                                    },
                                    onAddLabel = { name -> labels.add(name) },
                                    onAddPaymentMode = { mode -> paymentModes.add(mode) },
                                    onAddPaidVia = { method -> paidVia.add(method) },
                                    onUpdateStoreHistory = { newStore ->
                                        if (newStore.isNotBlank() && !storeHistory.contains(newStore)) {
                                            storeHistory.add(0, newStore)
                                            if (storeHistory.size > 50) {
                                                storeHistory.removeAt(storeHistory.size - 1)
                                            }
                                        } else if (newStore.isNotBlank() && storeHistory.contains(newStore)) {
                                            storeHistory.remove(newStore)
                                            storeHistory.add(0, newStore)
                                        }
                                    }
                                )
                            }

                            // Clear-all-data confirmation
                            if (showClearDataDialog) {
                                AuroraConfirmDialog(
                                    title = "Clear all data",
                                    message = "This will delete ALL expenses, categories, and settings. This cannot be undone. Are you sure?",
                                    confirmText = "Clear everything",
                                    destructive = true,
                                    onConfirm = {
                                        globalExpenses.clear()
                                        categories.clear()
                                        subcategoriesMap.clear()
                                        labels.clear()
                                        categoryBudgets.clear()
                                        subcategoryBudgets.clear()
                                        isDarkTheme = false
                                        showClearDataDialog = false
                                        showSyncMessage = "All data cleared"
                                        showSyncError = false
                                    },
                                    onDismiss = { showClearDataDialog = false }
                                )
                            }

                            // Exit confirmation
                            if (showExitConfirmationDialog) {
                                AuroraConfirmDialog(
                                    title = "Exit app",
                                    message = "Are you sure you want to exit the app?",
                                    confirmText = "Exit",
                                    onConfirm = { finish() },
                                    onDismiss = { showExitConfirmationDialog = false }
                                )
                            }

                            // Sync progress overlay
                            if (isSyncing) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.35f))
                                        .clickable(enabled = false) {},
                                    contentAlignment = Alignment.Center
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(28.dp),
                                        color = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp),
                                        tonalElevation = 6.dp
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(28.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            CircularProgressIndicator()
                                            Spacer(modifier = Modifier.height(16.dp))
                                            Text(
                                                "Syncing data…",
                                                style = MaterialTheme.typography.titleMedium
                                            )
                                            Text(
                                                "Please wait",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Auto-dismiss sync message after 3 seconds
                        LaunchedEffect(showSyncMessage) {
                            if (showSyncMessage.isNotEmpty()) {
                                kotlinx.coroutines.delay(3000)
                                showSyncMessage = ""
                            }
                        }

                        if (showSyncMessage.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .statusBarsPadding()
                                    .padding(16.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (showSyncError) MaterialTheme.colorScheme.errorContainer
                                    else MaterialTheme.colorScheme.secondaryContainer,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = showSyncMessage,
                                        color = if (showSyncError) MaterialTheme.colorScheme.onErrorContainer
                                        else MaterialTheme.colorScheme.onSecondaryContainer,
                                        style = MaterialTheme.typography.bodyMedium,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ModernBottomBar(currentScreen: Screen, onScreenSelected: (Screen) -> Unit) {
    val isMainScreen = currentScreen in listOf(
        Screen.Dashboard, Screen.ExpenseList, Screen.Budget, Screen.Settings
    )
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
    ) {
        NavigationBarItem(
            selected = isMainScreen && currentScreen == Screen.Dashboard,
            onClick = { onScreenSelected(Screen.Dashboard) },
            icon = { Icon(Icons.Default.Home, contentDescription = "Dashboard") },
            label = { Text("Home") }
        )
        NavigationBarItem(
            selected = isMainScreen && currentScreen == Screen.ExpenseList,
            onClick = { onScreenSelected(Screen.ExpenseList) },
            icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Expenses") },
            label = { Text("Expenses") }
        )
        NavigationBarItem(
            selected = isMainScreen && currentScreen == Screen.Budget,
            onClick = { onScreenSelected(Screen.Budget) },
            icon = { Icon(Icons.Default.PieChart, contentDescription = "Budget") },
            label = { Text("Budget") }
        )
        NavigationBarItem(
            selected = isMainScreen && currentScreen == Screen.Settings,
            onClick = { onScreenSelected(Screen.Settings) },
            icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
            label = { Text("Settings") }
        )
    }
}
