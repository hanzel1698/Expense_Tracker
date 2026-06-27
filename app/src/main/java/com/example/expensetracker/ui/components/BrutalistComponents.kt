package com.example.expensetracker.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import com.example.expensetracker.ui.theme.Black
import com.example.expensetracker.ui.theme.White
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.window.PopupProperties

@Composable
fun BrutalistCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color = MaterialTheme.colorScheme.onSurface,
    borderWidth: Dp = 2.dp,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier,
        shape = RectangleShape,
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        border = BorderStroke(borderWidth, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        content()
    }
}

@Composable
fun BrutalistButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.onSurface,
    contentColor: Color = MaterialTheme.colorScheme.surface,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = RectangleShape,
        colors = ButtonDefaults.buttonColors(containerColor = containerColor, contentColor = contentColor),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface),
        contentPadding = contentPadding
    ) {
        content()
    }
}

@Composable
fun BrutalistTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    singleLine: Boolean = true,
    readOnly: Boolean = false,
    enabled: Boolean = true,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    val textColor = MaterialTheme.colorScheme.onSurface
    val containerColor = MaterialTheme.colorScheme.surface

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontWeight = FontWeight.Bold, color = textColor) },
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        shape = RectangleShape,
        keyboardOptions = keyboardOptions,
        singleLine = singleLine,
        readOnly = readOnly,
        textStyle = TextStyle(color = textColor, fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
        trailingIcon = trailingIcon,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = containerColor,
            unfocusedContainerColor = containerColor,
            focusedIndicatorColor = textColor,
            unfocusedIndicatorColor = textColor,
            focusedTextColor = textColor,
            unfocusedTextColor = textColor,
            cursorColor = textColor
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrutalistDropdown(
    label: String,
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedOption,
            onValueChange = {},
            readOnly = true,
            label = { Text(label, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            shape = RectangleShape,
            textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedIndicatorColor = MaterialTheme.colorScheme.onSurface,
                unfocusedIndicatorColor = MaterialTheme.colorScheme.onSurface,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                cursorColor = MaterialTheme.colorScheme.onSurface
            )
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.surface).border(BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface))
        ) {
            options.forEach { selectionOption ->
                DropdownMenuItem(
                    text = { Text(selectionOption, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
                    onClick = {
                        onOptionSelected(selectionOption)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrutalistMultiSelectDropdown(
    label: String,
    options: List<String>,
    selectedOptions: Set<String>,
    onOptionToggled: (String) -> Unit,
    modifier: Modifier = Modifier,
    showSearch: Boolean = false
) {
    var expanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    
    LaunchedEffect(expanded) {
        if (!expanded) {
            searchQuery = ""
        }
    }

    val displaySelected = if (selectedOptions.isEmpty()) "All" 
    else if (selectedOptions.size <= 2) selectedOptions.joinToString(", ")
    else "${selectedOptions.size} selected"

    val filteredOptions = remember(options, searchQuery, showSearch) {
        if (!showSearch || searchQuery.isEmpty()) {
            options
        } else {
            val hasAddNew = options.contains("+ Add New")
            val coreOptions = options.filter { it != "+ Add New" }
            val filtered = coreOptions.filter { it.contains(searchQuery, ignoreCase = true) }
            if (hasAddNew) filtered + "+ Add New" else filtered
        }
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = displaySelected,
            onValueChange = {},
            readOnly = true,
            label = {
                if (label.isNotEmpty()) {
                    Text(label, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            shape = RectangleShape,
            singleLine = true,
            textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedIndicatorColor = MaterialTheme.colorScheme.onSurface,
                unfocusedIndicatorColor = MaterialTheme.colorScheme.onSurface,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                cursorColor = MaterialTheme.colorScheme.onSurface
            )
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            properties = PopupProperties(focusable = true),
            modifier = Modifier
                .exposedDropdownSize()
                .background(MaterialTheme.colorScheme.surface)
                .border(BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface))
        ) {
            if (showSearch) {
                BrutalistSearchField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = "Search labels...",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                )
            }

            filteredOptions.forEach { selectionOption ->
                val isSelected = selectedOptions.contains(selectionOption)
                val textColor = MaterialTheme.colorScheme.onSurface
                DropdownMenuItem(
                    text = { 
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isSelected) {
                                Text("✓ ", fontWeight = FontWeight.ExtraBold, color = textColor)
                            }
                            Text(selectionOption, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, color = textColor) 
                        }
                    },
                    onClick = {
                        onOptionToggled(selectionOption)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrutalistSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val textColor = MaterialTheme.colorScheme.onSurface
    val containerColor = MaterialTheme.colorScheme.surface
    
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        interactionSource = interactionSource,
        textStyle = TextStyle(color = textColor, fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
        singleLine = true,
        cursorBrush = SolidColor(textColor),
        decorationBox = { innerTextField ->
            TextFieldDefaults.DecorationBox(
                value = value,
                innerTextField = innerTextField,
                enabled = true,
                singleLine = true,
                visualTransformation = VisualTransformation.None,
                interactionSource = interactionSource,
                placeholder = { Text(placeholder, color = textColor.copy(alpha=0.5f), fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = textColor, modifier = Modifier.size(20.dp)) },
                shape = RectangleShape,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = containerColor,
                    unfocusedContainerColor = containerColor,
                    focusedIndicatorColor = textColor,
                    unfocusedIndicatorColor = textColor,
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                container = {
                    Box(
                        Modifier
                            .border(2.dp, textColor, RectangleShape)
                            .background(containerColor)
                    )
                }
            )
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrutalistDatePickerDialog(
    onDismissRequest: () -> Unit,
    onDateSelected: (Long?) -> Unit
) {
    val datePickerState = rememberDatePickerState()
    val surface = MaterialTheme.colorScheme.surface
    val onSurface = MaterialTheme.colorScheme.onSurface
    DatePickerDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = { onDateSelected(datePickerState.selectedDateMillis); onDismissRequest() }) {
                Text("OK", color = onSurface, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("CANCEL", color = onSurface)
            }
        },
        colors = androidx.compose.material3.DatePickerDefaults.colors(
            containerColor = surface,
        ),
        shape = RectangleShape
    ) {
        DatePicker(
            state = datePickerState,
            colors = androidx.compose.material3.DatePickerDefaults.colors(
                titleContentColor = onSurface,
                headlineContentColor = onSurface,
                weekdayContentColor = onSurface,
                subheadContentColor = onSurface,
                yearContentColor = onSurface,
                currentYearContentColor = surface,
                selectedYearContentColor = surface,
                selectedYearContainerColor = onSurface,
                dayContentColor = onSurface,
                selectedDayContentColor = surface,
                selectedDayContainerColor = onSurface,
                todayContentColor = onSurface,
                todayDateBorderColor = onSurface
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrutalistDateRangePickerDialog(
    onDismissRequest: () -> Unit,
    onDateRangeSelected: (Long?, Long?) -> Unit
) {
    val state = rememberDateRangePickerState()
    val surface = MaterialTheme.colorScheme.surface
    val onSurface = MaterialTheme.colorScheme.onSurface

    DatePickerDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = { onDateRangeSelected(state.selectedStartDateMillis, state.selectedEndDateMillis); onDismissRequest() }) {
                Text("OK", color = onSurface, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("CANCEL", color = onSurface)
            }
        },
        shape = RectangleShape,
        colors = androidx.compose.material3.DatePickerDefaults.colors(containerColor = surface)
    ) {
        DateRangePicker(
            state = state,
            modifier = Modifier.weight(1f),
            colors = androidx.compose.material3.DatePickerDefaults.colors(
                titleContentColor = onSurface,
                headlineContentColor = onSurface,
                weekdayContentColor = onSurface,
                subheadContentColor = onSurface,
                yearContentColor = onSurface,
                selectedYearContentColor = surface,
                selectedYearContainerColor = onSurface,
                dayContentColor = onSurface,
                selectedDayContentColor = surface,
                selectedDayContainerColor = onSurface,
                todayContentColor = onSurface,
                todayDateBorderColor = onSurface,
                dayInSelectionRangeContainerColor = onSurface.copy(alpha=0.1f),
                dayInSelectionRangeContentColor = onSurface
            )
        )
    }
}
