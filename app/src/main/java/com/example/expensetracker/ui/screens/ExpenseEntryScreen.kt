package com.example.expensetracker.ui.screens

import com.example.expensetracker.model.Expense
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.expensetracker.ui.components.BrutalistButton
import com.example.expensetracker.ui.components.BrutalistCard
import com.example.expensetracker.ui.components.BrutalistDropdown
import com.example.expensetracker.ui.components.BrutalistMultiSelectDropdown
import com.example.expensetracker.ui.components.BrutalistTextField
import com.example.expensetracker.ui.components.BrutalistDatePickerDialog
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.expensetracker.ui.theme.Black
import com.example.expensetracker.ui.theme.White
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class SubTransaction(
    val id: Int,
    var category: String = "",
    var subcategory: String = "",
    var amount: String = "",
    var description: String = "",
    var labels: List<String> = emptyList(),
    var quantity: String = "",
    var unit: String = "",
    var notes: String = "",
    var baseAmount: String = "",
    var gstPercentage: String = "",
    var gstAmount: String = ""
)

@Composable
fun ExpenseEntryScreen(
    categories: List<String>,
    subcategoriesMap: Map<String, List<String>>,
    labels: List<String>,
    paymentModes: List<String>,
    paidVia: List<String>,
    storeHistory: List<String> = emptyList(),
    expenseToEdit: Expense? = null,
    groupToEdit: List<Expense>? = null,
    onSave: (List<Expense>) -> Unit,
    onBack: () -> Unit,
    onAddCategory: (String) -> Unit = {},
    onAddSubcategory: (String, String) -> Unit = { _, _ -> },
    onAddLabel: (String) -> Unit = {},
    onAddPaymentMode: (String) -> Unit = {},
    onAddPaidVia: (String) -> Unit = {},
    onUpdateStoreHistory: (String) -> Unit = {},
    initialDate: LocalDate? = null
) {
    val initialStoreName = remember { expenseToEdit?.storeName ?: groupToEdit?.firstOrNull()?.storeName ?: "" }
    val initialLocation = remember { expenseToEdit?.location ?: groupToEdit?.firstOrNull()?.location ?: "" }
    val initialTotalAmount = remember { expenseToEdit?.amount?.toString() ?: "" }
    val initialMainCategory = remember { expenseToEdit?.category ?: "" }
    val initialMainSubcategory = remember { expenseToEdit?.subcategory ?: "" }
    val initialSelectedLabels = remember { expenseToEdit?.labels?.toList() ?: emptyList<String>() }
    val initialMainPaymentMode = remember { expenseToEdit?.paymentMode ?: "" }
    val initialMainPaidVia = remember { expenseToEdit?.paidVia ?: "" }
    val initialIsSplit = remember { groupToEdit != null && (groupToEdit.size > 1) }
    val initialQuantity = remember { expenseToEdit?.quantity?.toString() ?: groupToEdit?.firstOrNull()?.quantity?.toString() ?: "" }
    val initialUnit = remember { expenseToEdit?.unit ?: groupToEdit?.firstOrNull()?.unit ?: "" }
    val initialDateValue = remember { initialDate ?: expenseToEdit?.date ?: groupToEdit?.firstOrNull()?.date ?: LocalDate.now() }
    val initialSubTransactions = remember {
        groupToEdit?.mapIndexed { index, e ->
            SubTransaction(
                id = index + 1,
                category = e.category,
                subcategory = e.subcategory,
                amount = e.amount.toString(),
                description = e.itemDescription,
                labels = e.labels,
                quantity = e.quantity?.toString() ?: "",
                unit = e.unit ?: "",
                notes = e.notes ?: "",
                baseAmount = e.baseAmount?.toString() ?: e.amount.toString(),
                gstPercentage = e.gstPercentage?.toString() ?: "",
                gstAmount = e.gstAmount?.toString() ?: ""
            )
        } ?: listOf(SubTransaction(1))
    }

    var storeName by remember { mutableStateOf(initialStoreName) }
    var location by remember { mutableStateOf(initialLocation) }
    val initialDescription = remember { expenseToEdit?.itemDescription ?: groupToEdit?.firstOrNull()?.itemDescription ?: "" }
    var description by remember { mutableStateOf(initialDescription) }
    var totalAmount by remember { mutableStateOf(initialTotalAmount) }
    
    val initialBaseAmount = remember { expenseToEdit?.baseAmount?.toString() ?: expenseToEdit?.amount?.toString() ?: "" }
    val initialGstPercentage = remember { expenseToEdit?.gstPercentage?.toString() ?: "" }
    val initialGstAmount = remember { expenseToEdit?.gstAmount?.toString() ?: "" }
    var baseAmount by remember { mutableStateOf(initialBaseAmount) }
    var gstPercentage by remember { mutableStateOf(initialGstPercentage) }
    var gstAmount by remember { mutableStateOf(initialGstAmount) }
    
    val initialGlobalGstPercent = remember {
        val firstGst = groupToEdit?.firstOrNull()?.gstPercentage
        if (firstGst != null && groupToEdit.all { it.gstPercentage == firstGst }) {
            firstGst.toString()
        } else ""
    }
    val initialTotalGstPaid = remember {
        val totalGst = groupToEdit?.sumOf { it.gstAmount ?: 0.0 } ?: 0.0
        if (totalGst > 0.0) String.format("%.2f", totalGst).replace(".00", "") else ""
    }
    var globalGstPercent by remember { mutableStateOf(initialGlobalGstPercent) }
    var totalGstPaid by remember { mutableStateOf(initialTotalGstPaid) }

    var quantity by remember { mutableStateOf(initialQuantity) }
    var unit by remember { mutableStateOf(initialUnit) }
    val initialNotes = remember { expenseToEdit?.notes ?: groupToEdit?.firstOrNull()?.notes ?: "" }
    var notes by remember { mutableStateOf(initialNotes) }

    var mainCategory by remember { mutableStateOf(initialMainCategory) }
    var mainSubcategory by remember { mutableStateOf(initialMainSubcategory) }
    var mainPaymentMode by remember { mutableStateOf(initialMainPaymentMode) }
    var mainPaidVia by remember { mutableStateOf(initialMainPaidVia) }

    val selectedLabels = remember {
        mutableStateListOf<String>().apply {
            addAll(initialSelectedLabels)
        }
    }

    var isSplit by remember { mutableStateOf(initialIsSplit) }
    var subTransactions by remember {
        mutableStateOf(initialSubTransactions)
    }
    var expandedSplitId by remember { mutableStateOf<Int?>(subTransactions.firstOrNull()?.id) }

    var showExitConfirmation by remember { mutableStateOf(false) }
    var selectedDate by remember { mutableStateOf(initialDateValue) }
    var showDatePicker by remember { mutableStateOf(false) }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd/MM/yyyy") }

    // Add new item dialog states
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var showAddSubcategoryDialog by remember { mutableStateOf(false) }
    var showAddLabelDialog by remember { mutableStateOf(false) }
    var showAddPaymentModeDialog by remember { mutableStateOf(false) }
    var showAddPaidViaDialog by remember { mutableStateOf(false) }
    var newCategoryName by remember { mutableStateOf("") }
    var newSubcategoryName by remember { mutableStateOf("") }
    var newLabelName by remember { mutableStateOf("") }
    var newPaymentModeName by remember { mutableStateOf("") }
    var newPaidViaName by remember { mutableStateOf("") }
    var editingSplitIndex by remember { mutableStateOf<Int?>(null) }

    // Calculator state
    var showCalculator by remember { mutableStateOf(false) }
    var calcDisplay by remember { mutableStateOf("0") }
    var calcPrevious by remember { mutableStateOf("") }
    var calcOperation by remember { mutableStateOf<String?>(null) }
    var calcNewNumber by remember { mutableStateOf(true) }
    var calcTargetField by remember { mutableStateOf<String?>(null) }
    var calcTargetSplitIndex by remember { mutableStateOf<Int?>(null) }

    // Store suggestions state
    var showStoreSuggestions by remember { mutableStateOf(false) }
    val filteredStoreSuggestions = remember(storeName, storeHistory) {
        storeHistory.filter { it.isNotBlank() && it.contains(storeName, ignoreCase = true) }
            .distinct()
            .take(5)
    }

    val isEditing = expenseToEdit != null || (groupToEdit != null && groupToEdit.isNotEmpty())

    fun hasChanges(): Boolean {
        return storeName != initialStoreName ||
                location != initialLocation ||
                description != initialDescription ||
                totalAmount != initialTotalAmount ||
                baseAmount != initialBaseAmount ||
                gstPercentage != initialGstPercentage ||
                gstAmount != initialGstAmount ||
                quantity != initialQuantity ||
                unit != initialUnit ||
                notes != initialNotes ||
                mainCategory != initialMainCategory ||
                mainSubcategory != initialMainSubcategory ||
                mainPaymentMode != initialMainPaymentMode ||
                mainPaidVia != initialMainPaidVia ||
                selectedLabels.toList() != initialSelectedLabels ||
                isSplit != initialIsSplit ||
                subTransactions != initialSubTransactions ||
                selectedDate != initialDateValue
    }

    // Calculator helper functions
    fun onCalcDigit(digit: String) {
        if (calcNewNumber) {
            calcDisplay = digit
            calcNewNumber = false
        } else {
            calcDisplay = if (calcDisplay == "0") digit else calcDisplay + digit
        }
    }

    fun onCalcOperation(op: String) {
        calcPrevious = calcDisplay
        calcOperation = op
        calcNewNumber = true
    }

    fun onCalcEqual() {
        if (calcOperation != null && calcPrevious.isNotEmpty()) {
            val prev = calcPrevious.toDoubleOrNull() ?: 0.0
            val curr = calcDisplay.toDoubleOrNull() ?: 0.0
            val result = when (calcOperation) {
                "+" -> prev + curr
                "-" -> prev - curr
                "*" -> prev * curr
                "/" -> if (curr != 0.0) prev / curr else 0.0
                else -> curr
            }
            calcDisplay = String.format("%.2f", result).replace(".00", "")
            calcOperation = null
            calcPrevious = ""
            calcNewNumber = true
        }
    }

    fun onCalcClear() {
        calcDisplay = "0"
        calcPrevious = ""
        calcOperation = null
        calcNewNumber = true
    }

    fun applyGlobalGstPercent(splits: List<SubTransaction>, percentStr: String): List<SubTransaction> {
        val percentVal = percentStr.toDoubleOrNull() ?: 0.0
        return splits.map { subTx ->
            val baseVal = subTx.baseAmount.toDoubleOrNull() ?: 0.0
            val gstAmtVal = baseVal * (percentVal / 100.0)
            val totalVal = baseVal + gstAmtVal
            subTx.copy(
                gstPercentage = percentStr,
                gstAmount = if (baseVal > 0.0 && percentVal > 0.0) String.format("%.2f", gstAmtVal).replace(".00", "") else "",
                amount = if (totalVal > 0.0) String.format("%.2f", totalVal).replace(".00", "") else ""
            )
        }
    }

    fun applyTotalGstPaid(splits: List<SubTransaction>, totalGst: Double): List<SubTransaction> {
        val totalBase = splits.sumOf { it.baseAmount.toDoubleOrNull() ?: 0.0 }
        if (totalBase <= 0.0) return splits
        val percentVal = (totalGst / totalBase) * 100.0
        val percentStr = String.format("%.2f", percentVal).replace(".00", "")
        globalGstPercent = percentStr
        
        return splits.map { subTx ->
            val baseVal = subTx.baseAmount.toDoubleOrNull() ?: 0.0
            val gstAmtVal = baseVal * (percentVal / 100.0)
            val totalVal = baseVal + gstAmtVal
            subTx.copy(
                gstPercentage = percentStr,
                gstAmount = if (baseVal > 0.0) String.format("%.2f", gstAmtVal).replace(".00", "") else "",
                amount = if (totalVal > 0.0) String.format("%.2f", totalVal).replace(".00", "") else ""
            )
        }
    }

    fun onCalcInsert() {
        val target = calcTargetField
        val valueStr = calcDisplay
        
        if (target != null) {
            when (target) {
                "MAIN_BASE" -> {
                    baseAmount = valueStr
                    val baseVal = valueStr.toDoubleOrNull() ?: 0.0
                    val percentVal = gstPercentage.toDoubleOrNull() ?: 0.0
                    val gstAmtVal = gstAmount.toDoubleOrNull() ?: 0.0
                    if (gstPercentage.isNotEmpty()) {
                        val calculatedGst = baseVal * (percentVal / 100.0)
                        gstAmount = if (calculatedGst > 0.0) String.format("%.2f", calculatedGst).replace(".00", "") else ""
                        val totalVal = baseVal + calculatedGst
                        totalAmount = if (totalVal > 0.0) String.format("%.2f", totalVal).replace(".00", "") else ""
                    } else if (gstAmount.isNotEmpty()) {
                        val totalVal = baseVal + gstAmtVal
                        totalAmount = if (totalVal > 0.0) String.format("%.2f", totalVal).replace(".00", "") else ""
                        if (baseVal > 0.0) {
                            gstPercentage = String.format("%.2f", (gstAmtVal / baseVal) * 100.0).replace(".00", "")
                        }
                    } else {
                        totalAmount = valueStr
                    }
                }
                "MAIN_GST_PCT" -> {
                    gstPercentage = valueStr
                    val baseVal = baseAmount.toDoubleOrNull() ?: 0.0
                    val percentVal = valueStr.toDoubleOrNull() ?: 0.0
                    if (valueStr.isNotEmpty()) {
                        val calculatedGst = baseVal * (percentVal / 100.0)
                        gstAmount = if (calculatedGst > 0.0) String.format("%.2f", calculatedGst).replace(".00", "") else ""
                        val totalVal = baseVal + calculatedGst
                        totalAmount = if (totalVal > 0.0) String.format("%.2f", totalVal).replace(".00", "") else ""
                    } else {
                        gstAmount = ""
                        totalAmount = baseAmount
                    }
                }
                "MAIN_GST_AMT" -> {
                    gstAmount = valueStr
                    val baseVal = baseAmount.toDoubleOrNull() ?: 0.0
                    val gstAmtVal = valueStr.toDoubleOrNull() ?: 0.0
                    if (valueStr.isNotEmpty()) {
                        val totalVal = baseVal + gstAmtVal
                        totalAmount = if (totalVal > 0.0) String.format("%.2f", totalVal).replace(".00", "") else ""
                        if (baseVal > 0.0) {
                            gstPercentage = String.format("%.2f", (gstAmtVal / baseVal) * 100.0).replace(".00", "")
                        }
                    } else {
                        gstPercentage = ""
                        totalAmount = baseAmount
                    }
                }
                "SPLIT_BASE", "SPLIT_GST_PCT", "SPLIT_GST_AMT" -> {
                    val index = calcTargetSplitIndex
                    if (index != null && index >= 0 && index < subTransactions.size) {
                        val subTx = subTransactions[index]
                        
                        var newBase = subTx.baseAmount
                        var newGstPct = subTx.gstPercentage
                        var newGstAmt = subTx.gstAmount
                        var newTotal = subTx.amount
                        
                        when (target) {
                            "SPLIT_BASE" -> {
                                newBase = valueStr
                                val baseVal = valueStr.toDoubleOrNull() ?: 0.0
                                val percentVal = subTx.gstPercentage.toDoubleOrNull() ?: 0.0
                                val gstAmtVal = subTx.gstAmount.toDoubleOrNull() ?: 0.0
                                if (subTx.gstPercentage.isNotEmpty()) {
                                    val calculatedGst = baseVal * (percentVal / 100.0)
                                    newGstAmt = if (calculatedGst > 0.0) String.format("%.2f", calculatedGst).replace(".00", "") else ""
                                    val totalVal = baseVal + calculatedGst
                                    newTotal = if (totalVal > 0.0) String.format("%.2f", totalVal).replace(".00", "") else ""
                                } else if (subTx.gstAmount.isNotEmpty()) {
                                    val totalVal = baseVal + gstAmtVal
                                    newTotal = if (totalVal > 0.0) String.format("%.2f", totalVal).replace(".00", "") else ""
                                    newGstPct = if (baseVal > 0.0) String.format("%.2f", (gstAmtVal / baseVal) * 100.0).replace(".00", "") else ""
                                } else {
                                    newTotal = valueStr
                                }
                            }
                            "SPLIT_GST_PCT" -> {
                                newGstPct = valueStr
                                val baseVal = subTx.baseAmount.toDoubleOrNull() ?: 0.0
                                val percentVal = valueStr.toDoubleOrNull() ?: 0.0
                                if (valueStr.isNotEmpty()) {
                                    val calculatedGst = baseVal * (percentVal / 100.0)
                                    newGstAmt = if (calculatedGst > 0.0) String.format("%.2f", calculatedGst).replace(".00", "") else ""
                                    val totalVal = baseVal + calculatedGst
                                    newTotal = if (totalVal > 0.0) String.format("%.2f", totalVal).replace(".00", "") else ""
                                } else {
                                    newGstAmt = ""
                                    newTotal = subTx.baseAmount
                                }
                                globalGstPercent = ""
                                totalGstPaid = ""
                            }
                            "SPLIT_GST_AMT" -> {
                                newGstAmt = valueStr
                                val baseVal = subTx.baseAmount.toDoubleOrNull() ?: 0.0
                                val gstAmtVal = valueStr.toDoubleOrNull() ?: 0.0
                                if (valueStr.isNotEmpty()) {
                                    val totalVal = baseVal + gstAmtVal
                                    newTotal = if (totalVal > 0.0) String.format("%.2f", totalVal).replace(".00", "") else ""
                                    newGstPct = if (baseVal > 0.0) String.format("%.2f", (gstAmtVal / baseVal) * 100.0).replace(".00", "") else ""
                                } else {
                                    newGstPct = ""
                                    newTotal = subTx.baseAmount
                                }
                                globalGstPercent = ""
                                totalGstPaid = ""
                            }
                        }
                        
                        val updatedSplits = subTransactions.toMutableList().apply {
                            this[index] = subTx.copy(
                                baseAmount = newBase,
                                gstPercentage = newGstPct,
                                gstAmount = newGstAmt,
                                amount = newTotal
                            )
                        }
                        
                        if (globalGstPercent.isNotEmpty()) {
                            subTransactions = applyGlobalGstPercent(updatedSplits, globalGstPercent)
                        } else if (totalGstPaid.isNotEmpty()) {
                            val totalGstVal = totalGstPaid.toDoubleOrNull() ?: 0.0
                            subTransactions = applyTotalGstPaid(updatedSplits, totalGstVal)
                        } else {
                            subTransactions = updatedSplits
                        }
                    }
                }
            }
        } else {
            if (isSplit && expandedSplitId != null) {
                val index = subTransactions.indexOfFirst { it.id == expandedSplitId }
                if (index >= 0) {
                    val subTx = subTransactions[index]
                    val baseVal = calcDisplay.toDoubleOrNull() ?: 0.0
                    val percentVal = subTx.gstPercentage.toDoubleOrNull() ?: 0.0
                    val gstAmtVal = subTx.gstAmount.toDoubleOrNull() ?: 0.0
                    val calculatedGst = baseVal * (percentVal / 100.0)
                    val newGstAmtStr = if (calculatedGst > 0.0) String.format("%.2f", calculatedGst).replace(".00", "") else ""
                    val totalVal = baseVal + calculatedGst
                    val newTotalAmtStr = if (totalVal > 0.0) String.format("%.2f", totalVal).replace(".00", "") else ""
                    
                    val updatedSplits = subTransactions.toMutableList().apply {
                        this[index] = subTx.copy(
                            baseAmount = calcDisplay,
                            gstPercentage = subTx.gstPercentage,
                            gstAmount = newGstAmtStr,
                            amount = newTotalAmtStr
                        )
                    }
                    if (globalGstPercent.isNotEmpty()) {
                        subTransactions = applyGlobalGstPercent(updatedSplits, globalGstPercent)
                    } else if (totalGstPaid.isNotEmpty()) {
                        val totalGstVal = totalGstPaid.toDoubleOrNull() ?: 0.0
                        subTransactions = applyTotalGstPaid(updatedSplits, totalGstVal)
                    } else {
                        subTransactions = updatedSplits
                    }
                }
            } else {
                baseAmount = calcDisplay
                val baseVal = calcDisplay.toDoubleOrNull() ?: 0.0
                val percentVal = gstPercentage.toDoubleOrNull() ?: 0.0
                val calculatedGst = baseVal * (percentVal / 100.0)
                gstAmount = if (calculatedGst > 0.0) String.format("%.2f", calculatedGst).replace(".00", "") else ""
                val totalVal = baseVal + calculatedGst
                totalAmount = if (totalVal > 0.0) String.format("%.2f", totalVal).replace(".00", "") else ""
            }
        }
        showCalculator = false
        calcTargetField = null
        calcTargetSplitIndex = null
    }

    fun performSave(isDraft: Boolean = false) {
        // Update store history with the current store name if it's not empty
        if (storeName.isNotBlank()) {
            onUpdateStoreHistory(storeName)
        }

        fun expenseIdForIndex(index: Int): String {
            return groupToEdit?.getOrNull(index)?.id
                ?: expenseToEdit?.takeIf { index == 0 }?.id
                ?: java.util.UUID.randomUUID().toString()
        }

        val expenseDate = selectedDate
        val sharedGroupId = expenseToEdit?.groupId ?: groupToEdit?.firstOrNull()?.groupId ?: java.util.UUID.randomUUID().toString()
        val listToSave = if (isSplit && subTransactions.size > 1) {
            subTransactions.mapIndexed { index, subTx ->
                val finalAmount = subTx.amount.toDoubleOrNull() ?: 0.0
                Expense(
                    id = expenseIdForIndex(index),
                    groupId = sharedGroupId,
                    date = expenseDate,
                    storeName = storeName,
                    location = location,
                    amount = finalAmount,
                    category = subTx.category.ifEmpty { mainCategory },
                    subcategory = subTx.subcategory.ifEmpty { mainSubcategory },
                    itemDescription = subTx.description,
                    labels = subTx.labels.ifEmpty { selectedLabels.toList() },
                    quantity = subTx.quantity.toDoubleOrNull(),
                    unit = subTx.unit.ifEmpty { null },
                    notes = subTx.notes.ifEmpty { notes },
                    paymentMode = mainPaymentMode,
                    paidVia = mainPaidVia,
                    isDraft = isDraft,
                    baseAmount = subTx.baseAmount.toDoubleOrNull() ?: finalAmount,
                    gstPercentage = subTx.gstPercentage.toDoubleOrNull(),
                    gstAmount = subTx.gstAmount.toDoubleOrNull()
                )
            }
        } else if (isSplit && subTransactions.size == 1) {
            val subTx = subTransactions[0]
            val finalAmount = subTx.amount.toDoubleOrNull() ?: 0.0
            listOf(
                Expense(
                    id = expenseIdForIndex(0),
                    groupId = sharedGroupId,
                    date = expenseDate,
                    storeName = storeName,
                    location = location,
                    amount = finalAmount,
                    category = subTx.category.ifEmpty { mainCategory },
                    subcategory = subTx.subcategory.ifEmpty { mainSubcategory },
                    itemDescription = subTx.description,
                    labels = subTx.labels.ifEmpty { selectedLabels.toList() },
                    quantity = subTx.quantity.toDoubleOrNull(),
                    unit = subTx.unit.ifEmpty { null },
                    notes = subTx.notes.ifEmpty { notes },
                    paymentMode = mainPaymentMode,
                    paidVia = mainPaidVia,
                    isDraft = isDraft,
                    baseAmount = subTx.baseAmount.toDoubleOrNull() ?: finalAmount,
                    gstPercentage = subTx.gstPercentage.toDoubleOrNull(),
                    gstAmount = subTx.gstAmount.toDoubleOrNull()
                )
            )
        } else {
            val finalAmount = totalAmount.toDoubleOrNull() ?: 0.0
            listOf(
                Expense(
                    id = expenseIdForIndex(0),
                    groupId = sharedGroupId,
                    date = expenseDate,
                    storeName = storeName,
                    location = location,
                    amount = finalAmount,
                    category = mainCategory,
                    subcategory = mainSubcategory,
                    itemDescription = description,
                    labels = selectedLabels.toList(),
                    quantity = quantity.toDoubleOrNull(),
                    unit = unit.ifEmpty { null },
                    notes = notes,
                    paymentMode = mainPaymentMode,
                    paidVia = mainPaidVia,
                    isDraft = isDraft,
                    baseAmount = baseAmount.toDoubleOrNull() ?: finalAmount,
                    gstPercentage = gstPercentage.toDoubleOrNull(),
                    gstAmount = gstAmount.toDoubleOrNull()
                )
            )
        }
        onSave(listToSave)
    }

    // ── Exit confirmation dialog ──────────────────────────────────────────────
    if (showExitConfirmation) {
        Dialog(onDismissRequest = { showExitConfirmation = false }) {
            BrutalistCard(modifier = Modifier.padding(16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("UNSAVED CHANGES", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Do you want to save before leaving?")
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BrutalistButton(
                            onClick = {
                                showExitConfirmation = false
                                onBack()
                            },
                            modifier = Modifier.weight(1f),
                            containerColor = White
                        ) {
                            Text("DISCARD", color = Black, fontWeight = FontWeight.Bold)
                        }
                        BrutalistButton(
                            onClick = {
                                showExitConfirmation = false
                                performSave(isDraft = true)
                            },
                            modifier = Modifier.weight(1f),
                            containerColor = White
                        ) {
                            Text("SAVE DRAFT", color = Black, fontWeight = FontWeight.Bold)
                        }
                        BrutalistButton(
                            onClick = {
                                showExitConfirmation = false
                                performSave(isDraft = false)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("SAVE", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // ── Add new category dialog ───────────────────────────────────────────────
    if (showAddCategoryDialog) {
        Dialog(onDismissRequest = { showAddCategoryDialog = false }) {
            BrutalistCard(modifier = Modifier.padding(16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("NEW CATEGORY", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(modifier = Modifier.height(12.dp))
                    BrutalistTextField(
                        value = newCategoryName,
                        onValueChange = { newCategoryName = it },
                        label = "Category Name"
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BrutalistButton(
                            onClick = {
                                showAddCategoryDialog = false
                                newCategoryName = ""
                                editingSplitIndex = null
                            },
                            modifier = Modifier.weight(1f),
                            containerColor = White
                        ) {
                            Text("CANCEL", color = Black, fontWeight = FontWeight.Bold)
                        }
                        BrutalistButton(
                            onClick = {
                                if (newCategoryName.isNotBlank()) {
                                    onAddCategory(newCategoryName)
                                    if (editingSplitIndex != null) {
                                        subTransactions = subTransactions.toMutableList().apply {
                                            this[editingSplitIndex!!] = this[editingSplitIndex!!].copy(category = newCategoryName, subcategory = "")
                                        }
                                    } else {
                                        mainCategory = newCategoryName
                                        mainSubcategory = ""
                                    }
                                    showAddCategoryDialog = false
                                    newCategoryName = ""
                                    editingSplitIndex = null
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("ADD", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // ── Add new subcategory dialog ────────────────────────────────────────────
    if (showAddSubcategoryDialog) {
        Dialog(onDismissRequest = { showAddSubcategoryDialog = false }) {
            BrutalistCard(modifier = Modifier.padding(16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("NEW SUBCATEGORY", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(modifier = Modifier.height(12.dp))
                    val categoryForSubcategory = if (editingSplitIndex != null) {
                        subTransactions[editingSplitIndex!!].category.ifEmpty { mainCategory }
                    } else {
                        mainCategory
                    }
                    Text("For: $categoryForSubcategory", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    BrutalistTextField(
                        value = newSubcategoryName,
                        onValueChange = { newSubcategoryName = it },
                        label = "Subcategory Name"
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BrutalistButton(
                            onClick = {
                                showAddSubcategoryDialog = false
                                newSubcategoryName = ""
                                editingSplitIndex = null
                            },
                            modifier = Modifier.weight(1f),
                            containerColor = White
                        ) {
                            Text("CANCEL", color = Black, fontWeight = FontWeight.Bold)
                        }
                        BrutalistButton(
                            onClick = {
                                if (newSubcategoryName.isNotBlank() && categoryForSubcategory.isNotBlank()) {
                                    onAddSubcategory(categoryForSubcategory, newSubcategoryName)
                                    if (editingSplitIndex != null) {
                                        subTransactions = subTransactions.toMutableList().apply {
                                            this[editingSplitIndex!!] = this[editingSplitIndex!!].copy(subcategory = newSubcategoryName)
                                        }
                                    } else {
                                        mainSubcategory = newSubcategoryName
                                    }
                                    showAddSubcategoryDialog = false
                                    newSubcategoryName = ""
                                    editingSplitIndex = null
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("ADD", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // ── Add new label dialog ──────────────────────────────────────────────────
    if (showAddLabelDialog) {
        Dialog(onDismissRequest = { showAddLabelDialog = false }) {
            BrutalistCard(modifier = Modifier.padding(16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("NEW LABEL", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(modifier = Modifier.height(12.dp))
                    BrutalistTextField(
                        value = newLabelName,
                        onValueChange = { newLabelName = it },
                        label = "Label Name"
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BrutalistButton(
                            onClick = {
                                showAddLabelDialog = false
                                newLabelName = ""
                                editingSplitIndex = null
                            },
                            modifier = Modifier.weight(1f),
                            containerColor = White
                        ) {
                            Text("CANCEL", color = Black, fontWeight = FontWeight.Bold)
                        }
                        BrutalistButton(
                            onClick = {
                                if (newLabelName.isNotBlank()) {
                                    onAddLabel(newLabelName)
                                    if (editingSplitIndex != null) {
                                        subTransactions = subTransactions.toMutableList().apply {
                                            this[editingSplitIndex!!] = this[editingSplitIndex!!].copy(labels = this[editingSplitIndex!!].labels + newLabelName)
                                        }
                                    } else {
                                        selectedLabels.add(newLabelName)
                                    }
                                    showAddLabelDialog = false
                                    newLabelName = ""
                                    editingSplitIndex = null
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("ADD", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // ── Add new payment mode dialog ─────────────────────────────────────────────
    if (showAddPaymentModeDialog) {
        Dialog(onDismissRequest = { showAddPaymentModeDialog = false }) {
            BrutalistCard(modifier = Modifier.padding(16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("NEW PAYMENT MODE", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(modifier = Modifier.height(12.dp))
                    BrutalistTextField(
                        value = newPaymentModeName,
                        onValueChange = { newPaymentModeName = it },
                        label = "Payment Mode Name"
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BrutalistButton(
                            onClick = {
                                showAddPaymentModeDialog = false
                                newPaymentModeName = ""
                            },
                            modifier = Modifier.weight(1f),
                            containerColor = White
                        ) {
                            Text("CANCEL", color = Black, fontWeight = FontWeight.Bold)
                        }
                        BrutalistButton(
                            onClick = {
                                if (newPaymentModeName.isNotBlank()) {
                                    onAddPaymentMode(newPaymentModeName)
                                    mainPaymentMode = newPaymentModeName
                                    showAddPaymentModeDialog = false
                                    newPaymentModeName = ""
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("ADD", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // ── Add new paid via dialog ─────────────────────────────────────────────────
    if (showAddPaidViaDialog) {
        Dialog(onDismissRequest = { showAddPaidViaDialog = false }) {
            BrutalistCard(modifier = Modifier.padding(16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("NEW PAID VIA", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(modifier = Modifier.height(12.dp))
                    BrutalistTextField(
                        value = newPaidViaName,
                        onValueChange = { newPaidViaName = it },
                        label = "Paid Via Name"
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BrutalistButton(
                            onClick = {
                                showAddPaidViaDialog = false
                                newPaidViaName = ""
                            },
                            modifier = Modifier.weight(1f),
                            containerColor = White
                        ) {
                            Text("CANCEL", color = Black, fontWeight = FontWeight.Bold)
                        }
                        BrutalistButton(
                            onClick = {
                                if (newPaidViaName.isNotBlank()) {
                                    onAddPaidVia(newPaidViaName)
                                    mainPaidVia = newPaidViaName
                                    showAddPaidViaDialog = false
                                    newPaidViaName = ""
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("ADD", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // ── Main layout ───────────────────────────────────────────────────────────
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Mobile portrait mode: form at top, save button at bottom (already the current structure)
        // Tablet/landscape mode: same structure works well
        // ── Header: BACK + title + Calculator in one row ───────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = if (showCalculator) 8.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "BACK",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 13.sp,
                modifier = Modifier
                    .border(2.dp, Black)
                    .clickable {
                        if (hasChanges()) showExitConfirmation = true else onBack()
                    }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = if (isEditing) "EDIT EXPENSE" else "ADD EXPENSE",
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.weight(1f)
            )
            // Calculator toggle button
            Text(
                "CALC",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 13.sp,
                modifier = Modifier
                    .border(2.dp, Black)
                    .clickable { showCalculator = !showCalculator }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }

        // ── Calculator UI (Modal Dialog pop-up) ────────────────────────────
        if (showCalculator) {
            Dialog(onDismissRequest = { 
                showCalculator = false 
                calcTargetField = null
                calcTargetSplitIndex = null
            }) {
                BrutalistCard(
                    modifier = Modifier
                        .width(320.dp)
                        .padding(16.dp)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        // Display
                        Text(
                            text = calcDisplay,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(2.dp, Black)
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        // Calculator buttons row 1: 7 8 9 /
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("7", "8", "9", "/").forEach { btn ->
                                BrutalistButton(
                                    onClick = {
                                        if (btn in listOf("+", "-", "*", "/")) onCalcOperation(btn) else onCalcDigit(btn)
                                    },
                                    modifier = Modifier.weight(1f),
                                    containerColor = if (btn in listOf("+", "-", "*", "/")) Black else White,
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text(
                                        btn,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = if (btn in listOf("+", "-", "*", "/")) White else Black
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        // Calculator buttons row 2: 4 5 6 *
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("4", "5", "6", "*").forEach { btn ->
                                BrutalistButton(
                                    onClick = {
                                        if (btn in listOf("+", "-", "*", "/")) onCalcOperation(btn) else onCalcDigit(btn)
                                    },
                                    modifier = Modifier.weight(1f),
                                    containerColor = if (btn in listOf("+", "-", "*", "/")) Black else White,
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text(
                                        btn,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = if (btn in listOf("+", "-", "*", "/")) White else Black
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        // Calculator buttons row 3: 1 2 3 -
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("1", "2", "3", "-").forEach { btn ->
                                BrutalistButton(
                                    onClick = {
                                        if (btn in listOf("+", "-", "*", "/")) onCalcOperation(btn) else onCalcDigit(btn)
                                    },
                                    modifier = Modifier.weight(1f),
                                    containerColor = if (btn in listOf("+", "-", "*", "/")) Black else White,
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text(
                                        btn,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = if (btn in listOf("+", "-", "*", "/")) White else Black
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        // Calculator buttons row 4: C 0 . + = INS
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BrutalistButton(
                                onClick = { onCalcClear() },
                                modifier = Modifier.weight(1f).height(40.dp),
                                containerColor = MaterialTheme.colorScheme.error,
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("C", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = White)
                            }
                            BrutalistButton(
                                onClick = { onCalcDigit("0") },
                                modifier = Modifier.weight(1f).height(40.dp),
                                containerColor = White,
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("0", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Black)
                            }
                            BrutalistButton(
                                onClick = { onCalcDigit(".") },
                                modifier = Modifier.weight(1f).height(40.dp),
                                containerColor = White,
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(".", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Black)
                            }
                            BrutalistButton(
                                onClick = { onCalcOperation("+") },
                                modifier = Modifier.weight(1f).height(40.dp),
                                containerColor = Black,
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("+", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = White)
                            }
                            BrutalistButton(
                                onClick = { onCalcEqual() },
                                modifier = Modifier.weight(1f).height(40.dp),
                                containerColor = Black,
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("=", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = White)
                            }
                            BrutalistButton(
                                onClick = { onCalcInsert() },
                                modifier = Modifier.weight(1f).height(40.dp),
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(
                                    "INS",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.wrapContentSize(Alignment.Center)
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Date Picker Dialog ─────────────────────────────────────────────────────
        if (showDatePicker) {
            BrutalistDatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                onDateSelected = { millis ->
                    millis?.let {
                        selectedDate = java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                    }
                }
            )
        }

        // ── Mobile portrait: vertical layout ─────────────────────────────────────
        // Date field
        Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDatePicker = true }
                    .padding(bottom = 10.dp)
            ) {
                BrutalistTextField(
                    value = selectedDate.format(dateFormatter),
                    onValueChange = { /* Read only */ },
                    label = "DATE",
                    readOnly = true,
                    enabled = false,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Store field with suggestions
            Box(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                BrutalistTextField(
                    value = storeName,
                    onValueChange = {
                        storeName = it
                        showStoreSuggestions = it.isNotBlank()
                    },
                    label = "STORE",
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focusState ->
                            if (!focusState.isFocused) {
                                showStoreSuggestions = false
                            }
                        }
                )

                // Store suggestions dropdown
                if (showStoreSuggestions && filteredStoreSuggestions.isNotEmpty()) {
                    BrutalistCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset(y = 48.dp)
                    ) {
                        Column {
                            filteredStoreSuggestions.forEach { suggestion ->
                                Text(
                                    text = suggestion,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            storeName = suggestion
                                            showStoreSuggestions = false
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            // Location field
            BrutalistTextField(
                value = location,
                onValueChange = {
                    location = it
                    showStoreSuggestions = false
                },
                label = "LOCATION",
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
            )

            // Item name (non-split mode)
            if (!isSplit) {
                BrutalistTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = "ITEM NAME",
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                )
                // Base Amount field (non-split mode)
                BrutalistTextField(
                    value = baseAmount,
                    onValueChange = { newValue ->
                        baseAmount = newValue
                        val baseVal = newValue.toDoubleOrNull() ?: 0.0
                        val percentVal = gstPercentage.toDoubleOrNull() ?: 0.0
                        val gstAmtVal = gstAmount.toDoubleOrNull() ?: 0.0
                        
                        if (gstPercentage.isNotEmpty()) {
                            val calculatedGst = baseVal * (percentVal / 100.0)
                            gstAmount = if (calculatedGst > 0.0) String.format("%.2f", calculatedGst).replace(".00", "") else ""
                            val totalVal = baseVal + calculatedGst
                            totalAmount = if (totalVal > 0.0) String.format("%.2f", totalVal).replace(".00", "") else ""
                        } else if (gstAmount.isNotEmpty()) {
                            val totalVal = baseVal + gstAmtVal
                            totalAmount = if (totalVal > 0.0) String.format("%.2f", totalVal).replace(".00", "") else ""
                            if (baseVal > 0.0) {
                                val calculatedPercent = (gstAmtVal / baseVal) * 100.0
                                gstPercentage = String.format("%.2f", calculatedPercent).replace(".00", "")
                            }
                        } else {
                            totalAmount = newValue
                        }
                    },
                    label = "Base AMT (₹)",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    trailingIcon = {
                        Text(
                            "CALC",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.sp,
                            modifier = Modifier
                                .border(1.5.dp, Black)
                                .clickable {
                                    calcDisplay = if (baseAmount.isNotEmpty()) baseAmount else "0"
                                    calcTargetField = "MAIN_BASE"
                                    calcTargetSplitIndex = null
                                    showCalculator = true
                                }
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                )
                
                // GST % and GST Amount (non-split mode)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BrutalistTextField(
                        value = gstPercentage,
                        onValueChange = { newValue ->
                            gstPercentage = newValue
                            val baseVal = baseAmount.toDoubleOrNull() ?: 0.0
                            val percentVal = newValue.toDoubleOrNull() ?: 0.0
                            if (newValue.isNotEmpty()) {
                                val calculatedGst = baseVal * (percentVal / 100.0)
                                gstAmount = if (calculatedGst > 0.0) String.format("%.2f", calculatedGst).replace(".00", "") else ""
                                val totalVal = baseVal + calculatedGst
                                totalAmount = if (totalVal > 0.0) String.format("%.2f", totalVal).replace(".00", "") else ""
                            } else {
                                gstAmount = ""
                                totalAmount = baseAmount
                            }
                        },
                        label = "GST %",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        trailingIcon = {
                            Text(
                                "CALC",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 11.sp,
                                modifier = Modifier
                                    .border(1.5.dp, Black)
                                    .clickable {
                                        calcDisplay = if (gstPercentage.isNotEmpty()) gstPercentage else "0"
                                        calcTargetField = "MAIN_GST_PCT"
                                        calcTargetSplitIndex = null
                                        showCalculator = true
                                    }
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    )
                    BrutalistTextField(
                        value = gstAmount,
                        onValueChange = { newValue ->
                            gstAmount = newValue
                            val baseVal = baseAmount.toDoubleOrNull() ?: 0.0
                            val gstAmtVal = newValue.toDoubleOrNull() ?: 0.0
                            if (newValue.isNotEmpty()) {
                                val totalVal = baseVal + gstAmtVal
                                totalAmount = if (totalVal > 0.0) String.format("%.2f", totalVal).replace(".00", "") else ""
                                if (baseVal > 0.0) {
                                    val calculatedPercent = (gstAmtVal / baseVal) * 100.0
                                    gstPercentage = String.format("%.2f", calculatedPercent).replace(".00", "")
                                }
                            } else {
                                gstPercentage = ""
                                totalAmount = baseAmount
                            }
                        },
                        label = "GST (₹)",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        trailingIcon = {
                            Text(
                                "CALC",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 11.sp,
                                modifier = Modifier
                                    .border(1.5.dp, Black)
                                    .clickable {
                                        calcDisplay = if (gstAmount.isNotEmpty()) gstAmount else "0"
                                        calcTargetField = "MAIN_GST_AMT"
                                        calcTargetSplitIndex = null
                                        showCalculator = true
                                    }
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    )
                }
                
                // Total Amount field (non-split mode)
                BrutalistTextField(
                    value = totalAmount,
                    onValueChange = { /* Read-only */ },
                    label = "TOTAL AMOUNT (₹)",
                    readOnly = true,
                    enabled = false,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                )
                // QTY & UNIT fields (non-split mode)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BrutalistTextField(
                        value = quantity,
                        onValueChange = { quantity = it },
                        label = "QTY",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    BrutalistTextField(
                        value = unit,
                        onValueChange = { unit = it },
                        label = "UNIT",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

        // ── Category + Subcategory + Labels (non-split mode) ─────────────────
        if (!isSplit) {
            // Mobile: vertical stack
            BrutalistDropdown(
                    label = "CATEGORY",
                    options = categories + "+ Add New",
                    selectedOption = mainCategory,
                    onOptionSelected = {
                        if (it == "+ Add New") {
                            showAddCategoryDialog = true
                        } else {
                            mainCategory = it
                            mainSubcategory = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                )
                BrutalistDropdown(
                    label = "SUBCATEGORY",
                    options = if (mainCategory.isNotEmpty()) (subcategoriesMap[mainCategory] ?: emptyList()) + "+ Add New" else emptyList(),
                    selectedOption = mainSubcategory,
                    onOptionSelected = {
                        if (it == "+ Add New") {
                            if (mainCategory.isNotEmpty()) {
                                showAddSubcategoryDialog = true
                            }
                        } else {
                            mainSubcategory = it
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                )
                BrutalistMultiSelectDropdown(
                    label = "LABELS",
                    options = labels + "+ Add New",
                    selectedOptions = selectedLabels.toSet(),
                    onOptionToggled = { label: String ->
                        if (label == "+ Add New") {
                            showAddLabelDialog = true
                        } else {
                            if (selectedLabels.contains(label)) {
                                selectedLabels.remove(label)
                            } else {
                                selectedLabels.add(label)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    showSearch = true
                )

        }

        // Mobile: vertical stack
        BrutalistDropdown(
                label = "PAYMENT MODE",
                options = paymentModes + "+ Add New",
                selectedOption = mainPaymentMode,
                onOptionSelected = {
                    if (it == "+ Add New") {
                        showAddPaymentModeDialog = true
                    } else {
                        mainPaymentMode = it
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
            )
            BrutalistDropdown(
                label = "PAID VIA",
                options = paidVia + "+ Add New",
                selectedOption = mainPaidVia,
                onOptionSelected = {
                    if (it == "+ Add New") {
                        showAddPaidViaDialog = true
                    } else {
                        mainPaidVia = it
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
            )


        // ── Notes field (non-split mode) ───────────────────────────────────────
        if (!isSplit) {
            BrutalistTextField(
                value = notes,
                onValueChange = { notes = it },
                label = "NOTES",
                singleLine = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
            )
        }

        // ── Split toggle ──────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("SPLIT TRANSACTION", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Switch(
                checked = isSplit,
                onCheckedChange = {
                    isSplit = it
                    if (it && subTransactions.size == 1) {
                        subTransactions = listOf(
                            subTransactions[0].copy(
                                category = mainCategory,
                                subcategory = mainSubcategory,
                                amount = totalAmount,
                                description = description,
                                labels = selectedLabels.toList(),
                                quantity = quantity,
                                unit = unit,
                                notes = notes,
                                baseAmount = baseAmount,
                                gstPercentage = gstPercentage,
                                gstAmount = gstAmount
                            )
                        )
                    }
                    if (it) {
                        expandedSplitId = subTransactions.firstOrNull()?.id
                    }
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = White,
                    checkedTrackColor = Black,
                    uncheckedThumbColor = Black,
                    uncheckedTrackColor = White,
                    uncheckedBorderColor = Black
                )
            )
        }

        // ── Split items ───────────────────────────────────────────────────────
        if (isSplit) {
            // Split GST settings card
            BrutalistCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "SPLIT GST SETTINGS",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BrutalistTextField(
                            value = globalGstPercent,
                            onValueChange = { newValue ->
                                globalGstPercent = newValue
                                totalGstPaid = ""
                                subTransactions = applyGlobalGstPercent(subTransactions, newValue)
                            },
                            label = "Global GST %",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        BrutalistTextField(
                            value = totalGstPaid,
                            onValueChange = { newValue ->
                                totalGstPaid = newValue
                                val totalGst = newValue.toDoubleOrNull() ?: 0.0
                                if (newValue.isNotEmpty()) {
                                    subTransactions = applyTotalGstPaid(subTransactions, totalGst)
                                } else {
                                    globalGstPercent = ""
                                    subTransactions = subTransactions.map { subTx ->
                                        subTx.copy(
                                            gstPercentage = "",
                                            gstAmount = "",
                                            amount = subTx.baseAmount
                                        )
                                    }
                                }
                            },
                            label = "Total GST (₹)",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Split Totals summary card
            val totalBaseAmt = subTransactions.sumOf { it.baseAmount.toDoubleOrNull() ?: 0.0 }
            val totalGstAmt = subTransactions.sumOf { it.gstAmount.toDoubleOrNull() ?: 0.0 }
            val totalCalculatedAmt = subTransactions.sumOf { it.amount.toDoubleOrNull() ?: 0.0 }
            val totalBaseStr = String.format("%.2f", totalBaseAmt).replace(".00", "")
            val totalGstStr = String.format("%.2f", totalGstAmt).replace(".00", "")
            val totalCalcStr = String.format("%.2f", totalCalculatedAmt).replace(".00", "")

            BrutalistCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Base: ₹$totalBaseStr", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("GST: ₹$totalGstStr", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        "SPLIT TOTAL: ₹$totalCalcStr",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
            subTransactions.forEachIndexed { index, subTx ->
                val isExpanded = expandedSplitId == subTx.id
                
                BrutalistCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .clickable { expandedSplitId = subTx.id }
                ) {
                    if (isExpanded) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            // Split header
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                        ) {
                            Text("SPLIT ${index + 1}", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                            if (subTransactions.size > 1) {
                                Text(
                                    "REMOVE",
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    modifier = Modifier.clickable {
                                        subTransactions = subTransactions.filter { it.id != subTx.id }
                                    }
                                )
                            }
                        }

                        // Description + Amount + Qty + Unit
                        // Mobile: vertical stack
                        BrutalistTextField(
                                value = subTx.description,
                                onValueChange = {
                                    subTransactions = subTransactions.toMutableList().apply {
                                        this[index] = subTx.copy(description = it)
                                    }
                                },
                                label = "ITEM NAME",
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                            )
                            // Base AMT field
                            BrutalistTextField(
                                value = subTx.baseAmount,
                                onValueChange = { newValue ->
                                    val baseVal = newValue.toDoubleOrNull() ?: 0.0
                                    val percentVal = subTx.gstPercentage.toDoubleOrNull() ?: 0.0
                                    val gstAmtVal = subTx.gstAmount.toDoubleOrNull() ?: 0.0
                                    
                                    val newGstAmtStr: String
                                    val newGstPercentStr: String
                                    val newTotalAmtStr: String
                                    
                                    if (subTx.gstPercentage.isNotEmpty()) {
                                        val calculatedGst = baseVal * (percentVal / 100.0)
                                        newGstAmtStr = if (calculatedGst > 0.0) String.format("%.2f", calculatedGst).replace(".00", "") else ""
                                        newGstPercentStr = subTx.gstPercentage
                                        val totalVal = baseVal + calculatedGst
                                        newTotalAmtStr = if (totalVal > 0.0) String.format("%.2f", totalVal).replace(".00", "") else ""
                                    } else if (subTx.gstAmount.isNotEmpty()) {
                                        val totalVal = baseVal + gstAmtVal
                                        newTotalAmtStr = if (totalVal > 0.0) String.format("%.2f", totalVal).replace(".00", "") else ""
                                        newGstPercentStr = if (baseVal > 0.0) String.format("%.2f", (gstAmtVal / baseVal) * 100.0).replace(".00", "") else ""
                                        newGstAmtStr = subTx.gstAmount
                                    } else {
                                        newGstAmtStr = ""
                                        newGstPercentStr = ""
                                        newTotalAmtStr = newValue
                                    }
                                    
                                    val updatedSplits = subTransactions.toMutableList().apply {
                                        this[index] = subTx.copy(
                                            baseAmount = newValue,
                                            gstPercentage = newGstPercentStr,
                                            gstAmount = newGstAmtStr,
                                            amount = newTotalAmtStr
                                        )
                                    }
                                    
                                    if (globalGstPercent.isNotEmpty()) {
                                        subTransactions = applyGlobalGstPercent(updatedSplits, globalGstPercent)
                                    } else if (totalGstPaid.isNotEmpty()) {
                                        val totalGstVal = totalGstPaid.toDoubleOrNull() ?: 0.0
                                        subTransactions = applyTotalGstPaid(updatedSplits, totalGstVal)
                                    } else {
                                        subTransactions = updatedSplits
                                    }
                                },
                                label = "Base AMT (₹)",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                trailingIcon = {
                                    Text(
                                        "CALC",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 11.sp,
                                        modifier = Modifier
                                            .border(1.5.dp, Black)
                                            .clickable {
                                                calcDisplay = if (subTx.baseAmount.isNotEmpty()) subTx.baseAmount else "0"
                                                calcTargetField = "SPLIT_BASE"
                                                calcTargetSplitIndex = index
                                                showCalculator = true
                                            }
                                            .padding(horizontal = 6.dp, vertical = 3.dp)
                                    )
                                }
                            )
                            
                            // GST % & GST row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                BrutalistTextField(
                                    value = subTx.gstPercentage,
                                    onValueChange = { newValue ->
                                        val baseVal = subTx.baseAmount.toDoubleOrNull() ?: 0.0
                                        val percentVal = newValue.toDoubleOrNull() ?: 0.0
                                        
                                        val newGstAmtStr: String
                                        val newTotalAmtStr: String
                                        
                                        if (newValue.isNotEmpty()) {
                                            val calculatedGst = baseVal * (percentVal / 100.0)
                                            newGstAmtStr = if (calculatedGst > 0.0) String.format("%.2f", calculatedGst).replace(".00", "") else ""
                                            val totalVal = baseVal + calculatedGst
                                            newTotalAmtStr = if (totalVal > 0.0) String.format("%.2f", totalVal).replace(".00", "") else ""
                                        } else {
                                            newGstAmtStr = ""
                                            newTotalAmtStr = subTx.baseAmount
                                        }
                                        
                                        // Clear global fields when override occurs
                                        globalGstPercent = ""
                                        totalGstPaid = ""
                                        
                                        subTransactions = subTransactions.toMutableList().apply {
                                            this[index] = subTx.copy(
                                                gstPercentage = newValue,
                                                gstAmount = newGstAmtStr,
                                                amount = newTotalAmtStr
                                            )
                                        }
                                    },
                                    label = "GST %",
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f),
                                    trailingIcon = {
                                        Text(
                                            "CALC",
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 11.sp,
                                            modifier = Modifier
                                                .border(1.5.dp, Black)
                                                .clickable {
                                                    calcDisplay = if (subTx.gstPercentage.isNotEmpty()) subTx.gstPercentage else "0"
                                                    calcTargetField = "SPLIT_GST_PCT"
                                                    calcTargetSplitIndex = index
                                                    showCalculator = true
                                                }
                                                .padding(horizontal = 6.dp, vertical = 3.dp)
                                        )
                                    }
                                )
                                BrutalistTextField(
                                    value = subTx.gstAmount,
                                    onValueChange = { newValue ->
                                        val baseVal = subTx.baseAmount.toDoubleOrNull() ?: 0.0
                                        val gstAmtVal = newValue.toDoubleOrNull() ?: 0.0
                                        
                                        val newGstPercentStr: String
                                        val newTotalAmtStr: String
                                        
                                        if (newValue.isNotEmpty()) {
                                            val totalVal = baseVal + gstAmtVal
                                            newTotalAmtStr = if (totalVal > 0.0) String.format("%.2f", totalVal).replace(".00", "") else ""
                                            newGstPercentStr = if (baseVal > 0.0) String.format("%.2f", (gstAmtVal / baseVal) * 100.0).replace(".00", "") else ""
                                        } else {
                                            newGstPercentStr = ""
                                            newTotalAmtStr = subTx.baseAmount
                                        }
                                        
                                        // Clear global fields when override occurs
                                        globalGstPercent = ""
                                        totalGstPaid = ""
                                        
                                        subTransactions = subTransactions.toMutableList().apply {
                                            this[index] = subTx.copy(
                                                gstPercentage = newGstPercentStr,
                                                gstAmount = newValue,
                                                amount = newTotalAmtStr
                                            )
                                        }
                                    },
                                    label = "GST (₹)",
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f),
                                    trailingIcon = {
                                        Text(
                                            "CALC",
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 11.sp,
                                            modifier = Modifier
                                                .border(1.5.dp, Black)
                                                .clickable {
                                                    calcDisplay = if (subTx.gstAmount.isNotEmpty()) subTx.gstAmount else "0"
                                                    calcTargetField = "SPLIT_GST_AMT"
                                                    calcTargetSplitIndex = index
                                                    showCalculator = true
                                                }
                                                .padding(horizontal = 6.dp, vertical = 3.dp)
                                        )
                                    }
                                )
                            }
                            
                            // Calculated Total Amount
                            BrutalistTextField(
                                value = subTx.amount,
                                onValueChange = { /* Read-only */ },
                                label = "TOTAL AMOUNT (₹)",
                                readOnly = true,
                                enabled = false,
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                BrutalistTextField(
                                    value = subTx.quantity,
                                    onValueChange = {
                                        subTransactions = subTransactions.toMutableList().apply {
                                            this[index] = subTx.copy(quantity = it)
                                        }
                                    },
                                    label = "QTY",
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f)
                                )
                                BrutalistTextField(
                                    value = subTx.unit,
                                    onValueChange = {
                                        subTransactions = subTransactions.toMutableList().apply {
                                            this[index] = subTx.copy(unit = it)
                                        }
                                    },
                                    label = "UNIT",
                                    modifier = Modifier.weight(1f)
                                )
                            }



                        // Mobile: vertical stack
                        BrutalistDropdown(
                                label = "CATEGORY",
                                options = categories + "+ Add New",
                                selectedOption = subTx.category,
                                onOptionSelected = {
                                    if (it == "+ Add New") {
                                        editingSplitIndex = index
                                        showAddCategoryDialog = true
                                    } else {
                                        subTransactions = subTransactions.toMutableList().apply {
                                            this[index] = subTx.copy(category = it, subcategory = "")
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                            )
                            BrutalistDropdown(
                                label = "SUBCATEGORY",
                                options = if (subTx.category.isNotEmpty()) (subcategoriesMap[subTx.category] ?: emptyList()) + "+ Add New" else emptyList(),
                                selectedOption = subTx.subcategory,
                                onOptionSelected = {
                                    if (it == "+ Add New") {
                                        if (subTx.category.isNotEmpty()) {
                                            editingSplitIndex = index
                                            showAddSubcategoryDialog = true
                                        }
                                    } else {
                                        subTransactions = subTransactions.toMutableList().apply {
                                            this[index] = subTx.copy(subcategory = it)
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                            )
                            BrutalistMultiSelectDropdown(
                                label = "LABELS",
                                options = labels + "+ Add New",
                                selectedOptions = subTx.labels.toSet(),
                                onOptionToggled = { label: String ->
                                    if (label == "+ Add New") {
                                        editingSplitIndex = index
                                        showAddLabelDialog = true
                                    } else {
                                        val newLabels = if (subTx.labels.contains(label)) subTx.labels - label else subTx.labels + label
                                        subTransactions = subTransactions.toMutableList().apply {
                                            this[index] = subTx.copy(labels = newLabels)
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                showSearch = true
                            )



                        // Notes field
                        BrutalistTextField(
                            value = subTx.notes,
                            onValueChange = {
                                subTransactions = subTransactions.toMutableList().apply {
                                    this[index] = subTx.copy(notes = it)
                                }
                            },
                            label = "NOTES",
                            singleLine = false,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        )
                    } // Closes Column (line 407)
                } else { // Closes if (isExpanded)
                    // Collapsed View
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${index + 1}.",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Text(
                                    text = if (subTx.description.isNotBlank()) subTx.description else "NO ITEM NAME",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = if (subTx.description.isNotBlank()) Black else MaterialTheme.colorScheme.outline
                                )
                            }
                            Text(
                                text = if (subTx.amount.isNotBlank()) "₹${subTx.amount}" else "₹0.00",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }

            val lastSplit = subTransactions.lastOrNull()
            val canAddMore = lastSplit != null && 
                            lastSplit.description.isNotBlank() && 
                            lastSplit.amount.isNotBlank() && 
                            (lastSplit.amount.toDoubleOrNull() ?: 0.0) > 0.0

            BrutalistButton(
                onClick = {
                    val nextId = subTransactions.maxOfOrNull { it.id }?.plus(1) ?: 1
                    // Get data from split 1 (first transaction) for auto-prefill
                    val firstSplit = subTransactions.firstOrNull()
                    val inheritedGstPercent = if (globalGstPercent.isNotEmpty()) globalGstPercent else (firstSplit?.gstPercentage ?: "")
                    
                    subTransactions = subTransactions + SubTransaction(
                        id = nextId,
                        category = firstSplit?.category ?: mainCategory,
                        subcategory = firstSplit?.subcategory ?: mainSubcategory,
                        labels = firstSplit?.labels ?: selectedLabels.toList(),
                        gstPercentage = inheritedGstPercent
                    )
                    expandedSplitId = nextId
                },
                enabled = canAddMore,
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                containerColor = if (canAddMore) White else White.copy(alpha = 0.5f)
            ) {
                Text(
                    "+ ADD SPLIT", 
                    color = if (canAddMore) Black else Black.copy(alpha = 0.4f), 
                    fontWeight = FontWeight.Black
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BrutalistButton(
                onClick = { performSave(isDraft = true) },
                modifier = Modifier.weight(1f).height(52.dp),
                containerColor = White
            ) {
                Text("SAVE DRAFT", color = Black, fontWeight = FontWeight.Black, fontSize = 15.sp)
            }
            BrutalistButton(
                onClick = { performSave(isDraft = false) },
                modifier = Modifier.weight(1f).height(52.dp)
            ) {
                Text(if (isEditing) "UPDATE" else "SAVE", fontWeight = FontWeight.Black, fontSize = 17.sp)
            }
        }
    }
}
