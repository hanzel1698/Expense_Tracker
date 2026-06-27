package com.example.expensetracker.data

import android.content.Context
import com.example.expensetracker.model.Expense
import com.example.expensetracker.model.RecurringExpense
import com.example.expensetracker.model.RecurrenceFrequency
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

data class AppData(
    val expenses: List<Expense> = emptyList(),
    val categories: List<String> = listOf("Food", "Utilities", "Entertainment", "Transport", "Health", "Shopping"),
    val subcategoriesMap: Map<String, List<String>> = mapOf(
        "Food" to listOf("Groceries", "Coffee", "Snacks", "Dining Out"),
        "Utilities" to listOf("Internet", "Electricity", "Gas"),
        "Entertainment" to listOf("Movies", "Music", "Games"),
        "Transport" to listOf("Public Transit", "Fuel", "Parking"),
        "Health" to listOf("Medicine", "Doctor", "Gym"),
        "Shopping" to listOf("Clothing", "Electronics", "Home")
    ),
    val labels: List<String> = listOf("Personal", "Business", "Urgent", "Recurring", "One-time"),
    val paymentModes: List<String> = listOf("Cash", "Credit Card", "Debit Card", "UPI", "Net Banking", "Wallet"),
    val paidVia: List<String> = listOf("Google Pay", "PhonePe", "Paytm", "Amazon Pay", "BHIM", "Other"),
    val categoryBudgets: Map<String, Double> = mapOf(
        "Food" to 200.0,
        "Utilities" to 150.0,
        "Entertainment" to 100.0,
        "Transport" to 100.0,
        "Health" to 100.0,
        "Shopping" to 150.0
    ),
    val subcategoryBudgets: Map<String, Double> = emptyMap(),
    val storeHistory: List<String> = emptyList(),
    val isDarkTheme: Boolean = false,
    val recurringExpenses: List<RecurringExpense> = emptyList()
)

object DataRepository {
    private const val FILE_NAME = "expense_data.json"
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    // ── LocalDate adapter ──────────────────────────────────────────────────────
    private val localDateAdapter = object : TypeAdapter<LocalDate>() {
        override fun write(out: JsonWriter, value: LocalDate?) {
            out.value(value?.format(dateFormatter))
        }
        override fun read(`in`: JsonReader): LocalDate? {
            return try {
                val dateStr = `in`.nextString()
                if (dateStr == null || dateStr == "0000-00-00" || dateStr.isBlank()) {
                    android.util.Log.w("DataRepository", "Invalid date string: '$dateStr', using today")
                    return LocalDate.now()
                }
                LocalDate.parse(dateStr, dateFormatter)
            } catch (e: Exception) {
                android.util.Log.w("DataRepository", "Failed to parse date: ${e.message}, using today")
                LocalDate.now()
            }
        }
    }

    // ── Expense adapter ────────────────────────────────────────────────────────
    private val expenseAdapter = object : TypeAdapter<Expense>() {
        override fun write(out: JsonWriter, value: Expense?) {
            if (value == null) { out.nullValue(); return }
            out.beginObject()
            out.name("id").value(value.id)
            out.name("groupId").value(value.groupId)
            out.name("date").value(value.date.format(dateFormatter))
            out.name("storeName").value(value.storeName)
            out.name("location").value(value.location)
            out.name("amount").value(value.amount)
            out.name("category").value(value.category)
            out.name("subcategory").value(value.subcategory)
            out.name("itemDescription").value(value.itemDescription)
            out.name("labels").beginArray(); value.labels.forEach { out.value(it) }; out.endArray()
            out.name("quantity").value(value.quantity)
            out.name("unit").value(value.unit)
            out.name("notes").value(value.notes)
            out.name("paymentMode").value(value.paymentMode)
            out.name("paidVia").value(value.paidVia)
            out.name("isDraft").value(value.isDraft)
            out.name("baseAmount").value(value.baseAmount)
            out.name("gstPercentage").value(value.gstPercentage)
            out.name("gstAmount").value(value.gstAmount)
            out.endObject()
        }
        override fun read(`in`: JsonReader): Expense? {
            return try {
                `in`.beginObject()
                var id: String? = null; var groupId: String? = null; var date: LocalDate? = null
                var storeName: String? = null; var location = ""; var amount: Double? = null
                var category: String? = null; var subcategory: String? = null
                var itemDescription = ""; var labels: List<String> = emptyList()
                var quantity: Double? = null; var unit: String? = null
                var notes = ""; var paymentMode = ""; var paidVia = ""; var isDraft = false
                var baseAmount: Double? = null; var gstPercentage: Double? = null; var gstAmount: Double? = null
                while (`in`.hasNext()) {
                    when (`in`.nextName()) {
                        "id" -> id = `in`.nextString()
                        "groupId" -> groupId = `in`.nextString()
                        "date" -> {
                            val dateStr = `in`.nextString()
                            date = if (dateStr == null || dateStr == "0000-00-00" || dateStr.isBlank()) LocalDate.now()
                            else try { LocalDate.parse(dateStr, dateFormatter) } catch (e: Exception) { LocalDate.now() }
                        }
                        "storeName" -> storeName = `in`.nextString()
                        "location" -> location = `in`.nextString() ?: ""
                        "amount" -> amount = `in`.nextDouble()
                        "category" -> category = `in`.nextString()
                        "subcategory" -> subcategory = `in`.nextString()
                        "itemDescription" -> itemDescription = `in`.nextString() ?: ""
                        "labels" -> {
                            labels = mutableListOf(); `in`.beginArray()
                            while (`in`.hasNext()) { (labels as MutableList).add(`in`.nextString()) }
                            `in`.endArray()
                        }
                        "quantity" -> quantity = try { `in`.nextDouble() } catch (e: Exception) { null }
                        "unit" -> unit = try { `in`.nextString() } catch (e: Exception) { null }
                        "notes" -> notes = `in`.nextString() ?: ""
                        "paymentMode" -> paymentMode = `in`.nextString() ?: ""
                        "paidVia" -> paidVia = `in`.nextString() ?: ""
                        "isDraft" -> isDraft = try { `in`.nextBoolean() } catch (e: Exception) { false }
                        "baseAmount" -> baseAmount = try { `in`.nextDouble() } catch (e: Exception) { null }
                        "gstPercentage" -> gstPercentage = try { `in`.nextDouble() } catch (e: Exception) { null }
                        "gstAmount" -> gstAmount = try { `in`.nextDouble() } catch (e: Exception) { null }
                        else -> `in`.skipValue()
                    }
                }
                `in`.endObject()
                Expense(
                    id = id ?: UUID.randomUUID().toString(),
                    groupId = groupId ?: UUID.randomUUID().toString(),
                    date = date ?: LocalDate.now(),
                    storeName = storeName ?: "",
                    location = location,
                    amount = amount ?: 0.0,
                    category = category ?: "",
                    subcategory = subcategory ?: "",
                    itemDescription = itemDescription,
                    labels = labels, quantity = quantity, unit = unit,
                    notes = notes, paymentMode = paymentMode, paidVia = paidVia, isDraft = isDraft,
                    baseAmount = baseAmount, gstPercentage = gstPercentage, gstAmount = gstAmount
                )
            } catch (e: Exception) {
                android.util.Log.e("DataRepository", "Failed to parse Expense: ${e.message}")
                e.printStackTrace(); null
            }
        }
    }

    // ── RecurringExpense adapter ───────────────────────────────────────────────
    private val recurringExpenseAdapter = object : TypeAdapter<RecurringExpense>() {
        override fun write(out: JsonWriter, value: RecurringExpense?) {
            if (value == null) { out.nullValue(); return }
            out.beginObject()
            out.name("id").value(value.id)
            out.name("name").value(value.name)
            out.name("storeName").value(value.storeName)
            out.name("amount").value(value.amount)
            out.name("category").value(value.category)
            out.name("subcategory").value(value.subcategory)
            out.name("itemDescription").value(value.itemDescription)
            out.name("labels").beginArray(); value.labels.forEach { out.value(it) }; out.endArray()
            out.name("paymentMode").value(value.paymentMode)
            out.name("paidVia").value(value.paidVia)
            out.name("frequency").value(value.frequency.name)
            out.name("dayOfPeriod").value(value.dayOfPeriod)
            out.name("startDate").value(value.startDate.format(dateFormatter))
            out.name("notes").value(value.notes)
            out.name("isActive").value(value.isActive)
            out.name("lastGeneratedDate").value(value.lastGeneratedDate?.format(dateFormatter))
            out.name("monthOfPeriod").value(value.monthOfPeriod)
            out.name("endDate").value(value.endDate?.format(dateFormatter))
            out.endObject()
        }
        override fun read(`in`: JsonReader): RecurringExpense? {
            return try {
                `in`.beginObject()
                var id: String? = null; var name = ""; var storeName = ""
                var amount: Double? = null; var category = ""; var subcategory = ""
                var itemDescription = ""; var labels: List<String> = emptyList()
                var paymentMode = ""; var paidVia = ""
                var frequency = RecurrenceFrequency.MONTHLY; var dayOfPeriod = 1
                var startDate: LocalDate = LocalDate.now(); var notes = ""; var isActive = true
                var lastGeneratedDate: LocalDate? = null; var monthOfPeriod = 0; var endDate: LocalDate? = null
                while (`in`.hasNext()) {
                    when (`in`.nextName()) {
                        "id" -> id = `in`.nextString()
                        "name" -> name = `in`.nextString() ?: ""
                        "storeName" -> storeName = `in`.nextString() ?: ""
                        "amount" -> amount = `in`.nextDouble()
                        "category" -> category = `in`.nextString() ?: ""
                        "subcategory" -> subcategory = `in`.nextString() ?: ""
                        "itemDescription" -> itemDescription = `in`.nextString() ?: ""
                        "labels" -> {
                            labels = mutableListOf(); `in`.beginArray()
                            while (`in`.hasNext()) { (labels as MutableList).add(`in`.nextString()) }
                            `in`.endArray()
                        }
                        "paymentMode" -> paymentMode = `in`.nextString() ?: ""
                        "paidVia" -> paidVia = `in`.nextString() ?: ""
                        "frequency" -> frequency = try {
                            RecurrenceFrequency.valueOf(`in`.nextString())
                        } catch (e: Exception) { RecurrenceFrequency.MONTHLY }
                        "dayOfPeriod" -> dayOfPeriod = try { `in`.nextInt() } catch (e: Exception) { 1 }
                        "startDate" -> {
                            val dateStr = `in`.nextString()
                            startDate = try { LocalDate.parse(dateStr, dateFormatter) } catch (e: Exception) { LocalDate.now() }
                        }
                        "notes" -> notes = `in`.nextString() ?: ""
                        "isActive" -> isActive = try { `in`.nextBoolean() } catch (e: Exception) { true }
                        "lastGeneratedDate" -> {
                            val tok = `in`.peek()
                            if (tok == JsonToken.NULL) { `in`.nextNull() }
                            else {
                                val ds = `in`.nextString()
                                lastGeneratedDate = try { LocalDate.parse(ds, dateFormatter) } catch (e: Exception) { null }
                            }
                        }
                        "monthOfPeriod" -> monthOfPeriod = try { `in`.nextInt() } catch (e: Exception) { 0 }
                        "endDate" -> {
                            val tok = `in`.peek()
                            if (tok == JsonToken.NULL) { `in`.nextNull() }
                            else {
                                val ds = `in`.nextString()
                                endDate = try { LocalDate.parse(ds, dateFormatter) } catch (e: Exception) { null }
                            }
                        }
                        else -> `in`.skipValue()
                    }
                }
                `in`.endObject()
                RecurringExpense(
                    id = id ?: UUID.randomUUID().toString(),
                    name = name, storeName = storeName,
                    amount = amount ?: 0.0, category = category, subcategory = subcategory,
                    itemDescription = itemDescription, labels = labels,
                    paymentMode = paymentMode, paidVia = paidVia,
                    frequency = frequency, dayOfPeriod = dayOfPeriod,
                    startDate = startDate, notes = notes, isActive = isActive,
                    lastGeneratedDate = lastGeneratedDate, monthOfPeriod = monthOfPeriod, endDate = endDate
                )
            } catch (e: Exception) {
                android.util.Log.e("DataRepository", "Failed to parse RecurringExpense: ${e.message}")
                e.printStackTrace(); null
            }
        }
    }

    // ── Gson instance ──────────────────────────────────────────────────────────
    private val gson: Gson = GsonBuilder()
        .registerTypeAdapter(LocalDate::class.java, localDateAdapter)
        .registerTypeAdapter(Expense::class.java, expenseAdapter)
        .registerTypeAdapter(RecurringExpense::class.java, recurringExpenseAdapter)
        .setPrettyPrinting()
        .create()

    fun getDataFile(context: Context): File {
        return File(context.filesDir, FILE_NAME)
    }

    fun save(context: Context, appData: AppData) {
        try {
            android.util.Log.d("DataRepository", "Saving ${appData.expenses.size} expenses, ${appData.recurringExpenses.size} recurring to file")
            val json = gson.toJson(appData)
            getDataFile(context).writeText(json)
            android.util.Log.d("DataRepository", "Save complete, file size: ${json.length} chars")
        } catch (e: Exception) {
            android.util.Log.e("DataRepository", "Save failed: ${e.message}")
            e.printStackTrace()
        }
    }

    fun load(context: Context): AppData {
        val file = getDataFile(context)
        android.util.Log.d("DataRepository", "Loading from file: ${file.absolutePath}, exists=${file.exists()}")
        if (!file.exists()) return AppData()

        return try {
            val json = file.readText()
            android.util.Log.d("DataRepository", "Loaded JSON, size: ${json.length} chars")
            val data = gson.fromJson(json, AppData::class.java) ?: AppData()
            android.util.Log.d("DataRepository", "Parsed data: ${data.expenses.size} expenses, ${data.recurringExpenses.size} recurring")
            data
        } catch (e: Exception) {
            android.util.Log.e("DataRepository", "Load failed: ${e.message}")
            e.printStackTrace()
            AppData()
        }
    }
}
