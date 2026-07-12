package com.example.expensetracker

import android.content.Context

/**
 * Persisted choice between the two coexisting UIs:
 * - BRUTALIST — the original high-contrast black/white UI (MainActivity)
 * - MODERN    — the Aurora Material 3 UI (ModernMainActivity)
 *
 * Both UIs share the same data layer (DataRepository, sync services, models),
 * so switching styles never touches the stored data.
 */
object UiStylePreference {
    const val STYLE_BRUTALIST = "brutalist"
    const val STYLE_MODERN = "modern"

    private const val PREFS_NAME = "ui_style_prefs"
    private const val KEY_STYLE = "ui_style"

    fun getStyle(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_STYLE, STYLE_BRUTALIST) ?: STYLE_BRUTALIST

    fun setStyle(context: Context, style: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_STYLE, style)
            .apply()
    }

    fun isModern(context: Context): Boolean = getStyle(context) == STYLE_MODERN
}
