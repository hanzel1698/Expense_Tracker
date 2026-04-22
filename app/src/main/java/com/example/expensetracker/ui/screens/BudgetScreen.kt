package com.example.expensetracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.expensetracker.model.Expense
import com.example.expensetracker.ui.components.BrutalistButton
import com.example.expensetracker.ui.components.BrutalistCard
import com.example.expensetracker.ui.components.BrutalistTextField
import com.example.expensetracker.ui.theme.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

// ── Budget Progress Bar ──────────────────────────────────────────────────────

@Composable
fun BudgetProgressBar(
    spent: Double,
    budget: Double,
    barColor: Color,
    trackColor: Color,
    height: androidx.compose.ui.unit.Dp = 8.dp
) {
    val fraction = if (budget > 0) (spent / budget).toFloat().coerceIn(0f, 1f) else 0f
    val isOver = budget > 0 && spent > budget

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .background(trackColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction)
                .background(if (isOver) Color(0xFFFF4444) else barColor)
        )
    }
}

// ── Budget Edit Dialog ───────────────────────────────────────────────────────

@Composable
fun BudgetEditDialog(
    title: String,
    subtitle: String? = null,
    currentBudget: Double,
    onConfirm: (Double) -> Unit,
    onDismiss: () -> Unit
) {
    val themeBlack = MaterialTheme.colorScheme.onSurface
    val themeWhite = MaterialTheme.colorScheme.surface
    val isDark = isSystemInDarkTheme()


    var budgetText by remember {
        mutableStateOf(if (currentBudget > 0) String.format("%.2f", currentBudget) else "")
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BrutalistCard(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .padding(16.dp),
            backgroundColor = themeWhite
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // Dialog header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp,
                            color = Black,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (subtitle != null) {
                            Text(
                                text = subtitle,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Black.copy(alpha = 0.6f)
                            )
                        }
                    }
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Black,
                        modifier = Modifier
                            .size(24.dp)
                            .clickable { onDismiss() }
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Budget input
                BrutalistTextField(
                    value = budgetText,
                    onValueChange = { budgetText = it },
                    label = "BUDGET AMOUNT (₹)",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Quick-set buttons
                Text(
                    "QUICK SET",
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    color = Black.copy(alpha = 0.5f),
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(50, 100, 200, 500).forEach { amount ->
                        BrutalistButton(
                            onClick = { budgetText = amount.toString() },
                            modifier = Modifier.weight(1f),
                            containerColor = themeWhite
                        ) {
                            Text(
                                "₹$amount",
                                color = themeBlack,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BrutalistButton(
                        onClick = {
                            onConfirm(0.0)
                        },
                        modifier = Modifier.weight(1f),
                        containerColor = themeWhite
                    ) {
                        Text("CLEAR", color = themeBlack, fontWeight = FontWeight.Bold)
                    }
                    val saveButtonBg = if (isDark) Color(0xFF1A1A1A) else themeBlack
                    val saveButtonText = if (isDark) themeBlack else themeWhite
                    BrutalistButton(
                        onClick = {
                            val budgetValue = budgetText.toDoubleOrNull() ?: 0.0
                            onConfirm(budgetValue)
                        },
                        modifier = Modifier.weight(1f),
                        containerColor = saveButtonBg
                    ) {
                        Text("SAVE", color = saveButtonText, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun BudgetScreen(
    isMobilePortrait: Boolean = false,
    expenses: List<Expense>,
    categories: List<String>,
    subcategoriesMap: Map<String, List<String>>,
    overallBudget: Double,
    categoryBudgets: Map<String, Double>,
    onCategoryBudgetChanged: (String, Double) -> Unit,
    subcategoryBudgets: Map<String, Double>,
    onSubcategoryBudgetChanged: (String, Double) -> Unit
) {
    val themeBlack = MaterialTheme.colorScheme.onSurface
    val themeWhite = MaterialTheme.colorScheme.surface
    val isDark = isSystemInDarkTheme()


    var currentMonth by remember { mutableStateOf(YearMonth.now()) }
    val monthName = currentMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault()).uppercase()
    val year = currentMonth.year

    // Filter expenses for current month
    val monthExpenses = remember(expenses, currentMonth) {
        expenses.filter { YearMonth.from(it.date) == currentMonth }
    }

    val totalSpent = remember(monthExpenses) { monthExpenses.sumOf { it.amount } }

    // Spending per category
    val categorySpending = remember(monthExpenses) {
        monthExpenses.groupBy { it.category }.mapValues { (_, list) -> list.sumOf { it.amount } }
    }

    // Spending per subcategory (key: "Category/Subcategory")
    val subcategorySpending = remember(monthExpenses) {
        monthExpenses.groupBy { "${it.category}/${it.subcategory ?: ""}" }
            .mapValues { (_, list) -> list.sumOf { it.amount } }
    }


    var editingCategoryBudget by remember { mutableStateOf<String?>(null) }
    var editingSubcategoryBudget by remember { mutableStateOf<Pair<String, String>?>(null) }

    // Track which category is currently selected for the right-hand panel
    var selectedCategory by remember { mutableStateOf<String?>(categories.firstOrNull()) }
    
    // Auto-select first category if current selection becomes invalid (e.g. deleted in settings)
    LaunchedEffect(categories) {
        if (selectedCategory == null || !categories.contains(selectedCategory)) {
            selectedCategory = categories.firstOrNull()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .then(if (isMobilePortrait) Modifier.verticalScroll(rememberScrollState()) else Modifier)
            .statusBarsPadding()
            .padding(16.dp)
    ) {
        // ── Header (Static) ──
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "BUDGET",
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold
            )
            
            // Month Selector Widget
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .border(2.dp, themeBlack)
                    .background(themeWhite)
                    .padding(2.dp)
            ) {
                Box(
                    modifier = Modifier.size(28.dp).background(themeBlack).clickable { currentMonth = currentMonth.minusMonths(1) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Prev", tint = themeWhite, modifier = Modifier.size(20.dp))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(min = 84.dp).padding(horizontal = 8.dp)) {
                    Text(text = monthName, fontSize = 12.sp, fontWeight = FontWeight.Black, lineHeight = 12.sp, color = themeBlack)
                    Text(text = year.toString(), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = themeBlack.copy(alpha = 0.5f), lineHeight = 9.sp)
                }
                Box(
                    modifier = Modifier.size(28.dp).background(themeBlack).clickable { currentMonth = currentMonth.plusMonths(1) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next", tint = themeWhite, modifier = Modifier.size(20.dp))
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))

        // Mobile portrait mode: re-arrange according to MobilePortrait.md specs
        if (isMobilePortrait) {
            // Total budget allocation card at top
            BrutalistCard(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                backgroundColor = themeWhite
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("TOTAL BUDGET ALLOCATION", fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, color = themeBlack.copy(alpha = 0.5f))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                        Text("₹${String.format("%.0f", overallBudget)}", fontWeight = FontWeight.Black, fontSize = 28.sp, color = themeBlack)
                        Column(horizontalAlignment = Alignment.End) {
                            val isOverBudget = totalSpent > overallBudget && overallBudget > 0
                            Text("₹${String.format("%.0f", totalSpent)} SPENT", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = if (isOverBudget) Color(0xFFFF4444) else themeBlack)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    BudgetProgressBar(spent = totalSpent, budget = overallBudget, barColor = themeBlack, trackColor = themeBlack.copy(alpha = 0.1f))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Budget list (categories) below total budget
            Text("CATEGORIES", fontWeight = FontWeight.Black, fontSize = 14.sp, color = themeBlack.copy(alpha = 0.4f), modifier = Modifier.padding(bottom = 8.dp))
            
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                categories.forEach { category ->
                    val isSelected = category == selectedCategory
                    val catBudget = categoryBudgets[category] ?: 0.0
                    val catSpent = categorySpending[category] ?: 0.0

                    val cardBg = if (isSelected) {
                        if (isDark) Color(0xFF1A1A1A) else themeBlack
                    } else {
                        themeWhite
                    }
                    val cardContentColor = if (isSelected) {
                        if (isDark) themeBlack else themeWhite
                    } else {
                        themeBlack
                    }

                    BrutalistCard(
                        modifier = Modifier.fillMaxWidth().clickable { selectedCategory = category },
                        backgroundColor = cardBg,
                        borderWidth = if (isSelected) 3.dp else 2.dp,
                        borderColor = themeBlack
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(category.uppercase(), fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = cardContentColor, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = cardContentColor, modifier = Modifier.size(16.dp).clickable { editingCategoryBudget = category })
                            }
                            Text("₹${String.format("%.0f", catBudget)}", fontWeight = FontWeight.Black, fontSize = 18.sp, color = cardContentColor)
                            BudgetProgressBar(spent = catSpent, budget = catBudget, barColor = cardContentColor, trackColor = cardContentColor.copy(alpha = 0.2f), height = 4.dp)
                        }
                    }
                }
            }

            // When category is selected, show subcategory list with category-specific allocated & unallocated cards above
            selectedCategory?.let { category ->
                Spacer(modifier = Modifier.height(24.dp))
                
                val categoryTotalBudget = categoryBudgets[category] ?: 0.0
                val categoryAllocated = remember(category, categoryBudgets, subcategoryBudgets, subcategoriesMap) {
                    val subs = subcategoriesMap[category] ?: emptyList()
                    subs.sumOf { sub ->
                        subcategoryBudgets["$category/${sub ?: ""}"] ?: 0.0
                    }
                }
                val categoryUnallocated = categoryTotalBudget - categoryAllocated

                // Category-specific allocated & unallocated cards above the list
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val allocatedCardBg = if (isDark) Color(0xFF1A1A1A) else themeBlack
                    val allocatedContentColor = if (isDark) themeBlack else themeWhite

                    BrutalistCard(modifier = Modifier.weight(1f), backgroundColor = allocatedCardBg) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("${category.uppercase()} ALLOCATED", color = allocatedContentColor.copy(alpha=0.6f), fontWeight = FontWeight.Bold, fontSize = 10.sp)
                            Text("₹${String.format("%.0f", categoryAllocated)}", color = allocatedContentColor, fontWeight = FontWeight.Black, fontSize = 20.sp)
                        }
                    }
                    
                    val isOverAllocated = categoryUnallocated < 0
                    BrutalistCard(
                        modifier = Modifier.weight(1f),
                        backgroundColor = if (isOverAllocated) Color(0xFFFF4444) else themeWhite
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            val unallocatedContentColor = if (isOverAllocated) themeWhite else themeBlack
                            Text(if (isOverAllocated) "OVER-ALLOC" else "${category.uppercase()} UNALLOCATED", color = unallocatedContentColor.copy(alpha=0.6f), fontWeight = FontWeight.Bold, fontSize = 9.sp)
                            Text("₹${String.format("%.0f", kotlin.math.abs(categoryUnallocated))}", color = unallocatedContentColor, fontWeight = FontWeight.Black, fontSize = 20.sp)
                        }
                    }
                }

                Text("SUBCATEGORIES", fontWeight = FontWeight.Black, fontSize = 14.sp, color = themeBlack.copy(alpha = 0.4f), modifier = Modifier.padding(bottom = 8.dp))
                
                val subs = subcategoriesMap[category] ?: emptyList()
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    subs.forEach { sub ->
                        val key = "$category/${sub ?: ""}"
                        val subBudget = subcategoryBudgets[key] ?: 0.0
                        val subSpent = subcategorySpending[key] ?: 0.0

                        BrutalistCard(
                            modifier = Modifier.fillMaxWidth(),
                            backgroundColor = themeWhite,
                            borderColor = themeBlack
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text((sub ?: "Uncategorized").uppercase(), fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = themeBlack, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = themeBlack, modifier = Modifier.size(14.dp).clickable { editingSubcategoryBudget = category to sub })
                                }
                                Text("₹${String.format("%.0f", subBudget)}", fontWeight = FontWeight.Black, fontSize = 16.sp, color = themeBlack)
                                BudgetProgressBar(spent = subSpent, budget = subBudget, barColor = themeBlack, trackColor = themeBlack.copy(alpha = 0.2f), height = 4.dp)
                            }
                        }
                    }
                }
            }
        } else {
            // Tablet/landscape mode: keep current layout
            // ── Summary Cards (Dynamic to Selected Category) ──
            val currentCategory = selectedCategory
            val categoryTotalBudget = if (currentCategory != null) categoryBudgets[currentCategory] ?: 0.0 else 0.0
            val categoryAllocated = remember(currentCategory, categoryBudgets, subcategoryBudgets, subcategoriesMap) {
                if (currentCategory != null) {
                    val subs = subcategoriesMap[currentCategory] ?: emptyList()
                    subs.sumOf { sub ->
                        subcategoryBudgets["$currentCategory/${sub ?: ""}"] ?: 0.0
                    }
                } else {
                    0.0
                }
            }
            val categoryUnallocated = categoryTotalBudget - categoryAllocated

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val allocatedCardBg = if (isDark) Color(0xFF1A1A1A) else themeBlack
                val allocatedContentColor = if (isDark) themeBlack else themeWhite

                BrutalistCard(modifier = Modifier.weight(1f), backgroundColor = allocatedCardBg) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(if (currentCategory != null) "${currentCategory.uppercase()} ALLOCATED" else "ALLOCATED", color = allocatedContentColor.copy(alpha=0.6f), fontWeight = FontWeight.Bold, fontSize = 10.sp)
                        Text("₹${String.format("%.0f", categoryAllocated)}", color = allocatedContentColor, fontWeight = FontWeight.Black, fontSize = 20.sp)
                    }
                }
                
                val isOverAllocated = categoryUnallocated < 0
                BrutalistCard(
                    modifier = Modifier.weight(1f),
                    backgroundColor = if (isOverAllocated) Color(0xFFFF4444) else themeWhite
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        val unallocatedContentColor = if (isOverAllocated) themeWhite else themeBlack
                        Text(if (isOverAllocated) "OVER-ALLOC" else if (currentCategory != null) "${currentCategory.uppercase()} UNALLOCATED" else "UNALLOCATED", color = unallocatedContentColor.copy(alpha=0.6f), fontWeight = FontWeight.Bold, fontSize = 9.sp)
                        Text("₹${String.format("%.0f", kotlin.math.abs(categoryUnallocated))}", color = unallocatedContentColor, fontWeight = FontWeight.Black, fontSize = 20.sp)
                    }
                }
            }

            // ── Total Budget Card (Static) ──
            BrutalistCard(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                backgroundColor = themeWhite
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("TOTAL BUDGET ALLOCATION", fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, color = themeBlack.copy(alpha = 0.5f))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                        Text("₹${String.format("%.0f", overallBudget)}", fontWeight = FontWeight.Black, fontSize = 28.sp, color = themeBlack)
                        Column(horizontalAlignment = Alignment.End) {
                            val isOverBudget = totalSpent > overallBudget && overallBudget > 0
                            Text("₹${String.format("%.0f", totalSpent)} SPENT", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = if (isOverBudget) Color(0xFFFF4444) else themeBlack)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    BudgetProgressBar(spent = totalSpent, budget = overallBudget, barColor = themeBlack, trackColor = themeBlack.copy(alpha = 0.1f))
                }
            }

            // ── Split Section (Dynamic Scrollers) ──
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // LEFT HALF: Categories
            Column(modifier = Modifier.weight(1f)) {
                Text("CATEGORIES", fontWeight = FontWeight.Black, fontSize = 10.sp, color = themeBlack.copy(alpha = 0.4f), modifier = Modifier.padding(bottom = 8.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(categories) { category ->
                        val isSelected = category == selectedCategory
                        val catBudget = categoryBudgets[category] ?: 0.0
                        val catSpent = categorySpending[category] ?: 0.0

                        val cardBg = if (isSelected) {
                            if (isDark) Color(0xFF1A1A1A) else themeBlack
                        } else {
                            themeWhite
                        }
                        val cardContentColor = if (isSelected) {
                            if (isDark) themeBlack else themeWhite
                        } else {
                            themeBlack
                        }

                        BrutalistCard(
                            modifier = Modifier.fillMaxWidth().clickable { selectedCategory = category },
                            backgroundColor = cardBg,
                            borderWidth = if (isSelected) 3.dp else 2.dp,
                            borderColor = themeBlack
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(category.uppercase(), fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = cardContentColor, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = cardContentColor, modifier = Modifier.size(14.dp).clickable { editingCategoryBudget = category })
                                }
                                Text("₹${String.format("%.0f", catBudget)}", fontWeight = FontWeight.Black, fontSize = 16.sp, color = cardContentColor)
                                BudgetProgressBar(spent = catSpent, budget = catBudget, barColor = cardContentColor, trackColor = cardContentColor.copy(alpha = 0.2f), height = 4.dp)
                            }
                        }
                    }
                }
            }

            // RIGHT HALF: Subcategories
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (currentCategory != null) "${currentCategory.uppercase()} SUBS" else "SELECT CAT",
                    fontWeight = FontWeight.Black,
                    fontSize = 10.sp,
                    color = Black.copy(alpha = 0.4f),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                if (currentCategory != null) {
                    val subs = subcategoriesMap[currentCategory] ?: emptyList()
                    if (subs.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize().border(2.dp, themeBlack), contentAlignment = Alignment.Center) {
                            Text("NO SUBS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = themeBlack.copy(alpha = 0.3f))
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(subs) { sub ->
                                val subKey = "$currentCategory/${sub ?: ""}"
                                val subBudget = subcategoryBudgets[subKey] ?: 0.0
                                val subSpent = subcategorySpending[subKey] ?: 0.0
                                
                                BrutalistCard(modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(sub.uppercase(), fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                            Icon(Icons.Default.Edit, contentDescription = "Edit", tint = themeBlack, modifier = Modifier.size(14.dp).clickable { editingSubcategoryBudget = currentCategory to sub })
                                        }
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                            Text("₹${String.format("%.2f", subBudget)}", fontWeight = FontWeight.Black, fontSize = 14.sp)
                                            Text(text = "₹${String.format("%.2f", subSpent)} spent", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = themeBlack.copy(alpha = 0.5f))
                                        }
                                        BudgetProgressBar(spent = subSpent, budget = subBudget, barColor = themeBlack, trackColor = themeBlack.copy(alpha = 0.1f), height = 5.dp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Dialogs ──

    editingCategoryBudget?.let { category ->
        BudgetEditDialog(
            title = "${category.uppercase()} BUDGET",
            currentBudget = categoryBudgets[category] ?: 0.0,
            onConfirm = { newBudget ->
                onCategoryBudgetChanged(category, newBudget)
                editingCategoryBudget = null
            },
            onDismiss = { editingCategoryBudget = null }
        )
    }

    editingSubcategoryBudget?.let { (category, subcategory) ->
        val subKey = "$category/$subcategory"
        BudgetEditDialog(
            title = "${subcategory.uppercase()} BUDGET",
            subtitle = "▸ ${category.uppercase()}",
            currentBudget = subcategoryBudgets[subKey] ?: 0.0,
            onConfirm = { newBudget ->
                onSubcategoryBudgetChanged(subKey, newBudget)
                editingSubcategoryBudget = null
            },
            onDismiss = { editingSubcategoryBudget = null }
        )
    }
    }
}
