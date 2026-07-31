package com.example.expensetracker.ui.modern.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Popup
import com.example.expensetracker.model.Expense
import com.example.expensetracker.ui.modern.components.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** One line item within a (possibly split) expense entry. */
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

/**
 * Aurora expense entry — feature parity with the brutalist ExpenseEntryScreen:
 * date picker, store with history suggestions, location, base/GST%/GST amount
 * with auto-calculation, quantity/unit, category/subcategory/labels with
 * inline "+ Add New", payment mode, paid via, notes, split transactions with
 * global GST controls, built-in calculator with field targeting, save /
 * save-draft / update, and unsaved-changes confirmation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModernExpenseEntryScreen(
    categories: List<String>,
    subcategoriesMap: Map<String, List<String>>,
    labels: List<String>,
    paymentModes: List<String>,
    paidVia: List<String>,
    storeHistory: List<String> = emptyList(),
    storeLocationHistory: Map<String, List<String>> = emptyMap(),
    expenseToEdit: Expense? = null,
    groupToEdit: List<Expense>? = null,
    initialSelectedExpenseId: String? = null,
    onSave: (List<Expense>) -> Unit,
    onBack: () -> Unit,
    onAddCategory: (String) -> Unit = {},
    onAddSubcategory: (String, String) -> Unit = { _, _ -> },
    onAddLabel: (String) -> Unit = {},
    onAddPaymentMode: (String) -> Unit = {},
    onAddPaidVia: (String) -> Unit = {},
    onUpdateStoreHistory: (String) -> Unit = {},
    onUpdateStoreLocation: (String, String) -> Unit = { _, _ -> },
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
    val initialQuantity = remember {
        expenseToEdit?.quantity?.toString() ?: groupToEdit?.firstOrNull()?.quantity?.toString() ?: ""
    }
    val initialUnit = remember { expenseToEdit?.unit ?: groupToEdit?.firstOrNull()?.unit ?: "" }
    val initialDateValue = remember {
        initialDate ?: expenseToEdit?.date ?: groupToEdit?.firstOrNull()?.date ?: LocalDate.now()
    }
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
                notes = e.notes,
                baseAmount = e.baseAmount?.toString() ?: e.amount.toString(),
                gstPercentage = e.gstPercentage?.toString() ?: "",
                gstAmount = e.gstAmount?.toString() ?: ""
            )
        } ?: listOf(SubTransaction(1))
    }

    var storeName by remember { mutableStateOf(initialStoreName) }
    var location by remember { mutableStateOf(initialLocation) }
    val initialDescription = remember {
        expenseToEdit?.itemDescription ?: groupToEdit?.firstOrNull()?.itemDescription ?: ""
    }
    var description by remember { mutableStateOf(initialDescription) }
    var totalAmount by remember { mutableStateOf(initialTotalAmount) }

    val initialBaseAmount = remember {
        expenseToEdit?.baseAmount?.toString() ?: expenseToEdit?.amount?.toString() ?: ""
    }
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
        mutableStateListOf<String>().apply { addAll(initialSelectedLabels) }
    }

    var isSplit by remember { mutableStateOf(initialIsSplit) }
    var subTransactions by remember { mutableStateOf(initialSubTransactions) }
    val initialExpandedSplitId = remember {
        val selectedIndex = groupToEdit?.indexOfFirst { it.id == initialSelectedExpenseId } ?: -1
        if (selectedIndex >= 0) subTransactions.getOrNull(selectedIndex)?.id
        else subTransactions.firstOrNull()?.id
    }
    var expandedSplitId by remember { mutableStateOf<Int?>(initialExpandedSplitId) }

    var showExitConfirmation by remember { mutableStateOf(false) }
    var selectedDate by remember { mutableStateOf(initialDateValue) }
    var showDatePicker by remember { mutableStateOf(false) }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd/MM/yyyy") }

    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var showAddSubcategoryDialog by remember { mutableStateOf(false) }
    var showAddLabelDialog by remember { mutableStateOf(false) }
    var showAddPaymentModeDialog by remember { mutableStateOf(false) }
    var showAddPaidViaDialog by remember { mutableStateOf(false) }
    var editingSplitIndex by remember { mutableStateOf<Int?>(null) }

    // Calculator state
    var showCalculator by remember { mutableStateOf(false) }
    var calcDisplay by remember { mutableStateOf("0") }
    var calcPrevious by remember { mutableStateOf("") }
    var calcOperation by remember { mutableStateOf<String?>(null) }
    var calcNewNumber by remember { mutableStateOf(true) }
    var calcTargetField by remember { mutableStateOf<String?>(null) }
    var calcTargetSplitIndex by remember { mutableStateOf<Int?>(null) }

    // Store suggestions
    var showStoreSuggestions by remember { mutableStateOf(false) }
    val filteredStoreSuggestions = remember(storeName, storeHistory) {
        storeHistory.filter { it.isNotBlank() && it.contains(storeName, ignoreCase = true) }
            .distinct()
            .take(5)
    }

    // Location suggestions — bonded to the currently entered store
    var showLocationSuggestions by remember { mutableStateOf(false) }
    var storeNameTouched by remember { mutableStateOf(false) }
    val matchedStoreLocations = remember(storeName, storeLocationHistory) {
        storeLocationHistory.entries
            .firstOrNull { it.key.equals(storeName, ignoreCase = true) }
            ?.value ?: emptyList()
    }
    val filteredLocationSuggestions = remember(location, matchedStoreLocations) {
        matchedStoreLocations.filter { it.contains(location, ignoreCase = true) }
            .distinct()
            .take(5)
    }
    // Auto-open the location dropdown once the typed/selected store matches known history
    // (only in response to the user editing the store field, not on initial screen load)
    LaunchedEffect(storeName, matchedStoreLocations) {
        if (storeNameTouched && storeName.isNotBlank() && matchedStoreLocations.isNotEmpty()) {
            showLocationSuggestions = true
        }
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

    // ── Calculator helpers (same math as the brutalist screen) ───────────────
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
                gstAmount = if (baseVal > 0.0 && percentVal > 0.0)
                    String.format("%.2f", gstAmtVal).replace(".00", "") else "",
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
                        gstAmount = if (calculatedGst > 0.0)
                            String.format("%.2f", calculatedGst).replace(".00", "") else ""
                        val totalVal = baseVal + calculatedGst
                        totalAmount = if (totalVal > 0.0)
                            String.format("%.2f", totalVal).replace(".00", "") else ""
                    } else if (gstAmount.isNotEmpty()) {
                        val totalVal = baseVal + gstAmtVal
                        totalAmount = if (totalVal > 0.0)
                            String.format("%.2f", totalVal).replace(".00", "") else ""
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
                        gstAmount = if (calculatedGst > 0.0)
                            String.format("%.2f", calculatedGst).replace(".00", "") else ""
                        val totalVal = baseVal + calculatedGst
                        totalAmount = if (totalVal > 0.0)
                            String.format("%.2f", totalVal).replace(".00", "") else ""
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
                        totalAmount = if (totalVal > 0.0)
                            String.format("%.2f", totalVal).replace(".00", "") else ""
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
                                    newGstAmt = if (calculatedGst > 0.0)
                                        String.format("%.2f", calculatedGst).replace(".00", "") else ""
                                    val totalVal = baseVal + calculatedGst
                                    newTotal = if (totalVal > 0.0)
                                        String.format("%.2f", totalVal).replace(".00", "") else ""
                                } else if (subTx.gstAmount.isNotEmpty()) {
                                    val totalVal = baseVal + gstAmtVal
                                    newTotal = if (totalVal > 0.0)
                                        String.format("%.2f", totalVal).replace(".00", "") else ""
                                    newGstPct = if (baseVal > 0.0)
                                        String.format("%.2f", (gstAmtVal / baseVal) * 100.0).replace(".00", "") else ""
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
                                    newGstAmt = if (calculatedGst > 0.0)
                                        String.format("%.2f", calculatedGst).replace(".00", "") else ""
                                    val totalVal = baseVal + calculatedGst
                                    newTotal = if (totalVal > 0.0)
                                        String.format("%.2f", totalVal).replace(".00", "") else ""
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
                                    newTotal = if (totalVal > 0.0)
                                        String.format("%.2f", totalVal).replace(".00", "") else ""
                                    newGstPct = if (baseVal > 0.0)
                                        String.format("%.2f", (gstAmtVal / baseVal) * 100.0).replace(".00", "") else ""
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
                    val calculatedGst = baseVal * (percentVal / 100.0)
                    val newGstAmtStr = if (calculatedGst > 0.0)
                        String.format("%.2f", calculatedGst).replace(".00", "") else ""
                    val totalVal = baseVal + calculatedGst
                    val newTotalAmtStr = if (totalVal > 0.0)
                        String.format("%.2f", totalVal).replace(".00", "") else ""

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
                gstAmount = if (calculatedGst > 0.0)
                    String.format("%.2f", calculatedGst).replace(".00", "") else ""
                val totalVal = baseVal + calculatedGst
                totalAmount = if (totalVal > 0.0)
                    String.format("%.2f", totalVal).replace(".00", "") else ""
            }
        }
        showCalculator = false
        calcTargetField = null
        calcTargetSplitIndex = null
    }

    fun performSave(isDraft: Boolean = false) {
        if (storeName.isNotBlank()) {
            onUpdateStoreHistory(storeName)
        }
        if (storeName.isNotBlank() && location.isNotBlank()) {
            onUpdateStoreLocation(storeName, location)
        }

        fun expenseIdForIndex(index: Int): String {
            return groupToEdit?.getOrNull(index)?.id
                ?: expenseToEdit?.takeIf { index == 0 }?.id
                ?: java.util.UUID.randomUUID().toString()
        }

        val expenseDate = selectedDate
        val sharedGroupId = expenseToEdit?.groupId
            ?: groupToEdit?.firstOrNull()?.groupId
            ?: java.util.UUID.randomUUID().toString()
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

    // ── Exit confirmation ─────────────────────────────────────────────────────
    if (showExitConfirmation) {
        AlertDialog(
            onDismissRequest = { showExitConfirmation = false },
            shape = RoundedCornerShape(28.dp),
            title = { Text("Unsaved changes", style = MaterialTheme.typography.headlineSmall) },
            text = { Text("Do you want to save before leaving?") },
            confirmButton = {
                Button(
                    onClick = {
                        showExitConfirmation = false
                        performSave(isDraft = false)
                    },
                    shape = RoundedCornerShape(50)
                ) { Text("Save") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        showExitConfirmation = false
                        onBack()
                    }) { Text("Discard") }
                    TextButton(onClick = {
                        showExitConfirmation = false
                        performSave(isDraft = true)
                    }) { Text("Save draft") }
                }
            }
        )
    }

    // ── Inline "+ Add New" dialogs ────────────────────────────────────────────
    if (showAddCategoryDialog) {
        AuroraEditDialog(
            title = "New category",
            initialValue = "",
            fieldLabel = "Category name",
            onConfirm = { value ->
                if (value.isNotBlank()) {
                    onAddCategory(value)
                    if (editingSplitIndex != null) {
                        subTransactions = subTransactions.toMutableList().apply {
                            this[editingSplitIndex!!] =
                                this[editingSplitIndex!!].copy(category = value, subcategory = "")
                        }
                    } else {
                        mainCategory = value
                        mainSubcategory = ""
                    }
                }
                showAddCategoryDialog = false
                editingSplitIndex = null
            },
            onDismiss = {
                showAddCategoryDialog = false
                editingSplitIndex = null
            }
        )
    }

    if (showAddSubcategoryDialog) {
        val categoryForSubcategory = if (editingSplitIndex != null) {
            subTransactions[editingSplitIndex!!].category.ifEmpty { mainCategory }
        } else {
            mainCategory
        }
        AuroraEditDialog(
            title = "New subcategory in $categoryForSubcategory",
            initialValue = "",
            fieldLabel = "Subcategory name",
            onConfirm = { value ->
                if (value.isNotBlank() && categoryForSubcategory.isNotBlank()) {
                    onAddSubcategory(categoryForSubcategory, value)
                    if (editingSplitIndex != null) {
                        subTransactions = subTransactions.toMutableList().apply {
                            this[editingSplitIndex!!] = this[editingSplitIndex!!].copy(subcategory = value)
                        }
                    } else {
                        mainSubcategory = value
                    }
                }
                showAddSubcategoryDialog = false
                editingSplitIndex = null
            },
            onDismiss = {
                showAddSubcategoryDialog = false
                editingSplitIndex = null
            }
        )
    }

    if (showAddLabelDialog) {
        AuroraEditDialog(
            title = "New label",
            initialValue = "",
            fieldLabel = "Label name",
            onConfirm = { value ->
                if (value.isNotBlank()) {
                    onAddLabel(value)
                    if (editingSplitIndex != null) {
                        subTransactions = subTransactions.toMutableList().apply {
                            this[editingSplitIndex!!] =
                                this[editingSplitIndex!!].copy(labels = this[editingSplitIndex!!].labels + value)
                        }
                    } else {
                        selectedLabels.add(value)
                    }
                }
                showAddLabelDialog = false
                editingSplitIndex = null
            },
            onDismiss = {
                showAddLabelDialog = false
                editingSplitIndex = null
            }
        )
    }

    if (showAddPaymentModeDialog) {
        AuroraEditDialog(
            title = "New payment mode",
            initialValue = "",
            fieldLabel = "Payment mode name",
            onConfirm = { value ->
                if (value.isNotBlank()) {
                    onAddPaymentMode(value)
                    mainPaymentMode = value
                }
                showAddPaymentModeDialog = false
            },
            onDismiss = { showAddPaymentModeDialog = false }
        )
    }

    if (showAddPaidViaDialog) {
        AuroraEditDialog(
            title = "New paid via",
            initialValue = "",
            fieldLabel = "Paid via name",
            onConfirm = { value ->
                if (value.isNotBlank()) {
                    onAddPaidVia(value)
                    mainPaidVia = value
                }
                showAddPaidViaDialog = false
            },
            onDismiss = { showAddPaidViaDialog = false }
        )
    }

    // ── Calculator dialog ─────────────────────────────────────────────────────
    if (showCalculator) {
        Dialog(onDismissRequest = {
            showCalculator = false
            calcTargetField = null
            calcTargetSplitIndex = null
        }) {
            AuroraCard(
                modifier = Modifier.width(320.dp),
                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
                shape = RoundedCornerShape(28.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = calcDisplay,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = androidx.compose.ui.text.style.TextAlign.End
                    )

                    @Composable
                    fun CalcKey(
                        label: String,
                        modifier: Modifier = Modifier,
                        emphasis: Boolean = false,
                        danger: Boolean = false,
                        onClick: () -> Unit
                    ) {
                        val containerColor = when {
                            danger -> MaterialTheme.colorScheme.errorContainer
                            emphasis -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp)
                        }
                        val contentColor = when {
                            danger -> MaterialTheme.colorScheme.onErrorContainer
                            emphasis -> MaterialTheme.colorScheme.onPrimary
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                        Button(
                            onClick = onClick,
                            modifier = modifier.height(44.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = containerColor,
                                contentColor = contentColor
                            ),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(label, style = MaterialTheme.typography.titleMedium)
                        }
                    }

                    listOf(
                        listOf("7", "8", "9", "/"),
                        listOf("4", "5", "6", "*"),
                        listOf("1", "2", "3", "-")
                    ).forEach { row ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            row.forEach { btn ->
                                val isOp = btn in listOf("+", "-", "*", "/")
                                CalcKey(
                                    label = btn,
                                    modifier = Modifier.weight(1f),
                                    emphasis = isOp
                                ) {
                                    if (isOp) onCalcOperation(btn) else onCalcDigit(btn)
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CalcKey("C", Modifier.weight(1f), danger = true) { onCalcClear() }
                        CalcKey("0", Modifier.weight(1f)) { onCalcDigit("0") }
                        CalcKey(".", Modifier.weight(1f)) { onCalcDigit(".") }
                        CalcKey("+", Modifier.weight(1f), emphasis = true) { onCalcOperation("+") }
                        CalcKey("=", Modifier.weight(1f), emphasis = true) { onCalcEqual() }
                        CalcKey("INS", Modifier.weight(1f), emphasis = true) { onCalcInsert() }
                    }
                }
            }
        }
    }

    // ── Date picker ───────────────────────────────────────────────────────────
    if (showDatePicker) {
        AuroraDatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            onDateSelected = { millis ->
                millis?.let {
                    selectedDate = java.time.Instant.ofEpochMilli(it)
                        .atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                }
            }
        )
    }

    // Small trailing "calc" affordance used by amount fields. Not @Composable
    // itself — it only builds the lambda that Compose invokes later.
    fun calcTrailingIcon(currentValue: String, targetField: String, splitIndex: Int? = null):
        @Composable () -> Unit = {
        IconButton(onClick = {
            calcDisplay = currentValue.ifEmpty { "0" }
            calcTargetField = targetField
            calcTargetSplitIndex = splitIndex
            showCalculator = true
        }) {
            Icon(
                Icons.Default.Calculate,
                contentDescription = "Calculator",
                tint = MaterialTheme.colorScheme.primary
            )
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
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                if (hasChanges()) showExitConfirmation = true else onBack()
            }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = if (isEditing) "Edit expense" else "Add expense",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f)
            )
            FilledTonalIconButton(onClick = { showCalculator = !showCalculator }) {
                Icon(Icons.Default.Calculate, contentDescription = "Calculator")
            }
        }

        // Date
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showDatePicker = true }
                .padding(bottom = 10.dp)
        ) {
            AuroraTextField(
                value = selectedDate.format(dateFormatter),
                onValueChange = { },
                label = "Date",
                readOnly = true,
                enabled = false
            )
        }

        // Store with suggestions
        var storeFieldSize by remember { mutableStateOf(IntSize.Zero) }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp)
        ) {
            AuroraTextField(
                value = storeName,
                onValueChange = {
                    storeName = it
                    storeNameTouched = true
                    showStoreSuggestions = it.isNotBlank()
                },
                label = "Store",
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { storeFieldSize = it }
                    .onFocusChanged { focusState ->
                        if (!focusState.isFocused) showStoreSuggestions = false
                    }
            )

            if (showStoreSuggestions && filteredStoreSuggestions.isNotEmpty()) {
                val density = LocalDensity.current
                Popup(
                    alignment = Alignment.TopStart,
                    offset = IntOffset(0, storeFieldSize.height + with(density) { 4.dp.roundToPx() }),
                    onDismissRequest = { showStoreSuggestions = false }
                ) {
                    AuroraCard(
                        modifier = Modifier
                            .width(with(density) { storeFieldSize.width.toDp() })
                            .shadow(8.dp, RoundedCornerShape(20.dp)),
                        containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp)
                    ) {
                        Column {
                            filteredStoreSuggestions.forEach { suggestion ->
                                Text(
                                    text = suggestion,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            storeName = suggestion
                                            storeNameTouched = true
                                            showStoreSuggestions = false
                                        }
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }
        }

        // Location — bonded to store, with suggestions from prior entries at this store
        var locationFieldSize by remember { mutableStateOf(IntSize.Zero) }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp)
        ) {
            AuroraTextField(
                value = location,
                onValueChange = {
                    location = it
                    showStoreSuggestions = false
                    showLocationSuggestions = true
                },
                label = "Location",
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { locationFieldSize = it }
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused) {
                            if (matchedStoreLocations.isNotEmpty()) showLocationSuggestions = true
                        } else {
                            showLocationSuggestions = false
                        }
                    }
            )

            if (showLocationSuggestions && filteredLocationSuggestions.isNotEmpty()) {
                val density = LocalDensity.current
                Popup(
                    alignment = Alignment.TopStart,
                    offset = IntOffset(0, locationFieldSize.height + with(density) { 4.dp.roundToPx() }),
                    onDismissRequest = { showLocationSuggestions = false }
                ) {
                    AuroraCard(
                        modifier = Modifier
                            .width(with(density) { locationFieldSize.width.toDp() })
                            .shadow(8.dp, RoundedCornerShape(20.dp)),
                        containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp)
                    ) {
                        Column {
                            filteredLocationSuggestions.forEach { suggestion ->
                                Text(
                                    text = suggestion,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            location = suggestion
                                            showLocationSuggestions = false
                                        }
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }
        }

        // Split toggle
        AuroraCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Split transaction", style = MaterialTheme.typography.titleSmall)
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
                    }
                )
            }
        }

        if (!isSplit) {
            AuroraTextField(
                value = description,
                onValueChange = { description = it },
                label = "Item name",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
            )
            AuroraTextField(
                value = baseAmount,
                onValueChange = { newValue ->
                    baseAmount = newValue
                    val baseVal = newValue.toDoubleOrNull() ?: 0.0
                    val percentVal = gstPercentage.toDoubleOrNull() ?: 0.0
                    val gstAmtVal = gstAmount.toDoubleOrNull() ?: 0.0

                    if (gstPercentage.isNotEmpty()) {
                        val calculatedGst = baseVal * (percentVal / 100.0)
                        gstAmount = if (calculatedGst > 0.0)
                            String.format("%.2f", calculatedGst).replace(".00", "") else ""
                        val totalVal = baseVal + calculatedGst
                        totalAmount = if (totalVal > 0.0)
                            String.format("%.2f", totalVal).replace(".00", "") else ""
                    } else if (gstAmount.isNotEmpty()) {
                        val totalVal = baseVal + gstAmtVal
                        totalAmount = if (totalVal > 0.0)
                            String.format("%.2f", totalVal).replace(".00", "") else ""
                        if (baseVal > 0.0) {
                            gstPercentage = String.format("%.2f", (gstAmtVal / baseVal) * 100.0).replace(".00", "")
                        }
                    } else {
                        totalAmount = newValue
                    }
                },
                label = "Base amount (₹)",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                trailingIcon = calcTrailingIcon(baseAmount, "MAIN_BASE")
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AuroraTextField(
                    value = gstPercentage,
                    onValueChange = { newValue ->
                        gstPercentage = newValue
                        val baseVal = baseAmount.toDoubleOrNull() ?: 0.0
                        val percentVal = newValue.toDoubleOrNull() ?: 0.0
                        if (newValue.isNotEmpty()) {
                            val calculatedGst = baseVal * (percentVal / 100.0)
                            gstAmount = if (calculatedGst > 0.0)
                                String.format("%.2f", calculatedGst).replace(".00", "") else ""
                            val totalVal = baseVal + calculatedGst
                            totalAmount = if (totalVal > 0.0)
                                String.format("%.2f", totalVal).replace(".00", "") else ""
                        } else {
                            gstAmount = ""
                            totalAmount = baseAmount
                        }
                    },
                    label = "GST %",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    trailingIcon = calcTrailingIcon(gstPercentage, "MAIN_GST_PCT")
                )
                AuroraTextField(
                    value = gstAmount,
                    onValueChange = { newValue ->
                        gstAmount = newValue
                        val baseVal = baseAmount.toDoubleOrNull() ?: 0.0
                        val gstAmtVal = newValue.toDoubleOrNull() ?: 0.0
                        if (newValue.isNotEmpty()) {
                            val totalVal = baseVal + gstAmtVal
                            totalAmount = if (totalVal > 0.0)
                                String.format("%.2f", totalVal).replace(".00", "") else ""
                            if (baseVal > 0.0) {
                                gstPercentage =
                                    String.format("%.2f", (gstAmtVal / baseVal) * 100.0).replace(".00", "")
                            }
                        } else {
                            gstPercentage = ""
                            totalAmount = baseAmount
                        }
                    },
                    label = "GST (₹)",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    trailingIcon = calcTrailingIcon(gstAmount, "MAIN_GST_AMT")
                )
            }

            AuroraTextField(
                value = totalAmount,
                onValueChange = { },
                label = "Total amount (₹)",
                readOnly = true,
                enabled = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AuroraTextField(
                    value = quantity,
                    onValueChange = { quantity = it },
                    label = "Qty",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                AuroraTextField(
                    value = unit,
                    onValueChange = { unit = it },
                    label = "Unit",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Category / subcategory / labels (non-split)
        if (!isSplit) {
            AuroraDropdown(
                label = "Category",
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
            )
            AuroraDropdown(
                label = "Subcategory",
                options = if (mainCategory.isNotEmpty())
                    (subcategoriesMap[mainCategory] ?: emptyList()) + "+ Add New"
                else emptyList(),
                selectedOption = mainSubcategory,
                onOptionSelected = {
                    if (it == "+ Add New") {
                        if (mainCategory.isNotEmpty()) showAddSubcategoryDialog = true
                    } else {
                        mainSubcategory = it
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
            )
            AuroraMultiSelectDropdown(
                label = "Labels",
                options = labels + "+ Add New",
                selectedOptions = selectedLabels.toSet(),
                onOptionToggled = { label ->
                    if (label == "+ Add New") {
                        showAddLabelDialog = true
                    } else {
                        if (selectedLabels.contains(label)) selectedLabels.remove(label)
                        else selectedLabels.add(label)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                showSearch = true
            )
        }

        AuroraDropdown(
            label = "Payment mode",
            options = paymentModes + "+ Add New",
            selectedOption = mainPaymentMode,
            onOptionSelected = {
                if (it == "+ Add New") {
                    showAddPaymentModeDialog = true
                } else {
                    mainPaymentMode = it
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp)
        )
        AuroraDropdown(
            label = "Paid via",
            options = paidVia + "+ Add New",
            selectedOption = mainPaidVia,
            onOptionSelected = {
                if (it == "+ Add New") {
                    showAddPaidViaDialog = true
                } else {
                    mainPaidVia = it
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp)
        )

        if (!isSplit) {
            AuroraTextField(
                value = notes,
                onValueChange = { notes = it },
                label = "Notes",
                singleLine = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
            )
        }

        if (isSplit) {
            // Global GST settings
            AuroraCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        "Split GST settings",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AuroraTextField(
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
                        AuroraTextField(
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

            // Split totals
            val totalBaseAmt = subTransactions.sumOf { it.baseAmount.toDoubleOrNull() ?: 0.0 }
            val totalGstAmt = subTransactions.sumOf { it.gstAmount.toDoubleOrNull() ?: 0.0 }
            val totalCalculatedAmt = subTransactions.sumOf { it.amount.toDoubleOrNull() ?: 0.0 }
            val totalBaseStr = String.format("%.2f", totalBaseAmt).replace(".00", "")
            val totalGstStr = String.format("%.2f", totalGstAmt).replace(".00", "")
            val totalCalcStr = String.format("%.2f", totalCalculatedAmt).replace(".00", "")

            AuroraCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "Base ₹$totalBaseStr",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            "GST ₹$totalGstStr",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Text(
                        "Split total ₹$totalCalcStr",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            subTransactions.forEachIndexed { index, subTx ->
                val isExpanded = expandedSplitId == subTx.id

                AuroraCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .clickable { expandedSplitId = subTx.id }
                ) {
                    if (isExpanded) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp)
                            ) {
                                Text("Split ${index + 1}", style = MaterialTheme.typography.titleSmall)
                                if (subTransactions.size > 1) {
                                    TextButton(
                                        onClick = {
                                            subTransactions = subTransactions.filter { it.id != subTx.id }
                                        },
                                        colors = ButtonDefaults.textButtonColors(
                                            contentColor = MaterialTheme.colorScheme.error
                                        )
                                    ) {
                                        Text("Remove", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }

                            AuroraTextField(
                                value = subTx.description,
                                onValueChange = {
                                    subTransactions = subTransactions.toMutableList().apply {
                                        this[index] = subTx.copy(description = it)
                                    }
                                },
                                label = "Item name",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp)
                            )

                            AuroraTextField(
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
                                        newGstAmtStr = if (calculatedGst > 0.0)
                                            String.format("%.2f", calculatedGst).replace(".00", "") else ""
                                        newGstPercentStr = subTx.gstPercentage
                                        val totalVal = baseVal + calculatedGst
                                        newTotalAmtStr = if (totalVal > 0.0)
                                            String.format("%.2f", totalVal).replace(".00", "") else ""
                                    } else if (subTx.gstAmount.isNotEmpty()) {
                                        val totalVal = baseVal + gstAmtVal
                                        newTotalAmtStr = if (totalVal > 0.0)
                                            String.format("%.2f", totalVal).replace(".00", "") else ""
                                        newGstPercentStr = if (baseVal > 0.0)
                                            String.format("%.2f", (gstAmtVal / baseVal) * 100.0).replace(".00", "")
                                        else ""
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
                                label = "Base amount (₹)",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                trailingIcon = calcTrailingIcon(subTx.baseAmount, "SPLIT_BASE", index)
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                AuroraTextField(
                                    value = subTx.gstPercentage,
                                    onValueChange = { newValue ->
                                        val baseVal = subTx.baseAmount.toDoubleOrNull() ?: 0.0
                                        val percentVal = newValue.toDoubleOrNull() ?: 0.0

                                        val newGstAmtStr: String
                                        val newTotalAmtStr: String

                                        if (newValue.isNotEmpty()) {
                                            val calculatedGst = baseVal * (percentVal / 100.0)
                                            newGstAmtStr = if (calculatedGst > 0.0)
                                                String.format("%.2f", calculatedGst).replace(".00", "") else ""
                                            val totalVal = baseVal + calculatedGst
                                            newTotalAmtStr = if (totalVal > 0.0)
                                                String.format("%.2f", totalVal).replace(".00", "") else ""
                                        } else {
                                            newGstAmtStr = ""
                                            newTotalAmtStr = subTx.baseAmount
                                        }

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
                                    trailingIcon = calcTrailingIcon(subTx.gstPercentage, "SPLIT_GST_PCT", index)
                                )
                                AuroraTextField(
                                    value = subTx.gstAmount,
                                    onValueChange = { newValue ->
                                        val baseVal = subTx.baseAmount.toDoubleOrNull() ?: 0.0
                                        val gstAmtVal = newValue.toDoubleOrNull() ?: 0.0

                                        val newGstPercentStr: String
                                        val newTotalAmtStr: String

                                        if (newValue.isNotEmpty()) {
                                            val totalVal = baseVal + gstAmtVal
                                            newTotalAmtStr = if (totalVal > 0.0)
                                                String.format("%.2f", totalVal).replace(".00", "") else ""
                                            newGstPercentStr = if (baseVal > 0.0)
                                                String.format("%.2f", (gstAmtVal / baseVal) * 100.0)
                                                    .replace(".00", "")
                                            else ""
                                        } else {
                                            newGstPercentStr = ""
                                            newTotalAmtStr = subTx.baseAmount
                                        }

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
                                    trailingIcon = calcTrailingIcon(subTx.gstAmount, "SPLIT_GST_AMT", index)
                                )
                            }

                            AuroraTextField(
                                value = subTx.amount,
                                onValueChange = { },
                                label = "Total amount (₹)",
                                readOnly = true,
                                enabled = false,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp)
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                AuroraTextField(
                                    value = subTx.quantity,
                                    onValueChange = {
                                        subTransactions = subTransactions.toMutableList().apply {
                                            this[index] = subTx.copy(quantity = it)
                                        }
                                    },
                                    label = "Qty",
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f)
                                )
                                AuroraTextField(
                                    value = subTx.unit,
                                    onValueChange = {
                                        subTransactions = subTransactions.toMutableList().apply {
                                            this[index] = subTx.copy(unit = it)
                                        }
                                    },
                                    label = "Unit",
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            AuroraDropdown(
                                label = "Category",
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
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp)
                            )
                            AuroraDropdown(
                                label = "Subcategory",
                                options = if (subTx.category.isNotEmpty())
                                    (subcategoriesMap[subTx.category] ?: emptyList()) + "+ Add New"
                                else emptyList(),
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
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp)
                            )
                            AuroraMultiSelectDropdown(
                                label = "Labels",
                                options = labels + "+ Add New",
                                selectedOptions = subTx.labels.toSet(),
                                onOptionToggled = { label ->
                                    if (label == "+ Add New") {
                                        editingSplitIndex = index
                                        showAddLabelDialog = true
                                    } else {
                                        val newLabels = if (subTx.labels.contains(label))
                                            subTx.labels - label else subTx.labels + label
                                        subTransactions = subTransactions.toMutableList().apply {
                                            this[index] = subTx.copy(labels = newLabels)
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                showSearch = true
                            )

                            AuroraTextField(
                                value = subTx.notes,
                                onValueChange = {
                                    subTransactions = subTransactions.toMutableList().apply {
                                        this[index] = subTx.copy(notes = it)
                                    }
                                },
                                label = "Notes",
                                singleLine = false,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "${index + 1}.",
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.padding(end = 8.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = if (subTx.description.isNotBlank()) subTx.description
                                    else "No item name",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (subTx.description.isNotBlank())
                                        MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.outline,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = if (subTx.amount.isNotBlank()) "₹${subTx.amount}" else "₹0.00",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
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

            OutlinedButton(
                onClick = {
                    val nextId = subTransactions.maxOfOrNull { it.id }?.plus(1) ?: 1
                    val firstSplit = subTransactions.firstOrNull()
                    val inheritedGstPercent = if (globalGstPercent.isNotEmpty()) globalGstPercent
                    else (firstSplit?.gstPercentage ?: "")

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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(50)
            ) {
                Text("+ Add split")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { performSave(isDraft = true) },
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = RoundedCornerShape(50)
            ) {
                Text("Save draft")
            }
            Button(
                onClick = { performSave(isDraft = false) },
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = RoundedCornerShape(50)
            ) {
                Text(if (isEditing) "Update" else "Save")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
