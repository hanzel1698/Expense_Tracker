package com.example.expensetracker.model

import java.time.LocalDate
import java.util.UUID

enum class RecurrenceFrequency {
    DAILY, WEEKLY, MONTHLY, YEARLY
}

data class RecurringExpense(
    val id: String = UUID.randomUUID().toString(),
    val name: String,                              // Display name / title
    val storeName: String = "",
    val amount: Double,
    val category: String,
    val subcategory: String = "",
    val itemDescription: String = "",
    val labels: List<String> = emptyList(),
    val paymentMode: String = "",
    val paidVia: String = "",
    val frequency: RecurrenceFrequency = RecurrenceFrequency.MONTHLY,
    /** Day-of-month (1-31) for MONTHLY/YEARLY, day-of-week (1=Mon..7=Sun) for WEEKLY, ignored for DAILY */
    val dayOfPeriod: Int = 1,
    val startDate: LocalDate = LocalDate.now(),
    val notes: String = "",
    val isActive: Boolean = true,
    /** Last date on which an Expense was auto-generated for this template. Null = never generated. */
    val lastGeneratedDate: LocalDate? = null,
    /** Target month (1-12) for YEARLY recurrence. If 0, falls back to startDate.monthValue */
    val monthOfPeriod: Int = 0,
    /** Optional end date after which no recurring expenses should be generated */
    val endDate: LocalDate? = null
)
