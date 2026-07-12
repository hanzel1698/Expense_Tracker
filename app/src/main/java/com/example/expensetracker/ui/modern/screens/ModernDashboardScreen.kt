package com.example.expensetracker.ui.modern.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Drafts
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.expensetracker.ChartPoint
import com.example.expensetracker.TrendDimension
import com.example.expensetracker.model.Expense
import com.example.expensetracker.ui.modern.components.*
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

/**
 * Aurora dashboard — feature parity with the brutalist DashboardScreen:
 * calendar, budget/balance/spent stats, trends (time period + filters +
 * summary + bar chart with drill-through dialogs), category breakdown chart,
 * drafts shortcut with badge, quick-add, theme toggle.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModernDashboardScreen(
    expenses: List<Expense>,
    budget: Double = 0.0,
    categories: List<String> = emptyList(),
    subcategoriesMap: Map<String, List<String>> = emptyMap(),
    labels: List<String> = emptyList(),
    onNavigateToExpenses: (LocalDate) -> Unit,
    onNavigateToMonthExpenses: (YearMonth, TrendDimension, String?) -> Unit,
    onNavigateToFilteredExpenses: (LocalDate?, LocalDate?, Set<String>, Set<String>, Set<String>) -> Unit,
    onNewExpense: (LocalDate) -> Unit,
    isDarkTheme: Boolean = false,
    onThemeToggle: () -> Unit = {},
    onNavigateToDrafts: () -> Unit = {}
) {
    var viewedMonth by remember { mutableStateOf(YearMonth.now()) }
    var trendsMonth by remember { mutableStateOf(YearMonth.now()) }

    // Time period + filter state (same options as the brutalist dashboard)
    var selectedTimePeriod by remember { mutableStateOf("Selected Month") }
    var customStartDate by remember { mutableStateOf<LocalDate?>(null) }
    var customEndDate by remember { mutableStateOf<LocalDate?>(null) }
    var showCustomDateRangePicker by remember { mutableStateOf(false) }

    var selectedCategories by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedSubcategories by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedLabels by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showFilterOptions by remember { mutableStateOf(false) }

    var selectedCategoryForHorizontalChart by remember { mutableStateOf<String?>(null) }

    var showExpensesDialog by remember { mutableStateOf(false) }
    var selectedCategoryForExpenses by remember { mutableStateOf<String?>(null) }
    var selectedSubcategoryForExpenses by remember { mutableStateOf<String?>(null) }

    var showTrendExpensesDialog by remember { mutableStateOf(false) }
    var selectedTrendPointForExpenses by remember { mutableStateOf<ChartPoint?>(null) }

    val draftCount = remember(expenses) { expenses.count { it.isDraft } }

    // ── Date-range helpers (identical logic to the brutalist dashboard) ─────
    fun getDateRangeForPeriod(period: String): Pair<LocalDate, LocalDate> {
        val today = LocalDate.now()
        return when (period) {
            "This Week" -> today.minusDays((today.dayOfWeek.value % 7).toLong()) to
                today.plusDays((6 - today.dayOfWeek.value % 7).toLong())
            "Last Week" -> today.minusDays(((today.dayOfWeek.value % 7) + 7).toLong()) to
                today.minusDays((today.dayOfWeek.value % 7 + 1).toLong())
            "Selected Month" -> trendsMonth.atDay(1) to trendsMonth.atEndOfMonth()
            "This Quarter" -> {
                val quarter = (trendsMonth.monthValue - 1) / 3 + 1
                val startMonth = (quarter - 1) * 3 + 1
                trendsMonth.withMonth(startMonth).atDay(1) to
                    trendsMonth.withMonth(startMonth + 2).atEndOfMonth()
            }
            "Last Quarter" -> {
                val quarterViewed = trendsMonth.minusMonths(3)
                val quarter = (quarterViewed.monthValue - 1) / 3 + 1
                val startMonth = (quarter - 1) * 3 + 1
                quarterViewed.withMonth(startMonth).atDay(1) to
                    quarterViewed.withMonth(startMonth + 2).atEndOfMonth()
            }
            "This Year" -> trendsMonth.withMonth(1).atDay(1) to trendsMonth.withMonth(12).atEndOfMonth()
            "Last Year" -> trendsMonth.minusYears(1).withMonth(1).atDay(1) to
                trendsMonth.minusYears(1).withMonth(12).atEndOfMonth()
            "Custom" -> (customStartDate ?: today) to (customEndDate ?: today)
            else -> trendsMonth.atDay(1) to trendsMonth.atEndOfMonth()
        }
    }

    fun generateChartData(source: List<Expense>, period: String): List<ChartPoint> {
        val today = LocalDate.now()
        return when (period) {
            "This Week", "Last Week" -> {
                val startOfWeek = if (period == "This Week")
                    today.minusDays((today.dayOfWeek.value % 7).toLong())
                else today.minusDays(((today.dayOfWeek.value % 7) + 7).toLong())
                (0..6).map { dayOffset ->
                    val date = startOfWeek.plusDays(dayOffset.toLong())
                    val total = source.filter { it.date == date }.sumOf { it.amount }
                    ChartPoint(
                        label = date.dayOfWeek.name.take(3),
                        value = total.toFloat(),
                        period = date
                    )
                }
            }
            "Selected Month" -> {
                val startOfMonth = trendsMonth.atDay(1)
                val endOfMonth = trendsMonth.atEndOfMonth()
                val weeks = mutableListOf<Triple<LocalDate, LocalDate, String>>()
                var currentStart = startOfMonth
                var weekIdx = 1
                while (!currentStart.isAfter(endOfMonth)) {
                    val daysToSaturday = when (currentStart.dayOfWeek) {
                        java.time.DayOfWeek.SUNDAY -> 6
                        java.time.DayOfWeek.MONDAY -> 5
                        java.time.DayOfWeek.TUESDAY -> 4
                        java.time.DayOfWeek.WEDNESDAY -> 3
                        java.time.DayOfWeek.THURSDAY -> 2
                        java.time.DayOfWeek.FRIDAY -> 1
                        java.time.DayOfWeek.SATURDAY -> 0
                    }
                    val weekEnd = currentStart.plusDays(daysToSaturday.toLong()).coerceAtMost(endOfMonth)
                    weeks.add(Triple(currentStart, weekEnd, "W$weekIdx"))
                    currentStart = weekEnd.plusDays(1)
                    weekIdx++
                }
                weeks.map { (start, end, label) ->
                    val total = source.filter { !it.date.isBefore(start) && !it.date.isAfter(end) }
                        .sumOf { it.amount }
                    ChartPoint(label = label, value = total.toFloat(), period = start, endDate = end)
                }
            }
            "This Quarter", "Last Quarter" -> {
                val anchor = if (period == "This Quarter") trendsMonth else trendsMonth.minusMonths(3)
                val quarter = (anchor.monthValue - 1) / 3 + 1
                val startMonth = (quarter - 1) * 3 + 1
                (0..2).map { monthOffset ->
                    val targetMonth = anchor.withMonth(startMonth + monthOffset)
                    val total = source.filter { YearMonth.from(it.date) == targetMonth }.sumOf { it.amount }
                    ChartPoint(
                        label = targetMonth.month.name.take(3),
                        value = total.toFloat(),
                        period = targetMonth
                    )
                }
            }
            "This Year", "Last Year" -> {
                val anchor = if (period == "This Year") trendsMonth else trendsMonth.minusYears(1)
                (1..12).map { month ->
                    val targetMonth = anchor.withMonth(month)
                    val total = source.filter { YearMonth.from(it.date) == targetMonth }.sumOf { it.amount }
                    ChartPoint(
                        label = targetMonth.month.name.take(3),
                        value = total.toFloat(),
                        period = targetMonth
                    )
                }
            }
            "Custom" -> {
                val startDate = customStartDate ?: today
                val endDate = customEndDate ?: today
                val daysBetween = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate).toInt()
                when {
                    daysBetween <= 7 -> (0..daysBetween).map { dayOffset ->
                        val date = startDate.plusDays(dayOffset.toLong())
                        val total = source.filter { it.date == date }.sumOf { it.amount }
                        ChartPoint(label = date.dayOfMonth.toString(), value = total.toFloat(), period = date)
                    }
                    daysBetween < 30 -> {
                        val weeks = (daysBetween / 7) + 1
                        (0 until weeks).map { weekOffset ->
                            val weekStart = startDate.plusDays((weekOffset * 7).toLong())
                            val weekEnd = weekStart.plusDays(6).coerceAtMost(endDate)
                            val total = source.filter { !it.date.isBefore(weekStart) && !it.date.isAfter(weekEnd) }
                                .sumOf { it.amount }
                            ChartPoint(
                                label = "W${weekOffset + 1}",
                                value = total.toFloat(),
                                period = weekStart,
                                endDate = weekEnd
                            )
                        }
                    }
                    else -> {
                        val monthsBetween =
                            ((endDate.year - startDate.year) * 12 + (endDate.monthValue - startDate.monthValue)) + 1
                        (0 until monthsBetween).map { monthOffset ->
                            val targetMonth = YearMonth.from(startDate).plusMonths(monthOffset.toLong())
                            val monthStart = targetMonth.atDay(1)
                            val monthEnd = targetMonth.atEndOfMonth()
                            val total = source.filter { !it.date.isBefore(monthStart) && !it.date.isAfter(monthEnd) }
                                .sumOf { it.amount }
                            ChartPoint(
                                label = targetMonth.month.name.take(3),
                                value = total.toFloat(),
                                period = targetMonth
                            )
                        }
                    }
                }
            }
            else -> {
                val currentMonth = YearMonth.now()
                (0..11).map { i ->
                    val targetMonth = currentMonth.minusMonths(i.toLong())
                    val total = source.filter { YearMonth.from(it.date) == targetMonth }.sumOf { it.amount }
                    ChartPoint(
                        label = targetMonth.month.name.take(3),
                        value = total.toFloat(),
                        period = targetMonth
                    )
                }.reversed()
            }
        }
    }

    // ── Derived data ─────────────────────────────────────────────────────────
    val trendTimeFilteredExpenses = remember(
        expenses, selectedTimePeriod, customStartDate, customEndDate, trendsMonth
    ) {
        val (startDate, endDate) = getDateRangeForPeriod(selectedTimePeriod)
        expenses.filter { !it.date.isBefore(startDate) && !it.date.isAfter(endDate) }
    }

    val trendFilteredExpenses = remember(
        trendTimeFilteredExpenses, selectedCategories, selectedSubcategories, selectedLabels
    ) {
        trendTimeFilteredExpenses.filter { expense ->
            val categoryMatch = selectedCategories.isEmpty() || expense.category in selectedCategories
            val subcategoryMatch = selectedSubcategories.isEmpty() || expense.subcategory in selectedSubcategories
            val labelMatch = selectedLabels.isEmpty() || expense.labels.any { it in selectedLabels }
            categoryMatch && subcategoryMatch && labelMatch
        }
    }

    val monthlyExpenses = remember(expenses, viewedMonth) {
        expenses.filter { YearMonth.from(it.date) == viewedMonth }
    }
    val expensesMap = remember(expenses) {
        expenses
            .filter { !it.isDraft }
            .groupBy { it.date }
            .mapValues { entry -> entry.value.sumOf { it.amount } }
    }
    val totalSpent = remember(monthlyExpenses) { monthlyExpenses.sumOf { it.amount } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // ── Header ───────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Dashboard", style = MaterialTheme.typography.headlineLarge)
                Text(
                    text = viewedMonth.month.getDisplayName(
                        java.time.format.TextStyle.FULL, Locale.getDefault()
                    ) + " overview",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box {
                    IconButton(onClick = onNavigateToDrafts) {
                        Icon(
                            Icons.Default.Drafts,
                            contentDescription = "Drafts",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (draftCount > 0) {
                        Badge(
                            modifier = Modifier.align(Alignment.TopEnd),
                            containerColor = MaterialTheme.colorScheme.error
                        ) {
                            Text(draftCount.toString())
                        }
                    }
                }
                IconButton(onClick = onThemeToggle) {
                    Icon(
                        if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                        contentDescription = "Toggle theme",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FilledIconButton(
                    onClick = { onNewExpense(LocalDate.now()) },
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add expense")
                }
            }
        }

        // ── Calendar ─────────────────────────────────────────────────────────
        AuroraCalendar(
            expenses = expensesMap,
            modifier = Modifier.fillMaxWidth(),
            viewedMonth = viewedMonth,
            onMonthChanged = { viewedMonth = it },
            onViewExpensesForDate = onNavigateToExpenses,
            onNewExpenseForDate = onNewExpense
        )

        Spacer(modifier = Modifier.height(14.dp))

        // ── Budget / Balance / Spent stats ───────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AuroraCard(modifier = Modifier.weight(1f)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "Budget",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "₹${String.format("%.0f", budget)}",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            AuroraCard(
                modifier = Modifier.weight(1f),
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "Balance",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                    )
                    Text(
                        "₹${String.format("%.0f", budget - totalSpent)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            AuroraCard(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onNavigateToMonthExpenses(viewedMonth, TrendDimension.TOTAL, null) }
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "Spent",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "₹${String.format("%.0f", totalSpent)}",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ── Trends ───────────────────────────────────────────────────────────
        AuroraSectionHeader(
            title = "Trends",
            trailing = {
                AuroraMonthSwitcher(
                    monthLabel = trendsMonth.month.getDisplayName(
                        java.time.format.TextStyle.SHORT, Locale.getDefault()
                    ),
                    yearLabel = trendsMonth.year.toString(),
                    onPrevious = {
                        trendsMonth = trendsMonth.minusMonths(1)
                        selectedTimePeriod = "Selected Month"
                    },
                    onNext = {
                        trendsMonth = trendsMonth.plusMonths(1)
                        selectedTimePeriod = "Selected Month"
                    }
                )
            }
        )

        Spacer(modifier = Modifier.height(10.dp))

        val timePeriods = listOf(
            "Selected Month", "This Week", "Last Week", "This Quarter",
            "Last Quarter", "This Year", "Last Year", "Custom"
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AuroraDropdown(
                label = "Period",
                options = timePeriods,
                selectedOption = selectedTimePeriod,
                onOptionSelected = { period ->
                    selectedTimePeriod = period
                    if (period == "Custom") showCustomDateRangePicker = true
                },
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = showFilterOptions,
                onClick = { showFilterOptions = !showFilterOptions },
                label = { Text("Filters") },
                leadingIcon = {
                    Icon(
                        Icons.Default.FilterList,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                shape = RoundedCornerShape(50)
            )
        }

        if (showFilterOptions &&
            (selectedCategories.isNotEmpty() || selectedSubcategories.isNotEmpty() || selectedLabels.isNotEmpty())
        ) {
            TextButton(
                onClick = {
                    selectedCategories = emptySet()
                    selectedSubcategories = emptySet()
                    selectedLabels = emptySet()
                    selectedTimePeriod = "Selected Month"
                },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Clear filters")
            }
        }

        if (showCustomDateRangePicker) {
            AuroraDateRangePickerDialog(
                onDismissRequest = { showCustomDateRangePicker = false },
                onDateRangeSelected = { startMillis, endMillis ->
                    customStartDate = startMillis?.let {
                        java.time.Instant.ofEpochMilli(it)
                            .atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                    }
                    customEndDate = endMillis?.let {
                        java.time.Instant.ofEpochMilli(it)
                            .atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                    }
                    showCustomDateRangePicker = false
                }
            )
        }

        if (showFilterOptions) {
            Spacer(modifier = Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (categories.isNotEmpty()) {
                    AuroraMultiSelectDropdown(
                        label = "Categories",
                        options = categories,
                        selectedOptions = selectedCategories,
                        onOptionToggled = { option ->
                            selectedCategories =
                                if (option in selectedCategories) selectedCategories - option
                                else selectedCategories + option
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (selectedCategories.isNotEmpty()) {
                    val availableSubcategories = selectedCategories.flatMap { cat ->
                        subcategoriesMap[cat] ?: emptyList()
                    }.distinct()
                    if (availableSubcategories.isNotEmpty()) {
                        AuroraMultiSelectDropdown(
                            label = "Subcategories",
                            options = availableSubcategories,
                            selectedOptions = selectedSubcategories,
                            onOptionToggled = { option ->
                                selectedSubcategories =
                                    if (option in selectedSubcategories) selectedSubcategories - option
                                    else selectedSubcategories + option
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                if (labels.isNotEmpty()) {
                    AuroraMultiSelectDropdown(
                        label = "Labels",
                        options = labels,
                        selectedOptions = selectedLabels,
                        onOptionToggled = { option ->
                            selectedLabels =
                                if (option in selectedLabels) selectedLabels - option
                                else selectedLabels + option
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Trends summary card
        val totalAmount = trendFilteredExpenses.sumOf { it.amount }
        val transactionCount = trendFilteredExpenses.size

        AuroraCard(
            modifier = Modifier.fillMaxWidth(),
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Total spent",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                    )
                    Text(
                        "₹${String.format("%.0f", totalAmount)}",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "Transactions",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                    )
                    Text(
                        "$transactionCount",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Trend chart
        val trendChartData = remember(
            trendFilteredExpenses, selectedTimePeriod, customStartDate, customEndDate
        ) {
            generateChartData(trendFilteredExpenses, selectedTimePeriod)
        }

        AuroraCard(modifier = Modifier.fillMaxWidth()) {
            AuroraBarChart(
                chartData = trendChartData,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .padding(8.dp),
                onBarClicked = { point ->
                    selectedTrendPointForExpenses = point
                    showTrendExpensesDialog = true
                }
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ── Category breakdown ───────────────────────────────────────────────
        AuroraSectionHeader(
            title = "Category breakdown",
            subtitle = if (selectedCategoryForHorizontalChart != null)
                "Subcategories of ${selectedCategoryForHorizontalChart}"
            else "Tap a bar to view subcategories, long-press to open expenses",
            trailing = if (selectedCategoryForHorizontalChart != null) {
                {
                    TextButton(onClick = { selectedCategoryForHorizontalChart = null }) {
                        Text("Back")
                    }
                }
            } else null
        )

        Spacer(modifier = Modifier.height(8.dp))

        AuroraCard(modifier = Modifier.fillMaxWidth()) {
            AuroraHorizontalBarChart(
                expenses = trendTimeFilteredExpenses,
                categories = categories,
                subcategoriesMap = subcategoriesMap,
                selectedCategory = selectedCategoryForHorizontalChart,
                onCategorySelected = { category ->
                    selectedCategoryForHorizontalChart =
                        if (category == selectedCategoryForHorizontalChart) null else category
                },
                onBarLongPressed = { category, subcategory ->
                    selectedCategoryForExpenses = category
                    selectedSubcategoryForExpenses = subcategory
                    showExpensesDialog = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .padding(8.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── Drill-through dialogs ────────────────────────────────────────────
        if (showExpensesDialog) {
            val itemText = if (selectedSubcategoryForExpenses != null)
                "${selectedCategoryForExpenses} / ${selectedSubcategoryForExpenses}"
            else selectedCategoryForExpenses ?: ""
            AuroraConfirmDialog(
                title = "Show expenses?",
                message = "View expenses for $itemText?",
                confirmText = "Show",
                onConfirm = {
                    showExpensesDialog = false
                    val (startDate, endDate) = getDateRangeForPeriod(selectedTimePeriod)
                    onNavigateToFilteredExpenses(
                        startDate,
                        endDate,
                        if (selectedCategoryForExpenses != null) setOf(selectedCategoryForExpenses!!) else emptySet(),
                        if (selectedSubcategoryForExpenses != null) setOf(selectedSubcategoryForExpenses!!) else emptySet(),
                        selectedLabels
                    )
                },
                onDismiss = { showExpensesDialog = false }
            )
        }

        if (showTrendExpensesDialog && selectedTrendPointForExpenses != null) {
            val point = selectedTrendPointForExpenses!!
            val periodText = when (val p = point.period) {
                is LocalDate -> {
                    if (point.endDate != null && point.endDate is LocalDate) {
                        val end = point.endDate as LocalDate
                        "${p.format(java.time.format.DateTimeFormatter.ofPattern("dd MMM"))} – " +
                            end.format(java.time.format.DateTimeFormatter.ofPattern("dd MMM"))
                    } else {
                        p.format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy"))
                    }
                }
                is YearMonth -> p.format(java.time.format.DateTimeFormatter.ofPattern("MMM yyyy"))
                else -> point.label
            }
            AuroraConfirmDialog(
                title = "Show expenses?",
                message = "View expenses for $periodText?",
                confirmText = "Show",
                onConfirm = {
                    showTrendExpensesDialog = false
                    val (startDate, endDate) = when (val p = point.period) {
                        is LocalDate -> {
                            if (point.endDate != null && point.endDate is LocalDate) {
                                p to (point.endDate as LocalDate)
                            } else {
                                p to p
                            }
                        }
                        is YearMonth -> p.atDay(1) to p.atEndOfMonth()
                        else -> getDateRangeForPeriod(selectedTimePeriod)
                    }
                    onNavigateToFilteredExpenses(
                        startDate, endDate, selectedCategories, selectedSubcategories, selectedLabels
                    )
                },
                onDismiss = { showTrendExpensesDialog = false }
            )
        }
    }
}
