package com.example.expensetracker

import android.os.Bundle
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.expensetracker.ui.components.BrutalistButton
import com.example.expensetracker.ui.components.BrutalistCard
import com.example.expensetracker.ui.components.BrutalistTextField
import com.example.expensetracker.ui.theme.Black
import com.example.expensetracker.ui.theme.ExpenseTrackerTheme
import com.example.expensetracker.ui.theme.White
import com.example.expensetracker.ui.components.BrutalistCalendar
import com.example.expensetracker.ui.components.BrutalistDropdown
import com.example.expensetracker.ui.components.BrutalistSearchField
import com.example.expensetracker.ui.components.BrutalistDatePickerDialog
import com.example.expensetracker.ui.components.BrutalistDateRangePickerDialog
import com.example.expensetracker.ui.components.BrutalistMultiSelectDropdown
import com.example.expensetracker.model.Expense
import com.example.expensetracker.model.RecurringExpense
import com.example.expensetracker.model.RecurrenceFrequency
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import com.example.expensetracker.ui.screens.BudgetScreen
import com.example.expensetracker.ui.screens.ExpenseEntryScreen
import java.time.Instant
import java.util.UUID
import java.time.ZoneId
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.YearMonth
import com.example.expensetracker.data.DataRepository
import com.example.expensetracker.data.AppData
import com.example.expensetracker.data.SampleDataManager
import com.example.expensetracker.data.RecurringExpenseEngine
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.toArgb
import android.graphics.Paint
import android.graphics.Typeface
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import com.example.expensetracker.sync.SyncService
import com.example.expensetracker.sync.BackupInfo
import com.example.expensetracker.sync.SupabaseService
import kotlinx.coroutines.launch
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import androidx.lifecycle.lifecycleScope

fun cleanCsvField(field: String): String {
    var s = field.trim()
    if (s.startsWith("\"") && s.endsWith("\"")) {
        s = s.substring(1, s.length - 1).trim()
    }
    return s
}

fun parseCsvLine(line: String): List<String> {
    val result = mutableListOf<String>()
    var inQuotes = false
    val currentField = StringBuilder()
    var i = 0
    while (i < line.length) {
        val c = line[i]
        if (c == '"') {
            inQuotes = !inQuotes
        } else if (c == ',' && !inQuotes) {
            result.add(cleanCsvField(currentField.toString()))
            currentField.setLength(0)
        } else {
            currentField.append(c)
        }
        i++
    }
    result.add(cleanCsvField(currentField.toString()))
    return result
}

fun parseFlexibleDate(dateStr: String): LocalDate {
    val trimmed = dateStr.trim()
    return try {
        if (trimmed.contains("-")) {
            LocalDate.parse(trimmed, DateTimeFormatter.ISO_LOCAL_DATE)
        } else if (trimmed.contains("/")) {
            val parts = trimmed.split("/")
            if (parts[0].length == 4) {
                // YYYY/MM/DD
                LocalDate.of(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
            } else {
                // DD/MM/YYYY
                LocalDate.of(parts[2].toInt(), parts[1].toInt(), parts[0].toInt())
            }
        } else {
            LocalDate.now()
        }
    } catch (e: Exception) {
        LocalDate.now()
    }
}


val CSV_TEMPLATE_CONTENT = """
Date (YYYY-MM-DD),Store Name,Amount,Category,Subcategory,Item Description,Labels (comma-separated),Quantity,Unit,Notes,Payment Mode,Paid Via,Split ID,Is Recurring (Yes/No),Recurring Frequency (Daily/Weekly/Monthly/Yearly),Recurring End Date (YYYY-MM-DD)
2026-06-01,Groceries - Target,45.50,Food,Groceries,Weekly grocery shopping,"Personal, Urgent",1,Bag,Weekly milk and eggs,Credit Card,Google Pay,,No,,
2026-06-02,Costco,100.00,Food,Groceries,Food supplies,Personal,,,,,Credit Card,Google Pay,SplitA,No,,
2026-06-02,Costco,50.00,Shopping,Clothing,New shirt,Personal,,,,,Credit Card,Google Pay,SplitA,No,,
2026-06-03,Gym Membership,30.00,Health,Gym,Monthly Gym fee,Personal,,,,,Net Banking,Other,,Yes,Weekly,2026-12-31
""".trimIndent()

enum class Screen {
    Dashboard, ExpenseList, Budget, Settings, AddExpense, DraftList
}

enum class TrendDimension {
    TOTAL, CATEGORY, SUBCATEGORY, LABEL
}

enum class TrendTimeframe {
    MONTH, YEAR
}

data class ChartPoint(val label: String, val value: Float, val period: Any, val endDate: Any? = null)

class MainActivity : ComponentActivity() {
    private lateinit var syncService: SyncService
    private var signInRefreshTrigger by mutableStateOf(0)
    
    // Google Sign-In activity result launcher
    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        handleSignInResult(task)
    }
    
    private fun handleSignInResult(completedTask: Task<GoogleSignInAccount>) {
        Log.d("MainActivity", "handleSignInResult called")
        try {
            val account = completedTask.getResult(ApiException::class.java)
            Log.d("MainActivity", "Sign-in result received, account: ${account?.email ?: "NULL"}")
            // Sign-in successful, initialize Drive service
            if (account != null) {
                // Initialize the Drive service through SyncService (same instance used for isSignedIn check)
                Log.d("MainActivity", "Initializing Drive service for ${account.email} through SyncService")
                syncService.initializeDriveService(account)
                // Trigger a refresh of the sign-in status
                Log.d("MainActivity", "Triggering sign-in status refresh, trigger before: $signInRefreshTrigger")
                refreshSignInStatus()
                Log.d("MainActivity", "Sign-in status refresh triggered, trigger after: $signInRefreshTrigger")
            } else {
                Log.w("MainActivity", "Sign-in result returned null account")
                refreshSignInStatus()
            }
        } catch (e: ApiException) {
            Log.e("MainActivity", "Sign-in failed with ApiException: ${e.statusCode}", e)
            // Sign-in failed
            refreshSignInStatus()
        } catch (e: Exception) {
            Log.e("MainActivity", "Sign-in failed with unexpected error", e)
            refreshSignInStatus()
        }
    }
    
    private fun refreshSignInStatus() {
        // This will be called from within a coroutine scope in the setContent block
        // We'll use a global variable to trigger the refresh
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
            ExpenseTrackerTheme(darkTheme = isDarkTheme) {
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

                // Lifted settings state for categories, subcategories, and labels
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
                                    subcategoriesMap[category] = androidx.compose.runtime.mutableStateListOf()
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
                                
                                val isRecurring = isRecurringStr.trim().lowercase() == "yes" || isRecurringStr.trim().lowercase() == "true"
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
                                    val endDate = if (recurringEndDateStr.isNotBlank()) parseFlexibleDate(recurringEndDateStr) else null
                                    
                                    val recurringConfig = RecurringExpense(
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
                                    newRecurringConfigs.add(recurringConfig)
                                    recurringCount++
                                } else {
                                    val groupId = if (splitId.isNotBlank()) {
                                        val key = "${splitId.trim()}_${dateStr.trim()}_${storeName.trim()}"
                                        splitGroupMap.getOrPut(key) { UUID.randomUUID().toString() }
                                    } else {
                                        UUID.randomUUID().toString()
                                    }
                                    
                                    val expense = Expense(
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
                                    newExpenses.add(expense)
                                    importCount++
                                }
                            }
                            
                            if (newExpenses.isNotEmpty()) {
                                globalExpenses.addAll(newExpenses)
                            }
                            
                            val (occurrences, updatedConfigs) = RecurringExpenseEngine.generate(newRecurringConfigs, LocalDate.now())
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
                            android.util.Log.e("MainActivity", "Failed to import CSV", e)
                            android.widget.Toast.makeText(context, "Import failed: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
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
                            android.widget.Toast.makeText(context, "Template exported successfully!", android.widget.Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            android.util.Log.e("MainActivity", "Failed to export CSV template", e)
                            android.widget.Toast.makeText(context, "Export failed: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                }

                // Auto-save logic
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
                        android.util.Log.d("MainActivity", "Auto-saving ${data.expenses.size} expenses")
                        DataRepository.save(context, data)
                    }
                }

                // ── Recurring expense engine ──────────────────────────────────────
                // Runs once on launch: generates any due Expense entries for all
                // active recurring templates and stamps lastGeneratedDate on each.
                LaunchedEffect(Unit) {
                    val (newExpenses, updatedTemplates) =
                        RecurringExpenseEngine.generate(recurringExpenses.toList())

                    if (newExpenses.isNotEmpty()) {
                        android.util.Log.d(
                            "MainActivity",
                            "RecurringExpenseEngine: adding ${newExpenses.size} expense(s)"
                        )
                        globalExpenses.addAll(newExpenses)
                    }

                    // Update lastGeneratedDate on templates that produced new entries
                    updatedTemplates.forEachIndexed { i, updated ->
                        if (i < recurringExpenses.size &&
                            recurringExpenses[i].lastGeneratedDate != updated.lastGeneratedDate
                        ) {
                            recurringExpenses[i] = updated
                        }
                    }
                }

                // Budget state
                val overallBudget by remember(categoryBudgets, categories) {
                    derivedStateOf {
                        categories.sumOf { categoryBudgets[it] ?: 0.0 }
                    }
                }


                // Sync state
                var isSignedIn by remember { mutableStateOf(false) }
                var isSyncing by remember { mutableStateOf(false) }
                var showSyncMessage by remember { mutableStateOf("") }
                var showSyncError by remember { mutableStateOf(false) }
                val coroutineScope = rememberCoroutineScope()

                // ── Supabase Sync State ──────────────────────────────
                var supabaseSyncing by remember { mutableStateOf(false) }
                var supabaseSyncMessage by remember { mutableStateOf("") }
                var supabaseSyncSuccess by remember { mutableStateOf(false) }
                var supabaseConnected by remember { mutableStateOf(false) }

                val onSupabaseSyncNow: () -> Unit = {
                    coroutineScope.launch {
                        supabaseSyncing = true
                        supabaseSyncMessage = ""
                        val result: com.example.expensetracker.sync.SupabaseSyncResult = SupabaseService.syncAll(
                            localExpenses = globalExpenses.toList(),
                            localRecurring = recurringExpenses.toList(),
                            categories = categories.toList(),
                            subcategoriesMap = subcategoriesMap.mapValues { it.value.toList() },
                            labels = labels.toList(),
                            paymentModes = paymentModes.toList(),
                            paidVia = paidVia.toList(),
                            categoryBudgets = categoryBudgets.toMap(),
                            subcategoryBudgets = subcategoryBudgets.toMap(),
                            storeHistory = storeHistory.toList()
                        )
                        if (result.success) {
                            // Apply pulled data (last-write-wins)
                            globalExpenses.clear()
                            globalExpenses.addAll(result.pulledExpenses)
                            recurringExpenses.clear()
                            recurringExpenses.addAll(result.pulledRecurring)

                            // Apply pulled settings (last-write-wins)
                            result.categories?.let {
                                categories.clear()
                                categories.addAll(it)
                            }
                            result.subcategoriesMap?.let {
                                subcategoriesMap.clear()
                                it.forEach { (cat, subs) ->
                                    subcategoriesMap[cat] = mutableStateListOf(*subs.toTypedArray())
                                }
                            }
                            result.labels?.let {
                                labels.clear()
                                labels.addAll(it)
                            }
                            result.paymentModes?.let {
                                paymentModes.clear()
                                paymentModes.addAll(it)
                            }
                            result.paidVia?.let {
                                paidVia.clear()
                                paidVia.addAll(it)
                            }
                            result.categoryBudgets?.let {
                                categoryBudgets.clear()
                                categoryBudgets.putAll(it)
                            }
                            result.subcategoryBudgets?.let {
                                subcategoryBudgets.clear()
                                subcategoryBudgets.putAll(it)
                            }
                            result.storeHistory?.let {
                                storeHistory.clear()
                                storeHistory.addAll(it)
                            }

                            supabaseSyncMessage = "✓ ${result.message}"
                            supabaseSyncSuccess = true
                            supabaseConnected = true
                        } else {
                            supabaseSyncMessage = "✕ ${result.message}"
                            supabaseSyncSuccess = false
                        }
                        supabaseSyncing = false
                    }
                }

                // ── Startup Supabase Sync ──────────────────────────
                LaunchedEffect(Unit) {
                    // Trigger a sync after a small delay (1.5 seconds) to allow initial state load to settle
                    kotlinx.coroutines.delay(1500)
                    android.util.Log.d("MainActivity", "Startup: Auto-triggering Supabase sync")
                    onSupabaseSyncNow()
                }

                // ── Backgrounding (Exit) Supabase Sync ───────────────
                val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                        if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                            android.util.Log.d("MainActivity", "Backgrounding: Auto-triggering Supabase sync")
                            this@MainActivity.lifecycleScope.launch {
                                SupabaseService.syncAll(
                                    localExpenses = globalExpenses.toList(),
                                    localRecurring = recurringExpenses.toList(),
                                    categories = categories.toList(),
                                    subcategoriesMap = subcategoriesMap.mapValues { it.value.toList() },
                                    labels = labels.toList(),
                                    paymentModes = paymentModes.toList(),
                                    paidVia = paidVia.toList(),
                                    categoryBudgets = categoryBudgets.toMap(),
                                    subcategoryBudgets = subcategoryBudgets.toMap(),
                                    storeHistory = storeHistory.toList()
                                )
                            }
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }


                // Developer mode state (hidden feature for testing)
                var isDeveloperMode by remember { mutableStateOf(false) }
                var showDevModeIndicator by remember { mutableStateOf(false) }
                var devModeLongPressStart by remember { mutableStateOf(0L) }
                var showClearDataDialog by remember { mutableStateOf(false) }
                var showExitConfirmationDialog by remember { mutableStateOf(false) }

                // Check sign-in status on initial load and when trigger changes
                LaunchedEffect(Unit) {
                    Log.d("MainActivity", "Initial sign-in status check")
                    isSignedIn = syncService.isSignedIn()
                    
                    val (newOccurrences, updatedRe) = RecurringExpenseEngine.generate(recurringExpenses.toList(), LocalDate.now())
                    if (newOccurrences.isNotEmpty()) {
                        globalExpenses.addAll(newOccurrences)
                        recurringExpenses.clear()
                        recurringExpenses.addAll(updatedRe)
                    }
                }
                
                // Re-check sign-in status when refresh trigger changes
                LaunchedEffect(signInRefreshTrigger) {
                    if (signInRefreshTrigger > 0) {
                        Log.d("MainActivity", "Sign-in refresh triggered: $signInRefreshTrigger")
                        isSignedIn = syncService.isSignedIn()
                    }
                }



                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        BottomBar(
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
                    // Back gesture handler
                    BackHandler(enabled = true) {
                        when (currentScreen) {
                            Screen.Dashboard -> {
                                // Show exit confirmation dialog
                                showExitConfirmationDialog = true
                            }
                            Screen.ExpenseList -> {
                                // If viewing expense details, go back to main expense list
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
                                    // Otherwise, navigate to dashboard
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
                                // Navigate to dashboard and clear filters
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
                            Screen.Dashboard -> DashboardScreen(
                                expenses = globalExpenses,
                                budget = overallBudget,
                                categoryBudgets = categoryBudgets,
                                subcategoryBudgets = subcategoryBudgets,
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
                                    viewingCategoryFilter = if (dimension == TrendDimension.CATEGORY && item != null) setOf(item) else emptySet()
                                    viewingSubcategoryFilter = if (dimension == TrendDimension.SUBCATEGORY && item != null) setOf(item) else emptySet()
                                    viewingLabelFilter = if (dimension == TrendDimension.LABEL && item != null) setOf(item) else emptySet()
                                    currentScreen = Screen.ExpenseList
                                },
                                onNavigateToYearExpenses = { year, dimension, item ->
                                    viewingStartDateFilter = LocalDate.of(year, 1, 1)
                                    viewingEndDateFilter = LocalDate.of(year, 12, 31)
                                    viewingDateFilter = null
                                    viewingCategoryFilter = if (dimension == TrendDimension.CATEGORY && item != null) setOf(item) else emptySet()
                                    viewingSubcategoryFilter = if (dimension == TrendDimension.SUBCATEGORY && item != null) setOf(item) else emptySet()
                                    viewingLabelFilter = if (dimension == TrendDimension.LABEL && item != null) setOf(item) else emptySet()
                                    currentScreen = Screen.ExpenseList
                                },
                                onNavigateToCategory = { category, subcategory, yearMonth ->
                                    viewingCategoryFilter = setOf(category)
                                    viewingSubcategoryFilter = if (subcategory != null) setOf(subcategory) else emptySet()
                                    viewingLabelFilter = emptySet()
                                    viewingDateFilter = null
                                    if (yearMonth != null) {
                                        viewingStartDateFilter = yearMonth.atDay(1)
                                        viewingEndDateFilter = yearMonth.atEndOfMonth()
                                    } else {
                                        viewingStartDateFilter = null
                                        viewingEndDateFilter = null
                                    }
                                    viewingExpenseIdFilter = null
                                    currentScreen = Screen.ExpenseList
                                },
                                onNavigateToFilteredExpenses = { startDate, endDate, categories, subcategories, labels ->
                                    viewingStartDateFilter = startDate
                                    viewingEndDateFilter = endDate
                                    viewingDateFilter = null
                                    viewingCategoryFilter = categories
                                    viewingSubcategoryFilter = subcategories
                                    viewingLabelFilter = labels
                                    viewingExpenseIdFilter = null
                                    currentScreen = Screen.ExpenseList
                                },
                                onNavigateToExpenseDetails = { id ->
                                    viewingExpenseIdFilter = id
                                    viewingGroupIdFilter = null
                                    viewingDateFilter = null
                                    viewingStartDateFilter = null
                                    viewingEndDateFilter = null
                                    viewingCategoryFilter = emptySet()
                                    viewingSubcategoryFilter = emptySet()
                                    viewingLabelFilter = emptySet()
                                    currentScreen = Screen.ExpenseList
                                },
                                onNavigateToGroupDetails = { groupId ->
                                    viewingGroupIdFilter = groupId
                                    viewingExpenseIdFilter = null
                                    viewingDateFilter = null
                                    viewingStartDateFilter = null
                                    viewingEndDateFilter = null
                                    viewingCategoryFilter = emptySet()
                                    viewingSubcategoryFilter = emptySet()
                                    viewingLabelFilter = emptySet()
                                    currentScreen = Screen.ExpenseList
                                },
                                onNavigateToBudget = { currentScreen = Screen.Budget },
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
                                // Default to current month if no date filters are set
                                val currentMonthStart = LocalDate.now().withDayOfMonth(1)
                                val currentMonthEnd = LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth())
                                if (viewingDateFilter == null && viewingStartDateFilter == null && viewingEndDateFilter == null) {
                                    viewingStartDateFilter = currentMonthStart
                                    viewingEndDateFilter = currentMonthEnd
                                }
                                ExpenseListScreen(
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
                                onEditExpense = { expense: Expense ->
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
                                onDeleteExpense = { expense: Expense -> globalExpenses.removeAll { it.id == expense.id } }
                            )
                            }
                            Screen.DraftList -> {
                                ExpenseListScreen(
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
                                onEditExpense = { expense: Expense ->
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
                                onDeleteExpense = { expense: Expense -> globalExpenses.removeAll { it.id == expense.id } }
                            )
                            }
                            Screen.Settings -> SettingsScreen(

                                categories = categories,
                                onAddCategory = { name: String ->
                                    categories.add(name)
                                    subcategoriesMap[name] = mutableStateListOf()
                                },
                                onEditCategory = { index: Int, newValue: String ->
                                    val oldName = categories[index]
                                    val subs = subcategoriesMap.remove(oldName)
                                    categories[index] = newValue
                                    if (subs != null) subcategoriesMap[newValue] = subs
                                    
                                    // Migrate category budget
                                    categoryBudgets.remove(oldName)?.let { budget ->
                                        categoryBudgets[newValue] = budget
                                    }
                                    
                                    // Migrate subcategory budgets
                                    val keysToMigrate = subcategoryBudgets.keys.filter { it.startsWith("$oldName/") }
                                    keysToMigrate.forEach { oldKey ->
                                        val newKey = oldKey.replaceFirst("$oldName/", "$newValue/")
                                        subcategoryBudgets.remove(oldKey)?.let { budget ->
                                            subcategoryBudgets[newKey] = budget
                                        }
                                    }
                                },
                                onDeleteCategory = { index: Int ->
                                    val name = categories[index]
                                    subcategoriesMap.remove(name)
                                    categories.removeAt(index)
                                    
                                    // Remove category budget
                                    categoryBudgets.remove(name)
                                    
                                    // Remove subcategory budgets
                                    val keysToRemove = subcategoryBudgets.keys.filter { it.startsWith("$name/") }
                                    keysToRemove.forEach { subcategoryBudgets.remove(it) }
                                },
                                subcategoriesMap = subcategoriesMap,
                                onAddSubcategory = { category: String, name: String ->
                                    subcategoriesMap.getOrPut(category) { mutableStateListOf() }.add(name)
                                },
                                onEditSubcategory = { category: String, index: Int, newValue: String ->
                                    val list = subcategoriesMap[category]
                                    if (list != null) {
                                        val oldSubName = list[index]
                                        list[index] = newValue
                                        
                                        // Migrate subcategory budget
                                        val oldKey = "$category/$oldSubName"
                                        val newKey = "$category/$newValue"
                                        subcategoryBudgets.remove(oldKey)?.let { budget ->
                                            subcategoryBudgets[newKey] = budget
                                        }
                                    }
                                },
                                onDeleteSubcategory = { category: String, index: Int ->
                                    val list = subcategoriesMap[category]
                                    if (list != null) {
                                        val subName = list[index]
                                        list.removeAt(index)
                                        
                                        // Remove subcategory budget
                                        subcategoryBudgets.remove("$category/$subName")
                                    }
                                },
                                labels = labels,
                                onAddLabel = { label: String -> labels.add(label) },
                                onEditLabel = { index: Int, newValue: String -> labels[index] = newValue },
                                onDeleteLabel = { index: Int -> labels.removeAt(index) },
                                paymentModes = paymentModes,
                                onAddPaymentMode = { mode: String -> paymentModes.add(mode) },
                                onEditPaymentMode = { index: Int, newValue: String -> paymentModes[index] = newValue },
                                onDeletePaymentMode = { index: Int -> paymentModes.removeAt(index) },
                                paidVia = paidVia,
                                onAddPaidVia = { method: String -> paidVia.add(method) },
                                onEditPaidVia = { index: Int, newValue: String -> paidVia[index] = newValue },
                                onDeletePaidVia = { index: Int -> paidVia.removeAt(index) },
                                recurringExpenses = recurringExpenses,
                                onAddRecurringExpense = { re: RecurringExpense ->
                                    val (newExp, updated) = RecurringExpenseEngine.generate(listOf(re))
                                    if (newExp.isNotEmpty()) {
                                        globalExpenses.addAll(newExp)
                                        recurringExpenses.add(updated.first())
                                    } else {
                                        recurringExpenses.add(re)
                                    }
                                },
                                onEditRecurringExpense = { index: Int, re: RecurringExpense ->
                                    val (newExp, updated) = RecurringExpenseEngine.generate(listOf(re))
                                    if (newExp.isNotEmpty()) {
                                        globalExpenses.addAll(newExp)
                                        recurringExpenses[index] = updated.first()
                                    } else {
                                        recurringExpenses[index] = re
                                    }
                                },
                                onDeleteRecurringExpense = { index: Int -> recurringExpenses.removeAt(index) },
                                context = context,
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
                                        android.util.Log.d("MainActivity", "Upload started - categories: ${categories.size}, labels: ${labels.size}, paymentModes: ${paymentModes.size}, paidVia: ${paidVia.size}, subcategoriesMap: ${subcategoriesMap.size}")
                                        val result = syncService.uploadToDrive()
                                        result.fold(
                                            onSuccess = {
                                                showSyncMessage = it.message
                                                showSyncError = false
                                                android.util.Log.d("MainActivity", "Upload success")
                                            },
                                            onFailure = {
                                                showSyncMessage = "Upload failed: ${it.message}"
                                                showSyncError = true
                                                android.util.Log.e("MainActivity", "Upload failed: ${it.message}")
                                            }
                                        )
                                        isSyncing = false
                                    }
                                },
                                onDownloadBackup = { fileId ->
                                    coroutineScope.launch {
                                        isSyncing = true
                                        val result = syncService.downloadFromDrive(fileId)
                                        result.fold(
                                            onSuccess = {
                                                showSyncMessage = it.message
                                                showSyncError = false
                                                // Reload data after successful download
                                                val newData = DataRepository.load(context)
                                                android.util.Log.d("MainActivity", "Download reload - categories: ${newData.categories.size}, labels: ${newData.labels.size}, paymentModes: ${newData.paymentModes.size}, paidVia: ${newData.paidVia.size}")
                                                globalExpenses.clear()
                                                globalExpenses.addAll(newData.expenses)
                                                categories.clear()
                                                categories.addAll(newData.categories)
                                                android.util.Log.d("MainActivity", "After categories update: ${categories.size}")
                                                labels.clear()
                                                labels.addAll(newData.labels)
                                                android.util.Log.d("MainActivity", "After labels update: ${labels.size}")
                                                storeHistory.clear()
                                                storeHistory.addAll(newData.storeHistory)
                                                subcategoriesMap.clear()
                                                newData.subcategoriesMap.forEach { (cat, subs) ->
                                                    subcategoriesMap[cat] = mutableStateListOf(*subs.toTypedArray())
                                                }
                                                android.util.Log.d("MainActivity", "After subcategoriesMap update: ${subcategoriesMap.size}")
                                                paymentModes.clear()
                                                paymentModes.addAll(newData.paymentModes)
                                                android.util.Log.d("MainActivity", "After paymentModes update: ${paymentModes.size}")
                                                paidVia.clear()
                                                paidVia.addAll(newData.paidVia)
                                                android.util.Log.d("MainActivity", "After paidVia update: ${paidVia.size}")
                                                recurringExpenses.clear()
                                                recurringExpenses.addAll(newData.recurringExpenses)
                                                categoryBudgets.clear()
                                                categoryBudgets.putAll(newData.categoryBudgets)
                                                subcategoryBudgets.clear()
                                                subcategoryBudgets.putAll(newData.subcategoryBudgets)
                                            },
                                            onFailure = {
                                                showSyncMessage = "Download failed: ${it.message}"
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
                                                    // For now, just download the latest backup
                                                    val result = syncService.downloadFromDrive(backups.first().fileId)
                                                    result.fold(
                                                        onSuccess = { syncResult ->
                                                            if (syncResult.success) {
                                                                // The sync was successful, reload data from repository
                                                                android.util.Log.d("MainActivity", "Download success, reloading data...")
                                                                val reloadedData = DataRepository.load(context)
                                                                android.util.Log.d("MainActivity", "Reloaded data: ${reloadedData.expenses.size} expenses")
                                                                globalExpenses.clear()
                                                                globalExpenses.addAll(reloadedData.expenses)
                                                                android.util.Log.d("MainActivity", "Updated globalExpenses: ${globalExpenses.size} expenses")
                                                                categories.clear()
                                                                categories.addAll(reloadedData.categories)
                                                                android.util.Log.d("MainActivity", "Updated categories: ${categories.size}")
                                                                labels.clear()
                                                                labels.addAll(reloadedData.labels)
                                                                android.util.Log.d("MainActivity", "Updated labels: ${labels.size}")
                                                                storeHistory.clear()
                                                                storeHistory.addAll(reloadedData.storeHistory)
                                                                subcategoriesMap.clear()
                                                                reloadedData.subcategoriesMap.forEach { (cat, subs) ->
                                                                    subcategoriesMap[cat] = mutableStateListOf(*subs.toTypedArray())
                                                                }
                                                                android.util.Log.d("MainActivity", "Updated subcategoriesMap: ${subcategoriesMap.size}")
                                                                paymentModes.clear()
                                                                paymentModes.addAll(reloadedData.paymentModes)
                                                                android.util.Log.d("MainActivity", "Updated paymentModes: ${paymentModes.size}")
                                                                paidVia.clear()
                                                                paidVia.addAll(reloadedData.paidVia)
                                                                android.util.Log.d("MainActivity", "Updated paidVia: ${paidVia.size}")
                                                                recurringExpenses.clear()
                                                                recurringExpenses.addAll(reloadedData.recurringExpenses)
                                                                categoryBudgets.clear()
                                                                categoryBudgets.putAll(reloadedData.categoryBudgets)
                                                                subcategoryBudgets.clear()
                                                                subcategoryBudgets.putAll(reloadedData.subcategoryBudgets)
                                                                val removedMsg = if (syncResult.expensesRemoved > 0) ", ${syncResult.expensesRemoved} removed" else ""
                                                                showSyncMessage = "Backup restored (${syncResult.expensesAdded} added${removedMsg})"
                                                            } else {
                                                                showSyncMessage = "Restore failed: ${syncResult.message}"
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
                                // Developer mode parameters
                                isDeveloperMode = isDeveloperMode,
                                onToggleDevMode = {
                                    val currentTime = System.currentTimeMillis()
                                    if (currentTime - devModeLongPressStart >= 3000) {
                                        isDeveloperMode = !isDeveloperMode
                                        showDevModeIndicator = isDeveloperMode
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
                                onImportCsv = { importLauncher.launch("*/*") },
                                // Supabase sync
                                onSupabaseSyncNow = onSupabaseSyncNow,
                                isSupabaseSyncing = supabaseSyncing,
                                supabaseSyncMessage = supabaseSyncMessage,
                                supabaseSyncSuccess = supabaseSyncSuccess,
                                isSupabaseConnected = supabaseConnected
                            )
                            Screen.Budget -> BudgetScreen(

                                expenses = globalExpenses,
                                categories = categories,
                                subcategoriesMap = subcategoriesMap,
                                overallBudget = overallBudget,
                                categoryBudgets = categoryBudgets,
                                onCategoryBudgetChanged = { cat, amount -> categoryBudgets[cat] = amount },
                                subcategoryBudgets = subcategoryBudgets,
                                onSubcategoryBudgetChanged = { key, amount -> subcategoryBudgets[key] = amount }
                            )
                            Screen.AddExpense -> ExpenseEntryScreen(

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
                                onAddLabel = { name ->
                                    labels.add(name)
                                },
                                onAddPaymentMode = { mode ->
                                    paymentModes.add(mode)
                                },
                                onAddPaidVia = { method ->
                                    paidVia.add(method)
                                },
                                onUpdateStoreHistory = { newStore ->
                                    // Add store to history if not already present (add to front)
                                    if (newStore.isNotBlank() && !storeHistory.contains(newStore)) {
                                        storeHistory.add(0, newStore)
                                        // Keep only last 50 stores to avoid bloat
                                        if (storeHistory.size > 50) {
                                            storeHistory.removeAt(storeHistory.size - 1)
                                        }
                                    } else if (newStore.isNotBlank() && storeHistory.contains(newStore)) {
                                        // Move existing store to front
                                        storeHistory.remove(newStore)
                                        storeHistory.add(0, newStore)
                                    }
                                }
                            )
                            else -> DashboardScreen(
                                expenses = globalExpenses,
                                budget = overallBudget,
                                categoryBudgets = categoryBudgets,
                                subcategoryBudgets = subcategoryBudgets,
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
                                    viewingCategoryFilter = if (dimension == TrendDimension.CATEGORY && item != null) setOf(item) else emptySet()
                                    viewingSubcategoryFilter = if (dimension == TrendDimension.SUBCATEGORY && item != null) setOf(item) else emptySet()
                                    viewingLabelFilter = if (dimension == TrendDimension.LABEL && item != null) setOf(item) else emptySet()
                                    currentScreen = Screen.ExpenseList
                                },
                                onNavigateToYearExpenses = { year, dimension, item ->
                                    viewingStartDateFilter = LocalDate.of(year, 1, 1)
                                    viewingEndDateFilter = LocalDate.of(year, 12, 31)
                                    viewingDateFilter = null
                                    viewingCategoryFilter = if (dimension == TrendDimension.CATEGORY && item != null) setOf(item) else emptySet()
                                    viewingSubcategoryFilter = if (dimension == TrendDimension.SUBCATEGORY && item != null) setOf(item) else emptySet()
                                    viewingLabelFilter = if (dimension == TrendDimension.LABEL && item != null) setOf(item) else emptySet()
                                    currentScreen = Screen.ExpenseList
                                },
                                onNavigateToCategory = { category, subcategory, yearMonth ->
                                    viewingCategoryFilter = setOf(category)
                                    viewingSubcategoryFilter = if (subcategory != null) setOf(subcategory) else emptySet()
                                    viewingLabelFilter = emptySet()
                                    viewingDateFilter = null
                                    if (yearMonth != null) {
                                        viewingStartDateFilter = yearMonth.atDay(1)
                                        viewingEndDateFilter = yearMonth.atEndOfMonth()
                                    } else {
                                        viewingStartDateFilter = null
                                        viewingEndDateFilter = null
                                    }
                                    viewingExpenseIdFilter = null
                                    currentScreen = Screen.ExpenseList
                                },
                                onNavigateToFilteredExpenses = { startDate, endDate, categories, subcategories, labels ->
                                    viewingStartDateFilter = startDate
                                    viewingEndDateFilter = endDate
                                    viewingDateFilter = null
                                    viewingCategoryFilter = categories
                                    viewingSubcategoryFilter = subcategories
                                    viewingLabelFilter = labels
                                    viewingExpenseIdFilter = null
                                    currentScreen = Screen.ExpenseList
                                },
                                onNavigateToExpenseDetails = { id ->
                                    viewingExpenseIdFilter = id
                                    viewingGroupIdFilter = null
                                    viewingDateFilter = null
                                    viewingStartDateFilter = null
                                    viewingEndDateFilter = null
                                    viewingCategoryFilter = emptySet()
                                    viewingSubcategoryFilter = emptySet()
                                    viewingLabelFilter = emptySet()
                                    currentScreen = Screen.ExpenseList
                                },
                                onNavigateToGroupDetails = { groupId ->
                                    viewingGroupIdFilter = groupId
                                    viewingExpenseIdFilter = null
                                    viewingDateFilter = null
                                    viewingStartDateFilter = null
                                    viewingEndDateFilter = null
                                    viewingCategoryFilter = emptySet()
                                    viewingSubcategoryFilter = emptySet()
                                    viewingLabelFilter = emptySet()
                                    currentScreen = Screen.ExpenseList
                                },
                                onNavigateToBudget = { currentScreen = Screen.Budget },
                                onNewExpense = { date ->
                                    expenseToEdit = null
                                    groupToEdit = null
                                    initialDateForNewExpense = date
                                    currentScreen = Screen.AddExpense
                                },
                                isDarkTheme = isDarkTheme,
                                onThemeToggle = { isDarkTheme = !isDarkTheme }
                            )
                        }

                        // Clear all data confirmation dialog (outside when expression)
                        if (showClearDataDialog) {
                            BrutalistConfirmDialog(
                                title = "CLEAR ALL DATA",
                                message = "This will delete ALL expenses, categories, and settings. This cannot be undone. Are you sure?",
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

                        // Exit confirmation dialog
                        if (showExitConfirmationDialog) {
                            BrutalistConfirmDialog(
                                title = "EXIT APP",
                                message = "Are you sure you want to exit the app?",
                                onConfirm = {
                                    finish()
                                },
                                onDismiss = { showExitConfirmationDialog = false }
                            )
                        }

                        // Sync Progress Overlay
                        if (isSyncing) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.3f))
                                    .clickable(enabled = false) {},
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    modifier = Modifier
                                        .background(MaterialTheme.colorScheme.surface)
                                        .border(4.dp, Color.Black)
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "SYNCING DATA...",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 16.sp,
                                        color = Color.Black
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    LinearProgressIndicator(
                                        modifier = Modifier
                                            .width(200.dp)
                                            .height(16.dp)
                                            .border(2.dp, Color.Black),
                                        color = Color.Black,
                                        trackColor = Color.White
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "PLEASE WAIT",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = Color.Black
                                    )
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
                    
                    // Sync message display
                    if (showSyncMessage.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                                .background(if (showSyncError) Color(0xFFFF5252) else Color(0xFF4CAF50))
                                .border(2.dp, Color.Black)
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = showSyncMessage,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BottomBar(currentScreen: Screen, onScreenSelected: (Screen) -> Unit) {
    val isMainScreen = currentScreen in listOf(Screen.Dashboard, Screen.ExpenseList, Screen.Budget, Screen.Settings)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .height(64.dp)
            .background(MaterialTheme.colorScheme.surface)
            .border(2.dp, MaterialTheme.colorScheme.onSurface, RectangleShape),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BottomNavItem(
            icon = Icons.Default.Home,
            label = "DASHBOARD",
            isSelected = isMainScreen && currentScreen == Screen.Dashboard,
            onClick = { onScreenSelected(Screen.Dashboard) },
            modifier = Modifier.weight(1f)
        )
        BottomNavItem(
            icon = Icons.Default.List,
            label = "EXPENSES",
            isSelected = isMainScreen && currentScreen == Screen.ExpenseList,
            onClick = { onScreenSelected(Screen.ExpenseList) },
            modifier = Modifier.weight(1f)
        )
        BottomNavItem(
            icon = Icons.Default.Edit,
            label = "BUDGET",
            isSelected = isMainScreen && currentScreen == Screen.Budget,
            onClick = { onScreenSelected(Screen.Budget) },
            modifier = Modifier.weight(1f)
        )
        BottomNavItem(
            icon = Icons.Default.Settings,
            label = "SETTINGS",
            isSelected = isMainScreen && currentScreen == Screen.Settings,
            onClick = { onScreenSelected(Screen.Settings) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun BottomNavItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val themeBlack = MaterialTheme.colorScheme.onSurface
    val themeWhite = MaterialTheme.colorScheme.surface
    Box(
        modifier = modifier
            .fillMaxHeight()
            .background(if (isSelected) themeBlack else themeWhite)
            .border(1.dp, themeBlack, RectangleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = label, tint = if (isSelected) themeWhite else themeBlack)
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isSelected) themeWhite else themeBlack)
        }
    }
}

@Composable
fun BrutalistPieChart(
    expenses: List<Expense>, 
    modifier: Modifier = Modifier, 
    currentMonth: java.time.YearMonth? = null, 
    radiusScale: Float = 1.0f,
    onCategorySelected: (String, String?, java.time.YearMonth?) -> Unit = { _, _, _ -> }
) {
    val themeBlack = MaterialTheme.colorScheme.onSurface
    val themeWhite = MaterialTheme.colorScheme.surface
    var viewingSubCategoryOf by remember(expenses) { mutableStateOf<String?>(null) }

    data class PieData(val label: String, val fraction: Float, val amount: Float)

    val chartData = remember(expenses, viewingSubCategoryOf) {
        if (viewingSubCategoryOf == null) {
            val totals = expenses.groupBy { it.category }.mapValues { (_, list) -> list.sumOf { it.amount }.toFloat() }
            val sum = totals.values.sum()
            if (sum == 0f) emptyList() else totals.map { (cat, amt) -> PieData(cat, amt / sum, amt) }.sortedByDescending { it.fraction }
        } else {
            val subTotals = expenses.filter { it.category == viewingSubCategoryOf }
                .groupBy { it.subcategory }
                .mapValues { (_, list) -> list.sumOf { it.amount }.toFloat() }
            val sum = subTotals.values.sum()
            if (sum == 0f) emptyList() else subTotals.map { (sub, amt) -> PieData(sub, amt / sum, amt) }.sortedByDescending { it.fraction }
        }
    }

    if (chartData.isEmpty()) {
        Box(modifier = modifier.border(2.dp, themeBlack), contentAlignment = Alignment.Center) {
            Text(if (viewingSubCategoryOf == null) "NO DATA" else "NO SUBS", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = themeBlack.copy(alpha = 0.4f))
        }
        return
    }

    val colors = listOf(themeBlack, Color(0xFF444444), Color(0xFF777777), Color(0xFFAAAAAA), Color(0xFFDDDDDD))

    data class Slice(val label: String, val fraction: Float, val amount: Float, val startAngle: Float)

    val slices = remember(chartData) {
        var angle = -90f
        chartData.map { data ->
            val sweep = data.fraction * 360f
            val result = Slice(data.label, data.fraction, data.amount, angle)
            angle += sweep
            result
        }
    }

    var selectedIndex by remember(viewingSubCategoryOf) { mutableStateOf<Int?>(null) }

    Box(
        modifier = modifier
            .border(2.dp, themeBlack)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.Canvas(
            modifier = Modifier
                .fillMaxSize()
                .aspectRatio(1f)
                .pointerInput(slices) {
                    detectTapGestures(
                        onTap = { offset ->
                            val cx = size.width / 2f
                            val cy = size.height / 2f
                            val dx = offset.x - cx
                            val dy = offset.y - cy
                            val radius = minOf(cx, cy) * radiusScale
                            if (dx * dx + dy * dy <= radius * radius) {
                                var tapAngle = Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
                                tapAngle = (tapAngle + 360f) % 360f
                                val hitIndex = slices.indexOfFirst { slice ->
                                    val normStart = (slice.startAngle + 360f) % 360f
                                    val sweep = slice.fraction * 360f
                                    val normEnd = (normStart + sweep) % 360f
                                    if (normStart <= normEnd) tapAngle >= normStart && tapAngle < normEnd
                                    else tapAngle >= normStart || tapAngle < normEnd
                                }
                                selectedIndex = if (hitIndex == -1 || hitIndex == selectedIndex) null else hitIndex
                            }
                        },
                        onLongPress = { offset ->
                            val cx = size.width / 2f
                            val cy = size.height / 2f
                            val dx = offset.x - cx
                            val dy = offset.y - cy
                            val radius = minOf(cx, cy) * radiusScale
                            if (dx * dx + dy * dy <= radius * radius && viewingSubCategoryOf == null) {
                                var tapAngle = Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
                                tapAngle = (tapAngle + 360f) % 360f
                                val hitIndex = slices.indexOfFirst { slice ->
                                    val normStart = (slice.startAngle + 360f) % 360f
                                    val sweep = slice.fraction * 360f
                                    val normEnd = (normStart + sweep) % 360f
                                    if (normStart <= normEnd) tapAngle >= normStart && tapAngle < normEnd
                                    else tapAngle >= normStart || tapAngle < normEnd
                                }
                                if (hitIndex != -1) {
                                    viewingSubCategoryOf = slices[hitIndex].label
                                }
                            }
                        }
                    )
                }
        ) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val radius = minOf(cx, cy) * radiusScale

            slices.forEachIndexed { index, slice ->
                val sweepAngle = slice.fraction * 360f
                val isSelected = index == selectedIndex
                val color = colors[index % colors.size]

                if (isSelected) {
                    val midAngle = Math.toRadians((slice.startAngle + sweepAngle / 2f).toDouble())
                    val offsetX = (radius * 0.06f * kotlin.math.cos(midAngle)).toFloat()
                    val offsetY = (radius * 0.06f * kotlin.math.sin(midAngle)).toFloat()
                    drawArc(
                        color = color,
                        startAngle = slice.startAngle,
                        sweepAngle = sweepAngle,
                        useCenter = true,
                        topLeft = androidx.compose.ui.geometry.Offset(offsetX + (cx - radius), offsetY + (cy - radius)),
                        size = Size(radius * 2, radius * 2)
                    )
                } else {
                    drawArc(
                        color = color,
                        startAngle = slice.startAngle,
                        sweepAngle = sweepAngle,
                        useCenter = true,
                        topLeft = Offset(cx - radius, cy - radius),
                        size = Size(radius * 2, radius * 2)
                    )
                }

                val endAngleRad = Math.toRadians((slice.startAngle + sweepAngle).toDouble())
                drawLine(
                    color = themeWhite,
                    start = androidx.compose.ui.geometry.Offset(cx, cy),
                    end = androidx.compose.ui.geometry.Offset(
                        cx + (radius * kotlin.math.cos(endAngleRad)).toFloat(),
                        cy + (radius * kotlin.math.sin(endAngleRad)).toFloat()
                    ),
                    strokeWidth = 2f
                )
            }

            // Outline circle so pie is visible against dark backgrounds
            drawCircle(
                color = themeWhite,
                radius = radius,
                center = androidx.compose.ui.geometry.Offset(cx, cy),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
            )
        }

        // Overlay elements on top of Canvas
        if (viewingSubCategoryOf != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopStart) {
                BrutalistButton(
                    onClick = { viewingSubCategoryOf = null },
                    modifier = Modifier.padding(4.dp).height(24.dp).width(48.dp),
                    containerColor = themeBlack
                ) {
                    Text("BACK", color = themeWhite, fontSize = 12.sp, fontWeight = FontWeight.Black)
                }
            }
        }

        selectedIndex?.let { idx ->
            val slice = slices[idx]
            val pct = (slice.fraction * 100f).toInt()
            Box(
                modifier = Modifier
                    .size(if (radiusScale < 1f) 96.dp else 110.dp)
                    .background(themeBlack)
                    .border(2.dp, themeWhite)
                    .clickable { 
                        if (viewingSubCategoryOf != null) {
                            onCategorySelected(viewingSubCategoryOf!!, slice.label, currentMonth)
                        } else {
                            onCategorySelected(slice.label, null, currentMonth)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(
                        text = "₹${String.format("%.0f", slice.amount)}",
                        fontSize = if (radiusScale < 1f) 14.sp else 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = themeWhite
                    )
                    Text(
                        text = "$pct%",
                        fontSize = if (radiusScale < 1f) 20.sp else 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = themeWhite
                    )
                    Text(
                        text = slice.label.uppercase(),
                        fontSize = if (radiusScale < 1f) 9.sp else 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = themeWhite,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
        }
    }
}


@Composable
fun BrutalistBarChart(
    expenses: List<Expense>,
    timeframe: TrendTimeframe = TrendTimeframe.MONTH,
    modifier: Modifier = Modifier,
    showTrendLine: Boolean = true,
    barColor: Color = MaterialTheme.colorScheme.onSurface,
    trendLineColor: Color = Color(0xFF777777),
    labelColor: Color = MaterialTheme.colorScheme.onSurface,
    onValueSelected: (Float?, Any?) -> Unit = { _, _ -> },
    onNavigateToSelection: (Any) -> Unit = {}
) {
    val borderColor = MaterialTheme.colorScheme.onSurface

    val chartData = remember(expenses, timeframe) {
        if (timeframe == TrendTimeframe.MONTH) {
            val currentMonth = YearMonth.now()
            (0..11).map { i ->
                val targetMonth = currentMonth.minusMonths(i.toLong())
                val total = expenses.filter { YearMonth.from(it.date) == targetMonth }.sumOf { it.amount }
                ChartPoint(
                    label = targetMonth.month.name.take(3).uppercase(),
                    value = total.toFloat(),
                    period = targetMonth
                )
            }.reversed()
        } else {
            expenses.groupBy { it.date.year }
                .map { (year, list) ->
                    ChartPoint(
                        label = year.toString(),
                        value = list.sumOf { it.amount }.toFloat(),
                        period = year
                    )
                }.sortedBy { it.label }
        }
    }

    val maxSpent = remember(chartData) { (chartData.maxOfOrNull { it.value } ?: 0f).coerceAtLeast(100f) }
    var selectedIndex by remember(expenses, timeframe) { mutableStateOf<Int?>(null) }

    Box(modifier = modifier.border(2.dp, borderColor).padding(8.dp)) {
        Canvas(modifier = Modifier
            .fillMaxSize()
            .pointerInput(chartData) {
                detectTapGestures {
                    offset ->
                    val itemCount = chartData.size.coerceAtLeast(1)
                    val barWidth = size.width / (itemCount * 1.5f)
                    val spacing = (size.width - (barWidth * itemCount)) / (itemCount + 1)
                    
                    var hit = -1
                    chartData.forEachIndexed { i, _ ->
                        val x = spacing + i * (barWidth + spacing)
                        if (offset.x >= x && offset.x <= x + barWidth) {
                            hit = i
                        }
                    }
                    if (hit == -1 || hit == selectedIndex) {
                        selectedIndex = null
                        onValueSelected(null, null)
                    } else {
                        selectedIndex = hit
                        onValueSelected(chartData[hit].value, chartData[hit].period)
                    }
                }
            }
        ) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val itemCount = chartData.size.coerceAtLeast(1)
            val barWidth = canvasWidth / (itemCount * 1.5f)
            val spacing = (canvasWidth - (barWidth * itemCount)) / (itemCount + 1)

            val paint = Paint().apply {
                color = labelColor.toArgb()
                textAlign = Paint.Align.CENTER
                typeface = Typeface.DEFAULT
            }

            paint.textSize = 100f
            var maxLabelWidth = 0f
            chartData.forEach { point ->
                maxLabelWidth = maxOf(maxLabelWidth, paint.measureText(point.label))
            }
            maxLabelWidth = maxLabelWidth.coerceAtLeast(1f)
            // Cap unifiedSize to a reasonable maximum (e.g., 50f)
            val unifiedSize = (100f * (barWidth / maxLabelWidth)).coerceAtMost(50f)
            val labelPadding = 12f
            // Increased multiplier for better breathing room
            val labelBlockHeight = (unifiedSize * 1.5f) + labelPadding 
            
            val usableHeight = (canvasHeight - labelBlockHeight).coerceAtLeast(0f)

            chartData.forEachIndexed { index, point ->
                val x = spacing + index * (barWidth + spacing)
                val h = (point.value / maxSpent) * usableHeight
                val isSelected = index == selectedIndex
                drawRect(
                    color = if (isSelected) Color(0xFF444444) else barColor,
                    topLeft = Offset(x, canvasHeight - h),
                    size = Size(barWidth, h)
                )
            }

            if (showTrendLine && chartData.size > 1) {
                val path = Path()
                chartData.forEachIndexed { index, point ->
                    val x = spacing + index * (barWidth + spacing) + barWidth / 2f
                    val y = canvasHeight - (point.value / maxSpent) * usableHeight
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(
                    path = path,
                    color = trendLineColor,
                    style = Stroke(width = 4f)
                )
            }

            paint.textSize = unifiedSize
            chartData.forEachIndexed { index, point ->
                val x = spacing + index * (barWidth + spacing)
                val h = (point.value / maxSpent) * usableHeight
                val barTop = canvasHeight - h
                val centerX = x + barWidth / 2f
                val isSelected = index == selectedIndex

                paint.isFakeBoldText = isSelected
                drawContext.canvas.nativeCanvas.drawText(point.label, centerX, barTop - 5f, paint)
            }
        }
    }
}

@Composable
fun BrutalistBarChartWithData(
    chartData: List<ChartPoint>,
    modifier: Modifier = Modifier,
    showTrendLine: Boolean = true,
    barColor: Color = MaterialTheme.colorScheme.onSurface,
    trendLineColor: Color = Color(0xFF777777),
    labelColor: Color = MaterialTheme.colorScheme.onSurface,
    onValueSelected: (Float?, Any?) -> Unit = { _, _ -> },
    onBarClicked: (ChartPoint) -> Unit = {}
) {
    val borderColor = MaterialTheme.colorScheme.onSurface
    val maxSpent = remember(chartData) { (chartData.maxOfOrNull { it.value } ?: 0f).coerceAtLeast(100f) }
    var selectedIndex by remember { mutableStateOf<Int?>(null) }

    Box(modifier = modifier.border(2.dp, borderColor).padding(8.dp)) {
        Canvas(modifier = Modifier
            .fillMaxSize()
            .pointerInput(chartData) {
                detectTapGestures(
                    onTap = { offset ->
                        val itemCount = chartData.size.coerceAtLeast(1)
                        val barWidth = size.width / (itemCount * 1.5f)
                        val spacing = (size.width - (barWidth * itemCount)) / (itemCount + 1)
                        
                        var hit = -1
                        chartData.forEachIndexed { i, _ ->
                            val x = spacing + i * (barWidth + spacing)
                            if (offset.x >= x && offset.x <= x + barWidth) {
                                hit = i
                            }
                        }
                        if (hit == -1 || hit == selectedIndex) {
                            selectedIndex = null
                            onValueSelected(null, null)
                        } else {
                            selectedIndex = hit
                            onValueSelected(chartData[hit].value, chartData[hit].period)
                            onBarClicked(chartData[hit])
                        }
                    }
                )
            }
        ) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val itemCount = chartData.size.coerceAtLeast(1)
            val barWidth = canvasWidth / (itemCount * 1.5f)
            val spacing = (canvasWidth - (barWidth * itemCount)) / (itemCount + 1)

            val paint = Paint().apply {
                color = labelColor.toArgb()
                textAlign = Paint.Align.CENTER
                typeface = Typeface.DEFAULT
            }

            paint.textSize = 100f
            var maxLabelWidth = 0f
            chartData.forEach { point ->
                maxLabelWidth = maxOf(maxLabelWidth, paint.measureText(point.label))
            }
            maxLabelWidth = maxLabelWidth.coerceAtLeast(1f)
            // Cap unifiedSize to a reasonable maximum (e.g., 50f) to prevent massive text when there are few bars
            val unifiedSize = (100f * (barWidth / maxLabelWidth)).coerceAtMost(50f)
            val labelPadding = 12f
            
            // Account for amount text above and date range text below
            // Increased multiplier to 1.5 for better breathing room
            val topTextHeight = (unifiedSize * 1.5f) + labelPadding
            val bottomTextHeight = (unifiedSize * 1.5f) + labelPadding
            val totalTextHeight = topTextHeight + bottomTextHeight
            
            val usableHeight = (canvasHeight - totalTextHeight).coerceAtLeast(0f)

            chartData.forEachIndexed { index, point ->
                val x = spacing + index * (barWidth + spacing)
                val h = (point.value / maxSpent) * usableHeight
                val isSelected = index == selectedIndex
                drawRect(
                    color = if (isSelected) Color(0xFF444444) else barColor,
                    topLeft = Offset(x, canvasHeight - bottomTextHeight - h),
                    size = Size(barWidth, h)
                )
            }

            if (showTrendLine && chartData.size > 1) {
                val path = Path()
                chartData.forEachIndexed { index, point ->
                    val x = spacing + index * (barWidth + spacing) + barWidth / 2f
                    val y = canvasHeight - bottomTextHeight - (point.value / maxSpent) * usableHeight
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(
                    path = path,
                    color = trendLineColor,
                    style = Stroke(width = 4f)
                )
            }

            paint.textSize = unifiedSize
            chartData.forEachIndexed { index, point ->
                val x = spacing + index * (barWidth + spacing)
                val h = (point.value / maxSpent) * usableHeight
                val barTop = canvasHeight - bottomTextHeight - h
                val centerX = x + barWidth / 2f
                val isSelected = index == selectedIndex

                // Draw amount above bar
                val amountText = if (point.value > 0) "₹${point.value.toInt()}" else ""
                paint.isFakeBoldText = isSelected
                if (amountText.isNotEmpty()) {
                    // Scale amount text to fit within bar width
                    paint.textSize = unifiedSize
                    val amountWidth = paint.measureText(amountText)
                    val amountTextSize = if (amountWidth > barWidth) {
                        unifiedSize * (barWidth / amountWidth)
                    } else {
                        unifiedSize
                    }
                    paint.textSize = amountTextSize
                    // Fix: Changed barTop - unifiedSize - 5f to barTop - 5f to prevent overflow
                    drawContext.canvas.nativeCanvas.drawText(amountText, centerX, barTop - 5f, paint)
                }

                // Draw date range below bar
                val dateRangeText = if (point.endDate != null && point.period is LocalDate) {
                    val start = point.period as LocalDate
                    val end = point.endDate as LocalDate
                    if (start == end) "${start.dayOfMonth}" else "${start.dayOfMonth}-${end.dayOfMonth}"
                } else {
                    point.label
                }
                paint.textSize = unifiedSize
                val dateRangeWidth = paint.measureText(dateRangeText)
                val dateRangeTextSize = if (dateRangeWidth > barWidth) {
                    unifiedSize * (barWidth / dateRangeWidth)
                } else {
                    unifiedSize
                }
                paint.textSize = dateRangeTextSize
                drawContext.canvas.nativeCanvas.drawText(dateRangeText, centerX, canvasHeight - 5f, paint)
            }

        }
    }
}

@Composable
fun BudgetListItem(
    label: String,
    budget: Double,
    spent: Double,
    onClick: (() -> Unit)? = null
) {
    val themeBlack = MaterialTheme.colorScheme.onSurface
    val themeWhite = MaterialTheme.colorScheme.surface
    val progress = if (budget > 0) (spent / budget).coerceIn(0.0, 1.5).toFloat() else 0f
    val color = if (progress > 1.0f) Color(0xFF990000) else themeBlack
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, themeBlack)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick).background(themeWhite) else Modifier.background(themeWhite))
            .padding(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            @Suppress("DEPRECATION")
            Text(label.uppercase(), fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f), color = themeBlack)
            Text("₹${spent.toInt()}/₹${budget.toInt()}", fontWeight = FontWeight.Black, fontSize = 18.sp, color = color)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Box(modifier = Modifier.fillMaxWidth().height(10.dp).border(1.dp, themeBlack)) {
            Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(progress.coerceAtMost(1f)).background(color))
        }
    }
}

@Composable
fun HorizontalBarChart(
    expenses: List<Expense>,
    categories: List<String>,
    subcategoriesMap: Map<String, List<String>>,
    selectedCategory: String?,
    onCategorySelected: (String?) -> Unit,
    onBarLongPressed: (String, String?) -> Unit,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.onSurface,
    labelColor: Color = MaterialTheme.colorScheme.onSurface
) {
    val borderColor = MaterialTheme.colorScheme.onSurface
    data class BarData(val label: String, val value: Float)

    val chartData = remember(expenses, categories, selectedCategory) {
        if (selectedCategory != null) {
            // Show subcategories for selected category
            val subcategories = subcategoriesMap[selectedCategory] ?: emptyList()
            subcategories.map { sub ->
                val total = expenses.filter { it.category == selectedCategory && it.subcategory == sub }.sumOf { it.amount }
                BarData(sub, total.toFloat())
            }.filter { it.value > 0 }.sortedByDescending { it.value }
        } else {
            // Show categories
            categories.map { cat ->
                val total = expenses.filter { it.category == cat }.sumOf { it.amount }
                BarData(cat, total.toFloat())
            }.filter { it.value > 0 }.sortedByDescending { it.value }
        }
    }

    val maxValue = remember(chartData) { (chartData.maxOfOrNull { it.value } ?: 0f).coerceAtLeast(100f) }
    var selectedIndex by remember(chartData) { mutableStateOf<Int?>(null) }

    Box(modifier = modifier.border(2.dp, borderColor).padding(8.dp)) {
        Canvas(modifier = Modifier
            .fillMaxSize()
            .pointerInput(chartData) {
                fun hitIndex(offset: Offset): Int {
                    val fixedBarHeight = 50f
                    val spacing = 10f
                    val totalItemHeight = fixedBarHeight + spacing
                    
                    var hit = -1
                    chartData.forEachIndexed { i, _ ->
                        val y = spacing + i * totalItemHeight
                        if (offset.y >= y && offset.y <= y + fixedBarHeight) {
                            hit = i
                        }
                    }
                    return hit
                }

                detectTapGestures(
                    onTap = { offset ->
                        val hit = hitIndex(offset)
                        if (hit == -1 || hit == selectedIndex) {
                            selectedIndex = null
                            onCategorySelected(null)
                        } else {
                            selectedIndex = hit
                            if (selectedCategory != null) {
                                // Subcategory view: short press navigates directly to expenses
                                val category = selectedCategory
                                val subcategory = chartData[hit].label
                                onBarLongPressed(category, subcategory)
                            } else {
                                // Category view: short press drills down
                                onCategorySelected(chartData[hit].label)
                            }
                        }
                    },
                    onLongPress = { offset ->
                        val hit = hitIndex(offset)
                        if (hit != -1) {
                            val category = if (selectedCategory != null) selectedCategory else chartData[hit].label
                            val subcategory = if (selectedCategory != null) chartData[hit].label else null
                            if (selectedCategory == null) {
                                // Category view: long press shows dialog
                                onBarLongPressed(category, subcategory)
                            } else {
                                // Subcategory view: long press navigates directly to expenses (no dialog)
                                onBarLongPressed(category, subcategory)
                            }
                        }
                    }
                )
            }
        ) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val fixedBarHeight = 50f
            val spacing = 10f
            val totalItemHeight = fixedBarHeight + spacing
            val labelWidth = 100f
            val usableWidth = (canvasWidth - labelWidth - 10f).coerceAtLeast(0f)

            val paint = Paint().apply {
                color = labelColor.toArgb()
                textAlign = Paint.Align.LEFT
                typeface = Typeface.DEFAULT
            }

            paint.textSize = 40f
            var maxLabelWidth = 0f
            chartData.forEach { point ->
                maxLabelWidth = maxOf(maxLabelWidth, paint.measureText(point.label))
            }
            val adjustedLabelWidth = maxOf(maxLabelWidth, 80f).coerceAtMost(150f)
            val valueTextWidth = 60f
            val adjustedUsableWidth = (canvasWidth - adjustedLabelWidth - valueTextWidth - 30f).coerceAtLeast(0f)

            chartData.forEachIndexed { index, point ->
                val y = spacing + index * totalItemHeight
                val w = (point.value / maxValue) * adjustedUsableWidth
                val isSelected = index == selectedIndex
                drawRect(
                    color = if (isSelected) Color(0xFF444444) else barColor,
                    topLeft = Offset(adjustedLabelWidth + 10f, y),
                    size = Size(w, fixedBarHeight)
                )
            }

            paint.textSize = 28f
            chartData.forEachIndexed { index, point ->
                val y = spacing + index * totalItemHeight
                val isSelected = index == selectedIndex
                paint.isFakeBoldText = isSelected
                drawContext.canvas.nativeCanvas.drawText(point.label, 10f, y + fixedBarHeight / 2f + 10f, paint)
                
                // Draw value at end of bar
                val w = (point.value / maxValue) * adjustedUsableWidth
                paint.textAlign = Paint.Align.LEFT
                paint.isFakeBoldText = isSelected
                drawContext.canvas.nativeCanvas.drawText("₹${String.format("%.0f", point.value)}", adjustedLabelWidth + 10f + w + 5f, y + fixedBarHeight / 2f + 10f, paint)
            }
        }
    }
}

@Composable
fun DashboardScreen(
    expenses: List<Expense>,
    budget: Double = 800.0,
    categoryBudgets: Map<String, Double> = emptyMap(),
    subcategoryBudgets: Map<String, Double> = emptyMap(),
    categories: List<String> = emptyList(),
    subcategoriesMap: Map<String, List<String>> = emptyMap(),
    labels: List<String> = emptyList(),
    onNavigateToExpenses: (LocalDate) -> Unit,
    onNavigateToMonthExpenses: (YearMonth, TrendDimension, String?) -> Unit,
    onNavigateToYearExpenses: (Int, TrendDimension, String?) -> Unit,
    onNavigateToCategory: (String, String?, YearMonth?) -> Unit,
    onNavigateToFilteredExpenses: (LocalDate?, LocalDate?, Set<String>, Set<String>, Set<String>) -> Unit,
    onNavigateToExpenseDetails: (String) -> Unit,
    onNavigateToGroupDetails: (String) -> Unit,
    onNavigateToBudget: () -> Unit,
    onNewExpense: (LocalDate) -> Unit,
    isDarkTheme: Boolean = false,
    onThemeToggle: () -> Unit = {},
    onNavigateToDrafts: () -> Unit = {}
) {
    var viewedMonth by remember { mutableStateOf(YearMonth.now()) }
    var trendsMonth by remember { mutableStateOf(YearMonth.now()) }
    var trendDimension by remember { mutableStateOf(TrendDimension.TOTAL) }
    var trendItem by remember { mutableStateOf<String?>(null) }
    var trendTimeframe by remember { mutableStateOf(TrendTimeframe.MONTH) }
    var selectedCategoryForSub by remember { mutableStateOf<String?>(null) }
    var selectedTrendValue by remember { mutableStateOf<Float?>(null) }
    var selectedTrendPeriod by remember { mutableStateOf<Any?>(null) }
    
    // New state for enhanced time period selection
    var selectedTimePeriod by remember { mutableStateOf("Selected Month") }
    var customStartDate by remember { mutableStateOf<LocalDate?>(null) }
    var customEndDate by remember { mutableStateOf<LocalDate?>(null) }
    var showCustomDateRangePicker by remember { mutableStateOf(false) }
    
    // New state for filters
    var selectedCategories by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedSubcategories by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedLabels by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showFilterOptions by remember { mutableStateOf(false) }
    
    // New state for horizontal bar chart category drill-down
    var selectedCategoryForHorizontalChart by remember { mutableStateOf<String?>(null) }
    
    // New state for show expenses dialog
    var showExpensesDialog by remember { mutableStateOf(false) }
    var selectedCategoryForExpenses by remember { mutableStateOf<String?>(null) }
    var selectedSubcategoryForExpenses by remember { mutableStateOf<String?>(null) }
    
    // New state for trend expenses dialog
    var showTrendExpensesDialog by remember { mutableStateOf(false) }
    var selectedTrendPointForExpenses by remember { mutableStateOf<ChartPoint?>(null) }

    // Sync time period selection with viewedMonth
    LaunchedEffect(viewedMonth) {
        // Kept empty to detach calendar from trends
    }

    val availableItems = remember(expenses, trendDimension, selectedCategoryForSub) {
        when (trendDimension) {
            TrendDimension.TOTAL -> emptyList()
            TrendDimension.CATEGORY -> expenses.map { it.category }.distinct().sorted()
            TrendDimension.SUBCATEGORY -> {
                if (selectedCategoryForSub != null) {
                    expenses.filter { it.category == selectedCategoryForSub }.map { it.subcategory }.distinct().sorted()
                } else emptyList()
            }
            TrendDimension.LABEL -> expenses.flatMap { it.labels }.distinct().sorted()
        }
    }

    LaunchedEffect(trendDimension, trendItem, availableItems) {
        if (trendDimension == TrendDimension.CATEGORY && trendItem != null) {
            selectedCategoryForSub = trendItem
        }

        if (trendDimension != TrendDimension.TOTAL) {
            if (trendItem == null || !availableItems.contains(trendItem)) {
                trendItem = availableItems.firstOrNull()
            }
        } else {
            trendItem = null
        }
    }

    val filteredExpenses = remember(expenses, trendDimension, trendItem) {
        val nonDraftExpenses = expenses.filter { !it.isDraft }
        when (trendDimension) {
            TrendDimension.TOTAL -> nonDraftExpenses
            TrendDimension.CATEGORY -> nonDraftExpenses.filter { it.category == trendItem }
            TrendDimension.SUBCATEGORY -> nonDraftExpenses.filter { it.subcategory == trendItem }
            TrendDimension.LABEL -> if (trendItem != null) nonDraftExpenses.filter { it.labels.contains(trendItem) } else nonDraftExpenses
        }
    }

    val draftCount = remember(expenses) { expenses.count { it.isDraft } }

    // Helper functions for time period logic
    fun getDateRangeForPeriod(period: String): Pair<LocalDate, LocalDate> {
        val today = LocalDate.now()
        return when (period) {
            "This Week" -> today.minusDays((today.dayOfWeek.value % 7).toLong()) to today.plusDays((6 - today.dayOfWeek.value % 7).toLong())
            "Last Week" -> today.minusDays(((today.dayOfWeek.value % 7) + 7).toLong()) to today.minusDays((today.dayOfWeek.value % 7 + 1).toLong())
            "Selected Month" -> trendsMonth.atDay(1) to trendsMonth.atEndOfMonth()
            "This Quarter" -> {
                val quarter = (trendsMonth.monthValue - 1) / 3 + 1
                val startMonth = (quarter - 1) * 3 + 1
                trendsMonth.withMonth(startMonth).atDay(1) to trendsMonth.withMonth(startMonth + 2).atEndOfMonth()
            }
            "Last Quarter" -> {
                val quarterViewed = trendsMonth.minusMonths(3)
                val quarter = (quarterViewed.monthValue - 1) / 3 + 1
                val startMonth = (quarter - 1) * 3 + 1
                quarterViewed.withMonth(startMonth).atDay(1) to quarterViewed.withMonth(startMonth + 2).atEndOfMonth()
            }
            "This Year" -> trendsMonth.withMonth(1).atDay(1) to trendsMonth.withMonth(12).atEndOfMonth()
            "Last Year" -> trendsMonth.minusYears(1).withMonth(1).atDay(1) to trendsMonth.minusYears(1).withMonth(12).atEndOfMonth()
            "Custom" -> (customStartDate ?: today) to (customEndDate ?: today)
            else -> trendsMonth.atDay(1) to trendsMonth.atEndOfMonth()
        }
    }

    // Generate chart data based on time period
    fun generateChartData(expenses: List<Expense>, period: String): List<ChartPoint> {
        val today = LocalDate.now()
        return when (period) {
            "This Week" -> {
                val startOfWeek = today.minusDays((today.dayOfWeek.value % 7).toLong())
                (0..6).map { dayOffset ->
                    val date = startOfWeek.plusDays(dayOffset.toLong())
                    val total = expenses.filter { it.date == date }.sumOf { it.amount }
                    ChartPoint(
                        label = date.dayOfWeek.name.take(3).uppercase(),
                        value = total.toFloat(),
                        period = date
                    )
                }
            }
            "Last Week" -> {
                val startOfLastWeek = today.minusDays(((today.dayOfWeek.value % 7) + 7).toLong())
                (0..6).map { dayOffset ->
                    val date = startOfLastWeek.plusDays(dayOffset.toLong())
                    val total = expenses.filter { it.date == date }.sumOf { it.amount }
                    ChartPoint(
                        label = date.dayOfWeek.name.take(3).uppercase(),
                        value = total.toFloat(),
                        period = date
                    )
                }
            }
            "Selected Month" -> {
                val startOfMonth = trendsMonth.atDay(1)
                val endOfMonth = trendsMonth.atEndOfMonth()
                val weeks = mutableListOf<Triple<LocalDate, LocalDate, String>>()
                var currentStart = startOfMonth
                var weekIdx = 1
                while (!currentStart.isAfter(endOfMonth)) {
                    val daysToSaturday = when (currentStart.dayOfWeek) {
                        java.time.DayOfWeek.SUNDAY -> 6
                        java.time.DayOfWeek.MONDAY -> 5
                        java.time.DayOfWeek.TUESDAY -> 4
                        java.time.DayOfWeek.WEDNESDAY -> 3
                        java.time.DayOfWeek.THURSDAY -> 2
                        java.time.DayOfWeek.FRIDAY -> 1
                        java.time.DayOfWeek.SATURDAY -> 0
                    }
                    val weekEnd = currentStart.plusDays(daysToSaturday.toLong()).coerceAtMost(endOfMonth)
                    weeks.add(Triple(currentStart, weekEnd, "W$weekIdx"))
                    currentStart = weekEnd.plusDays(1)
                    weekIdx++
                }
                
                weeks.map { (start, end, label) ->
                    val total = expenses.filter { !it.date.isBefore(start) && !it.date.isAfter(end) }.sumOf { it.amount }
                    ChartPoint(label = label, value = total.toFloat(), period = start, endDate = end)
                }
            }
            "This Quarter" -> {
                val quarter = (trendsMonth.monthValue - 1) / 3 + 1
                val startMonth = (quarter - 1) * 3 + 1
                (0..2).map { monthOffset ->
                    val targetMonth = trendsMonth.withMonth(startMonth + monthOffset)
                    val total = expenses.filter { YearMonth.from(it.date) == targetMonth }.sumOf { it.amount }
                    ChartPoint(
                        label = targetMonth.month.name.take(3).uppercase(),
                        value = total.toFloat(),
                        period = targetMonth
                    )
                }
            }
            "Last Quarter" -> {
                val quarterViewed = trendsMonth.minusMonths(3)
                val quarter = (quarterViewed.monthValue - 1) / 3 + 1
                val startMonth = (quarter - 1) * 3 + 1
                (0..2).map { monthOffset ->
                    val targetMonth = quarterViewed.withMonth(startMonth + monthOffset)
                    val total = expenses.filter { YearMonth.from(it.date) == targetMonth }.sumOf { it.amount }
                    ChartPoint(
                        label = targetMonth.month.name.take(3).uppercase(),
                        value = total.toFloat(),
                        period = targetMonth
                    )
                }
            }
            "This Year" -> {
                (1..12).map { month ->
                    val targetMonth = trendsMonth.withMonth(month)
                    val total = expenses.filter { YearMonth.from(it.date) == targetMonth }.sumOf { it.amount }
                    ChartPoint(
                        label = targetMonth.month.name.take(3).uppercase(),
                        value = total.toFloat(),
                        period = targetMonth
                    )
                }
            }
            "Last Year" -> {
                val lastYear = trendsMonth.minusYears(1)
                (1..12).map { month ->
                    val targetMonth = lastYear.withMonth(month)
                    val total = expenses.filter { YearMonth.from(it.date) == targetMonth }.sumOf { it.amount }
                    ChartPoint(
                        label = targetMonth.month.name.take(3).uppercase(),
                        value = total.toFloat(),
                        period = targetMonth
                    )
                }
            }
            "Custom" -> {
                val startDate = customStartDate ?: today
                val endDate = customEndDate ?: today
                val daysBetween = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate).toInt()
                
                when {
                    daysBetween <= 7 -> {
                        // One bar per day
                        (0..daysBetween).map { dayOffset ->
                            val date = startDate.plusDays(dayOffset.toLong())
                            val total = expenses.filter { it.date == date }.sumOf { it.amount }
                            ChartPoint(
                                label = date.dayOfMonth.toString(),
                                value = total.toFloat(),
                                period = date
                            )
                        }
                    }
                    daysBetween < 30 -> {
                        // One bar per week
                        val weeks = (daysBetween / 7) + 1
                        (0 until weeks).map { weekOffset ->
                            val weekStart = startDate.plusDays((weekOffset * 7).toLong())
                            val weekEnd = weekStart.plusDays(6).coerceAtMost(endDate)
                            val total = expenses.filter { !it.date.isBefore(weekStart) && !it.date.isAfter(weekEnd) }.sumOf { it.amount }
                            ChartPoint(
                                label = "W${weekOffset + 1}",
                                value = total.toFloat(),
                                period = weekStart,
                                endDate = weekEnd
                            )
                        }
                    }
                    else -> {
                        // One bar per month
                        val monthsBetween = ((endDate.year - startDate.year) * 12 + (endDate.monthValue - startDate.monthValue)) + 1
                        (0 until monthsBetween).map { monthOffset ->
                            val targetMonth = YearMonth.from(startDate).plusMonths(monthOffset.toLong())
                            val monthStart = targetMonth.atDay(1)
                            val monthEnd = targetMonth.atEndOfMonth()
                            val total = expenses.filter { !it.date.isBefore(monthStart) && !it.date.isAfter(monthEnd) }.sumOf { it.amount }
                            ChartPoint(
                                label = targetMonth.month.name.take(3).uppercase(),
                                value = total.toFloat(),
                                period = targetMonth
                            )
                        }
                    }
                }
            }
            else -> {
                val currentMonth = YearMonth.now()
                (0..11).map { i ->
                    val targetMonth = currentMonth.minusMonths(i.toLong())
                    val total = expenses.filter { YearMonth.from(it.date) == targetMonth }.sumOf { it.amount }
                    ChartPoint(
                        label = targetMonth.month.name.take(3).uppercase(),
                        value = total.toFloat(),
                        period = targetMonth
                    )
                }.reversed()
            }
        }
    }

    // Get filtered expenses based on time period and filters
    val trendTimeFilteredExpenses = remember(expenses, selectedTimePeriod, customStartDate, customEndDate, trendsMonth) {
        val (startDate, endDate) = getDateRangeForPeriod(selectedTimePeriod)
        expenses.filter { expense ->
            !expense.date.isBefore(startDate) && !expense.date.isAfter(endDate)
        }
    }

    val trendFilteredExpenses = remember(trendTimeFilteredExpenses, selectedCategories, selectedSubcategories, selectedLabels) {
        trendTimeFilteredExpenses.filter { expense ->
            val categoryMatch = selectedCategories.isEmpty() || expense.category in selectedCategories
            val subcategoryMatch = selectedSubcategories.isEmpty() || expense.subcategory in selectedSubcategories
            val labelMatch = selectedLabels.isEmpty() || expense.labels.any { it in selectedLabels }
            categoryMatch && subcategoryMatch && labelMatch
        }
    }

    val monthlyExpenses = remember(expenses, viewedMonth) {
        expenses.filter { YearMonth.from(it.date) == viewedMonth }
    }

    val expensesMap = remember(expenses) {
        expenses
            .filter { !it.isDraft }
            .groupBy { it.date }
            .mapValues { entry -> entry.value.sumOf { it.amount } }
    }
    val totalSpent = remember(monthlyExpenses) { monthlyExpenses.sumOf { it.amount } }
    val dateFormatter = java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy", java.util.Locale.getDefault())

    val themeBlack = MaterialTheme.colorScheme.onSurface
    val themeWhite = MaterialTheme.colorScheme.surface
    val themeDivider = MaterialTheme.colorScheme.tertiary

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(16.dp)
            .background(themeWhite),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "DASHBOARD",
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    IconButton(
                        onClick = onNavigateToDrafts,
                        modifier = Modifier
                            .border(2.dp, MaterialTheme.colorScheme.onBackground, RectangleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Drafts",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    if (draftCount > 0) {
                        Text(
                            text = draftCount.toString(),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                                .align(Alignment.TopEnd)
                        )
                    }
                }
                IconButton(
                    onClick = { onNewExpense(LocalDate.now()) },
                    modifier = Modifier
                        .border(2.dp, MaterialTheme.colorScheme.onBackground, RectangleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Expense",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                IconButton(
                    onClick = onThemeToggle,
                    modifier = Modifier
                        .border(2.dp, MaterialTheme.colorScheme.onBackground, RectangleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .size(40.dp)
                ) {
                    Text(
                        text = if (isDarkTheme) "☀" else "☽",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }

        // Mobile portrait mode: vertical layout according to MobilePortrait.md specs
        // Calendar at top
            BrutalistCalendar(
                expenses = expensesMap,
                modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                viewedMonth = viewedMonth,
                onMonthChanged = { viewedMonth = it },
                onViewExpensesForDate = onNavigateToExpenses,
                onNewExpenseForDate = onNewExpense
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Expense summary cards below calendar
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BrutalistCard(
                    modifier = Modifier.weight(1f).height(64.dp),
                    backgroundColor = themeWhite
                ) {
                    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalArrangement = Arrangement.Center) {
                        Text("BUDGET", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = themeBlack)
                        Text("₹${String.format("%.0f", budget)}", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = themeBlack)
                    }
                }
                BrutalistCard(
                    modifier = Modifier.weight(1f).height(64.dp),
                    backgroundColor = themeBlack
                ) {
                    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalArrangement = Arrangement.Center) {
                        Text("BALANCE", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = themeWhite)
                        Text("₹${String.format("%.0f", budget - totalSpent)}", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = themeWhite)
                    }
                }
                BrutalistCard(
                    modifier = Modifier.weight(1f).height(64.dp).clickable { onNavigateToMonthExpenses(viewedMonth, TrendDimension.TOTAL, null) },
                    backgroundColor = themeWhite
                ) {
                    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalArrangement = Arrangement.Center) {
                        Text("SPENT", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = themeBlack)
                        Text("₹${String.format("%.0f", totalSpent)}", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = themeBlack)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Enhanced Trends section with time period selector and filters
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("TRENDS", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = themeBlack)
                    
                    // Month Switcher for Trends
                    val monthName = trendsMonth.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault()).uppercase()
                    val year = trendsMonth.year
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .border(2.dp, themeBlack)
                            .background(themeWhite)
                            .padding(2.dp)
                    ) {
                        Box(
                            modifier = Modifier.size(28.dp).background(themeBlack).clickable {
                                trendsMonth = trendsMonth.minusMonths(1)
                                selectedTimePeriod = "Selected Month"
                            },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(androidx.compose.material.icons.Icons.Default.KeyboardArrowLeft, contentDescription = "Prev", tint = themeWhite, modifier = Modifier.size(20.dp))
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(min = 84.dp).padding(horizontal = 8.dp)) {
                            Text(text = monthName, fontSize = 12.sp, fontWeight = FontWeight.Black, lineHeight = 12.sp, color = themeBlack)
                            Text(text = year.toString(), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = themeBlack.copy(alpha = 0.5f), lineHeight = 9.sp)
                        }
                        Box(
                            modifier = Modifier.size(28.dp).background(themeBlack).clickable {
                                trendsMonth = trendsMonth.plusMonths(1)
                                selectedTimePeriod = "Selected Month"
                            },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(androidx.compose.material.icons.Icons.Default.KeyboardArrowRight, contentDescription = "Next", tint = themeWhite, modifier = Modifier.size(20.dp))
                        }
                    }
                }
                
                // Time Period Selector
                val timePeriods = listOf("Selected Month", "This Week", "Last Week", "This Quarter", "Last Quarter", "This Year", "Last Year", "Custom")
                var expandedTimePeriod by remember { mutableStateOf(false) }
                
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f).height(38.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight()
                                .background(themeWhite)
                                .border(1.dp, themeBlack)
                                .clickable { expandedTimePeriod = true }
                                .padding(horizontal = 8.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = selectedTimePeriod.uppercase(),
                                fontSize = 14.sp,
                                color = themeBlack,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        DropdownMenu(
                            expanded = expandedTimePeriod,
                            onDismissRequest = { expandedTimePeriod = false },
                            modifier = Modifier.background(themeWhite).border(1.dp, themeBlack)
                        ) {
                            timePeriods.forEach { period ->
                                DropdownMenuItem(
                                    text = { Text(period.uppercase(), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = themeBlack) },
                                    onClick = {
                                        selectedTimePeriod = period
                                        expandedTimePeriod = false
                                        if (period == "Custom") {
                                            showCustomDateRangePicker = true
                                        }
                                    }
                                )
                            }
                        }
                    }
                    
                    // Filter Toggle Button
                    Box(
                        modifier = Modifier
                            .height(38.dp)
                            .background(if (showFilterOptions) themeBlack else themeWhite)
                            .border(1.dp, themeBlack)
                            .clickable { showFilterOptions = !showFilterOptions }
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "FILTER",
                            color = if (showFilterOptions) themeWhite else themeBlack,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                    
                    // Clear Filters Button
                    if (showFilterOptions && (selectedCategories.isNotEmpty() || selectedSubcategories.isNotEmpty() || selectedLabels.isNotEmpty())) {
                        Box(
                            modifier = Modifier
                                .height(38.dp)
                                .background(themeWhite)
                                .border(1.dp, themeBlack)
                                .clickable {
                                    selectedCategories = emptySet()
                                    selectedSubcategories = emptySet()
                                    selectedLabels = emptySet()
                                    selectedTimePeriod = "Selected Month"
                                }
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "CLEAR",
                                color = themeBlack,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }
                
                // Custom Date Range Picker Dialog
                if (showCustomDateRangePicker) {
                    BrutalistDateRangePickerDialog(
                        onDismissRequest = { showCustomDateRangePicker = false },
                        onDateRangeSelected = { startMillis, endMillis ->
                            customStartDate = startMillis?.let { java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate() }
                            customEndDate = endMillis?.let { java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate() }
                            showCustomDateRangePicker = false
                        }
                    )
                }
                
                // Filter Options
                if (showFilterOptions) {
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Categories Filter
                    if (categories.isNotEmpty()) {
                        var expandedCategories by remember { mutableStateOf(false) }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("CATEGORIES:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = themeBlack)
                            Box(modifier = Modifier.weight(1f).height(56.dp)) {
                                BrutalistMultiSelectDropdown(
                                    label = "",
                                    options = categories,
                                    selectedOptions = selectedCategories,
                                    onOptionToggled = { option ->
                                        val newSet = selectedCategories.toMutableSet()
                                        if (option in newSet) {
                                            newSet.remove(option)
                                        } else {
                                            newSet.add(option)
                                        }
                                        selectedCategories = newSet
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                    
                    // Subcategories Filter (only show if categories are selected)
                    if (selectedCategories.isNotEmpty()) {
                        val availableSubcategories = selectedCategories.flatMap { cat ->
                            subcategoriesMap[cat] ?: emptyList()
                        }.distinct()
                        
                        if (availableSubcategories.isNotEmpty()) {
                            var expandedSubcategories by remember { mutableStateOf(false) }
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("SUBCATEGORIES:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = themeBlack)
                                Box(modifier = Modifier.weight(1f).height(56.dp)) {
                                    BrutalistMultiSelectDropdown(
                                        label = "",
                                        options = availableSubcategories,
                                        selectedOptions = selectedSubcategories,
                                        onOptionToggled = { option ->
                                            val newSet = selectedSubcategories.toMutableSet()
                                            if (option in newSet) {
                                                newSet.remove(option)
                                            } else {
                                                newSet.add(option)
                                            }
                                            selectedSubcategories = newSet
                                        },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }
                    }
                    
                    // Labels Filter
                    if (labels.isNotEmpty()) {
                        var expandedLabels by remember { mutableStateOf(false) }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("LABELS:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = themeBlack)
                            Box(modifier = Modifier.weight(1f).height(56.dp)) {
                                BrutalistMultiSelectDropdown(
                                    label = "",
                                    options = labels,
                                    selectedOptions = selectedLabels,
                                    onOptionToggled = { option ->
                                        val newSet = selectedLabels.toMutableSet()
                                        if (option in newSet) {
                                            newSet.remove(option)
                                        } else {
                                            newSet.add(option)
                                        }
                                        selectedLabels = newSet
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Summary Card
                val totalAmount = trendFilteredExpenses.sumOf { it.amount }
                val transactionCount = trendFilteredExpenses.size
                
                BrutalistCard(
                    modifier = Modifier.fillMaxWidth().height(80.dp),
                    backgroundColor = themeBlack
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("TOTAL SPENT", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = themeWhite)
                            Text("₹${String.format("%.0f", totalAmount)}", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = themeWhite)
                        }
                        Column(
                            horizontalAlignment = Alignment.End
                        ) {
                            Text("TRANSACTIONS", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = themeWhite)
                            Text("$transactionCount", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = themeWhite)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Trend Chart with filtered data
                val trendChartData = remember(trendFilteredExpenses, selectedTimePeriod, customStartDate, customEndDate) {
                    generateChartData(trendFilteredExpenses, selectedTimePeriod)
                }
                BrutalistBarChartWithData(
                    chartData = trendChartData,
                    modifier = Modifier.fillMaxWidth().height(220.dp),
                    onValueSelected = { value, period ->
                        selectedTrendValue = value
                        selectedTrendPeriod = period
                    },
                    onBarClicked = { point ->
                        selectedTrendPointForExpenses = point
                        showTrendExpensesDialog = true
                    }
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Horizontal Bar Chart for Category Breakdown
                Text("CATEGORY BREAKDOWN", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = themeBlack)
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (selectedCategoryForHorizontalChart != null) "BACK TO CATEGORIES" else "SELECT A CATEGORY TO VIEW SUBCATEGORIES",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = themeBlack,
                        modifier = Modifier.weight(1f)
                    )
                    if (selectedCategoryForHorizontalChart != null) {
                        Box(
                            modifier = Modifier
                                .height(32.dp)
                                .background(themeWhite)
                                .border(1.dp, themeBlack)
                                .clickable { selectedCategoryForHorizontalChart = null }
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "BACK",
                                color = themeBlack,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }
                
                HorizontalBarChart(
                    expenses = trendTimeFilteredExpenses,
                    categories = categories,
                    subcategoriesMap = subcategoriesMap,
                    selectedCategory = selectedCategoryForHorizontalChart,
                    onCategorySelected = { category ->
                        selectedCategoryForHorizontalChart = if (category == selectedCategoryForHorizontalChart) null else category
                    },
                    onBarLongPressed = { category, subcategory ->
                        selectedCategoryForExpenses = category
                        selectedSubcategoryForExpenses = subcategory
                        showExpensesDialog = true
                    },
                    modifier = Modifier.fillMaxWidth().height(300.dp),
                    barColor = themeBlack,
                    labelColor = themeBlack
                )
                
                // Show Expenses Confirmation Dialog
                if (showExpensesDialog) {
                    AlertDialog(
                        onDismissRequest = { showExpensesDialog = false },
                        title = { Text("Show Expenses?", fontWeight = FontWeight.Bold) },
                        text = {
                            val itemText = if (selectedSubcategoryForExpenses != null) {
                                "$selectedCategoryForExpenses / $selectedSubcategoryForExpenses"
                            } else {
                                selectedCategoryForExpenses ?: ""
                            }
                            Text("View expenses for $itemText?")
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showExpensesDialog = false
                                    val (startDate, endDate) = getDateRangeForPeriod(selectedTimePeriod)
                                    onNavigateToFilteredExpenses(
                                        startDate,
                                        endDate,
                                        if (selectedCategoryForExpenses != null) setOf(selectedCategoryForExpenses!!) else emptySet(),
                                        if (selectedSubcategoryForExpenses != null) setOf(selectedSubcategoryForExpenses!!) else emptySet(),
                                        selectedLabels
                                    )
                                }
                            ) {
                                Text("SHOW", fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            Button(
                                onClick = { showExpensesDialog = false }
                            ) {
                                Text("CANCEL", fontWeight = FontWeight.Bold)
                            }
                        }
                    )
                }

                // Show Trend Expenses Confirmation Dialog
                if (showTrendExpensesDialog && selectedTrendPointForExpenses != null) {
                    val point = selectedTrendPointForExpenses!!
                    AlertDialog(
                        onDismissRequest = { showTrendExpensesDialog = false },
                        title = { Text("Show Expenses?", fontWeight = FontWeight.Bold) },
                        text = {
                            val periodText = when (val p = point.period) {
                                is LocalDate -> {
                                    if (point.endDate != null && point.endDate is LocalDate) {
                                        val start = p
                                        val end = point.endDate as LocalDate
                                        "${start.format(java.time.format.DateTimeFormatter.ofPattern("dd MMM"))} - ${end.format(java.time.format.DateTimeFormatter.ofPattern("dd MMM"))}"
                                    } else {
                                        p.format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy"))
                                    }
                                }
                                is YearMonth -> p.format(java.time.format.DateTimeFormatter.ofPattern("MMM yyyy"))
                                else -> point.label
                            }
                            Text("View expenses for $periodText?")
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showTrendExpensesDialog = false
                                    val (startDate, endDate) = when (val p = point.period) {
                                        is LocalDate -> {
                                            if (point.endDate != null && point.endDate is LocalDate) {
                                                p to (point.endDate as LocalDate)
                                            } else {
                                                p to p
                                            }
                                        }
                                        is YearMonth -> p.atDay(1) to p.atEndOfMonth()
                                        else -> getDateRangeForPeriod(selectedTimePeriod)
                                    }
                                    
                                    onNavigateToFilteredExpenses(
                                        startDate,
                                        endDate,
                                        selectedCategories,
                                        selectedSubcategories,
                                        selectedLabels
                                    )
                                }
                            ) {
                                Text("SHOW", fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            Button(
                                onClick = { showTrendExpensesDialog = false }
                            ) {
                                Text("CANCEL", fontWeight = FontWeight.Bold)
                            }
                        }
                    )
                }
            }
    }
}

@Composable
fun ExpenseListScreen(
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
    val themeBlack = MaterialTheme.colorScheme.onSurface
    val themeWhite = MaterialTheme.colorScheme.surface
    val themeDivider = MaterialTheme.colorScheme.tertiary

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

    // Sync filters if initial state changes from external navigation
    LaunchedEffect(viewingDate, initialStartDate, initialEndDate, initialCategories, initialSubcategories, initialLabels, initialSelectedExpenseId, initialSelectedGroupId) {
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
                // Clear filters to ensure the expense is in the filtered list
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
                // Clear filters to ensure the group is in the filtered list
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

    val filteredExpenses = expenses.filter { expense ->
        val matchesDate = when {
            viewingDate != null -> expense.date == viewingDate
            exactDateFilter != null -> expense.date == exactDateFilter
            startDateFilter != null && endDateFilter != null -> {
                !expense.date.isBefore(startDateFilter) && !expense.date.isAfter(endDateFilter)
            }
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
    }.sortedByDescending { it.date }

    val groupedExpenses = displayExpenses
        .groupBy { it.groupId }
        .values
        .sortedByDescending { it.first().date }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp)
    ) {
        // Mobile portrait mode: re-arrange according to MobilePortrait.md specs
        if (selectedGroup != null || selectedExpense != null) {
                // MOBILE DETAIL VIEW
                Column(modifier = Modifier.fillMaxSize()) {
                    BrutalistButton(
                        onClick = {
                            selectedGroup = null
                            selectedExpense = null
                        },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        containerColor = themeBlack
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = themeWhite)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("BACK TO LIST", color = themeWhite, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (selectedGroup != null) {
                        val group = selectedGroup!!
                        BrutalistCard(modifier = Modifier.fillMaxWidth().weight(1f)) {
                            Column(modifier = Modifier.padding(12.dp).verticalScroll(rememberScrollState())) {
                                // Store name + total on same row
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(group.first().date.format(dateFormatter).uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = themeBlack.copy(alpha=0.5f))
                                        Text(group.first().storeName, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = themeBlack, maxLines = 2)
                                    }
                                    val total = group.sumOf { it.amount }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("TOTAL", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = themeBlack.copy(alpha=0.5f))
                                        Text("₹${String.format("%.0f", total)}", fontSize = 22.sp, fontWeight = FontWeight.Black, color = themeBlack)
                                    }
                                }

                                // Location tag (inline, compact)
                                if (group.first().location.isNotBlank()) {
                                    Text(
                                        text = group.first().location.uppercase(),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = themeBlack.copy(alpha = 0.6f),
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    )
                                }

                                // Payment Mode + Paid Via in a single row
                                if (!group.first().paymentMode.isNullOrBlank() || !group.first().paidVia.isNullOrBlank()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        if (!group.first().paymentMode.isNullOrBlank()) {
                                            Column {
                                                Text("PAYMENT", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = themeBlack.copy(alpha=0.5f))
                                                Text(group.first().paymentMode, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = themeBlack)
                                            }
                                        }
                                        if (!group.first().paidVia.isNullOrBlank()) {
                                            Column {
                                                Text("PAID VIA", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = themeBlack.copy(alpha=0.5f))
                                                Text(group.first().paidVia, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = themeBlack)
                                            }
                                        }
                                    }
                                }
                                
                                // Divider before items
                                Spacer(modifier = Modifier.height(2.dp))
                                Box(modifier = Modifier.fillMaxWidth().height(2.dp).background(themeBlack))
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("ITEMS (${group.size})", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = themeBlack.copy(alpha=0.5f), modifier = Modifier.padding(bottom = 4.dp))
                                
                                group.forEachIndexed { i, subTx ->
                                    BrutalistCard(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp), backgroundColor = themeWhite) {
                                        Column(modifier = Modifier.padding(8.dp)) {
                                            // Row 1: Item number + description + amount
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                val desc = if (subTx.itemDescription.isNotEmpty()) subTx.itemDescription else subTx.category
                                                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                                    Text("${i+1}.", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, color = themeBlack.copy(alpha=0.5f), modifier = Modifier.padding(end = 4.dp))
                                                    Text(desc, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = themeBlack, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                                }
                                                Text("₹${String.format("%.2f", subTx.amount)}", fontWeight = FontWeight.Black, fontSize = 14.sp, color = themeBlack, modifier = Modifier.padding(start = 8.dp))
                                            }
                                            
                                            // Row 2: Category + quantity (compact secondary info)
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("${subTx.category} / ${subTx.subcategory}", fontSize = 10.sp, fontWeight = FontWeight.Medium, color = themeBlack.copy(alpha=0.6f))
                                                if (subTx.quantity != null && subTx.quantity > 0) {
                                                    val quantityText = if (subTx.unit != null) "${subTx.quantity} ${subTx.unit}" else subTx.quantity.toString()
                                                    val unitRate = subTx.amount / subTx.quantity
                                                    val rateText = if (subTx.unit != null) " • ₹${String.format("%.2f", unitRate)}/${subTx.unit}" else ""
                                                    Text(quantityText + rateText, fontSize = 10.sp, color = themeBlack.copy(alpha=0.6f))
                                                }
                                            }
                                            
                                            // Labels (compact, only if present)
                                            if (subTx.labels.isNotEmpty()) {
                                                @Suppress("DEPRECATION")
                                                Text(subTx.labels.joinToString(", ").uppercase(), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = themeBlack.copy(alpha=0.5f), modifier = Modifier.padding(top = 2.dp))
                                            }
                                            
                                            // Notes (compact, only if present)
                                            if (subTx.notes.isNotBlank()) {
                                                Text(subTx.notes, fontSize = 10.sp, color = themeBlack.copy(alpha=0.7f), modifier = Modifier.padding(top = 2.dp), maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                            }
                                            
                                            // Action buttons (smaller)
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                BrutalistButton(
                                                    onClick = { onEditExpense(subTx) },
                                                    modifier = Modifier.weight(1f).height(28.dp),
                                                    containerColor = themeBlack,
                                                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp)
                                                ) {
                                                    Text("EDIT", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = themeWhite)
                                                }
                                                BrutalistButton(
                                                    onClick = {
                                                        android.util.Log.d("ExpenseListScreen", "DELETE button clicked for expense: ${subTx.id}")
                                                        expenseToDelete = subTx
                                                    },
                                                    modifier = Modifier.weight(1f).height(28.dp),
                                                    containerColor = themeWhite,
                                                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp)
                                                ) {
                                                    Text("DELETE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = themeBlack)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else if (selectedExpense != null) {
                        val expense = selectedExpense!!
                        BrutalistCard(modifier = Modifier.fillMaxWidth().weight(1f)) {
                            Column(modifier = Modifier.padding(12.dp).verticalScroll(rememberScrollState())) {
                                // Row 1: Item description + amount
                                val title = if (expense.itemDescription.isNotEmpty()) expense.itemDescription else expense.storeName
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("ITEM", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = themeBlack.copy(alpha=0.5f))
                                        Text(title.ifEmpty { "No description" }, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = themeBlack, maxLines = 2)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("AMOUNT", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = themeBlack.copy(alpha=0.5f))
                                        Text("₹${String.format("%.2f", expense.amount)}", fontSize = 22.sp, fontWeight = FontWeight.Black, color = themeBlack)
                                    }
                                }

                                // Row 2: Store + Date
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("STORE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = themeBlack.copy(alpha=0.5f))
                                        Text(expense.storeName, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = themeBlack)
                                    }
                                    Column {
                                        Text("DATE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = themeBlack.copy(alpha=0.5f))
                                        Text(expense.date.format(dateFormatter).uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = themeBlack)
                                    }
                                }
                                
                                // Location (inline, compact)
                                if (expense.location.isNotBlank()) {
                                    Text(
                                        text = expense.location.uppercase(),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = themeBlack.copy(alpha = 0.6f),
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    )
                                }

                                // Row 3: Category + Quantity
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("CATEGORY", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = themeBlack.copy(alpha=0.5f))
                                        Text("${expense.category} / ${expense.subcategory}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = themeBlack)
                                    }
                                    if (expense.quantity != null && expense.quantity > 0) {
                                        Column {
                                            Text("QTY", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = themeBlack.copy(alpha=0.5f))
                                            val quantityText = if (expense.unit != null) "${expense.quantity} ${expense.unit}" else expense.quantity.toString()
                                            Text(quantityText, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = themeBlack)
                                        }
                                        Column {
                                            Text("RATE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = themeBlack.copy(alpha=0.5f))
                                            val unitRate = expense.amount / expense.quantity
                                            val rateText = if (expense.unit != null) "₹${String.format("%.2f", unitRate)}/${expense.unit}" else "₹${String.format("%.2f", unitRate)}/unit"
                                            Text(rateText, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = themeBlack)
                                        }
                                    }
                                }

                                // Row 4: Payment Mode + Paid Via (side by side)
                                if (!expense.paymentMode.isNullOrBlank() || !expense.paidVia.isNullOrBlank()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        if (!expense.paymentMode.isNullOrBlank()) {
                                            Column {
                                                Text("PAYMENT", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = themeBlack.copy(alpha=0.5f))
                                                Text(expense.paymentMode, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = themeBlack)
                                            }
                                        }
                                        if (!expense.paidVia.isNullOrBlank()) {
                                            Column {
                                                Text("PAID VIA", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = themeBlack.copy(alpha=0.5f))
                                                Text(expense.paidVia, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = themeBlack)
                                            }
                                        }
                                    }
                                }

                                // Labels (compact)
                                if (expense.labels.isNotEmpty()) {
                                    Text("LABELS", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = themeBlack.copy(alpha=0.5f))
                                    @Suppress("DEPRECATION")
                                    Text(expense.labels.joinToString(", ").uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = themeBlack, modifier = Modifier.padding(bottom = 4.dp))
                                }

                                // Notes (compact)
                                if (!expense.notes.isNullOrBlank()) {
                                    Text("NOTES", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = themeBlack.copy(alpha=0.5f))
                                    Text(expense.notes ?: "", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = themeBlack, modifier = Modifier.padding(bottom = 4.dp))
                                }

                                // Divider + action buttons
                                Spacer(modifier = Modifier.height(8.dp))
                                Box(modifier = Modifier.fillMaxWidth().height(2.dp).background(themeBlack))
                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    BrutalistButton(
                                        onClick = { onEditExpense(expense) },
                                        modifier = Modifier.weight(1f).height(40.dp),
                                        containerColor = themeWhite
                                    ) {
                                        Text("EDIT", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = themeBlack)
                                    }
                                    BrutalistButton(
                                        onClick = {
                                            android.util.Log.d("ExpenseListScreen", "DELETE button clicked for expense: ${expense.id}")
                                            expenseToDelete = expense
                                        },
                                        modifier = Modifier.weight(1f).height(40.dp),
                                        containerColor = themeBlack
                                    ) {
                                        @Suppress("DEPRECATION")
                                        Text("DELETE", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = themeWhite)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // MOBILE LIST VIEW
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // MOVE ALL HEADERS INTO LazyColumn as items if mobile
                    item {
                        // Title at top
                        val titleText = when {
                            showOnlyDrafts -> "DRAFTS"
                            viewingDate != null -> "EXPENSES FOR ${viewingDate.format(dateFormatter).uppercase()}"
                            initialSubcategories.size == 1 -> "EXPENSES: ${initialSubcategories.first().uppercase()}"
                            initialSubcategories.size > 1 -> "EXPENSES: MULTI"
                            initialCategories.size == 1 -> "EXPENSES: ${initialCategories.first().uppercase()}"
                            initialCategories.size > 1 -> "EXPENSES: MULTI"
                            else -> "EXPENSES"
                        }
                        Text(
                            text = titleText,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                    }

                    item {
                        // Search and add button at top
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (viewingDate != null || initialCategories.isNotEmpty() || initialSubcategories.isNotEmpty() || initialLabels.isNotEmpty()) {
                                BrutalistButton(onClick = onClearFilter, containerColor = themeWhite, modifier = Modifier.height(48.dp)) {
                                    Text("CLEAR", color = themeBlack, fontWeight = FontWeight.Black, fontSize = 12.sp)
                                }
                            }
                            BrutalistSearchField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = "SEARCH...",
                                modifier = Modifier.weight(1f).height(48.dp)
                            )
                            BrutalistButton(onClick = onAddExpense, modifier = Modifier.height(48.dp)) {
                                Icon(Icons.Default.Add, contentDescription = "Add Expense", tint = themeWhite)
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(16.dp)) }

                    item {
                        // Combined date filter button
                        val dateFilterText = when {
                            exactDateFilter != null -> exactDateFilter!!.format(dateFormatter)
                            startDateFilter != null && endDateFilter != null -> "${startDateFilter!!.format(dateFormatter)} - ${endDateFilter!!.format(dateFormatter)}"
                            else -> "DATE FILTER"
                        }
                        BrutalistButton(
                            onClick = { showDateFilterDialog = true },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                            containerColor = if (exactDateFilter != null || startDateFilter != null) themeBlack else themeWhite
                        ) {
                            Text(dateFilterText, color = if (exactDateFilter != null || startDateFilter != null) themeWhite else themeBlack, fontWeight = FontWeight.Bold)
                        }
                    }

                    item {
                        // Filter options below search/add
                        BrutalistButton(
                            onClick = { filtersExpanded = !filtersExpanded },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                            containerColor = if (filtersExpanded) themeBlack else themeWhite
                        ) {
                            Text(if (filtersExpanded) "HIDE FILTERS" else "SHOW FILTERS", color = if (filtersExpanded) themeWhite else themeBlack, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (filtersExpanded) {
                        item {
                            val categories = expenses.map { it.category }.distinct().sorted()
                            val subcats = expenses.map { it.subcategory }.distinct().sorted()
                            val availableLabels = expenses.flatMap { it.labels }.distinct().sorted()
                            val paymentModes = expenses.map { it.paymentMode }.filter { it.isNotBlank() }.distinct().sorted()
                            val paidVias = expenses.map { it.paidVia }.filter { it.isNotBlank() }.distinct().sorted()

                            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                BrutalistMultiSelectDropdown(
                                    label = "Categories",
                                    options = categories,
                                    selectedOptions = filterCategories,
                                    onOptionToggled = {
                                        filterCategories = if (filterCategories.contains(it)) filterCategories - it else filterCategories + it
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                BrutalistMultiSelectDropdown(
                                    label = "Subcategories",
                                    options = subcats,
                                    selectedOptions = filterSubcategories,
                                    onOptionToggled = {
                                        filterSubcategories = if (filterSubcategories.contains(it)) filterSubcategories - it else filterSubcategories + it
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                BrutalistMultiSelectDropdown(
                                    label = "Labels",
                                    options = availableLabels,
                                    selectedOptions = filterLabels,
                                    onOptionToggled = {
                                        filterLabels = if (filterLabels.contains(it)) filterLabels - it else filterLabels + it
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                BrutalistMultiSelectDropdown(
                                    label = "Payment Modes",
                                    options = paymentModes,
                                    selectedOptions = filterPaymentModes,
                                    onOptionToggled = {
                                        filterPaymentModes = if (filterPaymentModes.contains(it)) filterPaymentModes - it else filterPaymentModes + it
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                BrutalistMultiSelectDropdown(
                                    label = "Paid Vias",
                                    options = paidVias,
                                    selectedOptions = filterPaidVia,
                                    onOptionToggled = {
                                        filterPaidVia = if (filterPaidVia.contains(it)) filterPaidVia - it else filterPaidVia + it
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                if (filterCategories.isNotEmpty() || filterSubcategories.isNotEmpty() || filterLabels.isNotEmpty() || filterPaymentModes.isNotEmpty() || filterPaidVia.isNotEmpty() || searchQuery.isNotBlank()) {
                                    BrutalistButton(
                                        onClick = {
                                            filterCategories = emptySet()
                                            filterSubcategories = emptySet()
                                            filterLabels = emptySet()
                                            filterPaymentModes = emptySet()
                                            filterPaidVia = emptySet()
                                            searchQuery = ""
                                            onClearFilter()
                                        },
                                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                        containerColor = themeWhite
                                    ) {
                                        Text("CLEAR FILTERS", color = themeBlack, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }



                    item { Spacer(modifier = Modifier.height(16.dp)) }

                    item {
                        // By store and by item buttons below filters
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            BrutalistButton(
                                onClick = { viewByStore = true },
                                modifier = Modifier.weight(1f),
                                containerColor = if (viewByStore) themeBlack else themeWhite
                            ) {
                                Text("BY STORE", color = if (viewByStore) themeWhite else themeBlack, fontWeight = FontWeight.Bold)
                            }
                            BrutalistButton(
                                onClick = { viewByStore = false },
                                modifier = Modifier.weight(1f),
                                containerColor = if (!viewByStore) themeBlack else themeWhite
                            ) {
                                Text("BY ITEM", color = if (!viewByStore) themeWhite else themeBlack, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(16.dp)) }

                    item {
                        // Filtered total summary
                        val filteredTotal = displayExpenses.sumOf { it.amount }
                        val filteredCount = if (viewByStore) groupedExpenses.size else displayExpenses.size
                        val itemLabel = if (viewByStore) "groups" else "items"

                        BrutalistCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            backgroundColor = themeBlack
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "$filteredCount $itemLabel",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = themeWhite.copy(alpha = 0.8f)
                                )
                                Text(
                                    text = "TOTAL: ₹${String.format("%.2f", filteredTotal)}",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = themeWhite
                                )
                            }
                        }
                    }

                    // Actual list items below
                    if (viewByStore) {
                        items(groupedExpenses) { group ->
                            val groupTotal = group.sumOf { it.amount }
                            val storeName = group.first().storeName
                            
                            BrutalistCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedGroup = group },
                                backgroundColor = themeWhite,
                                borderColor = themeBlack
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(themeBlack)
                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                            Text(storeName.uppercase(), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = themeWhite, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(group.first().date.format(dateFormatter).uppercase(), fontWeight = FontWeight.Medium, fontSize = 11.sp, color = themeWhite.copy(alpha = 0.75f))
                                        }
                                        Text("₹${String.format("%.0f", groupTotal)}", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = themeWhite)
                                    }
                                    
                                    Column(modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 8.dp, top = 4.dp)) {
                                        group.forEach { expense ->
                                            val title = if (expense.itemDescription.isNotEmpty()) expense.itemDescription else expense.storeName
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(title, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = themeBlack)
                                                }
                                                Text("₹${String.format("%.0f", expense.amount)}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = themeBlack)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        items(displayExpenses) { expense ->
                            val title = if (expense.itemDescription.isNotEmpty()) expense.itemDescription else expense.storeName
                            BrutalistCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedExpense = expense },
                                backgroundColor = themeWhite,
                                borderColor = themeBlack
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(themeBlack)
                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                            Text(title.uppercase(), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = themeWhite, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(expense.date.format(dateFormatter).uppercase(), fontWeight = FontWeight.Medium, fontSize = 11.sp, color = themeWhite.copy(alpha = 0.75f))
                                        }
                                        Text("₹${String.format("%.0f", expense.amount)}", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = themeWhite)
                                    }
                                    if (expense.storeName.isNotEmpty() && expense.itemDescription.isNotEmpty()) {
                                        Text(
                                            expense.storeName,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 13.sp,
                                            color = themeBlack.copy(alpha = 0.7f),
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

    // Date Picker Dialogs
    if (showExactDatePicker) {
            BrutalistDatePickerDialog(
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
            BrutalistDateRangePickerDialog(
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
            Dialog(
                onDismissRequest = { showDateFilterDialog = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                BrutalistCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    backgroundColor = themeWhite,
                    borderColor = themeBlack
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("DATE FILTER", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = themeBlack)
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Preset options
                        Text("PRESETS", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = themeBlack.copy(alpha = 0.6f))
                        
                        BrutalistButton(
                            onClick = {
                                val now = LocalDate.now()
                                startDateFilter = now.minusMonths(6)
                                endDateFilter = now
                                exactDateFilter = null
                                showDateFilterDialog = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            containerColor = themeWhite
                        ) {
                            Text("LAST 6 MONTHS", color = themeBlack, fontWeight = FontWeight.Bold)
                        }
                        
                        BrutalistButton(
                            onClick = {
                                val now = LocalDate.now()
                                startDateFilter = now.withDayOfYear(1)
                                endDateFilter = now
                                exactDateFilter = null
                                showDateFilterDialog = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            containerColor = themeWhite
                        ) {
                            Text("THIS YEAR", color = themeBlack, fontWeight = FontWeight.Bold)
                        }
                        
                        BrutalistButton(
                            onClick = {
                                val now = LocalDate.now()
                                startDateFilter = now.minusYears(1)
                                endDateFilter = now
                                exactDateFilter = null
                                showDateFilterDialog = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            containerColor = themeWhite
                        ) {
                            Text("LAST 12 MONTHS", color = themeBlack, fontWeight = FontWeight.Bold)
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text("CUSTOM", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = themeBlack.copy(alpha = 0.6f))
                        
                        BrutalistButton(
                            onClick = {
                                showDateFilterDialog = false
                                showExactDatePicker = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                            containerColor = themeWhite
                        ) {
                            Text("EXACT DATE", color = themeBlack, fontWeight = FontWeight.Bold)
                        }
                        
                        BrutalistButton(
                            onClick = {
                                showDateFilterDialog = false
                                showRangeDatePicker = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                            containerColor = themeWhite
                        ) {
                            Text("CUSTOM RANGE", color = themeBlack, fontWeight = FontWeight.Bold)
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        if (exactDateFilter != null || startDateFilter != null) {
                            BrutalistButton(
                                onClick = {
                                    exactDateFilter = null
                                    startDateFilter = null
                                    endDateFilter = null
                                    showDateFilterDialog = false
                                },
                                modifier = Modifier.fillMaxWidth(),
                                containerColor = themeBlack
                            ) {
                                Text("CLEAR DATE FILTER", color = themeWhite, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

    // Delete Confirmation Dialog (after Column closes)
    if (expenseToDelete != null) {
        AlertDialog(
            onDismissRequest = { expenseToDelete = null },
            title = { Text("CONFIRM DELETE", fontWeight = FontWeight.ExtraBold) },
            text = {
                Text(
                    "Are you sure you want to delete this expense?\n\n" +
                    "Store: ${expenseToDelete!!.storeName}\n" +
                    "Item: ${if (expenseToDelete!!.itemDescription.isNotEmpty()) expenseToDelete!!.itemDescription else expenseToDelete!!.category}\n" +
                    "Amount: ₹${String.format("%.2f", expenseToDelete!!.amount)}"
                )
            },
            confirmButton = {
                BrutalistButton(
                    onClick = {
                        android.util.Log.d("ExpenseListScreen", "Confirming delete for expense: ${expenseToDelete!!.id}")
                        onDeleteExpense(expenseToDelete!!)
                        expenseToDelete = null
                        selectedGroup = null
                        selectedExpense = null
                    },
                    containerColor = themeBlack
                ) {
                    Text("DELETE", color = themeWhite, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                BrutalistButton(
                    onClick = { expenseToDelete = null },
                    containerColor = themeWhite
                ) {
                    Text("CANCEL", color = themeBlack, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

// ── Settings Screen with Three Parallel Columns ──────────────────────────────

@Composable
fun SettingsScreen(
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
    context: Context,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onUploadBackup: () -> Unit,
    onDownloadBackup: (String) -> Unit,
    onViewBackups: () -> Unit,
    isSignedIn: Boolean,
    // Developer mode parameters
    isDeveloperMode: Boolean = false,
    onToggleDevMode: () -> Unit = {},
    onDevModePressStart: () -> Unit = {},
    onDevModePressEnd: () -> Unit = {},
    onPopulateSampleData: () -> Unit = {},
    onClearAllData: () -> Unit = {},
    onExportTemplate: () -> Unit = {},
    onImportCsv: () -> Unit = {},
    // Supabase sync
    onSupabaseSyncNow: () -> Unit = {},
    isSupabaseSyncing: Boolean = false,
    supabaseSyncMessage: String = "",
    supabaseSyncSuccess: Boolean = false,
    isSupabaseConnected: Boolean = false
) {
    // Track which category is selected to show its subcategories
    var selectedCategory by remember { mutableStateOf(categories.firstOrNull()) }
    // Keep selection valid when categories change
    val validSelection = if (selectedCategory != null && categories.contains(selectedCategory)) selectedCategory else categories.firstOrNull()
    LaunchedEffect(validSelection) { selectedCategory = validSelection }

    val currentSubcategories = selectedCategory?.let { subcategoriesMap[it] } ?: emptyList()

    var showPopulateConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 16.dp)
    ) {
        // Mobile portrait mode: re-arrange according to MobilePortrait.md specs
        Text(
                text = "SETTINGS",
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

        // ── Supabase Sync Card (top) ──────────────────────────────────────
            val isDarkMode = isSystemInDarkTheme()
            val supaBg = if (isDarkMode) Color(0xFF0A1628) else Color(0xFF1C3553)
            val supaAccent = Color(0xFF3ECF8E) // Supabase green

            BrutalistCard(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                backgroundColor = supaBg,
                borderColor = supaAccent
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "SUPABASE SYNC",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp,
                                color = supaAccent
                            )
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .background(
                                            if (isSupabaseConnected) supaAccent else Color(0xFFFF5555),
                                            shape = CircleShape
                                        )
                                )
                                Text(
                                    if (isSupabaseSyncing) "Syncing..." else if (isSupabaseConnected) "Connected" else "Not synced",
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        BrutalistButton(
                            onClick = { if (!isSupabaseSyncing) onSupabaseSyncNow() },
                            containerColor = if (isSupabaseSyncing) supaBg else supaAccent
                        ) {
                            Text(
                                if (isSupabaseSyncing) "↻ SYNCING..." else "↻ SYNC NOW",
                                color = if (isSupabaseSyncing) supaAccent else Color(0xFF1C3553),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.sp
                            )
                        }
                    }

                    if (supabaseSyncMessage.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            supabaseSyncMessage,
                            fontSize = 11.sp,
                            color = if (supabaseSyncSuccess) supaAccent else Color(0xFFFF5555),
                            fontWeight = FontWeight.Bold,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Project: xlxhikvvckszsyvnodkr.supabase.co\nSingle user \u2022 Manual sync \u2022 Last-write-wins",
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.3f),
                        lineHeight = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Google drive sync card at top
            SyncSection(
                isSignedIn = isSignedIn,
                onSignIn = onSignIn,
                onSignOut = onSignOut,
                onUploadBackup = onUploadBackup,
                onDownloadBackup = onDownloadBackup,
                onViewBackups = onViewBackups
            )
            
            Spacer(modifier = Modifier.height(24.dp))

            ImportExportSection(
                onExportTemplate = onExportTemplate,
                onImportCsv = onImportCsv
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Category management section below sync
            ManageableColumn(
                title = "CATEGORIES",
                items = categories,
                onAdd = onAddCategory,
                onEdit = onEditCategory,
                onDelete = onDeleteCategory,
                selectedIndex = categories.indexOf(selectedCategory),
                onItemSelected = { index -> selectedCategory = categories[index] },
                modifier = Modifier.fillMaxWidth(),
                isLazy = false
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Sub category management section below categories
            ManageableColumn(
                title = "SUBCATEGORIES",
                items = currentSubcategories,
                onAdd = { name -> selectedCategory?.let { cat -> onAddSubcategory(cat, name) } },
                onEdit = { index, newValue -> selectedCategory?.let { cat -> onEditSubcategory(cat, index, newValue) } },
                onDelete = { index -> selectedCategory?.let { cat -> onDeleteSubcategory(cat, index) } },
                parentLabel = selectedCategory,
                modifier = Modifier.fillMaxWidth(),
                isLazy = false
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Labels management section below subcategories
            ManageableColumn(
                title = "LABELS",
                items = labels,
                onAdd = onAddLabel,
                onEdit = onEditLabel,
                onDelete = onDeleteLabel,
                modifier = Modifier.fillMaxWidth(),
                isLazy = false
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Payment Modes management section below labels
            ManageableColumn(
                title = "PAYMENT MODES",
                items = paymentModes,
                onAdd = onAddPaymentMode,
                onEdit = onEditPaymentMode,
                onDelete = onDeletePaymentMode,
                modifier = Modifier.fillMaxWidth(),
                isLazy = false
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Paid Via management section below payment modes
            ManageableColumn(
                title = "PAID VIA",
                items = paidVia,
                onAdd = onAddPaidVia,
                onEdit = onEditPaidVia,
                onDelete = onDeletePaidVia,
                modifier = Modifier.fillMaxWidth(),
                isLazy = false
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Recurring Expenses management section
            RecurringExpensesSection(
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
                onAddLabel = onAddLabel,
                modifier = Modifier.fillMaxWidth()
            )

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "VERSION 1.0.0-BRUTAL",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        // Developer mode indicator and menu
        if (isDeveloperMode) {
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(2.dp, MaterialTheme.colorScheme.error, RectangleShape)
                    .padding(8.dp)
            ) {
                Column {
                    Text(
                        "DEV MODE ACTIVE",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BrutalistButton(
                            onClick = { showPopulateConfirm = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("POPULATE DATA", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        BrutalistButton(
                            onClick = onClearAllData,
                            modifier = Modifier.weight(1f),
                            containerColor = MaterialTheme.colorScheme.error
                        ) {
                            Text("CLEAR ALL", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Populate sample data confirmation dialog
        if (showPopulateConfirm) {
            BrutalistConfirmDialog(
                title = "POPULATE SAMPLE DATA",
                message = "This will generate approximately 500 sample expenses spanning 18 months. Existing data will be replaced. Continue?",
                onConfirm = {
                    onPopulateSampleData()
                    showPopulateConfirm = false
                },
                onDismiss = { showPopulateConfirm = false }
            )
        }
    }
}

@Composable
fun EditDialog(
    title: String,
    initialValue: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val themeBlack = MaterialTheme.colorScheme.onSurface
    val themeWhite = MaterialTheme.colorScheme.surface
    var textValue by remember { mutableStateOf(initialValue) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .background(themeWhite)
                .border(2.dp, themeBlack)
                .padding(20.dp)
        ) {
            Column {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp,
                        color = themeBlack
                    )
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = themeBlack,
                        modifier = Modifier
                            .size(24.dp)
                            .clickable { onDismiss() }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                BrutalistTextField(
                    value = textValue,
                    onValueChange = { textValue = it },
                    label = "NAME",
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BrutalistButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        containerColor = themeWhite
                    ) {
                        @Suppress("DEPRECATION")
                        Text("CANCEL", color = themeBlack, fontWeight = FontWeight.Bold)
                    }
                    BrutalistButton(
                        onClick = { onConfirm(textValue) },
                        modifier = Modifier.weight(1f),
                        containerColor = themeBlack
                    ) {
                        Text("SAVE", color = themeWhite, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun BrutalistConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val themeBlack = MaterialTheme.colorScheme.onSurface
    val themeWhite = MaterialTheme.colorScheme.surface

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .background(themeWhite)
                .border(2.dp, themeBlack)
                .padding(20.dp)
        ) {
            Column {
                @Suppress("DEPRECATION")
                Text(
                    text = title.uppercase(),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    color = themeBlack
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = message,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = themeBlack
                )
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    BrutalistButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        containerColor = themeWhite
                    ) {
                        Text("CANCEL", color = themeBlack, fontWeight = FontWeight.Bold)
                    }
                    BrutalistButton(
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
                        containerColor = themeBlack
                    ) {
                        Text("CONFIRM", color = themeWhite, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun ManageableColumn(
    title: String,
    items: List<String>,
    onAdd: (String) -> Unit,
    onEdit: (Int, String) -> Unit,
    onDelete: (Int) -> Unit,
    modifier: Modifier = Modifier,
    selectedIndex: Int = -1,
    onItemSelected: ((Int) -> Unit)? = null,
    parentLabel: String? = null,
    onUpdateSubcategories: (String, List<String>) -> Unit = { _, _ -> },
    isLazy: Boolean = true
) {
    val themeBlack = MaterialTheme.colorScheme.onSurface
    val themeWhite = MaterialTheme.colorScheme.surface
    
    var showAddDialog by remember { mutableStateOf(false) }
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var deleteConfirmIndex by remember { mutableStateOf<Int?>(null) }

    BrutalistCard(modifier = modifier.fillMaxHeight()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Column header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(themeBlack)
                    .padding(horizontal = 8.dp, vertical = 10.dp)
            ) {
                Column {
                    Text(
                        text = title,
                        color = themeWhite,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        maxLines = 1
                    )
                    if (parentLabel != null) {
                        Text(
                            text = "▸ $parentLabel",
                            color = themeWhite.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Items list
            if (parentLabel != null && items.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "NO ITEMS",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = themeBlack.copy(alpha = 0.3f)
                    )
                }
            } else {
            if (isLazy) {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    itemsIndexed(items) { index, item ->
                        SettingsListItem(
                            text = item,
                            onEdit = { editingIndex = index },
                            onDelete = { deleteConfirmIndex = index },
                            isSelected = index == selectedIndex,
                            onClick = if (onItemSelected != null) { { onItemSelected(index) } } else null
                        )
                        if (index < items.lastIndex) {
                            HorizontalDivider(color = themeBlack.copy(alpha = 0.15f), thickness = 1.dp)
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    items.forEachIndexed { index, item ->
                        SettingsListItem(
                            text = item,
                            onEdit = { editingIndex = index },
                            onDelete = { deleteConfirmIndex = index },
                            isSelected = index == selectedIndex,
                            onClick = if (onItemSelected != null) { { onItemSelected(index) } } else null
                        )
                        if (index < items.lastIndex) {
                            HorizontalDivider(color = themeBlack.copy(alpha = 0.15f), thickness = 1.dp)
                        }
                    }
                }
            }
            }

            // Add button at bottom
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(themeWhite)
                    .border(width = 2.dp, color = themeBlack, shape = RectangleShape)
                    .clickable { showAddDialog = true }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Add",
                        tint = themeBlack,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        "ADD",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 11.sp,
                        color = themeBlack
                    )
                }
            }
        }
    }

    // Add dialog
    if (showAddDialog) {
        EditDialog(
            title = "ADD ${title}",
            initialValue = "",
            onConfirm = { value: String ->
                if (value.isNotBlank()) onAdd(value.trim())
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }

    // Edit dialog
    editingIndex?.let { index ->
        if (index in items.indices) {
            EditDialog(
                title = "EDIT ${title}",
                initialValue = items[index],
                onConfirm = { value: String ->
                    if (value.isNotBlank()) onEdit(index, value.trim())
                    editingIndex = null
                },
                onDismiss = { editingIndex = null }
            )
        }
    }

    // Delete confirmation dialog
    deleteConfirmIndex?.let { index ->
        if (index in items.indices) {
            BrutalistConfirmDialog(
                title = "DELETE",
                message = "Remove \"${items[index]}\"?",
                onConfirm = {
                    onDelete(index)
                    deleteConfirmIndex = null
                },
                onDismiss = { deleteConfirmIndex = null }
            )
        }
    }
}

// ── Recurring Expenses Section ────────────────────────────────────────────────

@Composable
fun RecurringExpensesSection(
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
    val themeBlack = MaterialTheme.colorScheme.onSurface
    val themeWhite = MaterialTheme.colorScheme.surface

    var showAddDialog by remember { mutableStateOf(false) }
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var deleteConfirmIndex by remember { mutableStateOf<Int?>(null) }
    var detailIndex by remember { mutableStateOf<Int?>(null) }

    val activeRecurring = recurringExpenses.filter { it.isActive }
    val monthlySum = activeRecurring.filter { it.frequency == RecurrenceFrequency.MONTHLY }.sumOf { it.amount }
    val yearlySum = activeRecurring.filter { it.frequency == RecurrenceFrequency.YEARLY }.sumOf { it.amount }
    val otherSum = activeRecurring.filter { it.frequency == RecurrenceFrequency.DAILY || it.frequency == RecurrenceFrequency.WEEKLY }.sumOf { it.amount }

    BrutalistCard(modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(themeBlack)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "RECURRING EXPENSES",
                            color = themeWhite,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "${recurringExpenses.count { it.isActive }} active · ${recurringExpenses.size} total",
                            color = themeWhite.copy(alpha = 0.65f),
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                        Text(
                            text = buildString {
                                append("Monthly: ₹${String.format("%.2f", monthlySum)}")
                                append(" · Yearly: ₹${String.format("%.2f", yearlySum)}")
                                if (otherSum > 0) {
                                    append(" · Other: ₹${String.format("%.2f", otherSum)}")
                                }
                            },
                            color = themeWhite.copy(alpha = 0.55f),
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            // List of recurring expenses
            if (recurringExpenses.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "NO RECURRING EXPENSES",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = themeBlack.copy(alpha = 0.35f)
                    )
                }
            } else {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    recurringExpenses.forEachIndexed { index, re ->
                        RecurringExpenseListItem(
                            recurringExpense = re,
                            onClick = { detailIndex = index },
                            onEdit = { editingIndex = index },
                            onDelete = { deleteConfirmIndex = index },
                            onToggleActive = { onEdit(index, re.copy(isActive = !re.isActive)) }
                        )
                        if (index < recurringExpenses.lastIndex) {
                            HorizontalDivider(color = themeBlack.copy(alpha = 0.12f), thickness = 1.dp)
                        }
                    }
                }
            }

            // Add button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(themeWhite)
                    .border(width = 2.dp, color = themeBlack, shape = RectangleShape)
                    .clickable { showAddDialog = true }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add", tint = themeBlack, modifier = Modifier.size(16.dp))
                    Text("ADD RECURRING EXPENSE", fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, color = themeBlack)
                }
            }
        }
    }

    // Add dialog
    if (showAddDialog) {
        RecurringExpenseDialog(
            title = "ADD RECURRING EXPENSE",
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

    // Edit dialog
    editingIndex?.let { idx ->
        if (idx in recurringExpenses.indices) {
            RecurringExpenseDialog(
                title = "EDIT RECURRING EXPENSE",
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
                    editingIndex = null
                },
                onDismiss = { editingIndex = null }
            )
        }
    }

    // Delete confirmation
    deleteConfirmIndex?.let { idx ->
        if (idx in recurringExpenses.indices) {
            BrutalistConfirmDialog(
                title = "DELETE RECURRING EXPENSE",
                message = "Remove \"${recurringExpenses[idx].name}\"? This will not delete any expenses already generated.",
                onConfirm = {
                    onDelete(idx)
                    deleteConfirmIndex = null
                },
                onDismiss = { deleteConfirmIndex = null }
            )
        }
    }

    // Detail info dialog
    detailIndex?.let { idx ->
        if (idx in recurringExpenses.indices) {
            val re = recurringExpenses[idx]
            val dateFormatter = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")
            val scheduleStr = buildString {
                when (re.frequency) {
                    RecurrenceFrequency.DAILY -> append("Every day")
                    RecurrenceFrequency.WEEKLY -> {
                        val dayNames = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
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
                containerColor = themeWhite,
                shape = RectangleShape,
                title = {
                    Text(
                        text = "RECURRING EXPENSE DETAILS",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp,
                        color = themeBlack
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Name: ${re.name}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = themeBlack)
                        if (re.storeName.isNotEmpty()) {
                            Text("Store: ${re.storeName}", fontSize = 12.sp, color = themeBlack)
                        }
                        Text("Amount: ₹${String.format("%.2f", re.amount)}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = themeBlack)
                        if (re.category.isNotEmpty()) {
                            Text("Category: ${re.category}${if (re.subcategory.isNotEmpty()) " › ${re.subcategory}" else ""}", fontSize = 12.sp, color = themeBlack)
                        }
                        if (re.labels.isNotEmpty()) {
                            Text("Labels: ${re.labels.joinToString()}", fontSize = 12.sp, color = themeBlack)
                        }
                        if (re.paymentMode.isNotEmpty()) {
                            Text("Payment Mode: ${re.paymentMode}", fontSize = 12.sp, color = themeBlack)
                        }
                        if (re.paidVia.isNotEmpty()) {
                            Text("Paid Via: ${re.paidVia}", fontSize = 12.sp, color = themeBlack)
                        }
                        Text("Frequency: ${re.frequency.name}", fontSize = 12.sp, color = themeBlack)
                        Text("Schedule: $scheduleStr", fontSize = 12.sp, color = themeBlack)
                        Text("Start Date: ${re.startDate.format(dateFormatter)}", fontSize = 12.sp, color = themeBlack)
                        Text("End Date: ${re.endDate?.format(dateFormatter) ?: "None"}", fontSize = 12.sp, color = themeBlack)
                        if (re.notes.isNotEmpty()) {
                            Text("Notes: ${re.notes}", fontSize = 12.sp, color = themeBlack)
                        }
                        Text("Status: ${if (re.isActive) "Active" else "Inactive"}", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (re.isActive) Color(0xFF2ECC71) else themeBlack.copy(alpha = 0.5f))
                        if (re.lastGeneratedDate != null) {
                            Text("Last Generated: ${re.lastGeneratedDate.format(dateFormatter)}", fontSize = 11.sp, color = themeBlack.copy(alpha = 0.7f))
                        }
                    }
                },
                confirmButton = {
                    BrutalistButton(
                        onClick = { detailIndex = null },
                        containerColor = themeBlack
                    ) {
                        Text("CLOSE", color = themeWhite, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            )
        }
    }
}

@Composable
fun RecurringExpenseListItem(
    recurringExpense: RecurringExpense,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleActive: () -> Unit
) {
    val themeBlack = MaterialTheme.colorScheme.onSurface
    val themeWhite = MaterialTheme.colorScheme.surface

    val scheduleLabel = buildString {
        when (recurringExpense.frequency) {
            RecurrenceFrequency.DAILY -> append("Every day")
            RecurrenceFrequency.WEEKLY -> {
                val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                append("Every ${dayNames.getOrElse(recurringExpense.dayOfPeriod - 1) { "?" }}")
            }
            RecurrenceFrequency.MONTHLY -> append("Day ${recurringExpense.dayOfPeriod} of each month")
            RecurrenceFrequency.YEARLY -> {
                val mVal = if (recurringExpense.monthOfPeriod > 0) recurringExpense.monthOfPeriod else recurringExpense.startDate.monthValue
                val monthName = java.time.Month.of(mVal.coerceIn(1, 12)).name.take(3)
                append("Yearly on $monthName ${recurringExpense.dayOfPeriod}")
            }
        }
        val fmt = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yy")
        append(" [From ${recurringExpense.startDate.format(fmt)}")
        if (recurringExpense.endDate != null) append(" to ${recurringExpense.endDate.format(fmt)}")
        append("]")
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (!recurringExpense.isActive) themeBlack.copy(alpha = 0.04f) else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Active indicator dot
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(
                    if (recurringExpense.isActive) Color(0xFF2ECC71) else themeBlack.copy(alpha = 0.25f),
                    CircleShape
                )
        )
        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = recurringExpense.name,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 13.sp,
                color = if (recurringExpense.isActive) themeBlack else themeBlack.copy(alpha = 0.45f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "₹${String.format("%.2f", recurringExpense.amount)}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = if (recurringExpense.isActive) themeBlack else themeBlack.copy(alpha = 0.40f)
                )
                Text("·", fontSize = 10.sp, color = themeBlack.copy(alpha = 0.35f))
                Text(
                    text = scheduleLabel,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = themeBlack.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (recurringExpense.category.isNotEmpty()) {
                Text(
                    text = buildString {
                        append(recurringExpense.category)
                        if (recurringExpense.subcategory.isNotEmpty()) append(" › ${recurringExpense.subcategory}")
                    },
                    fontSize = 10.sp,
                    color = themeBlack.copy(alpha = 0.4f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Action row
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Toggle active
            Box(
                modifier = Modifier
                    .border(1.5.dp, if (recurringExpense.isActive) Color(0xFF2ECC71) else themeBlack.copy(alpha = 0.3f), RectangleShape)
                    .clickable { onToggleActive() }
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            ) {
                Text(
                    text = if (recurringExpense.isActive) "ON" else "OFF",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (recurringExpense.isActive) Color(0xFF2ECC71) else themeBlack.copy(alpha = 0.3f)
                )
            }
            Icon(
                Icons.Default.Edit,
                contentDescription = "Edit",
                tint = themeBlack,
                modifier = Modifier.size(22.dp).clickable { onEdit() }
            )
            Icon(
                Icons.Default.Delete,
                contentDescription = "Delete",
                tint = themeBlack,
                modifier = Modifier.size(22.dp).clickable { onDelete() }
            )
        }
    }
}

@Composable
fun RecurringExpenseDialog(
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
    val themeBlack = MaterialTheme.colorScheme.onSurface
    val themeWhite = MaterialTheme.colorScheme.surface

    // ── Form state ────────────────────────────────────────────────────────────
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var storeName by remember { mutableStateOf(initial?.storeName ?: "") }
    var amountStr by remember { mutableStateOf(if (initial != null) String.format("%.2f", initial.amount) else "") }
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

    // Selected labels as mutable list (multiselect)
    val selectedLabels = remember {
        mutableStateListOf<String>().apply { addAll(initial?.labels ?: emptyList()) }
    }

    // ── Inline-add dialog state ───────────────────────────────────────────────
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var showAddSubcategoryDialog by remember { mutableStateOf(false) }
    var showAddLabelDialog by remember { mutableStateOf(false) }
    var newCategoryName by remember { mutableStateOf("") }
    var newSubcategoryName by remember { mutableStateOf("") }
    var newLabelName by remember { mutableStateOf("") }

    val currentSubcategories = subcategoriesMap[selectedCategory] ?: emptyList()

    val frequencyOptions = listOf(
        RecurrenceFrequency.DAILY to "Daily",
        RecurrenceFrequency.WEEKLY to "Weekly",
        RecurrenceFrequency.MONTHLY to "Monthly",
        RecurrenceFrequency.YEARLY to "Yearly"
    )

    // ── Inline add dialogs ────────────────────────────────────────────────────
    if (showAddCategoryDialog) {
        Dialog(onDismissRequest = { showAddCategoryDialog = false }) {
            BrutalistCard(modifier = Modifier.padding(16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("NEW CATEGORY", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(modifier = Modifier.height(12.dp))
                    BrutalistTextField(
                        value = newCategoryName,
                        onValueChange = { newCategoryName = it },
                        label = "Category Name"
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BrutalistButton(
                            onClick = { showAddCategoryDialog = false; newCategoryName = "" },
                            modifier = Modifier.weight(1f),
                            containerColor = themeWhite
                        ) { Text("CANCEL", color = themeBlack, fontWeight = FontWeight.Bold) }
                        BrutalistButton(
                            onClick = {
                                if (newCategoryName.isNotBlank()) {
                                    onAddCategory(newCategoryName)
                                    selectedCategory = newCategoryName
                                    selectedSubcategory = ""
                                    showAddCategoryDialog = false
                                    newCategoryName = ""
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("ADD", color = themeWhite, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }

    if (showAddSubcategoryDialog) {
        Dialog(onDismissRequest = { showAddSubcategoryDialog = false }) {
            BrutalistCard(modifier = Modifier.padding(16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("NEW SUBCATEGORY", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("For: $selectedCategory", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = themeBlack.copy(alpha = 0.6f))
                    Spacer(modifier = Modifier.height(12.dp))
                    BrutalistTextField(
                        value = newSubcategoryName,
                        onValueChange = { newSubcategoryName = it },
                        label = "Subcategory Name"
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BrutalistButton(
                            onClick = { showAddSubcategoryDialog = false; newSubcategoryName = "" },
                            modifier = Modifier.weight(1f),
                            containerColor = themeWhite
                        ) { Text("CANCEL", color = themeBlack, fontWeight = FontWeight.Bold) }
                        BrutalistButton(
                            onClick = {
                                if (newSubcategoryName.isNotBlank() && selectedCategory.isNotBlank()) {
                                    onAddSubcategory(selectedCategory, newSubcategoryName)
                                    selectedSubcategory = newSubcategoryName
                                    showAddSubcategoryDialog = false
                                    newSubcategoryName = ""
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("ADD", color = themeWhite, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }

    if (showAddLabelDialog) {
        Dialog(onDismissRequest = { showAddLabelDialog = false }) {
            BrutalistCard(modifier = Modifier.padding(16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("NEW LABEL", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(modifier = Modifier.height(12.dp))
                    BrutalistTextField(
                        value = newLabelName,
                        onValueChange = { newLabelName = it },
                        label = "Label Name"
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BrutalistButton(
                            onClick = { showAddLabelDialog = false; newLabelName = "" },
                            modifier = Modifier.weight(1f),
                            containerColor = themeWhite
                        ) { Text("CANCEL", color = themeBlack, fontWeight = FontWeight.Bold) }
                        BrutalistButton(
                            onClick = {
                                if (newLabelName.isNotBlank()) {
                                    onAddLabel(newLabelName)
                                    selectedLabels.add(newLabelName)
                                    showAddLabelDialog = false
                                    newLabelName = ""
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("ADD", color = themeWhite, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }

    // ── Main dialog ──────────────────────────────────────────────────────────
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .background(themeWhite)
                .border(2.dp, themeBlack)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = title, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = themeBlack)
                    Icon(
                        Icons.Default.Close, contentDescription = "Close", tint = themeBlack,
                        modifier = Modifier.size(24.dp).clickable { onDismiss() }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Name (required)
                BrutalistTextField(
                    value = name,
                    onValueChange = { name = it; nameError = false },
                    label = if (nameError) "NAME * (required)" else "NAME *",
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))

                // Amount (required)
                BrutalistTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it; amountError = false },
                    label = if (amountError) "AMOUNT (₹) * (invalid)" else "AMOUNT (₹) *",
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))

                // Store / Merchant
                BrutalistTextField(
                    value = storeName,
                    onValueChange = { storeName = it },
                    label = "STORE / MERCHANT",
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))

                // Item Description
                BrutalistTextField(
                    value = itemDescription,
                    onValueChange = { itemDescription = it },
                    label = "ITEM DESCRIPTION",
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))

                // ── Category (with + Add New) ─────────────────────────────────
                BrutalistDropdown(
                    label = "CATEGORY",
                    options = categories + "+ Add New",
                    selectedOption = selectedCategory,
                    onOptionSelected = {
                        if (it == "+ Add New") {
                            showAddCategoryDialog = true
                        } else {
                            selectedCategory = it
                            selectedSubcategory = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))

                // ── Subcategory (with + Add New, only when category selected) ─
                if (selectedCategory.isNotEmpty()) {
                    BrutalistDropdown(
                        label = "SUBCATEGORY",
                        options = listOf("") + currentSubcategories + "+ Add New",
                        selectedOption = selectedSubcategory,
                        onOptionSelected = {
                            if (it == "+ Add New") {
                                showAddSubcategoryDialog = true
                            } else {
                                selectedSubcategory = it
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // ── Labels (multiselect with + Add New) ───────────────────────
                BrutalistMultiSelectDropdown(
                    label = "LABELS",
                    options = labels + "+ Add New",
                    selectedOptions = selectedLabels.toSet(),
                    onOptionToggled = { lbl ->
                        if (lbl == "+ Add New") {
                            showAddLabelDialog = true
                        } else {
                            if (selectedLabels.contains(lbl)) selectedLabels.remove(lbl)
                            else selectedLabels.add(lbl)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))

                // ── Frequency selector ────────────────────────────────────────
                Text("FREQUENCY", fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, color = themeBlack)
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    frequencyOptions.forEach { (freq, label) ->
                        val isSelected = selectedFrequency == freq
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(if (isSelected) themeBlack else themeWhite)
                                .border(1.5.dp, themeBlack, RectangleShape)
                                .clickable { selectedFrequency = freq }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label.uppercase(),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isSelected) themeWhite else themeBlack,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))

                // ── Context-sensitive day picker ──────────────────────────────
                when (selectedFrequency) {
                    RecurrenceFrequency.MONTHLY -> {
                        Text("DAY OF MONTH", fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, color = themeBlack)
                        Spacer(modifier = Modifier.height(6.dp))
                        BrutalistDropdown(
                            label = "DAY",
                            options = (1..31).map { it.toString() },
                            selectedOption = dayOfPeriod.toString(),
                            onOptionSelected = { dayOfPeriod = it.toIntOrNull() ?: 1 },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    RecurrenceFrequency.WEEKLY -> {
                        Text("DAY OF WEEK", fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, color = themeBlack)
                        Spacer(modifier = Modifier.height(6.dp))
                        val weekDays = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
                        BrutalistDropdown(
                            label = "DAY",
                            options = weekDays,
                            selectedOption = weekDays.getOrElse(dayOfPeriod - 1) { "Monday" },
                            onOptionSelected = { dayOfPeriod = weekDays.indexOf(it) + 1 },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    RecurrenceFrequency.YEARLY -> {
                        Text("MONTH OF YEAR", fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, color = themeBlack)
                        Spacer(modifier = Modifier.height(6.dp))
                        val months = listOf("Current Month (Default)", "January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
                        BrutalistDropdown(
                            label = "MONTH",
                            options = months,
                            selectedOption = if (monthOfPeriod in 1..12) months[monthOfPeriod] else "Current Month (Default)",
                            onOptionSelected = { monthOfPeriod = months.indexOf(it) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("DAY OF MONTH (YEARLY)", fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, color = themeBlack)
                        Spacer(modifier = Modifier.height(6.dp))
                        BrutalistDropdown(
                            label = "DAY",
                            options = (1..31).map { it.toString() },
                            selectedOption = dayOfPeriod.toString(),
                            onOptionSelected = { dayOfPeriod = it.toIntOrNull() ?: 1 },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    RecurrenceFrequency.DAILY -> { /* no extra picker */ }
                }
                Spacer(modifier = Modifier.height(10.dp))

                // Start Date & End Date pickers
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(modifier = Modifier.weight(1f).clickable { showStartDatePicker = true }) {
                        BrutalistTextField(
                            value = startDate.format(dateFormatter),
                            onValueChange = {},
                            label = "START DATE",
                            readOnly = true,
                            enabled = false
                        )
                    }
                    Box(modifier = Modifier.weight(1f).clickable { showEndDatePicker = true }) {
                        BrutalistTextField(
                            value = endDate?.format(dateFormatter) ?: "None",
                            onValueChange = {},
                            label = "END DATE",
                            readOnly = true,
                            enabled = false
                        )
                    }
                }
                // Clear End Date option if set
                if (endDate != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "CLEAR END DATE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.clickable { endDate = null }.padding(vertical = 4.dp).align(Alignment.End)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))

                // Payment Mode
                if (paymentModes.isNotEmpty()) {
                    BrutalistDropdown(
                        label = "PAYMENT MODE",
                        options = listOf("") + paymentModes,
                        selectedOption = selectedPaymentMode,
                        onOptionSelected = { selectedPaymentMode = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Paid Via
                if (paidVia.isNotEmpty()) {
                    BrutalistDropdown(
                        label = "PAID VIA",
                        options = listOf("") + paidVia,
                        selectedOption = selectedPaidVia,
                        onOptionSelected = { selectedPaidVia = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Notes
                BrutalistTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = "NOTES",
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))

                // Active toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("ACTIVE", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, color = themeBlack)
                    Switch(
                        checked = isActive,
                        onCheckedChange = { isActive = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = themeWhite,
                            checkedTrackColor = themeBlack,
                            uncheckedThumbColor = themeBlack,
                            uncheckedTrackColor = themeBlack.copy(alpha = 0.2f)
                        )
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))

                if (showStartDatePicker) {
                    BrutalistDatePickerDialog(
                        onDismissRequest = { showStartDatePicker = false },
                        onDateSelected = { millis ->
                            millis?.let {
                                startDate = java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                            }
                        }
                    )
                }
                if (showEndDatePicker) {
                    BrutalistDatePickerDialog(
                        onDismissRequest = { showEndDatePicker = false },
                        onDateSelected = { millis ->
                            millis?.let {
                                endDate = java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                            }
                        }
                    )
                }

                // Action buttons
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BrutalistButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        containerColor = themeWhite
                    ) { Text("CANCEL", color = themeBlack, fontWeight = FontWeight.Bold) }
                    BrutalistButton(
                        onClick = {
                            val amount = amountStr.toDoubleOrNull()
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
                        modifier = Modifier.weight(1f),
                        containerColor = themeBlack
                    ) { Text("SAVE", color = themeWhite, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}
@Composable
fun SettingsListItem(
    text: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    isSelected: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val themeBlack = MaterialTheme.colorScheme.onSurface
    val themeWhite = MaterialTheme.colorScheme.surface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isSelected) themeBlack else Color.Transparent)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = if (isSelected) themeWhite else themeBlack,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        // Action icons - aligned in a row and increased in size
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 4.dp)
        ) {
            val iconColor = if (isSelected) themeWhite else themeBlack
            Icon(
                Icons.Default.Edit,
                contentDescription = "Edit",
                tint = iconColor,
                modifier = Modifier
                    .size(24.dp)
                    .clickable { onEdit() }
            )
            Icon(
                Icons.Default.Delete,
                contentDescription = "Delete",
                tint = iconColor,
                modifier = Modifier
                    .size(24.dp)
                    .clickable { onDelete() }
            )
        }
    }
}

// -- Screens ----------------------------------------------------------------
@Composable
fun SyncSection(
    isSignedIn: Boolean,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onUploadBackup: () -> Unit,
    onDownloadBackup: (String) -> Unit,
    onViewBackups: () -> Unit
) {
    val themeBlack = MaterialTheme.colorScheme.onSurface
    val themeWhite = MaterialTheme.colorScheme.surface
    
    var showSignInDialog by remember { mutableStateOf(false) }
    var showBackupsDialog by remember { mutableStateOf(false) }
    
    BrutalistCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(themeBlack)
                    .padding(horizontal = 12.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "GOOGLE DRIVE SYNC",
                    color = themeWhite,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp
                )
            }
            
            // Status and buttons
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isSignedIn) "✓ CONNECTED" else "✗ NOT CONNECTED",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = if (isSignedIn) Color(0xFF4CAF50) else Color(0xFFF44336)
                    )
                    
                    if (isSignedIn) {
                        BrutalistButton(
                            onClick = onSignOut,
                            modifier = Modifier.height(32.dp),
                            containerColor = Color(0xFFF44336)
                        ) {
                            Text("SIGN OUT", color = themeWhite, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        BrutalistButton(
                            onClick = { showSignInDialog = true },
                            modifier = Modifier.height(32.dp),
                            containerColor = Color(0xFF4CAF50)
                        ) {
                            Text("SIGN IN", color = themeWhite, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                
                if (isSignedIn) {
                    // Sync buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BrutalistButton(
                            onClick = onUploadBackup,
                            modifier = Modifier.weight(1f).height(36.dp),
                            containerColor = themeBlack
                        ) {
                            Text("UPLOAD", color = themeWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        
                        BrutalistButton(
                            onClick = { showBackupsDialog = true },
                            modifier = Modifier.weight(1f).height(36.dp),
                            containerColor = themeWhite
                        ) {
                            Text("DOWNLOAD", color = themeBlack, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    
                    Text(
                        text = "• Upload saves your data to Google Drive\n• Download retrieves backups from Drive",
                        fontSize = 10.sp,
                        color = themeBlack.copy(alpha = 0.7f),
                        lineHeight = 12.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }

    // Sign In Dialog
    if (showSignInDialog) {
        BrutalistConfirmDialog(
            title = "SIGN IN",
            message = "Sign in to enable backup and sync functionality for your expense data across devices.",
            onConfirm = {
                showSignInDialog = false
                onSignIn()
            },
            onDismiss = { showSignInDialog = false }
        )
    }

    // Backups Dialog (placeholder for now)
    if (showBackupsDialog) {
        BrutalistConfirmDialog(
            title = "DOWNLOAD",
            message = "This will show available backups. For now, please use the download option after viewing backups.",
            onConfirm = {
                showBackupsDialog = false
                onViewBackups()
            },
            onDismiss = { showBackupsDialog = false }
        )
    }
}

@Composable
fun ImportExportSection(
    onExportTemplate: () -> Unit,
    onImportCsv: () -> Unit
) {
    val themeBlack = MaterialTheme.colorScheme.onSurface
    val themeWhite = MaterialTheme.colorScheme.surface
    
    BrutalistCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(themeBlack)
                    .padding(horizontal = 12.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "DATA IMPORT / EXPORT (CSV)",
                    color = themeWhite,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp
                )
            }
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BrutalistButton(
                        onClick = onExportTemplate,
                        modifier = Modifier.weight(1f).height(36.dp),
                        containerColor = themeWhite
                    ) {
                        Text("EXPORT TEMPLATE", color = themeBlack, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    
                    BrutalistButton(
                        onClick = onImportCsv,
                        modifier = Modifier.weight(1f).height(36.dp),
                        containerColor = themeBlack
                    ) {
                        Text("IMPORT CSV", color = themeWhite, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
                
                Text(
                    text = "• Export a blank template with sample entries\n• Import CSV to add multiple expenses and settings",
                    fontSize = 10.sp,
                    color = themeBlack.copy(alpha = 0.7f),
                    lineHeight = 12.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}


