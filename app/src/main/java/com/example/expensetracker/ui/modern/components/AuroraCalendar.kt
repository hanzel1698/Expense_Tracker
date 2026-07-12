package com.example.expensetracker.ui.modern.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * Modern calendar with per-day spend badges. Feature-parity with BrutalistCalendar:
 * month navigation, day selection, long-press action dialog (view expenses / new expense).
 */
@Composable
fun AuroraCalendar(
    expenses: Map<LocalDate, Double>,
    modifier: Modifier = Modifier,
    viewedMonth: YearMonth = YearMonth.now(),
    onMonthChanged: (YearMonth) -> Unit = {},
    onViewExpensesForDate: ((LocalDate) -> Unit)? = null,
    onNewExpenseForDate: ((LocalDate) -> Unit)? = null
) {
    val today = remember { LocalDate.now() }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    var actionMenuDate by remember { mutableStateOf<LocalDate?>(null) }

    if (actionMenuDate != null) {
        val formattedDate = actionMenuDate?.format(
            java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy")
        ) ?: ""
        AlertDialog(
            onDismissRequest = { actionMenuDate = null },
            shape = RoundedCornerShape(28.dp),
            title = { Text(formattedDate, style = MaterialTheme.typography.headlineSmall) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            actionMenuDate?.let { onViewExpensesForDate?.invoke(it) }
                            actionMenuDate = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text("View expenses")
                    }
                    FilledTonalButton(
                        onClick = {
                            actionMenuDate?.let { onNewExpenseForDate?.invoke(it) }
                            actionMenuDate = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text("New expense")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { actionMenuDate = null }) { Text("Cancel") }
            }
        )
    }

    AuroraCard(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        Column(modifier = Modifier.padding(bottom = 8.dp)) {
            // Month header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { onMonthChanged(viewedMonth.minusMonths(1)) }) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Previous month",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = viewedMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault()) +
                        " " + viewedMonth.year,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = { onMonthChanged(viewedMonth.plusMonths(1)) }) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Next month",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Weekday header
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
                listOf("S", "M", "T", "W", "T", "F", "S").forEach { day ->
                    Text(
                        text = day,
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 4.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AuroraMonthGrid(viewedMonth, today, expenses, selectedDate) { date, isLong ->
                if (isLong) actionMenuDate = date else selectedDate = date
            }
        }
    }
}

@Composable
private fun AuroraMonthGrid(
    month: YearMonth,
    today: LocalDate,
    expenses: Map<LocalDate, Double>,
    selectedDate: LocalDate?,
    onDateAction: (LocalDate, Boolean) -> Unit
) {
    val firstOfMonth = month.atDay(1)
    val lastOfMonth = month.atEndOfMonth()
    val startOfGrid = firstOfMonth.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
    val endOfGrid = lastOfMonth.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))

    var currentDate = startOfGrid
    val dates = mutableListOf<LocalDate>()
    while (!currentDate.isAfter(endOfGrid)) {
        dates.add(currentDate)
        currentDate = currentDate.plusDays(1)
    }

    Column(modifier = Modifier.padding(horizontal = 6.dp)) {
        dates.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    Box(modifier = Modifier.weight(1f)) {
                        AuroraDayCell(
                            date = date,
                            isToday = date == today,
                            isSelected = date == selectedDate,
                            isCurrentMonth = date.month == month.month,
                            expense = expenses[date],
                            onDateSelected = { onDateAction(date, false) },
                            onLongPress = { onDateAction(date, true) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AuroraDayCell(
    date: LocalDate,
    isToday: Boolean,
    isSelected: Boolean,
    isCurrentMonth: Boolean,
    expense: Double?,
    onDateSelected: () -> Unit,
    onLongPress: () -> Unit
) {
    val hasSpend = expense != null && expense > 0

    val cellBackground = when {
        isSelected -> MaterialTheme.colorScheme.primary
        isToday -> MaterialTheme.colorScheme.primaryContainer
        else -> androidx.compose.ui.graphics.Color.Transparent
    }
    val dayColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        isToday -> MaterialTheme.colorScheme.onPrimaryContainer
        isCurrentMonth -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(cellBackground)
            .combinedClickable(onClick = onDateSelected, onLongClick = onLongPress)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            fontSize = 13.sp,
            fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Medium,
            color = dayColor
        )
        if (hasSpend) {
            val formatted = Math.round(expense!!).toString()
            Text(
                text = "₹$formatted",
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.primary
            )
        } else {
            // Reserve badge height so rows stay even
            Spacer(modifier = Modifier.height(12.dp))
        }
        if (hasSpend && !isSelected) {
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            )
        } else {
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}
