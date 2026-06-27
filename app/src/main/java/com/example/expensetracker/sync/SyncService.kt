package com.example.expensetracker.sync

import android.content.Context
import com.example.expensetracker.data.AppData
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.example.expensetracker.data.DataRepository
import com.example.expensetracker.model.Expense
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonSyntaxException
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

enum class ConflictResolutionStrategy {
    KEEP_LOCAL, KEEP_REMOTE, MERGE_BY_DATE
}

data class SyncResult(
    val success: Boolean,
    val message: String,
    val expensesAdded: Int = 0,
    val expensesUpdated: Int = 0,
    val expensesRemoved: Int = 0,
    val conflictsResolved: Int = 0
)

class SyncService(private val context: Context) {
    
    // Properly configured Gson with LocalDate TypeAdapter
    private val gson: Gson = GsonBuilder()
        .registerTypeAdapter(LocalDate::class.java, object : TypeAdapter<LocalDate>() {
            private val formatter = DateTimeFormatter.ISO_LOCAL_DATE
            override fun write(out: JsonWriter, value: LocalDate?) {
                out.value(value?.format(formatter))
            }
            override fun read(`in`: JsonReader): LocalDate? {
                return try {
                    val dateStr = `in`.nextString()
                    if (dateStr == null || dateStr == "0000-00-00" || dateStr.isBlank()) {
                        LocalDate.now()
                    } else {
                        LocalDate.parse(dateStr, formatter)
                    }
                } catch (e: Exception) {
                    LocalDate.now()
                }
            }
        })
        .setPrettyPrinting()
        .create()
    private val driveManager = SimpleGoogleDriveManager(context)
    
    suspend fun uploadToDrive(): Result<SyncResult> {
        return try {
            if (!driveManager.isSignedIn()) {
                return Result.failure(Exception("Not signed in to Google Drive"))
            }
            
            val localData = DataRepository.load(context)
            val jsonData = gson.toJson(localData)
            
            val backupInfo = driveManager.uploadBackup(jsonData)
            
            backupInfo.fold(
                onSuccess = { 
                    Result.success(SyncResult(
                        success = true,
                        message = "Successfully uploaded backup: ${it.fileName}",
                        expensesAdded = localData.expenses.size
                    ))
                },
                onFailure = { Result.failure(it) }
            )
            
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun downloadFromDrive(
        fileId: String,
        conflictStrategy: ConflictResolutionStrategy = ConflictResolutionStrategy.MERGE_BY_DATE
    ): Result<SyncResult> {
        return try {
            if (!driveManager.isSignedIn()) {
                return Result.failure(Exception("Not signed in to Google Drive"))
            }
            
            val jsonDataResult = driveManager.downloadBackup(fileId)
            
            jsonDataResult.fold(
                onSuccess = { jsonData ->
                    validateAndMergeData(jsonData, conflictStrategy)
                },
                onFailure = { Result.failure(it) }
            )
            
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun listAvailableBackups(): Result<List<BackupInfo>> {
        return try {
            if (!driveManager.isSignedIn()) {
                return Result.failure(Exception("Not signed in to Google Drive"))
            }
            
            driveManager.listBackups()
            
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun deleteBackup(fileId: String): Result<Boolean> {
        return try {
            if (!driveManager.isSignedIn()) {
                return Result.failure(Exception("Not signed in to Google Drive"))
            }
            
            driveManager.deleteBackup(fileId)
            
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private suspend fun validateAndMergeData(
        jsonData: String,
        conflictStrategy: ConflictResolutionStrategy
    ): Result<SyncResult> {
        return withContext(Dispatchers.IO) {
            try {
                // Validate JSON format
                val remoteData: AppData
                try {
                    remoteData = gson.fromJson(jsonData, AppData::class.java)
                        ?: return@withContext Result.failure(Exception("Invalid data format"))
                } catch (e: JsonSyntaxException) {
                    return@withContext Result.failure(Exception("Corrupted backup file"))
                }
                
                // Validate data integrity
                val validationResult = validateDataIntegrity(remoteData)
                if (!validationResult.isValid) {
                    return@withContext Result.failure(Exception(validationResult.errorMessage))
                }
                
                val localData = DataRepository.load(context)
                
                // Apply conflict resolution strategy
                when (conflictStrategy) {
                    ConflictResolutionStrategy.KEEP_LOCAL -> {
                        Result.success(SyncResult(
                            success = true,
                            message = "Download completed. Local data kept."
                        ))
                    }
                    ConflictResolutionStrategy.KEEP_REMOTE -> {
                        DataRepository.save(context, remoteData)
                        Result.success(SyncResult(
                            success = true,
                            message = "Download completed. Remote data applied.",
                            expensesAdded = remoteData.expenses.size
                        ))
                    }
                    ConflictResolutionStrategy.MERGE_BY_DATE -> {
                        val mergeResult = mergeExpenses(localData, remoteData)
                        val mergedData = AppData(
                            expenses = mergeResult.mergedExpenses,
                            categories = mergeDataLists(localData.categories, remoteData.categories),
                            subcategoriesMap = mergeSubcategoriesMap(localData.subcategoriesMap, remoteData.subcategoriesMap),
                            labels = mergeDataLists(localData.labels, remoteData.labels),
                            paymentModes = mergeDataLists(localData.paymentModes, remoteData.paymentModes),
                            paidVia = mergeDataLists(localData.paidVia, remoteData.paidVia),
                            storeHistory = mergeDataLists(localData.storeHistory, remoteData.storeHistory),
                            categoryBudgets = mergeBudgets(localData.categoryBudgets, remoteData.categoryBudgets),
                            subcategoryBudgets = mergeBudgets(localData.subcategoryBudgets, remoteData.subcategoryBudgets),
                            isDarkTheme = remoteData.isDarkTheme // Keep remote theme preference
                        )
                        
                        DataRepository.save(context, mergedData)
                        
                        Result.success(SyncResult(
                            success = true,
                            message = "Successfully merged data from backup.",
                            expensesAdded = mergeResult.expensesAdded,
                            expensesUpdated = mergeResult.expensesUpdated,
                            expensesRemoved = mergeResult.expensesRemoved,
                            conflictsResolved = mergeResult.conflictsResolved
                        ))
                    }
                }
                
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    private fun validateDataIntegrity(data: AppData): ValidationResult {
        // Validate expenses
        data.expenses.forEach { expense ->
            if (expense.id.isBlank()) {
                return ValidationResult(false, "Invalid expense: missing ID")
            }
            if (expense.amount < 0) {
                return ValidationResult(false, "Invalid expense: negative amount")
            }
            if (expense.date.isAfter(LocalDate.now().plusDays(1))) {
                return ValidationResult(false, "Invalid expense: future date")
            }
        }
        
        // Validate categories
        if (data.categories.isEmpty()) {
            return ValidationResult(false, "No categories found")
        }
        
        // Validate budgets
        data.categoryBudgets.forEach { (category, budget) ->
            if (budget < 0) {
                return ValidationResult(false, "Invalid budget for category '$category': negative amount")
            }
        }
        
        return ValidationResult(true, "")
    }
    
    private data class ValidationResult(
        val isValid: Boolean,
        val errorMessage: String
    )
    
    private data class MergeResult(
        val mergedExpenses: List<Expense>,
        val expensesAdded: Int,
        val expensesUpdated: Int,
        val expensesRemoved: Int,
        val conflictsResolved: Int
    )
    
    private fun mergeExpenses(localData: AppData, remoteData: AppData): MergeResult {
        val localExpenses = localData.expenses.associateBy { it.id }
        val remoteExpenses = remoteData.expenses.associateBy { it.id }

        android.util.Log.d("SyncService", "mergeExpenses: local=${localData.expenses.size}, remote=${remoteData.expenses.size}")

        val mergedExpenses = mutableListOf<Expense>()
        var expensesAdded = 0
        var expensesUpdated = 0
        var expensesRemoved = 0
        var conflictsResolved = 0

        // Process all remote expenses - these are the "source of truth"
        remoteData.expenses.forEach { remoteExpense ->
            val localExpense = localExpenses[remoteExpense.id]

            android.util.Log.d("SyncService", "Checking remote expense: id=${remoteExpense.id}, store=${remoteExpense.storeName}, localExists=${localExpense != null}")

            when {
                localExpense == null -> {
                    // New expense from remote
                    mergedExpenses.add(remoteExpense)
                    expensesAdded++
                    android.util.Log.d("SyncService", "Added new expense: ${remoteExpense.storeName}")
                }
                remoteExpense.date.isAfter(localExpense.date) -> {
                    // Remote expense is newer, replace local
                    mergedExpenses.add(remoteExpense)
                    expensesUpdated++
                    conflictsResolved++
                    android.util.Log.d("SyncService", "Updated expense (remote newer): ${remoteExpense.storeName}")
                }
                else -> {
                    // Local is same or newer, keep local
                    mergedExpenses.add(localExpense)
                }
            }
        }

        // Check for local expenses that don't exist in remote (were deleted elsewhere)
        localData.expenses.forEach { localExpense ->
            if (!remoteExpenses.containsKey(localExpense.id)) {
                // This expense was deleted on another device - don't include it
                expensesRemoved++
                android.util.Log.d("SyncService", "Removed expense (deleted elsewhere): ${localExpense.storeName}")
            }
        }

        android.util.Log.d("SyncService", "Merge complete: total=${mergedExpenses.size}, added=$expensesAdded, updated=$expensesUpdated, removed=$expensesRemoved")

        return MergeResult(
            mergedExpenses = mergedExpenses,
            expensesAdded = expensesAdded,
            expensesUpdated = expensesUpdated,
            expensesRemoved = expensesRemoved,
            conflictsResolved = conflictsResolved
        )
    }
    
    private fun <T> mergeDataLists(local: List<T>, remote: List<T>): List<T> {
        return (local + remote).distinct()
    }
    
    private fun mergeBudgets(local: Map<String, Double>, remote: Map<String, Double>): Map<String, Double> {
        val merged = local.toMutableMap()
        remote.forEach { (key, value) ->
            if (!merged.containsKey(key) || value > merged[key]!!) {
                merged[key] = value
            }
        }
        return merged
    }
    
    private fun mergeSubcategoriesMap(
        local: Map<String, List<String>>,
        remote: Map<String, List<String>>
    ): Map<String, List<String>> {
        val merged = mutableMapOf<String, List<String>>()
        
        // Get all unique categories
        val allCategories = (local.keys + remote.keys).distinct()
        
        allCategories.forEach { category ->
            val localSubs = local[category] ?: emptyList()
            val remoteSubs = remote[category] ?: emptyList()
            merged[category] = (localSubs + remoteSubs).distinct()
        }
        
        return merged
    }
    
    suspend fun signIn(): Result<Boolean> {
        return try {
            val success = driveManager.signIn()
            if (success) {
                Result.success(true)
            } else {
                Result.failure(Exception("Sign-in failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun signOut() {
        driveManager.signOut()
    }
    
    suspend fun isSignedIn(): Boolean {
        return driveManager.isSignedIn()
    }
    
    fun initializeDriveService(account: GoogleSignInAccount) {
        driveManager.initializeDriveService(account)
    }
    
    fun getGoogleSignInClient() = driveManager.getGoogleSignInClient()
}
