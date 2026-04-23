package com.example.expensetracker.model

import java.time.LocalDate
import java.util.UUID

data class Expense(
    val id: String = UUID.randomUUID().toString(),
    val groupId: String = UUID.randomUUID().toString(),
    val date: LocalDate,
    val storeName: String,
    val amount: Double,
    val category: String,
    val subcategory: String,
    val itemDescription: String = "",
    val labels: List<String> = emptyList(),
    val quantity: Double? = null,
    val unit: String? = null,
    val notes: String = "",
    val paymentMode: String = "",
    val paidVia: String = ""
)
