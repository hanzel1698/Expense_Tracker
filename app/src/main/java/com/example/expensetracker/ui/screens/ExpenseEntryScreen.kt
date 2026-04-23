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
    var paymentMode: String = "",
    var paidVia: String = ""
)

@Composable
fun ExpenseEntryScreen(
    isMobilePortrait: Boolean = false,
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
                paymentMode = e.paymentMode,
                paidVia = e.paidVia
            )
        } ?: listOf(SubTransaction(1))
    }

    var storeName by remember { mutableStateOf(initialStoreName) }
    val initialDescription = remember { expenseToEdit?.itemDescription ?: groupToEdit?.firstOrNull()?.itemDescription ?: "" }
    var description by remember { mutableStateOf(initialDescription) }
    var totalAmount by remember { mutableStateOf(initialTotalAmount) }
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
                description != initialDescription ||
                totalAmount != initialTotalAmount ||
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

    fun onCalcInsert() {
        if (isSplit && expandedSplitId != null) {
            // Insert into the AMT field of the expanded split transaction
            val index = subTransactions.indexOfFirst { it.id == expandedSplitId }
            if (index >= 0) {
                val subTx = subTransactions[index]
                subTransactions = subTransactions.toMutableList().apply {
                    this[index] = subTx.copy(amount = calcDisplay)
                }
            }
        } else {
            // Insert into TOTAL field
            totalAmount = calcDisplay
        }
        showCalculator = false
    }

    fun performSave() {
        // Update store history with the current store name if it's not empty
        if (storeName.isNotBlank()) {
            onUpdateStoreHistory(storeName)
        }

        val expenseDate = selectedDate
        val sharedGroupId = expenseToEdit?.groupId ?: groupToEdit?.firstOrNull()?.groupId ?: java.util.UUID.randomUUID().toString()
        val listToSave = if (isSplit && subTransactions.size > 1) {
            subTransactions.map { subTx ->
                Expense(
                    groupId = sharedGroupId,
                    date = expenseDate,
                    storeName = storeName,
                    amount = subTx.amount.toDoubleOrNull() ?: 0.0,
                    category = subTx.category.ifEmpty { mainCategory },
                    subcategory = subTx.subcategory.ifEmpty { mainSubcategory },
                    itemDescription = subTx.description,
                    labels = subTx.labels.ifEmpty { selectedLabels.toList() },
                    quantity = subTx.quantity.toDoubleOrNull(),
                    unit = subTx.unit.ifEmpty { null },
                    notes = subTx.notes.ifEmpty { notes },
                    paymentMode = subTx.paymentMode.ifEmpty { mainPaymentMode },
                    paidVia = subTx.paidVia.ifEmpty { mainPaidVia }
                )
            }
        } else if (isSplit && subTransactions.size == 1) {
            val subTx = subTransactions[0]
            listOf(
                Expense(
                    groupId = sharedGroupId,
                    date = expenseDate,
                    storeName = storeName,
                    amount = subTx.amount.toDoubleOrNull() ?: 0.0,
                    category = subTx.category.ifEmpty { mainCategory },
                    subcategory = subTx.subcategory.ifEmpty { mainSubcategory },
                    itemDescription = subTx.description,
                    labels = subTx.labels.ifEmpty { selectedLabels.toList() },
                    quantity = subTx.quantity.toDoubleOrNull(),
                    unit = subTx.unit.ifEmpty { null },
                    notes = subTx.notes.ifEmpty { notes },
                    paymentMode = subTx.paymentMode.ifEmpty { mainPaymentMode },
                    paidVia = subTx.paidVia.ifEmpty { mainPaidVia }
                )
            )
        } else {
            listOf(
                Expense(
                    groupId = sharedGroupId,
                    date = expenseDate,
                    storeName = storeName,
                    amount = totalAmount.toDoubleOrNull() ?: 0.0,
                    category = mainCategory,
                    subcategory = mainSubcategory,
                    itemDescription = description,
                    labels = selectedLabels.toList(),
                    quantity = quantity.toDoubleOrNull(),
                    unit = unit.ifEmpty { null },
                    notes = notes,
                    paymentMode = mainPaymentMode,
                    paidVia = mainPaidVia
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
                    Text("Do you want to update before leaving?")
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
                            Text("NO", color = Black, fontWeight = FontWeight.Bold)
                        }
                        BrutalistButton(
                            onClick = {
                                showExitConfirmation = false
                                performSave()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("YES", fontWeight = FontWeight.Bold)
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
                                editingSplitIndex = null
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
                                    if (editingSplitIndex != null) {
                                        subTransactions = subTransactions.toMutableList().apply {
                                            this[editingSplitIndex!!] = this[editingSplitIndex!!].copy(paymentMode = newPaymentModeName)
                                        }
                                    } else {
                                        mainPaymentMode = newPaymentModeName
                                    }
                                    showAddPaymentModeDialog = false
                                    newPaymentModeName = ""
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
                                editingSplitIndex = null
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
                                    if (editingSplitIndex != null) {
                                        subTransactions = subTransactions.toMutableList().apply {
                                            this[editingSplitIndex!!] = this[editingSplitIndex!!].copy(paidVia = newPaidViaName)
                                        }
                                    } else {
                                        mainPaidVia = newPaidViaName
                                    }
                                    showAddPaidViaDialog = false
                                    newPaidViaName = ""
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

        // ── Calculator UI (inline, right-aligned) ────────────────────────────
        if (showCalculator) {
            BrutalistCard(
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .wrapContentWidth(align = Alignment.End)
                    .padding(bottom = 10.dp)
                    .align(Alignment.End)
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
                                containerColor = if (btn in listOf("+", "-", "*", "/")) Black else White
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
                                containerColor = if (btn in listOf("+", "-", "*", "/")) Black else White
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
                                containerColor = if (btn in listOf("+", "-", "*", "/")) Black else White
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
                            containerColor = MaterialTheme.colorScheme.error
                        ) {
                            Text("C", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = White)
                        }
                        BrutalistButton(
                            onClick = { onCalcDigit("0") },
                            modifier = Modifier.weight(1f).height(40.dp)
                        ) {
                            Text("0", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        BrutalistButton(
                            onClick = { onCalcDigit(".") },
                            modifier = Modifier.weight(1f).height(40.dp)
                        ) {
                            Text(".", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        BrutalistButton(
                            onClick = { onCalcOperation("+") },
                            modifier = Modifier.weight(1f).height(40.dp),
                            containerColor = Black
                        ) {
                            Text("+", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = White)
                        }
                        BrutalistButton(
                            onClick = { onCalcEqual() },
                            modifier = Modifier.weight(1f).height(40.dp),
                            containerColor = Black
                        ) {
                            Text("=", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = White)
                        }
                        BrutalistButton(
                            onClick = { onCalcInsert() },
                            modifier = Modifier.weight(1f).height(40.dp),
                            containerColor = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                "INS",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = White,
                                modifier = Modifier.wrapContentSize(Alignment.Center)
                            )
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

        // ── Date + Store + Item Row ───────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clickable { showDatePicker = true }
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
            Box(modifier = Modifier.weight(1f)) {
                BrutalistTextField(
                    value = storeName,
                    onValueChange = { 
                        storeName = it
                        showStoreSuggestions = it.isNotBlank()
                    },
                    label = "STORE",
                    modifier = Modifier.fillMaxWidth()
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
            if (!isSplit) {
                BrutalistTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = "ITEM NAME",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // ── Amount + Quantity + Unit inline (non-split mode) ─────────────
        if (!isSplit) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                BrutalistTextField(
                    value = totalAmount,
                    onValueChange = { totalAmount = it },
                    label = "TOTAL (₹)",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1.3f)
                )
                BrutalistTextField(
                    value = quantity,
                    onValueChange = { quantity = it },
                    label = "QTY",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(0.7f)
                )
                BrutalistTextField(
                    value = unit,
                    onValueChange = { unit = it },
                    label = "UNIT",
                    modifier = Modifier.weight(0.8f)
                )
            }
        }

        // ── Category + Subcategory + Labels inline (non-split mode) ───────────
        if (!isSplit) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
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
                    modifier = Modifier.weight(1f)
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
                    modifier = Modifier.weight(1f)
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
                    modifier = Modifier.weight(1f)
                )
            }

            // ── Payment Mode + Paid Via inline (non-split mode) ─────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
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
                    modifier = Modifier.weight(1f)
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
                    modifier = Modifier.weight(1f)
                )
            }

            // ── Notes field ─────────────────────────────────────────────────────
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
                    if (it && subTransactions.size == 1 && subTransactions[0].category.isEmpty()) {
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
                                paymentMode = mainPaymentMode,
                                paidVia = mainPaidVia
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

                        // Description + Amount + Qty + Unit inline
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            BrutalistTextField(
                                value = subTx.description,
                                onValueChange = {
                                    subTransactions = subTransactions.toMutableList().apply {
                                        this[index] = subTx.copy(description = it)
                                    }
                                },
                                label = "ITEM NAME",
                                modifier = Modifier.weight(1.3f)
                            )
                            BrutalistTextField(
                                value = subTx.amount,
                                onValueChange = {
                                    subTransactions = subTransactions.toMutableList().apply {
                                        this[index] = subTx.copy(amount = it)
                                    }
                                },
                                label = "AMT (₹)",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                        }

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


                        // Category + Subcategory + Labels inline
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
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
                                modifier = Modifier.weight(1f)
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
                                modifier = Modifier.weight(1f)
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
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Payment Mode + Paid Via inline (split mode)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            BrutalistDropdown(
                                label = "PAYMENT MODE",
                                options = paymentModes + "+ Add New",
                                selectedOption = subTx.paymentMode,
                                onOptionSelected = {
                                    if (it == "+ Add New") {
                                        editingSplitIndex = index
                                        showAddPaymentModeDialog = true
                                    } else {
                                        subTransactions = subTransactions.toMutableList().apply {
                                            this[index] = subTx.copy(paymentMode = it)
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                            BrutalistDropdown(
                                label = "PAID VIA",
                                options = paidVia + "+ Add New",
                                selectedOption = subTx.paidVia,
                                onOptionSelected = {
                                    if (it == "+ Add New") {
                                        editingSplitIndex = index
                                        showAddPaidViaDialog = true
                                    } else {
                                        subTransactions = subTransactions.toMutableList().apply {
                                            this[index] = subTx.copy(paidVia = it)
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }

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
                    subTransactions = subTransactions + SubTransaction(
                        id = nextId,
                        category = mainCategory,
                        subcategory = mainSubcategory,
                        labels = selectedLabels.toList(),
                        paymentMode = mainPaymentMode,
                        paidVia = mainPaidVia
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

        BrutalistButton(
            onClick = { performSave() },
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Text(if (isEditing) "UPDATE EXPENSE" else "SAVE EXPENSE", fontWeight = FontWeight.Black, fontSize = 17.sp)
        }
    }
}
