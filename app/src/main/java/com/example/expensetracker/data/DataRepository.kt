package com.example.expensetracker.data

import android.content.Context
import com.example.expensetracker.model.Expense
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

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
    val isDarkTheme: Boolean = false
)

object DataRepository {
    private const val FILE_NAME = "expense_data.json"
    
    // Custom Gson for LocalDate with error handling for invalid dates
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
                        android.util.Log.w("DataRepository", "Invalid date string: '$dateStr', using today")
                        return LocalDate.now()
                    }
                    LocalDate.parse(dateStr, formatter)
                } catch (e: Exception) {
                    android.util.Log.w("DataRepository", "Failed to parse date: ${e.message}, using today")
                    LocalDate.now()
                }
            }
        })
        .setPrettyPrinting()
        .create()

    fun getDataFile(context: Context): File {
        return File(context.filesDir, FILE_NAME)
    }

    fun save(context: Context, appData: AppData) {
        try {
            android.util.Log.d("DataRepository", "Saving ${appData.expenses.size} expenses to file")
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
            android.util.Log.d("DataRepository", "Parsed data: ${data.expenses.size} expenses")
            data
        } catch (e: Exception) {
            android.util.Log.e("DataRepository", "Load failed: ${e.message}")
            e.printStackTrace()
            AppData()
        }
    }
}
