package com.example.expensetracker

import android.os.Bundle
import android.content.res.Configuration
import androidx.activity.ComponentActivity
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
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
import com.example.expensetracker.ui.screens.BudgetScreen
import com.example.expensetracker.ui.screens.ExpenseEntryScreen
import java.time.Instant
import java.time.ZoneId
import java.time.LocalDate
import java.time.YearMonth
import com.example.expensetracker.data.DataRepository
import com.example.expensetracker.data.AppData
import com.example.expensetracker.data.SampleDataManager
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
import kotlinx.coroutines.launch
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task

enum class Screen {
    Dashboard, ExpenseList, Budget, Settings, AddExpense
}

enum class TrendDimension {
    TOTAL, CATEGORY, SUBCATEGORY, LABEL
}

enum class TrendTimeframe {
    MONTH, YEAR
}

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
                var viewingCategoryFilter by remember { mutableStateOf<String?>(null) }
                var viewingSubcategoryFilter by remember { mutableStateOf<String?>(null) }
                var viewingExpenseIdFilter by remember { mutableStateOf<String?>(null) }
                var viewingGroupIdFilter by remember { mutableStateOf<String?>(null) }
                var viewingLabelFilter by remember { mutableStateOf<String?>(null) }

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

                val storeHistory = remember { mutableStateListOf(*initialData.storeHistory.toTypedArray()) }

                val categoryBudgets = remember { mutableStateMapOf(*initialData.categoryBudgets.toList().toTypedArray()) }
                val subcategoryBudgets = remember { mutableStateMapOf(*initialData.subcategoryBudgets.toList().toTypedArray()) }

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
                            isDarkTheme = isDarkTheme
                        )
                    }.collect { data ->
                        android.util.Log.d("MainActivity", "Auto-saving ${data.expenses.size} expenses")
                        DataRepository.save(context, data)
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

                // Developer mode state (hidden feature for testing)
                var isDeveloperMode by remember { mutableStateOf(false) }
                var showDevModeIndicator by remember { mutableStateOf(false) }
                var devModeLongPressStart by remember { mutableStateOf(0L) }
                var showClearDataDialog by remember { mutableStateOf(false) }

                // Check sign-in status on initial load and when trigger changes
                LaunchedEffect(Unit) {
                    Log.d("MainActivity", "Initial sign-in status check")
                    isSignedIn = syncService.isSignedIn()
                }
                
                // Re-check sign-in status when refresh trigger changes
                LaunchedEffect(signInRefreshTrigger) {
                    if (signInRefreshTrigger > 0) {
                        Log.d("MainActivity", "Sign-in refresh triggered: $signInRefreshTrigger")
                        isSignedIn = syncService.isSignedIn()
                    }
                }

                // Device type and orientation detection for conditional layouts
                val configuration = LocalConfiguration.current
                val context = LocalContext.current
                val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                val screenWidthDp = configuration.screenWidthDp
                // Consider device as tablet if width is >= 600dp
                val isTablet = screenWidthDp >= 600
                // Mobile portrait mode: mobile device in portrait orientation
                val isMobilePortrait = !isLandscape && !isTablet
                // Tablet/landscape mode: either tablet (any orientation) or mobile in landscape
                val isTabletOrLandscape = isTablet || isLandscape
                val isMobileLandscape = isLandscape && !isTablet

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        BottomBar(
                            currentScreen = currentScreen,
                            onScreenSelected = { 
                                currentScreen = it
                                 if (it != Screen.ExpenseList) {
                                    viewingDateFilter = null
                                    viewingStartDateFilter = null
                                    viewingEndDateFilter = null
                                    viewingCategoryFilter = null
                                    viewingSubcategoryFilter = null
                                    viewingLabelFilter = null
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
                    Box(modifier = Modifier.padding(innerPadding)) {
                        when (currentScreen) {
                            Screen.Dashboard -> DashboardScreen(
                                expenses = globalExpenses,
                                budget = overallBudget,
                                categoryBudgets = categoryBudgets,
                                subcategoryBudgets = subcategoryBudgets,
                                categories = categories,
                                subcategoriesMap = subcategoriesMap,
                                isMobilePortrait = isMobilePortrait,
                                onNavigateToExpenses = { date ->
                                    viewingDateFilter = date
                                    viewingStartDateFilter = null
                                    viewingEndDateFilter = null
                                    viewingCategoryFilter = null
                                    currentScreen = Screen.ExpenseList
                                },
                                onNavigateToMonthExpenses = { ym, dimension, item ->
                                    viewingStartDateFilter = ym.atDay(1)
                                    viewingEndDateFilter = ym.atEndOfMonth()
                                    viewingDateFilter = null
                                    viewingCategoryFilter = if (dimension == TrendDimension.CATEGORY) item else null
                                    viewingSubcategoryFilter = if (dimension == TrendDimension.SUBCATEGORY) item else null
                                    viewingLabelFilter = if (dimension == TrendDimension.LABEL) item else null
                                    currentScreen = Screen.ExpenseList
                                },
                                onNavigateToYearExpenses = { year, dimension, item ->
                                    viewingStartDateFilter = LocalDate.of(year, 1, 1)
                                    viewingEndDateFilter = LocalDate.of(year, 12, 31)
                                    viewingDateFilter = null
                                    viewingCategoryFilter = if (dimension == TrendDimension.CATEGORY) item else null
                                    viewingSubcategoryFilter = if (dimension == TrendDimension.SUBCATEGORY) item else null
                                    viewingLabelFilter = if (dimension == TrendDimension.LABEL) item else null
                                    currentScreen = Screen.ExpenseList
                                },
                                onNavigateToCategory = { category, subcategory, yearMonth ->
                                    viewingCategoryFilter = category
                                    viewingSubcategoryFilter = subcategory
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
                                onNavigateToExpenseDetails = { id ->
                                    viewingExpenseIdFilter = id
                                    viewingGroupIdFilter = null
                                    viewingDateFilter = null
                                    viewingStartDateFilter = null
                                    viewingEndDateFilter = null
                                    viewingCategoryFilter = null
                                    viewingSubcategoryFilter = null
                                    currentScreen = Screen.ExpenseList
                                },
                                onNavigateToGroupDetails = { groupId ->
                                    viewingGroupIdFilter = groupId
                                    viewingExpenseIdFilter = null
                                    viewingDateFilter = null
                                    viewingStartDateFilter = null
                                    viewingEndDateFilter = null
                                    viewingCategoryFilter = null
                                    viewingSubcategoryFilter = null
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
                            Screen.ExpenseList -> {
                                // Default to current month if no date filters are set
                                val currentMonthStart = LocalDate.now().withDayOfMonth(1)
                                val currentMonthEnd = LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth())
                                if (viewingDateFilter == null && viewingStartDateFilter == null && viewingEndDateFilter == null) {
                                    viewingStartDateFilter = currentMonthStart
                                    viewingEndDateFilter = currentMonthEnd
                                }
                                ExpenseListScreen(
                                expenses = globalExpenses,
                                isMobilePortrait = isMobilePortrait,
                                viewingDate = viewingDateFilter,
                                initialStartDate = viewingStartDateFilter,
                                initialEndDate = viewingEndDateFilter,
                                initialCategory = viewingCategoryFilter,
                                initialSubcategory = viewingSubcategoryFilter,
                                initialLabel = viewingLabelFilter,
                                initialSelectedExpenseId = viewingExpenseIdFilter,
                                initialSelectedGroupId = viewingGroupIdFilter,
                                onClearFilter = { 
                                    viewingDateFilter = null
                                    viewingStartDateFilter = null
                                    viewingEndDateFilter = null
                                    viewingCategoryFilter = null
                                    viewingSubcategoryFilter = null
                                    viewingLabelFilter = null
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
                                isMobilePortrait = isMobilePortrait,
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
                                                globalExpenses.clear()
                                                globalExpenses.addAll(newData.expenses)
                                                categories.clear()
                                                categories.addAll(newData.categories)
                                                labels.clear()
                                                labels.addAll(newData.labels)
                                                storeHistory.clear()
                                                storeHistory.addAll(newData.storeHistory)
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
                                                                labels.clear()
                                                                labels.addAll(reloadedData.labels)
                                                                storeHistory.clear()
                                                                storeHistory.addAll(reloadedData.storeHistory)
                                                                subcategoriesMap.clear()
                                                                reloadedData.subcategoriesMap.forEach { (cat, subs) ->
                                                                    subcategoriesMap[cat] = mutableStateListOf(*subs.toTypedArray())
                                                                }
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
                                }
                            )
                            Screen.Budget -> BudgetScreen(
                                isMobilePortrait = isMobilePortrait,
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
                                isMobilePortrait = isMobilePortrait,
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
                                onNavigateToExpenses = { date ->
                                    viewingDateFilter = date
                                    viewingStartDateFilter = null
                                    viewingEndDateFilter = null
                                    viewingCategoryFilter = null
                                    currentScreen = Screen.ExpenseList
                                },
                                onNavigateToMonthExpenses = { ym, dimension, item ->
                                    viewingStartDateFilter = ym.atDay(1)
                                    viewingEndDateFilter = ym.atEndOfMonth()
                                    viewingDateFilter = null
                                    viewingCategoryFilter = if (dimension == TrendDimension.CATEGORY) item else null
                                    viewingSubcategoryFilter = if (dimension == TrendDimension.SUBCATEGORY) item else null
                                    viewingLabelFilter = if (dimension == TrendDimension.LABEL) item else null
                                    currentScreen = Screen.ExpenseList
                                },
                                onNavigateToYearExpenses = { year, dimension, item ->
                                    viewingStartDateFilter = LocalDate.of(year, 1, 1)
                                    viewingEndDateFilter = LocalDate.of(year, 12, 31)
                                    viewingDateFilter = null
                                    viewingCategoryFilter = if (dimension == TrendDimension.CATEGORY) item else null
                                    viewingSubcategoryFilter = if (dimension == TrendDimension.SUBCATEGORY) item else null
                                    viewingLabelFilter = if (dimension == TrendDimension.LABEL) item else null
                                    currentScreen = Screen.ExpenseList
                                },
                                onNavigateToCategory = { category, subcategory, yearMonth ->
                                    viewingCategoryFilter = category
                                    viewingSubcategoryFilter = subcategory
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
                                onNavigateToExpenseDetails = { id ->
                                    viewingExpenseIdFilter = id
                                    viewingGroupIdFilter = null
                                    viewingDateFilter = null
                                    viewingStartDateFilter = null
                                    viewingEndDateFilter = null
                                    viewingCategoryFilter = null
                                    viewingSubcategoryFilter = null
                                    currentScreen = Screen.ExpenseList
                                },
                                onNavigateToGroupDetails = { groupId ->
                                    viewingGroupIdFilter = groupId
                                    viewingExpenseIdFilter = null
                                    viewingDateFilter = null
                                    viewingStartDateFilter = null
                                    viewingEndDateFilter = null
                                    viewingCategoryFilter = null
                                    viewingSubcategoryFilter = null
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
            isSelected = currentScreen == Screen.Dashboard,
            onClick = { onScreenSelected(Screen.Dashboard) },
            modifier = Modifier.weight(1f)
        )
        BottomNavItem(
            icon = Icons.Default.List,
            label = "EXPENSES",
            isSelected = currentScreen == Screen.ExpenseList,
            onClick = { onScreenSelected(Screen.ExpenseList) },
            modifier = Modifier.weight(1f)
        )
        BottomNavItem(
            icon = Icons.Default.Edit,
            label = "BUDGET",
            isSelected = currentScreen == Screen.Budget,
            onClick = { onScreenSelected(Screen.Budget) },
            modifier = Modifier.weight(1f)
        )
        BottomNavItem(
            icon = Icons.Default.Settings,
            label = "SETTINGS",
            isSelected = currentScreen == Screen.Settings,
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
    data class ChartPoint(val label: String, val value: Float, val period: Any)

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
            val unifiedSize = (100f * (barWidth / maxLabelWidth))
            val labelPadding = 10f
            val labelBlockHeight = (unifiedSize * 1.1f) + labelPadding 
            
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
fun DashboardScreen(
    expenses: List<Expense>,
    budget: Double = 800.0,
    categoryBudgets: Map<String, Double> = emptyMap(),
    subcategoryBudgets: Map<String, Double> = emptyMap(),
    categories: List<String> = emptyList(),
    subcategoriesMap: Map<String, List<String>> = emptyMap(),
    isMobilePortrait: Boolean = false,
    onNavigateToExpenses: (LocalDate) -> Unit,
    onNavigateToMonthExpenses: (YearMonth, TrendDimension, String?) -> Unit,
    onNavigateToYearExpenses: (Int, TrendDimension, String?) -> Unit,
    onNavigateToCategory: (String, String?, YearMonth?) -> Unit,
    onNavigateToExpenseDetails: (String) -> Unit,
    onNavigateToGroupDetails: (String) -> Unit,
    onNavigateToBudget: () -> Unit,
    onNewExpense: (LocalDate) -> Unit,
    isDarkTheme: Boolean = false,
    onThemeToggle: () -> Unit = {}
) {
    var viewedMonth by remember { mutableStateOf(YearMonth.now()) }
    var trendDimension by remember { mutableStateOf(TrendDimension.TOTAL) }
    var trendItem by remember { mutableStateOf<String?>(null) }
    var trendTimeframe by remember { mutableStateOf(TrendTimeframe.MONTH) }
    var selectedCategoryForSub by remember { mutableStateOf<String?>(null) }
    var selectedTrendValue by remember { mutableStateOf<Float?>(null) }
    var selectedTrendPeriod by remember { mutableStateOf<Any?>(null) }
    var recentByGroup by remember { mutableStateOf(true) }

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
        when (trendDimension) {
            TrendDimension.TOTAL -> expenses
            TrendDimension.CATEGORY -> expenses.filter { it.category == trendItem }
            TrendDimension.SUBCATEGORY -> expenses.filter { it.subcategory == trendItem }
            TrendDimension.LABEL -> if (trendItem != null) expenses.filter { it.labels.contains(trendItem) } else expenses
        }
    }

    val monthlyExpenses = remember(expenses, viewedMonth) {
        expenses.filter { YearMonth.from(it.date) == viewedMonth }
    }

    val expensesMap = remember(expenses) {
        expenses.groupBy { it.date }.mapValues { entry -> entry.value.sumOf { it.amount } }
    }
    val totalSpent = remember(monthlyExpenses) { monthlyExpenses.sumOf { it.amount } }
    val recentGroups = remember(expenses) {
        expenses.groupBy { it.groupId }
            .values
            .sortedByDescending { it.first().date }
            .take(10)
    }
    val recentItems = remember(expenses) {
        expenses.sortedByDescending { it.date }.take(10)
    }
    val recentCount = if (recentByGroup) recentGroups.size else recentItems.size
    val dateFormatter = java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy", java.util.Locale.getDefault())

    val themeBlack = MaterialTheme.colorScheme.onSurface
    val themeWhite = MaterialTheme.colorScheme.surface
    val themeDivider = MaterialTheme.colorScheme.tertiary

    Column(
        modifier = Modifier
            .fillMaxSize()
            .then(if (isMobilePortrait) Modifier.verticalScroll(rememberScrollState()) else Modifier)
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

        // Mobile portrait mode: vertical layout according to MobilePortrait.md specs
        if (isMobilePortrait) {
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
            
            // Recent expenses list below summary cards
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("RECENT", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = themeBlack)
                        
                        // Toggle Button
                        Row(modifier = Modifier.border(1.dp, themeBlack)) {
                            listOf(true to "GRP", false to "ITM").forEach { (isGroup, label) ->
                                Box(
                                    modifier = Modifier
                                        .height(24.dp)
                                        .background(if (recentByGroup == isGroup) themeBlack else themeWhite)
                                        .clickable { recentByGroup = isGroup }
                                        .padding(horizontal = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        label,
                                        color = if (recentByGroup == isGroup) themeWhite else themeBlack,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }
                        }
                    }
                    Text("$recentCount", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = themeBlack.copy(alpha = 0.5f))
                }
                
                if (recentCount == 0) {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp).border(2.dp, themeBlack, RectangleShape), contentAlignment = Alignment.Center) {
                        Text("EMPTY", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = themeBlack.copy(alpha=0.3f))
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (recentByGroup) {
                            recentGroups.forEach { group ->
                                val first = group.first()
                                val total = group.sumOf { it.amount }
                                BrutalistCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onNavigateToGroupDetails(first.groupId) },
                                    backgroundColor = themeWhite,
                                    borderColor = themeBlack
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp).fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f).padding(end = 4.dp)) {
                                            Text(first.storeName, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = themeBlack)
                                            Text("${group.size} ITEMS • ${first.category.uppercase()}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = themeBlack.copy(alpha = 0.5f))
                                        }
                                        Text("₹${String.format("%.0f", total)}", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = themeBlack)
                                    }
                                }
                            }
                        } else {
                            recentItems.take(5).forEach { expense ->
                                val title = if (expense.itemDescription.isNotEmpty()) expense.itemDescription else expense.storeName
                                BrutalistCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onNavigateToExpenseDetails(expense.id) },
                                    backgroundColor = themeWhite,
                                    borderColor = themeBlack
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp).fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f).padding(end = 4.dp)) {
                                            Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = themeBlack)
                                            Text(expense.category.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = themeBlack.copy(alpha = 0.5f))
                                        }
                                        Text("₹${String.format("%.0f", expense.amount)}", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = themeBlack)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Budget overview below recent expenses
            var drillDownCategory by remember { mutableStateOf<String?>(null) }
            BrutalistCard(
                modifier = Modifier.fillMaxWidth().height(300.dp),
                backgroundColor = themeWhite
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().background(themeBlack).padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = (drillDownCategory ?: "ALLOCATIONS").uppercase(),
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp,
                            color = themeWhite
                        )
                        if (drillDownCategory != null) {
                            Text(
                                text = "BACK",
                                modifier = Modifier.clickable { drillDownCategory = null },
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp,
                                color = themeWhite,
                                textDecoration = TextDecoration.Underline
                            )
                        }
                    }
                    
                    Column(
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (drillDownCategory == null) {
                            categories.forEach { cat ->
                                val catBudget = categoryBudgets[cat] ?: 0.0
                                val catSpent = monthlyExpenses.filter { it.category == cat }.sumOf { it.amount }
                                BudgetListItem(label = cat, budget = catBudget, spent = catSpent) {
                                    drillDownCategory = cat
                                }
                            }
                        } else {
                            val subs = subcategoriesMap[drillDownCategory!!] ?: emptyList()
                            subs.forEach { sub ->
                                val key = "${drillDownCategory}/$sub"
                                val subBudget = subcategoryBudgets[key] ?: 0.0
                                val subSpent = monthlyExpenses.filter { it.category == drillDownCategory && it.subcategory == sub }.sumOf { it.amount }
                                BudgetListItem(label = sub, budget = subBudget, spent = subSpent)
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Category breakdown chart below budget overview
            Box(modifier = Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
                BrutalistPieChart(
                    expenses = monthlyExpenses,
                    modifier = Modifier.fillMaxSize(),
                    currentMonth = viewedMonth,
                    radiusScale = 0.75f,
                    onCategorySelected = onNavigateToCategory
                )
            }

            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Trends section below category breakdown
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("TRENDS", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = themeBlack)
                Spacer(modifier = Modifier.height(8.dp))
                
                // Timeframe Toggle
                Row(
                    modifier = Modifier.border(1.dp, themeBlack)
                ) {
                    TrendTimeframe.values().forEach { tf ->
                        val label = if (tf == TrendTimeframe.MONTH) "12M" else "YRS"
                        Box(
                            modifier = Modifier
                                .height(38.dp)
                                .background(if (trendTimeframe == tf) themeBlack else themeWhite)
                                .clickable { trendTimeframe = tf }
                                .padding(horizontal = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            @Suppress("DEPRECATION")
                            Text(
                                label,
                                color = if (trendTimeframe == tf) themeWhite else themeBlack,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Dimension selector
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    TrendDimension.values().forEach { dim ->
                        if (dim == TrendDimension.SUBCATEGORY && selectedCategoryForSub == null) {
                            return@forEach
                        }

                        val label = when(dim) {
                            TrendDimension.TOTAL -> "ALL"
                            TrendDimension.CATEGORY -> "CAT"
                            TrendDimension.SUBCATEGORY -> "SUB"
                            TrendDimension.LABEL -> "LBL"
                        }
                        Box(
                            modifier = Modifier
                                .height(38.dp)
                                .background(if (trendDimension == dim) themeBlack else themeWhite)
                                .border(1.dp, themeBlack)
                                .clickable { trendDimension = dim }
                                .padding(horizontal = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (trendDimension == dim) themeWhite else themeBlack,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Trend chart section
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // THE BLACK BOX (Selection Detail)
                    if (selectedTrendValue != null) {
                        Box(
                            modifier = Modifier
                                .height(38.dp)
                                .background(themeBlack)
                                .border(2.dp, themeWhite)
                                .clickable {
                                    selectedTrendPeriod?.let { period ->
                                        if (period is YearMonth) {
                                            onNavigateToMonthExpenses(period, trendDimension, trendItem)
                                        } else if (period is Int) {
                                            onNavigateToYearExpenses(period, trendDimension, trendItem)
                                        }
                                    }
                                }
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "₹${String.format("%.0f", selectedTrendValue!!)}",
                                color = themeWhite,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }
                }

                if (trendDimension != TrendDimension.TOTAL && availableItems.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val displayItem = trendItem ?: "Select"
                        var expanded by remember { mutableStateOf(false) }
                        
                        Box(modifier = Modifier.weight(1f).height(32.dp)) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight()
                                    .background(themeWhite)
                                    .border(1.dp, themeBlack)
                                    .clickable { expanded = true }
                                    .padding(horizontal = 4.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = displayItem.uppercase(),
                                    fontSize = 12.sp,
                                    color = themeBlack,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false },
                                modifier = Modifier.background(themeWhite).border(1.dp, themeBlack)
                            ) {
                                availableItems.forEach { item ->
                                    DropdownMenuItem(
                                        text = { Text(item.uppercase(), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = themeBlack) },
                                        onClick = {
                                            trendItem = item
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                BrutalistBarChart(
                    expenses = filteredExpenses,
                    timeframe = trendTimeframe,
                    modifier = Modifier.fillMaxWidth().height(220.dp),
                    onValueSelected = { value, period ->
                        selectedTrendValue = value
                        selectedTrendPeriod = period
                    },
                    onNavigateToSelection = { period ->
                        if (period is YearMonth) {
                            onNavigateToMonthExpenses(period, trendDimension, trendItem)
                        } else if (period is Int) {
                            onNavigateToYearExpenses(period, trendDimension, trendItem)
                        }
                    }
                )

            }
        } else {
            // Tablet/landscape mode: keep current layout
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp).height(420.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                    BrutalistCalendar(
                        expenses = expensesMap,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        viewedMonth = viewedMonth,
                        onMonthChanged = { viewedMonth = it },
                        onViewExpensesForDate = onNavigateToExpenses,
                        onNewExpenseForDate = onNewExpense
                    )

                    Row(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        var drillDownCategory by remember { mutableStateOf<String?>(null) }
                        
                        Column(
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
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

                            BrutalistCard(
                                modifier = Modifier.fillMaxWidth().weight(1f),
                                backgroundColor = themeWhite
                            ) {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().background(themeBlack).padding(8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = (drillDownCategory ?: "ALLOCATIONS").uppercase(),
                                            fontWeight = FontWeight.Black,
                                            fontSize = 15.sp,
                                            color = themeWhite
                                        )
                                        if (drillDownCategory != null) {
                                            Text(
                                                text = "BACK",
                                                modifier = Modifier.clickable { drillDownCategory = null },
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 13.sp,
                                                color = themeWhite,
                                                textDecoration = TextDecoration.Underline
                                            )
                                        }
                                    }
                                    
                                    Column(
                                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        if (drillDownCategory == null) {
                                            categories.forEach { cat ->
                                                val catBudget = categoryBudgets[cat] ?: 0.0
                                                val catSpent = monthlyExpenses.filter { it.category == cat }.sumOf { it.amount }
                                                BudgetListItem(label = cat, budget = catBudget, spent = catSpent) {
                                                    drillDownCategory = cat
                                                }
                                            }
                                        } else {
                                            val subs = subcategoriesMap[drillDownCategory!!] ?: emptyList()
                                            subs.forEach { sub ->
                                                val key = "${drillDownCategory}/$sub"
                                                val subBudget = subcategoryBudgets[key] ?: 0.0
                                                val subSpent = monthlyExpenses.filter { it.category == drillDownCategory && it.subcategory == sub }.sumOf { it.amount }
                                                BudgetListItem(label = sub, budget = subBudget, spent = subSpent)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                            BrutalistPieChart(
                                expenses = monthlyExpenses,
                                modifier = Modifier.fillMaxSize(),
                                currentMonth = viewedMonth,
                                onCategorySelected = onNavigateToCategory
                            )
                        }
                    }
                }


            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 24.dp).height(280.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Recent Activity Compact
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("RECENT", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = themeBlack)
                            
                            // Toggle Button
                            Row(modifier = Modifier.border(1.dp, themeBlack)) {
                                listOf(true to "GROUP", false to "ITEMS").forEach { (isGroup, label) ->
                                    Box(
                                        modifier = Modifier
                                            .height(28.dp)
                                            .background(if (recentByGroup == isGroup) themeBlack else themeWhite)
                                            .clickable { recentByGroup = isGroup }
                                            .padding(horizontal = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        @Suppress("DEPRECATION")
                                        Text(
                                            label,
                                            color = if (recentByGroup == isGroup) themeWhite else themeBlack,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                    }
                                }
                            }
                        }
                        Text("$recentCount", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = themeBlack.copy(alpha = 0.5f))
                    }
                    
                    if (recentCount == 0) {
                        Box(modifier = Modifier.fillMaxSize().border(2.dp, themeBlack, RectangleShape), contentAlignment = Alignment.Center) {
                            Text("EMPTY", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = themeBlack.copy(alpha=0.3f))
                        }
                    } else {
                        Column(
                            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (recentByGroup) {
                                recentGroups.forEach { group ->
                                    val first = group.first()
                                    val total = group.sumOf { it.amount }
                                    BrutalistCard(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onNavigateToGroupDetails(first.groupId) },
                                        backgroundColor = themeWhite,
                                        borderColor = themeBlack
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp).fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f).padding(end = 4.dp)) {
                                                Text(first.storeName, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = themeBlack)
                                                Text("${group.size} ITEMS • ${first.category.uppercase()}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = themeBlack.copy(alpha = 0.5f))
                                            }
                                            Text("₹${String.format("%.0f", total)}", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = themeBlack)
                                        }
                                    }
                                }
                            } else {
                                recentItems.forEach { expense ->
                                    val title = if (expense.itemDescription.isNotEmpty()) expense.itemDescription else expense.storeName
                                    BrutalistCard(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onNavigateToExpenseDetails(expense.id) },
                                        backgroundColor = themeWhite,
                                        borderColor = themeBlack
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp).fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f).padding(end = 4.dp)) {
                                                Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = themeBlack)
                                                Text(expense.category.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = themeBlack.copy(alpha = 0.5f))
                                            }
                                            Text("₹${String.format("%.0f", expense.amount)}", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = themeBlack)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Bar Chart Section
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text("TRENDS", fontWeight = FontWeight.Bold, fontSize = 22.sp)
                            
                            
                            // Timeframe Toggle
                            Row(
                                modifier = Modifier.border(1.dp, themeBlack)
                            ) {
                                TrendTimeframe.values().forEach { tf ->
                                    val label = if (tf == TrendTimeframe.MONTH) "12M" else "YRS"
                                    Box(
                                        modifier = Modifier
                                            .height(38.dp)
                                            .background(if (trendTimeframe == tf) themeBlack else themeWhite)
                                            .clickable { trendTimeframe = tf }
                                            .padding(horizontal = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        @Suppress("DEPRECATION")
                                        Text(
                                            label,
                                            color = if (trendTimeframe == tf) themeWhite else themeBlack,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        // THE BLACK BOX (Selection Detail)
                        if (selectedTrendValue != null) {
                            Box(
                                modifier = Modifier
                                    .height(38.dp)
                                    .background(themeBlack)
                                    .border(2.dp, themeWhite)
                                    .clickable { selectedTrendValue = null },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = selectedTrendValue.toString(),
                                    color = themeWhite,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            TrendDimension.values().forEach { dim ->
                                if (dim == TrendDimension.SUBCATEGORY && selectedCategoryForSub == null) {
                                    return@forEach
                                }

                                val label = when(dim) {
                                    TrendDimension.TOTAL -> "ALL"
                                    TrendDimension.CATEGORY -> "CAT"
                                    TrendDimension.SUBCATEGORY -> "SUB"
                                    TrendDimension.LABEL -> "LBL"
                                }
                                Box(
                                    modifier = Modifier
                                        .height(38.dp)
                                        .background(if (trendDimension == dim) themeBlack else themeWhite)
                                        .border(1.dp, themeBlack)
                                        .clickable { trendDimension = dim }
                                        .padding(horizontal = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        color = if (trendDimension == dim) themeWhite else themeBlack,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }
                        }
                    }
                    // Item Selector (if needed)
                    if (trendDimension != TrendDimension.TOTAL && availableItems.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val displayItem = trendItem ?: "Select"
                            var expanded by remember { mutableStateOf(false) }
                            
                            Box(modifier = Modifier.weight(1f).height(32.dp)) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .fillMaxHeight()
                                        .background(themeWhite)
                                        .border(1.dp, themeBlack)
                                        .clickable { expanded = true }
                                        .padding(horizontal = 4.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Text(
                                        text = displayItem.uppercase(),
                                        fontSize = 12.sp,
                                        color = themeBlack,
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                DropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false },
                                    modifier = Modifier.background(themeWhite).border(1.dp, themeBlack)
                                ) {
                                    availableItems.forEach { item ->
                                        DropdownMenuItem(
                                            text = { Text(item.uppercase(), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = themeBlack) },
                                            onClick = {
                                                trendItem = item
                                                expanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    BrutalistBarChart(
                        expenses = filteredExpenses,
                        timeframe = trendTimeframe,
                        modifier = Modifier.fillMaxSize(),
                        onValueSelected = { value, period ->
                            selectedTrendValue = value
                            selectedTrendPeriod = period
                        },
                        onNavigateToSelection = { period ->
                            if (period is YearMonth) {
                                onNavigateToMonthExpenses(period, trendDimension, trendItem)
                            } else if (period is Int) {
                                onNavigateToYearExpenses(period, trendDimension, trendItem)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ExpenseListScreen(
    expenses: List<Expense>,
    isMobilePortrait: Boolean = false,
    viewingDate: LocalDate? = null,
    initialStartDate: LocalDate? = null,
    initialEndDate: LocalDate? = null,
    initialCategory: String? = null,
    initialSubcategory: String? = null,
    initialLabel: String? = null,
    initialSelectedExpenseId: String? = null,
    initialSelectedGroupId: String? = null,
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
    var filterCategories by remember { mutableStateOf(if (initialCategory != null) setOf(initialCategory) else emptySet<String>()) }
    var filterSubcategories by remember { mutableStateOf(if (initialSubcategory != null) setOf(initialSubcategory) else emptySet<String>()) }
    
    var exactDateFilter by remember { mutableStateOf<LocalDate?>(null) }
    var startDateFilter by remember { mutableStateOf<LocalDate?>(null) }
    var endDateFilter by remember { mutableStateOf<LocalDate?>(null) }

    var filterLabels by remember { mutableStateOf(if (initialLabel != null) setOf(initialLabel) else emptySet<String>()) }
    var filtersExpanded by remember { mutableStateOf(false) }

    // Sync filters if initial state changes from external navigation
    LaunchedEffect(viewingDate, initialStartDate, initialEndDate, initialCategory, initialSubcategory, initialLabel, initialSelectedExpenseId, initialSelectedGroupId) {
        if (initialCategory != null) {
            filterCategories = setOf(initialCategory)
        }
        if (initialSubcategory != null) {
            filterSubcategories = setOf(initialSubcategory)
        }
        if (initialLabel != null) {
            filterLabels = setOf(initialLabel)
        }
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
                
                viewByStore = false
                selectedExpense = found
            }
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
                
                viewByStore = true
                selectedGroup = group
            }
        }
    }


    var showExactDatePicker by remember { mutableStateOf(false) }
    var showRangeDatePicker by remember { mutableStateOf(false) }

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
        if (isMobilePortrait) {
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
                            Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                                Text("STORE DETAILS", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = themeBlack, modifier = Modifier.padding(bottom = 16.dp))
                                
                                Text("STORE NAME", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = themeBlack.copy(alpha=0.6f))
                                Text(group.first().storeName, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = themeBlack, modifier = Modifier.padding(bottom = 12.dp))
                                
                                Text("DATE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = themeBlack.copy(alpha=0.6f))
                                Text(group.first().date.format(dateFormatter).uppercase(), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = themeBlack, modifier = Modifier.padding(bottom = 12.dp))
                                
                                val total = group.sumOf { it.amount }
                                Text("TOTAL AMOUNT", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = themeBlack.copy(alpha=0.6f))
                                Text("₹${String.format("%.0f", total)}", fontSize = 32.sp, fontWeight = FontWeight.Black, color = themeBlack, modifier = Modifier.padding(bottom = 24.dp))
                                
                                Text("TRANSACTIONS (${group.size})", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = themeBlack.copy(alpha=0.6f))
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                group.forEachIndexed { i, subTx ->
                                    BrutalistCard(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), backgroundColor = themeWhite) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text("ITEM ${i+1}", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = themeBlack, modifier = Modifier.padding(bottom = 8.dp))
                                            
                                            // Item description
                                            val desc = if (subTx.itemDescription.isNotEmpty()) subTx.itemDescription else subTx.category
                                            Text(desc, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = themeBlack, modifier = Modifier.padding(bottom = 4.dp))
                                            
                                            // Amount and quantity
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text("₹${String.format("%.2f", subTx.amount)}", fontWeight = FontWeight.Black, fontSize = 18.sp, color = themeBlack)
                                                    if (subTx.quantity != null && subTx.quantity > 0) {
                                                        val quantityText = if (subTx.unit != null) "${subTx.quantity} ${subTx.unit}" else subTx.quantity.toString()
                                                        Text(quantityText, fontSize = 12.sp, color = themeBlack.copy(alpha=0.7f))
                                                    }
                                                }
                                            }
                                            
                                            // Category and subcategory
                                            Text("${subTx.category} / ${subTx.subcategory}", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = themeBlack.copy(alpha=0.8f), modifier = Modifier.padding(bottom = 4.dp))
                                            
                                            // Labels
                                            if (subTx.labels.isNotEmpty()) {
                                                @Suppress("DEPRECATION")
                                                Text(subTx.labels.joinToString(", ").uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = themeBlack.copy(alpha=0.6f), modifier = Modifier.padding(bottom = 4.dp))
                                            }
                                            
                                            // Payment info
                                            if (!subTx.paymentMode.isNullOrBlank() || !subTx.paidVia.isNullOrBlank()) {
                                                val paymentInfo = listOfNotNull(
                                                    subTx.paymentMode.takeIf { !it.isNullOrBlank() },
                                                    subTx.paidVia.takeIf { !it.isNullOrBlank() }
                                                ).joinToString(" • ")
                                                Text(paymentInfo, fontSize = 10.sp, color = themeBlack.copy(alpha=0.7f), modifier = Modifier.padding(bottom = 4.dp))
                                            }
                                            
                                            // Notes
                                            if (subTx.notes.isNotBlank()) {
                                                Text(subTx.notes, fontSize = 11.sp, color = themeBlack.copy(alpha=0.8f), modifier = Modifier.padding(bottom = 8.dp))
                                            }
                                            
                                            // Action buttons
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                BrutalistButton(
                                                    onClick = { onEditExpense(subTx) },
                                                    modifier = Modifier.weight(1f).height(36.dp),
                                                    containerColor = themeBlack
                                                ) {
                                                    Text("EDIT", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = themeWhite)
                                                }
                                                BrutalistButton(
                                                    onClick = {
                                                        android.util.Log.d("ExpenseListScreen", "DELETE button clicked for expense: ${subTx.id}")
                                                        expenseToDelete = subTx
                                                    },
                                                    modifier = Modifier.weight(1f).height(36.dp),
                                                    containerColor = themeWhite
                                                ) {
                                                    Text("DELETE", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = themeBlack)
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
                            Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                                Text("EXPENSE DETAILS", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = themeBlack, modifier = Modifier.padding(bottom = 16.dp))

                                // Basic Information Section
                                Text("BASIC INFO", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = themeBlack.copy(alpha=0.7f), modifier = Modifier.padding(bottom = 8.dp))
                                
                                val title = if (expense.itemDescription.isNotEmpty()) expense.itemDescription else expense.storeName
                                Text("ITEM DESCRIPTION", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = themeBlack.copy(alpha=0.6f))
                                Text(title.ifEmpty { "No description" }, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = themeBlack, modifier = Modifier.padding(bottom = 12.dp))

                                Text("STORE NAME", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = themeBlack.copy(alpha=0.6f))
                                Text(expense.storeName, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = themeBlack, modifier = Modifier.padding(bottom = 12.dp))

                                Text("DATE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = themeBlack.copy(alpha=0.6f))
                                Text(expense.date.format(dateFormatter).uppercase(), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = themeBlack, modifier = Modifier.padding(bottom = 12.dp))

                                Text("CATEGORY", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = themeBlack.copy(alpha=0.6f))
                                Text("${expense.category} / ${expense.subcategory}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = themeBlack, modifier = Modifier.padding(bottom = 12.dp))

                                // Amount and Quantity Section
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("FINANCIAL INFO", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = themeBlack.copy(alpha=0.7f), modifier = Modifier.padding(bottom = 8.dp))
                                
                                Text("AMOUNT", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = themeBlack.copy(alpha=0.6f))
                                Text("₹${String.format("%.2f", expense.amount)}", fontSize = 28.sp, fontWeight = FontWeight.Black, color = themeBlack, modifier = Modifier.padding(bottom = 12.dp))

                                if (expense.quantity != null && expense.quantity > 0) {
                                    Text("QUANTITY", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = themeBlack.copy(alpha=0.6f))
                                    val quantityText = if (expense.unit != null) "${expense.quantity} ${expense.unit}" else expense.quantity.toString()
                                    Text(quantityText, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = themeBlack, modifier = Modifier.padding(bottom = 12.dp))
                                }

                                // Payment Information Section
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("PAYMENT INFO", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = themeBlack.copy(alpha=0.7f), modifier = Modifier.padding(bottom = 8.dp))
                                
                                if (!expense.paymentMode.isNullOrBlank()) {
                                    Text("PAYMENT MODE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = themeBlack.copy(alpha=0.6f))
                                    Text(expense.paymentMode, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = themeBlack, modifier = Modifier.padding(bottom = 12.dp))
                                }

                                if (!expense.paidVia.isNullOrBlank()) {
                                    Text("PAID VIA", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = themeBlack.copy(alpha=0.6f))
                                    Text(expense.paidVia, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = themeBlack, modifier = Modifier.padding(bottom = 12.dp))
                                }

                                // Additional Information Section
                                if (expense.labels.isNotEmpty() || !expense.notes.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("ADDITIONAL INFO", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = themeBlack.copy(alpha=0.7f), modifier = Modifier.padding(bottom = 8.dp))
                                }

                                if (expense.labels.isNotEmpty()) {
                                    Text("LABELS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = themeBlack.copy(alpha=0.6f))
                                    @Suppress("DEPRECATION")
                                    Text(expense.labels.joinToString(", ").uppercase(), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = themeBlack, modifier = Modifier.padding(bottom = 12.dp))
                                }

                                if (!expense.notes.isNullOrBlank()) {
                                    Text("NOTES", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = themeBlack.copy(alpha=0.6f))
                                    Text(expense.notes ?: "", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = themeBlack, modifier = Modifier.padding(bottom = 12.dp))
                                }

                                
                                Spacer(modifier = Modifier.height(24.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    BrutalistButton(
                                        onClick = { onEditExpense(expense) },
                                        modifier = Modifier.weight(1f).height(56.dp),
                                        containerColor = themeWhite
                                    ) {
                                        Text("EDIT", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = themeBlack)
                                    }
                                    BrutalistButton(
                                        onClick = {
                                            android.util.Log.d("ExpenseListScreen", "DELETE button clicked for expense: ${expense.id}")
                                            expenseToDelete = expense
                                        },
                                        modifier = Modifier.weight(1f).height(56.dp),
                                        containerColor = themeBlack
                                    ) {
                                        @Suppress("DEPRECATION")
                                        Text("DELETE", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = themeWhite)
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
                            viewingDate != null -> "EXPENSES FOR ${viewingDate.format(dateFormatter).uppercase()}"
                            initialSubcategory != null -> "EXPENSES: ${initialSubcategory.uppercase()}"
                            initialCategory != null -> "EXPENSES: ${initialCategory.uppercase()}"
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
                            if (viewingDate != null || initialCategory != null || initialSubcategory != null) {
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
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    BrutalistButton(onClick = { showExactDatePicker = true }, modifier = Modifier.weight(1f), containerColor = themeWhite) {
                                        Text(exactDateFilter?.format(dateFormatter) ?: "EXACT DATE", color = themeBlack, fontSize = 12.sp)
                                    }
                                    BrutalistButton(onClick = { showRangeDatePicker = true }, modifier = Modifier.weight(1f), containerColor = themeWhite) {
                                        val ranTxt = if (startDateFilter != null && endDateFilter != null) {
                                            "${startDateFilter!!.format(dateFormatter)} - ${endDateFilter!!.format(dateFormatter)}"
                                        } else "DATE RANGE"
                                        Text(ranTxt, color = themeBlack, fontSize = 12.sp)
                                    }
                                }
                                if (exactDateFilter != null || startDateFilter != null || filterCategories.isNotEmpty() || filterSubcategories.isNotEmpty() || filterLabels.isNotEmpty() || searchQuery.isNotBlank()) {
                                    BrutalistButton(
                                        onClick = {
                                            exactDateFilter = null
                                            startDateFilter = null
                                            endDateFilter = null
                                            filterCategories = emptySet()
                                            filterSubcategories = emptySet()
                                            filterLabels = emptySet()
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
                                Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(storeName.uppercase(), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = themeBlack)
                                        Text("₹${String.format("%.0f", groupTotal)}", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = themeBlack)
                                    }
                                    
                                    group.forEach { expense ->
                                        val title = if (expense.itemDescription.isNotEmpty()) expense.itemDescription else expense.storeName
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(title, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = themeBlack)
                                                Text(expense.category.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = themeBlack.copy(alpha = 0.5f))
                                            }
                                            Text("₹${String.format("%.0f", expense.amount)}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = themeBlack)
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
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = themeBlack)
                                        Text(expense.category.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = themeBlack.copy(alpha = 0.5f))
                                    }
                                    Text("₹${String.format("%.0f", expense.amount)}", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = themeBlack)
                                }
                            }
                        }
                    }
                }
            }
        } else {

            // Tablet/landscape mode: keep current layout
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val titleText = when {
                    viewingDate != null -> "EXPENSES FOR ${viewingDate.format(dateFormatter).uppercase()}"
                    initialSubcategory != null -> "EXPENSES: ${initialSubcategory.uppercase()}"
                    initialCategory != null -> "EXPENSES: ${initialCategory.uppercase()}"
                    else -> "EXPENSES"
                }
                Text(
                    text = titleText,
                    fontSize = if (viewingDate != null || initialCategory != null || initialSubcategory != null) 20.sp else 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    lineHeight = 24.sp,
                    modifier = Modifier.padding(end = 16.dp)
                )
                
                BrutalistSearchField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = "SEARCH...",
                    modifier = Modifier.weight(1f).height(48.dp)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(start = 16.dp)) {
                    if (viewingDate != null || initialCategory != null || initialSubcategory != null) {
                        BrutalistButton(onClick = onClearFilter, containerColor = themeWhite, modifier = Modifier.height(44.dp)) {
                            @Suppress("DEPRECATION")
                            Text("CLEAR", color = themeBlack, fontWeight = FontWeight.Black, fontSize = 12.sp)
                        }
                    }
                    BrutalistButton(onClick = onAddExpense, modifier = Modifier.height(44.dp)) {
                        Icon(Icons.Default.Add, contentDescription = "Add Expense", tint = themeWhite)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            BrutalistButton(
                onClick = { filtersExpanded = !filtersExpanded },
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                containerColor = if (filtersExpanded) themeBlack else themeWhite
            ) {
                Text(if (filtersExpanded) "HIDE FILTERS" else "SHOW FILTERS", color = if (filtersExpanded) themeWhite else themeBlack, fontWeight = FontWeight.Bold)
            }

        if (filtersExpanded) {
            val categories = expenses.map { it.category }.distinct().sorted()
            val subcats = expenses.map { it.subcategory }.distinct().sorted()
            val availableLabels = expenses.flatMap { it.labels }.distinct().sorted()

            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BrutalistMultiSelectDropdown(
                    label = "Categories",
                    options = categories,
                    selectedOptions = filterCategories,
                    onOptionToggled = {
                        filterCategories = if (filterCategories.contains(it)) filterCategories - it else filterCategories + it
                    },
                    modifier = Modifier.weight(1f)
                )
                BrutalistMultiSelectDropdown(
                    label = "Subcategories",
                    options = subcats,
                    selectedOptions = filterSubcategories,
                    onOptionToggled = {
                        filterSubcategories = if (filterSubcategories.contains(it)) filterSubcategories - it else filterSubcategories + it
                    },
                    modifier = Modifier.weight(1f)
                )
                BrutalistMultiSelectDropdown(
                    label = "Labels",
                    options = availableLabels,
                    selectedOptions = filterLabels,
                    onOptionToggled = {
                        filterLabels = if (filterLabels.contains(it)) filterLabels - it else filterLabels + it
                    },
                    modifier = Modifier.weight(1f)
                )
            }
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BrutalistButton(onClick = { showExactDatePicker = true }, modifier = Modifier.weight(1f), containerColor = themeWhite) {
                    Text(exactDateFilter?.format(dateFormatter) ?: "EXACT DATE", color = themeBlack, fontSize = 12.sp)
                }
                BrutalistButton(onClick = { showRangeDatePicker = true }, modifier = Modifier.weight(1f), containerColor = themeWhite) {
                    val ranTxt = if (startDateFilter != null && endDateFilter != null) {
                        "${startDateFilter?.format(dateFormatter) ?: ""} - ${endDateFilter?.format(dateFormatter) ?: ""}"
                    } else "DATE RANGE"
                    Text(ranTxt, color = themeBlack, fontSize = 10.sp)
                }
            }
            if (exactDateFilter != null || startDateFilter != null || filterCategories.isNotEmpty() || filterSubcategories.isNotEmpty() || filterLabels.isNotEmpty() || searchQuery.isNotBlank()) {
                BrutalistButton(onClick = {
                    exactDateFilter = null
                    startDateFilter = null
                    endDateFilter = null
                    filterCategories = emptySet()
                    filterSubcategories = emptySet()
                    filterLabels = emptySet()
                    searchQuery = ""
                }, modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), containerColor = themeWhite) {
                    Text("CLEAR FILTERS", color = themeBlack, fontWeight = FontWeight.Bold)
                }
            }
        }


        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BrutalistButton(
                onClick = { 
                    if (!viewByStore) {
                        viewByStore = true 
                        selectedGroup = null
                        selectedExpense = null
                    }
                },
                modifier = Modifier.weight(1f),
                containerColor = if (viewByStore) themeBlack else themeWhite
            ) {
                Text("BY STORE", color = if (viewByStore) themeWhite else themeBlack, fontWeight = FontWeight.Bold)
            }
            BrutalistButton(
                onClick = { 
                    if (viewByStore) {
                        viewByStore = false 
                        selectedGroup = null
                        selectedExpense = null
                    }
                },
                modifier = Modifier.weight(1f),
                containerColor = if (!viewByStore) themeBlack else themeWhite
            ) {
                Text("BY ITEM", color = if (!viewByStore) themeWhite else themeBlack, fontWeight = FontWeight.Bold)
            }
        }

        if (displayExpenses.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("NO EXPENSES", fontWeight = FontWeight.Bold, color = themeBlack.copy(alpha=0.5f))
            }
        } else {
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
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Left Half: List
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (viewByStore) {
                        items(groupedExpenses) { group ->
                            val isSelected = group == selectedGroup
                            BrutalistCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedGroup = group },
                                backgroundColor = if (isSelected) themeBlack else themeWhite
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            val title = group.first().storeName
                                            Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = if (isSelected) themeWhite else themeBlack)
                                            Text(group.first().date.format(dateFormatter).uppercase(), fontSize = 12.sp, color = if (isSelected) themeWhite else themeBlack)
                                        }
                                        val groupTotal = group.sumOf { it.amount }
                                        Text("₹${String.format("%.2f", groupTotal)}", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = if (isSelected) themeWhite else themeBlack)
                                    }
                                }
                            }
                        }
                    } else {
                        items(displayExpenses) { expense ->
                            val isSelected = expense == selectedExpense
                            BrutalistCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedExpense = expense },
                                backgroundColor = if (isSelected) themeBlack else themeWhite
                            ) {
                                Row(
                                    modifier = Modifier
                                        .padding(16.dp)
                                        .fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                        val title = if (expense.itemDescription.isNotEmpty()) expense.itemDescription else expense.storeName
                                        Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = if (isSelected) themeWhite else themeBlack, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        val labelStore = if (expense.itemDescription.isNotEmpty()) "${expense.storeName} • " else ""
                                        Text("${labelStore}${expense.date.format(dateFormatter).uppercase()}", fontSize = 12.sp, color = if (isSelected) themeWhite else themeBlack, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Text("₹${String.format("%.2f", expense.amount)}", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = if (isSelected) themeWhite else themeBlack)
                                }
                            }
                        }
                    }
                }

                // Right Half: Details
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    if (viewByStore && selectedGroup != null) {
                        val group = selectedGroup!!
                        BrutalistCard(modifier = Modifier.fillMaxSize()) {
                            Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                                Text("STORE DETAILS", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = themeBlack, modifier = Modifier.padding(bottom = 16.dp))
                                
                                Text("STORE NAME", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = themeBlack.copy(alpha=0.6f))
                                Text(group.first().storeName, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = themeBlack, modifier = Modifier.padding(bottom = 12.dp))
                                
                                Text("DATE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = themeBlack.copy(alpha=0.6f))
                                Text(group.first().date.format(dateFormatter).uppercase(), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = themeBlack, modifier = Modifier.padding(bottom = 12.dp))
                                
                                val total = group.sumOf { it.amount }
                                Text("TOTAL AMOUNT", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = themeBlack.copy(alpha=0.6f))
                                Text("₹${String.format("%.2f", total)}", fontSize = 32.sp, fontWeight = FontWeight.Black, color = themeBlack, modifier = Modifier.padding(bottom = 24.dp))
                                
                                Text("TRANSACTIONS (${group.size})", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = themeBlack.copy(alpha=0.6f))
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                group.forEachIndexed { i, subTx ->
                                    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                                        Text("ITEM ${i+1}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            val desc = if (subTx.itemDescription.isNotEmpty()) subTx.itemDescription else subTx.category
                                            Text(desc, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                            Text("₹${String.format("%.2f", subTx.amount)}", fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp))
                                        }
                                        Text("${subTx.category} • ${subTx.subcategory}", fontSize = 12.sp, color = themeBlack.copy(alpha=0.7f))
                                        if (subTx.labels.isNotEmpty()) {
                                            Text(subTx.labels.joinToString(", ").uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = themeBlack.copy(alpha=0.5f))
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            BrutalistButton(
                                                onClick = { onEditExpense(subTx) },
                                                modifier = Modifier.weight(1f).height(48.dp),
                                                containerColor = themeWhite
                                            ) {
                                                Text("EDIT", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = themeBlack)
                                            }
                                            BrutalistButton(
                                                onClick = {
                                                    android.util.Log.d("ExpenseListScreen", "DELETE button clicked for expense: ${subTx.id}")
                                                    expenseToDelete = subTx
                                                },
                                                modifier = Modifier.weight(1f).height(48.dp),
                                                containerColor = themeBlack
                                            ) {
                                                Text("DELETE", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = themeWhite)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else if (!viewByStore && selectedExpense != null) {
                        val expense = selectedExpense!!
                        BrutalistCard(modifier = Modifier.fillMaxSize()) {
                            Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                                Text("EXPENSE DETAILS", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = themeBlack, modifier = Modifier.padding(bottom = 16.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        val title = if (expense.itemDescription.isNotEmpty()) expense.itemDescription else expense.storeName
                                        Text("ITEM", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = themeBlack.copy(alpha=0.6f))
                                        Text(title, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = themeBlack, modifier = Modifier.padding(bottom = 12.dp))

                                        Text("STORE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = themeBlack.copy(alpha=0.6f))
                                        Text(expense.storeName, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = themeBlack, modifier = Modifier.padding(bottom = 12.dp))

                                        Text("DATE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = themeBlack.copy(alpha=0.6f))
                                        Text(expense.date.format(dateFormatter).uppercase(), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = themeBlack, modifier = Modifier.padding(bottom = 12.dp))

                                        Text("CATEGORY", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = themeBlack.copy(alpha=0.6f))
                                        Text("${expense.category} / ${expense.subcategory}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = themeBlack, modifier = Modifier.padding(bottom = 12.dp))

                                        Text("AMOUNT", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = themeBlack.copy(alpha=0.6f))
                                        Text("₹${String.format("%.2f", expense.amount)}", fontSize = 32.sp, fontWeight = FontWeight.Black, color = themeBlack, modifier = Modifier.padding(bottom = 12.dp))

                                        if (expense.labels.isNotEmpty()) {
                                            Text("LABELS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = themeBlack.copy(alpha=0.6f))
                                            Text(expense.labels.joinToString(", ").uppercase(), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = themeBlack, modifier = Modifier.padding(bottom = 12.dp))
                                        }
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        if (!expense.notes.isNullOrBlank()) {
                                            Text("NOTES", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = themeBlack.copy(alpha=0.6f))
                                            Text(expense.notes ?: "", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = themeBlack, modifier = Modifier.padding(bottom = 12.dp))
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(24.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    BrutalistButton(
                                        onClick = { onEditExpense(expense) },
                                        modifier = Modifier.weight(1f).height(56.dp),
                                        containerColor = themeWhite
                                    ) {
                                        Text("EDIT", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = themeBlack)
                                    }
                                    BrutalistButton(
                                        onClick = {
                                            android.util.Log.d("ExpenseListScreen", "DELETE button clicked for expense: ${expense.id}")
                                            expenseToDelete = expense
                                        },
                                        modifier = Modifier.weight(1f).height(56.dp),
                                        containerColor = themeBlack
                                    ) {
                                        Text("DELETE", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = themeWhite)
                                    }
                                }
                            }
                        }
                    } else {
                        BrutalistCard(modifier = Modifier.fillMaxSize(), backgroundColor = themeWhite) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    "SELECT AN EXPENSE\nTO VIEW DETAILS",
                                    textAlign = TextAlign.Center,
                                    color = themeBlack.copy(alpha = 0.4f),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
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
    isMobilePortrait: Boolean = false,
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
    onClearAllData: () -> Unit = {}
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
            .then(if (isMobilePortrait) Modifier.verticalScroll(rememberScrollState()) else Modifier)
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 16.dp)
    ) {
        // Mobile portrait mode: re-arrange according to MobilePortrait.md specs
        if (isMobilePortrait) {
            Text(
                text = "SETTINGS",
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

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
        } else {
            // Tablet/landscape mode: keep current layout
            Text(
                text = "SETTINGS",
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier
                    .padding(bottom = 16.dp, start = 4.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                onDevModePressStart()
                                tryAwaitRelease()
                                onDevModePressEnd()
                                onToggleDevMode()
                            }
                        )
                    }
            )

            // Sync section
            SyncSection(
                isSignedIn = isSignedIn,
                onSignIn = onSignIn,
                onSignOut = onSignOut,
                onUploadBackup = onUploadBackup,
                onDownloadBackup = onDownloadBackup,
                onViewBackups = onViewBackups
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Five parallel columns
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                // First row: Categories, Subcategories, Labels
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ManageableColumn(
                        title = "CATEGORIES",
                        items = categories,
                        onAdd = onAddCategory,
                        onEdit = onEditCategory,
                        onDelete = onDeleteCategory,
                        selectedIndex = categories.indexOf(selectedCategory),
                        onItemSelected = { index: Int -> selectedCategory = categories[index] },
                        modifier = Modifier.weight(1f)
                    )
                    ManageableColumn(
                        title = "SUBCATEGORIES",
                        items = currentSubcategories,
                        onAdd = { name -> selectedCategory?.let { cat -> onAddSubcategory(cat, name) } },
                        onEdit = { index, newValue -> selectedCategory?.let { cat -> onEditSubcategory(cat, index, newValue) } },
                        onDelete = { index: Int -> selectedCategory?.let { cat -> onDeleteSubcategory(cat, index) } },
                        parentLabel = selectedCategory,
                        modifier = Modifier.weight(1f)
                    )
                    ManageableColumn(
                        title = "LABELS",
                        items = labels,
                        onAdd = onAddLabel,
                        onEdit = onEditLabel,
                        onDelete = onDeleteLabel,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                // Second row: Payment Modes, Paid Via
                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ManageableColumn(
                        title = "PAYMENT MODES",
                        items = paymentModes,
                        onAdd = onAddPaymentMode,
                        onEdit = onEditPaymentMode,
                        onDelete = onDeletePaymentMode,
                        modifier = Modifier.weight(1f)
                    )
                    ManageableColumn(
                        title = "PAID VIA",
                        items = paidVia,
                        onAdd = onAddPaidVia,
                        onEdit = onEditPaidVia,
                        onDelete = onDeletePaidVia,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.weight(1f)) // Empty space to balance layout
                }
            }
        }

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
