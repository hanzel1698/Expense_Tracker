package com.example.expensetracker.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import com.example.expensetracker.ui.theme.Black
import com.example.expensetracker.ui.theme.DividerGray
import com.example.expensetracker.ui.theme.White
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.*

@Composable
fun BrutalistCalendar(
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

    val themeBlack = MaterialTheme.colorScheme.onSurface
    val themeWhite = MaterialTheme.colorScheme.surface

    if (actionMenuDate != null) {
        AlertDialog(
            onDismissRequest = { actionMenuDate = null },
            title = { 
                val formattedDate = actionMenuDate?.let { 
                    it.format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"))
                } ?: ""
                Text(text = "DATE: $formattedDate", fontWeight = FontWeight.Black, color = themeBlack) 
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    com.example.expensetracker.ui.components.BrutalistButton(
                        onClick = { 
                            if (actionMenuDate != null) {
                                onViewExpensesForDate?.invoke(actionMenuDate!!)
                            }
                            actionMenuDate = null 
                        },
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = themeBlack
                    ) {
                        Text("VIEW EXPENSES", color = themeWhite, fontWeight = FontWeight.Black)
                    }
                    com.example.expensetracker.ui.components.BrutalistButton(
                        onClick = { 
                            if (actionMenuDate != null) {
                                onNewExpenseForDate?.invoke(actionMenuDate!!)
                            }
                            actionMenuDate = null 
                        },
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = themeWhite
                    ) {
                        Text("NEW EXPENSE", color = themeBlack, fontWeight = FontWeight.Black)
                    }
                }
            },
            confirmButton = {
                Text(
                    text = "CANCEL",
                    fontWeight = FontWeight.Black,
                    color = themeBlack,
                    modifier = Modifier.clickable { actionMenuDate = null }.padding(8.dp)
                )
            },
            shape = RectangleShape,
            containerColor = themeWhite,
            titleContentColor = themeBlack,
            textContentColor = themeBlack
        )
    }

    BrutalistCard(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        Column {
            // Month Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(themeBlack)
                    .padding(vertical = 12.dp, horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowLeft,
                    contentDescription = "Previous Month",
                    tint = themeWhite,
                    modifier = Modifier.size(28.dp).clickable { onMonthChanged(viewedMonth.minusMonths(1)) }
                )
                Text(
                    text = viewedMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault()).uppercase() + " " + viewedMonth.year,
                    color = themeWhite,
                    fontWeight = FontWeight.Black,
                    fontSize = 22.sp
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowRight,
                    contentDescription = "Next Month",
                    tint = themeWhite,
                    modifier = Modifier.size(28.dp).clickable { onMonthChanged(viewedMonth.plusMonths(1)) }
                )
            }

            // Days of week header
            Row(modifier = Modifier.fillMaxWidth()) {
                val daysOfWeek = listOf("SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT")
                daysOfWeek.forEach { day ->
                    Text(
                        text = day,
                        modifier = Modifier.weight(1f).padding(vertical = 10.dp),
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp,
                        color = themeBlack
                    )
                }
            }
            
            // Divider
            Box(modifier = Modifier.fillMaxWidth().height(2.dp).background(themeBlack))

            FullMonthGrid(viewedMonth, today, expenses, selectedDate) { date, isLong ->
                if (isLong) actionMenuDate = date else selectedDate = date
            }
        }
    }
}


@Composable
private fun FullMonthGrid(
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

    Column {
        dates.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    Box(modifier = Modifier.weight(1f).height(44.dp)) {
                        DayCell(
                            date = date,
                            isToday = date == today,
                            isSelected = date == selectedDate,
                            isCurrentMonth = date.month == month.month,
                            expense = expenses[date],
                            onDateSelected = { onDateAction(date, false) },
                            onLongPress = { onDateAction(date, true) },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DayCell(
    date: LocalDate,
    isToday: Boolean,
    isSelected: Boolean,
    isCurrentMonth: Boolean,
    expense: Double?,
    onDateSelected: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val themeBlack = MaterialTheme.colorScheme.onSurface
    val themeWhite = MaterialTheme.colorScheme.surface
    val themeDivider = MaterialTheme.colorScheme.tertiary
    
    val backgroundColor = if (isSelected) themeBlack else if (isToday) themeDivider else themeWhite
    val contentColor = if (isSelected) themeWhite else if (isCurrentMonth) themeBlack else themeBlack.copy(alpha = 0.4f)
    
    Box(
        modifier = modifier
            .background(backgroundColor)
            .border(1.dp, themeBlack)
            .combinedClickable(
                onClick = onDateSelected,
                onLongClick = onLongPress
            )
    ) {
        // Date Number
        Text(
            text = date.dayOfMonth.toString(),
            fontWeight = if (isToday) FontWeight.Black else FontWeight.Bold,
            fontSize = 15.sp,
            color = contentColor,
            modifier = Modifier.align(Alignment.TopCenter),
            textAlign = TextAlign.Center
        )
        
        // Expense Banner
        if (expense != null && expense > 0) {
            Column(modifier = Modifier.align(Alignment.BottomCenter)) {
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(if(isSelected) themeWhite else themeBlack))
                val formatted = when {
                    expense % 1 == 0.0 -> String.format("%.0f", expense)
                    (expense * 10) % 1 == 0.0 -> String.format("%.1f", expense)
                    else -> String.format("%.2f", expense)
                }
                
                val bannerBg = if (isSelected) themeWhite else if (isToday) themeBlack else themeDivider
                val bannerTextCol = if (isSelected) themeBlack else if (isToday) themeWhite else themeBlack
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(bannerBg),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "₹$formatted",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        color = bannerTextCol,
                        maxLines = 1,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
