package com.example.expensetracker.sync

import android.util.Log
import com.example.expensetracker.model.Expense
import com.example.expensetracker.model.RecurringExpense
import com.example.expensetracker.model.RecurrenceFrequency
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.serializer.KotlinXSerializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// ── Supabase Configuration ─────────────────────────────────────────────────────
private const val SUPABASE_URL = "https://xlxhikvvckszsyvnodkr.supabase.co"
private const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InhseGhpa3Z2Y2tzenN5dm5vZGtyIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODA4MTcyNzQsImV4cCI6MjA5NjM5MzI3NH0.F3wjfNlmXeoG_U5OAa2LnqFagtdy-WNJxhNWdre8kX0"

private val supabaseJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    isLenient = true
}

private val supabase = createSupabaseClient(
    supabaseUrl = SUPABASE_URL,
    supabaseKey = SUPABASE_ANON_KEY
) {
    defaultSerializer = KotlinXSerializer(supabaseJson)
    install(Postgrest)
}

private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE
private const val TAG = "SupabaseService"

// ── Data Transfer Objects ──────────────────────────────────────────────────────

@Serializable
data class ExpenseRow(
    val id: String,
    val group_id: String? = null,
    val date: String,
    val store_name: String? = null,
    val location: String? = null,
    val amount: Double? = null,
    val category: String? = null,
    val subcategory: String? = null,
    val item_description: String? = null,
    val labels: List<String>? = null,
    val quantity: Double? = null,
    val unit: String? = null,
    val notes: String? = null,
    val payment_mode: String? = null,
    val paid_via: String? = null,
    val is_draft: Boolean? = null,
    val base_amount: Double? = null,
    val gst_percentage: Double? = null,
    val gst_amount: Double? = null,
    val deleted_at: String? = null
)

@Serializable
data class RecurringExpenseRow(
    val id: String,
    val name: String? = null,
    val store_name: String? = null,
    val amount: Double? = null,
    val category: String? = null,
    val subcategory: String? = null,
    val item_description: String? = null,
    val labels: List<String>? = null,
    val payment_mode: String? = null,
    val paid_via: String? = null,
    val frequency: String? = null,
    val day_of_period: Int? = null,
    val start_date: String? = null,
    val notes: String? = null,
    val is_active: Boolean? = null,
    val last_generated_date: String? = null,
    val month_of_period: Int? = null,
    val end_date: String? = null
)

@Serializable
data class AppSettingRow(
    val key: String,
    val value: JsonElement
)

// ── Sync Result ────────────────────────────────────────────────────────────────

data class SupabaseSyncResult(
    val success: Boolean,
    val message: String,
    val pulledExpenses: List<Expense> = emptyList(),
    val pulledRecurring: List<RecurringExpense> = emptyList(),
    val categories: List<String>? = null,
    val subcategoriesMap: Map<String, List<String>>? = null,
    val labels: List<String>? = null,
    val paymentModes: List<String>? = null,
    val paidVia: List<String>? = null,
    val categoryBudgets: Map<String, Double>? = null,
    val subcategoryBudgets: Map<String, Double>? = null,
    val storeHistory: List<String>? = null
)

enum class SupabaseSyncDirection {
    /** Local → Supabase only (manual sync, app exit). */
    PUSH_ONLY,
    /** Supabase → Local only (app startup). */
    PULL_ONLY
}

// ── Supabase Service Object ────────────────────────────────────────────────────

object SupabaseService {

    // ── Test Connection ──────────────────────────────────────────────────────────

    suspend fun testConnection(): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            supabase.postgrest["app_settings"].select(Columns.raw("key")) {
                limit(1)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Connection test failed: ${e.message}")
            false
        }
    }

    // ── PUSH: Local → Supabase ───────────────────────────────────────────────────

    suspend fun pushExpenses(expenses: List<Expense>): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            if (expenses.isEmpty()) return@withContext true
            val rows = expenses.map { it.toRow() }
            supabase.postgrest["expenses"].upsert(rows)
            Log.d(TAG, "Pushed ${expenses.size} expenses to Supabase")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Push expenses failed: ${e.message}")
            false
        }
    }

    suspend fun pushRecurringExpenses(items: List<RecurringExpense>): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            if (items.isEmpty()) return@withContext true
            val rows = items.map { it.toRow() }
            supabase.postgrest["recurring_expenses"].upsert(rows)
            Log.d(TAG, "Pushed ${items.size} recurring expenses to Supabase")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Push recurring failed: ${e.message}")
            false
        }
    }

    suspend fun pushSetting(key: String, value: JsonElement): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            supabase.postgrest["app_settings"].upsert(
                AppSettingRow(key = key, value = value)
            )
            true
        } catch (e: Exception) {
            Log.e(TAG, "Push setting '$key' failed: ${e.message}")
            false
        }
    }

    // ── PULL: Supabase → Local ───────────────────────────────────────────────────

    suspend fun pullExpenses(): List<Expense>? = withContext(Dispatchers.IO) {
        return@withContext try {
            val rows = supabase.postgrest["expenses"]
                .select()
                .decodeList<ExpenseRow>()
                .filter { it.deleted_at == null }  // client-side null filter
            Log.d(TAG, "Pulled ${rows.size} expenses from Supabase")
            rows.map { it.toExpense() }
        } catch (e: Exception) {
            Log.e(TAG, "Pull expenses failed: ${e.message}")
            null
        }
    }

    suspend fun pullRecurringExpenses(): List<RecurringExpense>? = withContext(Dispatchers.IO) {
        return@withContext try {
            val rows = supabase.postgrest["recurring_expenses"]
                .select()
                .decodeList<RecurringExpenseRow>()
            Log.d(TAG, "Pulled ${rows.size} recurring expenses from Supabase")
            rows.map { it.toRecurringExpense() }
        } catch (e: Exception) {
            Log.e(TAG, "Pull recurring failed: ${e.message}")
            null
        }
    }

    suspend fun pullSettings(): Map<String, JsonElement>? = withContext(Dispatchers.IO) {
        return@withContext try {
            val rows = supabase.postgrest["app_settings"].select().decodeList<AppSettingRow>()
            rows.associate { it.key to it.value }
        } catch (e: Exception) {
            Log.e(TAG, "Pull settings failed: ${e.message}")
            null
        }
    }

    // ── Soft Delete ──────────────────────────────────────────────────────────────

    suspend fun softDeleteExpense(id: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            supabase.postgrest["expenses"].update({
                set("deleted_at", java.time.Instant.now().toString())
            }) {
                filter { eq("id", id) }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Soft delete expense failed: ${e.message}")
            false
        }
    }

    suspend fun softDeleteExpenses(ids: List<String>): Boolean = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext true
        return@withContext try {
            val deletedAt = java.time.Instant.now().toString()
            ids.forEach { id ->
                supabase.postgrest["expenses"].update({
                    set("deleted_at", deletedAt)
                }) {
                    filter { eq("id", id) }
                }
            }
            Log.d(TAG, "Soft deleted ${ids.size} orphaned expenses from Supabase")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Soft delete expenses failed: ${e.message}")
            false
        }
    }

    /** Prefer submitted expenses when duplicate rows share the same groupId. */
    fun dedupeExpenses(expenses: List<Expense>): List<Expense> {
        return expenses
            .groupBy { it.groupId }
            .flatMap { (_, group) ->
                if (group.size == 1) {
                    group
                } else {
                    val submitted = group.filter { !it.isDraft }
                    if (submitted.isNotEmpty()) submitted else listOf(group.first())
                }
            }
    }

    // ── Sync ─────────────────────────────────────────────────────────────────────

    suspend fun syncAll(
        direction: SupabaseSyncDirection,
        localExpenses: List<Expense>,
        localRecurring: List<RecurringExpense>,
        categories: List<String>,
        subcategoriesMap: Map<String, List<String>>,
        labels: List<String>,
        paymentModes: List<String>,
        paidVia: List<String>,
        categoryBudgets: Map<String, Double>,
        subcategoryBudgets: Map<String, Double>,
        storeHistory: List<String>
    ): SupabaseSyncResult = withContext(Dispatchers.IO) {
        return@withContext try {
            val json = Json { ignoreUnknownKeys = true }

            when (direction) {
                SupabaseSyncDirection.PUSH_ONLY -> {
                    // Remove remote rows that no longer exist locally (deleted or replaced drafts)
                    val remoteExpenses = pullExpenses() ?: emptyList()
                    val localIds = localExpenses.map { it.id }.toSet()
                    val orphanIds = remoteExpenses.map { it.id }.filter { it !in localIds }
                    softDeleteExpenses(orphanIds)

                    pushExpenses(localExpenses)
                    pushRecurringExpenses(localRecurring)
                    pushSetting("categories", json.encodeToJsonElement(categories))
                    pushSetting("subcategories_map", json.encodeToJsonElement(subcategoriesMap))
                    pushSetting("labels", json.encodeToJsonElement(labels))
                    pushSetting("payment_modes", json.encodeToJsonElement(paymentModes))
                    pushSetting("paid_via", json.encodeToJsonElement(paidVia))
                    pushSetting("category_budgets", json.encodeToJsonElement(categoryBudgets))
                    pushSetting("subcategory_budgets", json.encodeToJsonElement(subcategoryBudgets))
                    pushSetting("store_history", json.encodeToJsonElement(storeHistory))

                    Log.d(TAG, "Push sync complete: ${localExpenses.size} expenses pushed")
                    SupabaseSyncResult(
                        success = true,
                        message = "Pushed to Supabase"
                    )
                }

                SupabaseSyncDirection.PULL_ONLY -> {
                    val pulledExpensesRaw = pullExpenses()
                        ?: return@withContext SupabaseSyncResult(false, "Pull failed: could not fetch expenses")
                    val pulledRecurring = pullRecurringExpenses()
                        ?: return@withContext SupabaseSyncResult(false, "Pull failed: could not fetch recurring expenses")
                    val settingsMap = pullSettings()
                        ?: return@withContext SupabaseSyncResult(false, "Pull failed: could not fetch settings")

                    val pulledExpenses = dedupeExpenses(pulledExpensesRaw)

                    val pulledCategories = settingsMap["categories"]?.let {
                        try { json.decodeFromJsonElement<List<String>>(it) } catch (e: Exception) { null }
                    }
                    val pulledSubcategoriesMap = settingsMap["subcategories_map"]?.let {
                        try { json.decodeFromJsonElement<Map<String, List<String>>>(it) } catch (e: Exception) { null }
                    }
                    val pulledLabels = settingsMap["labels"]?.let {
                        try { json.decodeFromJsonElement<List<String>>(it) } catch (e: Exception) { null }
                    }
                    val pulledPaymentModes = settingsMap["payment_modes"]?.let {
                        try { json.decodeFromJsonElement<List<String>>(it) } catch (e: Exception) { null }
                    }
                    val pulledPaidVia = settingsMap["paid_via"]?.let {
                        try { json.decodeFromJsonElement<List<String>>(it) } catch (e: Exception) { null }
                    }
                    val pulledCategoryBudgets = settingsMap["category_budgets"]?.let {
                        try { json.decodeFromJsonElement<Map<String, Double>>(it) } catch (e: Exception) { null }
                    }
                    val pulledSubcategoryBudgets = settingsMap["subcategory_budgets"]?.let {
                        try { json.decodeFromJsonElement<Map<String, Double>>(it) } catch (e: Exception) { null }
                    }
                    val pulledStoreHistory = settingsMap["store_history"]?.let {
                        try { json.decodeFromJsonElement<List<String>>(it) } catch (e: Exception) { null }
                    }

                    Log.d(TAG, "Pull sync complete: ${pulledExpenses.size} expenses pulled")
                    SupabaseSyncResult(
                        success = true,
                        message = "Pulled from Supabase",
                        pulledExpenses = pulledExpenses,
                        pulledRecurring = pulledRecurring,
                        categories = pulledCategories,
                        subcategoriesMap = pulledSubcategoriesMap,
                        labels = pulledLabels,
                        paymentModes = pulledPaymentModes,
                        paidVia = pulledPaidVia,
                        categoryBudgets = pulledCategoryBudgets,
                        subcategoryBudgets = pulledSubcategoryBudgets,
                        storeHistory = pulledStoreHistory
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed: ${e.message}")
            SupabaseSyncResult(false, "Sync failed: ${e.message}")
        }
    }
}

// ── Extension Functions: Model ↔ DB Row ────────────────────────────────────────

private fun Expense.toRow() = ExpenseRow(
    id = id,
    group_id = groupId,
    date = date.format(dateFormatter),
    store_name = storeName,
    location = location,
    amount = amount,
    category = category,
    subcategory = subcategory,
    item_description = itemDescription,
    labels = labels,
    quantity = quantity,
    unit = unit,
    notes = notes,
    payment_mode = paymentMode,
    paid_via = paidVia,
    is_draft = isDraft,
    base_amount = baseAmount,
    gst_percentage = gstPercentage,
    gst_amount = gstAmount,
    deleted_at = null
)

private fun ExpenseRow.toExpense(): Expense {
    val parsedDate = try {
        LocalDate.parse(date, dateFormatter)
    } catch (e: Exception) { LocalDate.now() }

    return Expense(
        id = id,
        groupId = group_id ?: id,
        date = parsedDate,
        storeName = store_name ?: "",
        location = location ?: "",
        amount = amount ?: 0.0,
        category = category ?: "",
        subcategory = subcategory ?: "",
        itemDescription = item_description ?: "",
        labels = labels ?: emptyList(),
        quantity = quantity,
        unit = unit,
        notes = notes ?: "",
        paymentMode = payment_mode ?: "",
        paidVia = paid_via ?: "",
        isDraft = is_draft ?: false,
        baseAmount = base_amount,
        gstPercentage = gst_percentage,
        gstAmount = gst_amount
    )
}

private fun RecurringExpense.toRow() = RecurringExpenseRow(
    id = id,
    name = name,
    store_name = storeName,
    amount = amount,
    category = category,
    subcategory = subcategory,
    item_description = itemDescription,
    labels = labels,
    payment_mode = paymentMode,
    paid_via = paidVia,
    frequency = frequency.name,
    day_of_period = dayOfPeriod,
    start_date = startDate.format(dateFormatter),
    notes = notes,
    is_active = isActive,
    last_generated_date = lastGeneratedDate?.format(dateFormatter),
    month_of_period = monthOfPeriod,
    end_date = endDate?.format(dateFormatter)
)

private fun RecurringExpenseRow.toRecurringExpense(): RecurringExpense {
    val parseDate: (String?) -> LocalDate? = { s ->
        if (s.isNullOrBlank()) null
        else try { LocalDate.parse(s, dateFormatter) } catch (e: Exception) { null }
    }
    return RecurringExpense(
        id = id,
        name = name ?: "",
        storeName = store_name ?: "",
        amount = amount ?: 0.0,
        category = category ?: "",
        subcategory = subcategory ?: "",
        itemDescription = item_description ?: "",
        labels = labels ?: emptyList(),
        paymentMode = payment_mode ?: "",
        paidVia = paid_via ?: "",
        frequency = try { RecurrenceFrequency.valueOf(frequency ?: "MONTHLY") } catch (e: Exception) { RecurrenceFrequency.MONTHLY },
        dayOfPeriod = day_of_period ?: 1,
        startDate = parseDate(start_date) ?: LocalDate.now(),
        notes = notes ?: "",
        isActive = is_active ?: true,
        lastGeneratedDate = parseDate(last_generated_date),
        monthOfPeriod = month_of_period ?: 0,
        endDate = parseDate(end_date)
    )
}
