package com.example.expensetracker

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

// ── App-wide shared types and helpers ─────────────────────────────────────────
// Navigation destinations, trend dimension, chart model, and CSV import/export
// helpers used across the Aurora UI and the data pipeline.

enum class Screen {
    SignInGate, Dashboard, ExpenseList, Budget, Settings, AddExpense, DraftList
}

enum class TrendDimension {
    TOTAL, CATEGORY, SUBCATEGORY, LABEL
}

data class ChartPoint(val label: String, val value: Float, val period: Any, val endDate: Any? = null)

// ── CSV helpers ───────────────────────────────────────────────────────────────

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

/** Parses "HH:mm", "H:mm", "HH:mm:ss" or 12-hour "h:mm AM/PM"; blank or unreadable → null. */
fun parseFlexibleTime(timeStr: String): LocalTime? {
    val trimmed = timeStr.trim().uppercase()
    if (trimmed.isEmpty()) return null
    return try {
        val isPm = trimmed.endsWith("PM")
        val isAm = trimmed.endsWith("AM")
        val parts = trimmed.removeSuffix("AM").removeSuffix("PM").trim().split(":")
        var hour = parts[0].trim().toInt()
        val minute = parts.getOrNull(1)?.trim()?.toInt() ?: 0
        if (isAm || isPm) {
            if (hour !in 1..12) return null
            hour = hour % 12 + if (isPm) 12 else 0
        }
        LocalTime.of(hour, minute)
    } catch (e: Exception) {
        null
    }
}

val CSV_TEMPLATE_CONTENT = """
Date (YYYY-MM-DD),Store Name,Amount,Category,Subcategory,Item Description,Labels (comma-separated),Quantity,Unit,Notes,Payment Mode,Paid Via,Split ID,Is Recurring (Yes/No),Recurring Frequency (Daily/Weekly/Monthly/Yearly),Recurring End Date (YYYY-MM-DD),Time (HH:mm - optional)
2026-06-01,Groceries - Target,45.50,Food,Groceries,Weekly grocery shopping,"Personal, Urgent",1,Bag,Weekly milk and eggs,Credit Card,Google Pay,,No,,,18:30
2026-06-02,Costco,100.00,Food,Groceries,Food supplies,Personal,,,,Credit Card,Google Pay,SplitA,No,,,10:15
2026-06-02,Costco,50.00,Shopping,Clothing,New shirt,Personal,,,,Credit Card,Google Pay,SplitA,No,,,10:15
2026-06-03,Gym Membership,30.00,Health,Gym,Monthly Gym fee,Personal,,,,Net Banking,Other,,Yes,Weekly,2026-12-31,
""".trimIndent()
