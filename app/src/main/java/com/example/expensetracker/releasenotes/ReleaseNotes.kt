package com.example.expensetracker.releasenotes

import kotlinx.serialization.Serializable

@Serializable
data class ReleaseNotes(
    val versionCode: Int,
    val versionName: String,
    val title: String = "What's New",
    val features: List<String> = emptyList(),
)
