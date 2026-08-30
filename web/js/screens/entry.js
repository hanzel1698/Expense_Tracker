// ── Add / edit expense ────────────────────────────────────────────────────────
// Port of ui/modern/screens/ModernExpenseEntryScreen.kt — date picker, store
// with history suggestions, location bonded to the store, base/GST%/GST amount
// with auto-calculation, quantity/unit, category/subcategory/labels with inline
// "+ Add New", payment mode, paid via, notes, split transactions with global GST
// controls, the built-in calculator with field targeting, save / save-draft /
// update, and the unsaved-changes prompt.

import { h, icon } from '../dom.js';
import {
  card, textField, dropdown, multiSelectDropdown, toggle, editDialog,
  customDialog, datePickerDialog,
} from '../components.js';
import { data, update } from '../store.js';
import { createExpense } from '../model.js';
import { uuid, today, fmtSlash, fmt2Trim, toDoubleOrNull } from '../util.js';

/** One line item within a (possibly split) expense — SubTransaction. */
function subTransaction(id, props = {}) {
  return {
    id,
    category: '', subcategory: '', amount: '', description: '',
    labels: [], quantity: '', unit: '', notes: '',
    baseAmount: '', gstPercentage: '', gstAmount: '',
    ...props,
  };
}

const form = {};
let initial = {};

/** Sets up the form for a new expense, a single edit, or a split-group edit. */
export function initEntry({ expenseToEdit = null, groupToEdit = null, selectedExpenseId = null, initialDate = null }) {
  const first = groupToEdit && groupToEdit[0];

  form.storeName = expenseToEdit?.storeName ?? first?.storeName ?? '';
  form.location = expenseToEdit?.location ?? first?.location ?? '';
  form.description = expenseToEdit?.itemDescription ?? first?.itemDescription ?? '';
  form.totalAmount = expenseToEdit ? String(expenseToEdit.amount) : '';
  form.baseAmount = expenseToEdit
    ? String(expenseToEdit.baseAmount ?? expenseToEdit.amount ?? '')
    : '';
  form.gstPercentage = expenseToEdit?.gstPercentage != null ? String(expenseToEdit.gstPercentage) : '';
  form.gstAmount = expenseToEdit?.gstAmount != null ? String(expenseToEdit.gstAmount) : '';
  form.quantity = expenseToEdit?.quantity != null
    ? String(expenseToEdit.quantity)
    : (first?.quantity != null ? String(first.quantity) : '');
  form.unit = expenseToEdit?.unit ?? first?.unit ?? '';
  form.notes = expenseToEdit?.notes ?? first?.notes ?? '';
  form.mainCategory = expenseToEdit?.category ?? '';
  form.mainSubcategory = expenseToEdit?.subcategory ?? '';
  form.mainPaymentMode = expenseToEdit?.paymentMode ?? '';
  form.mainPaidVia = expenseToEdit?.paidVia ?? '';
  form.selectedLabels = [...(expenseToEdit?.labels ?? [])];
  form.selectedDate = initialDate || expenseToEdit?.date || first?.date || today();
  form.isSplit = !!(groupToEdit && groupToEdit.length > 1);

  form.subTransactions = groupToEdit
    ? groupToEdit.map((e, index) => subTransaction(index + 1, {
      category: e.category,
      subcategory: e.subcategory,
      amount: String(e.amount),
      description: e.itemDescription,
      labels: [...e.labels],
      quantity: e.quantity != null ? String(e.quantity) : '',
      unit: e.unit ?? '',
      notes: e.notes,
      baseAmount: String(e.baseAmount ?? e.amount),
      gstPercentage: e.gstPercentage != null ? String(e.gstPercentage) : '',
      gstAmount: e.gstAmount != null ? String(e.gstAmount) : '',
    }))
    : [subTransaction(1)];

  // A split group carries a single global GST% only when every line agrees.
  const firstGst = first?.gstPercentage;
  form.globalGstPercent = (groupToEdit && firstGst != null
    && groupToEdit.every((e) => e.gstPercentage === firstGst))
    ? String(firstGst) : '';
  const totalGst = groupToEdit ? groupToEdit.reduce((sum, e) => sum + (e.gstAmount || 0), 0) : 0;
  form.totalGstPaid = totalGst > 0 ? fmt2Trim(totalGst) : '';

  const selectedIndex = groupToEdit ? groupToEdit.findIndex((e) => e.id === selectedExpenseId) : -1;
  form.expandedSplitId = selectedIndex >= 0
    ? form.subTransactions[selectedIndex]?.id
    : form.subTransactions[0]?.id;

  form.expenseToEdit = expenseToEdit;
  form.groupToEdit = groupToEdit;
  form.isEditing = !!(expenseToEdit || (groupToEdit && groupToEdit.length > 0));
  form.storeNameTouched = false;

  initial = JSON.parse(JSON.stringify({
    storeName: form.storeName, location: form.location, description: form.description,
    totalAmount: form.totalAmount, baseAmount: form.baseAmount,
    gstPercentage: form.gstPercentage, gstAmount: form.gstAmount,
    quantity: form.quantity, unit: form.unit, notes: form.notes,
    mainCategory: form.mainCategory, mainSubcategory: form.mainSubcategory,
    mainPaymentMode: form.mainPaymentMode, mainPaidVia: form.mainPaidVia,
    selectedLabels: form.selectedLabels, isSplit: form.isSplit,
    subTransactions: form.subTransactions, selectedDate: form.selectedDate,
  }));
}

export function hasChanges() {
  const current = {
    storeName: form.storeName, location: form.location, description: form.description,
    totalAmount: form.totalAmount, baseAmount: form.baseAmount,
    gstPercentage: form.gstPercentage, gstAmount: form.gstAmount,
    quantity: form.quantity, unit: form.unit, notes: form.notes,
    mainCategory: form.mainCategory, mainSubcategory: form.mainSubcategory,
    mainPaymentMode: form.mainPaymentMode, mainPaidVia: form.mainPaidVia,
    selectedLabels: form.selectedLabels, isSplit: form.isSplit,
    subTransactions: form.subTransactions, selectedDate: form.selectedDate,
  };
  return JSON.stringify(current) !== JSON.stringify(initial);
}

// ── GST maths (shared by the main form and each split) ───────────────────────

/** Recomputes gstAmount / total from a new base amount. */
function recalcFromBase(t, newBase) {
  const baseVal = toDoubleOrNull(newBase) ?? 0;
  const percentVal = toDoubleOrNull(t.gstPercentage) ?? 0;
  const gstAmtVal = toDoubleOrNull(t.gstAmount) ?? 0;

  if (t.gstPercentage !== '') {
    const calculatedGst = baseVal * (percentVal / 100);
    const totalVal = baseVal + calculatedGst;
    return {
      gstPercentage: t.gstPercentage,
      gstAmount: calculatedGst > 0 ? fmt2Trim(calculatedGst) : '',
      total: totalVal > 0 ? fmt2Trim(totalVal) : '',
    };
  }
  if (t.gstAmount !== '') {
    const totalVal = baseVal + gstAmtVal;
    return {
      gstPercentage: baseVal > 0 ? fmt2Trim((gstAmtVal / baseVal) * 100) : '',
      gstAmount: t.gstAmount,
      total: totalVal > 0 ? fmt2Trim(totalVal) : '',
    };
  }
  return { gstPercentage: '', gstAmount: '', total: newBase };
}

/** Recomputes gstAmount / total from a new GST percentage. */
function recalcFromPercent(t, newPercent) {
  const baseVal = toDoubleOrNull(t.baseAmount) ?? 0;
  const percentVal = toDoubleOrNull(newPercent) ?? 0;
  if (newPercent !== '') {
    const calculatedGst = baseVal * (percentVal / 100);
    const totalVal = baseVal + calculatedGst;
    return {
      gstAmount: calculatedGst > 0 ? fmt2Trim(calculatedGst) : '',
      total: totalVal > 0 ? fmt2Trim(totalVal) : '',
    };
  }
  return { gstAmount: '', total: t.baseAmount };
}

/** Recomputes GST% / total from a new GST amount. */
function recalcFromGstAmount(t, newGstAmount) {
  const baseVal = toDoubleOrNull(t.baseAmount) ?? 0;
  const gstAmtVal = toDoubleOrNull(newGstAmount) ?? 0;
  if (newGstAmount !== '') {
    const totalVal = baseVal + gstAmtVal;
    return {
      gstPercentage: baseVal > 0 ? fmt2Trim((gstAmtVal / baseVal) * 100) : '',
      total: totalVal > 0 ? fmt2Trim(totalVal) : '',
    };
  }
  return { gstPercentage: '', total: t.baseAmount };
}

function applyGlobalGstPercent(splits, percentStr) {
  const percentVal = toDoubleOrNull(percentStr) ?? 0;
  return splits.map((subTx) => {
    const baseVal = toDoubleOrNull(subTx.baseAmount) ?? 0;
    const gstAmtVal = baseVal * (percentVal / 100);
    const totalVal = baseVal + gstAmtVal;
    return {
      ...subTx,
      gstPercentage: percentStr,
      gstAmount: baseVal > 0 && percentVal > 0 ? fmt2Trim(gstAmtVal) : '',
      amount: totalVal > 0 ? fmt2Trim(totalVal) : '',
    };
  });
}

function applyTotalGstPaid(splits, totalGst) {
  const totalBase = splits.reduce((sum, s) => sum + (toDoubleOrNull(s.baseAmount) ?? 0), 0);
  if (totalBase <= 0) return splits;
  const percentVal = (totalGst / totalBase) * 100;
  const percentStr = fmt2Trim(percentVal);
  form.globalGstPercent = percentStr;

  return splits.map((subTx) => {
    const baseVal = toDoubleOrNull(subTx.baseAmount) ?? 0;
    const gstAmtVal = baseVal * (percentVal / 100);
    const totalVal = baseVal + gstAmtVal;
    return {
      ...subTx,
      gstPercentage: percentStr,
      gstAmount: baseVal > 0 ? fmt2Trim(gstAmtVal) : '',
      amount: totalVal > 0 ? fmt2Trim(totalVal) : '',
    };
  });
}

/** Re-applies whichever global GST control is active after a split edit. */
function reapplyGlobalGst(splits) {
  if (form.globalGstPercent !== '') return applyGlobalGstPercent(splits, form.globalGstPercent);
  if (form.totalGstPaid !== '') return applyTotalGstPaid(splits, toDoubleOrNull(form.totalGstPaid) ?? 0);
  return splits;
}

// ── Save ─────────────────────────────────────────────────────────────────────

function performSave(ctx, isDraft = false) {
  if (form.storeName.trim()) ctx.updateStoreHistory(form.storeName);
  if (form.storeName.trim() && form.location.trim()) {
    ctx.updateStoreLocation(form.storeName, form.location);
  }

  const expenseIdForIndex = (index) => form.groupToEdit?.[index]?.id
    ?? (index === 0 ? form.expenseToEdit?.id : null)
    ?? uuid();

  const sharedGroupId = form.expenseToEdit?.groupId
    ?? form.groupToEdit?.[0]?.groupId
    ?? uuid();

  const fromSplit = (subTx, index) => {
    const finalAmount = toDoubleOrNull(subTx.amount) ?? 0;
    return createExpense({
      id: expenseIdForIndex(index),
      groupId: sharedGroupId,
      date: form.selectedDate,
      storeName: form.storeName,
      location: form.location,
      amount: finalAmount,
      category: subTx.category || form.mainCategory,
      subcategory: subTx.subcategory || form.mainSubcategory,
      itemDescription: subTx.description,
      labels: subTx.labels.length > 0 ? subTx.labels : [...form.selectedLabels],
      quantity: toDoubleOrNull(subTx.quantity),
      unit: subTx.unit || null,
      notes: subTx.notes || form.notes,
      paymentMode: form.mainPaymentMode,
      paidVia: form.mainPaidVia,
      isDraft,
      baseAmount: toDoubleOrNull(subTx.baseAmount) ?? finalAmount,
      gstPercentage: toDoubleOrNull(subTx.gstPercentage),
      gstAmount: toDoubleOrNull(subTx.gstAmount),
    });
  };

  let listToSave;
  if (form.isSplit) {
    listToSave = form.subTransactions.map(fromSplit);
  } else {
    const finalAmount = toDoubleOrNull(form.totalAmount) ?? 0;
    listToSave = [createExpense({
      id: expenseIdForIndex(0),
      groupId: sharedGroupId,
      date: form.selectedDate,
      storeName: form.storeName,
      location: form.location,
      amount: finalAmount,
      category: form.mainCategory,
      subcategory: form.mainSubcategory,
      itemDescription: form.description,
      labels: [...form.selectedLabels],
      quantity: toDoubleOrNull(form.quantity),
      unit: form.unit || null,
      notes: form.notes,
      paymentMode: form.mainPaymentMode,
      paidVia: form.mainPaidVia,
      isDraft,
      baseAmount: toDoubleOrNull(form.baseAmount) ?? finalAmount,
      gstPercentage: toDoubleOrNull(form.gstPercentage),
      gstAmount: toDoubleOrNull(form.gstAmount),
    })];
  }

  ctx.saveExpenses(listToSave, sharedGroupId, form.expenseToEdit || form.groupToEdit?.[0]);
}

// ── Screen ───────────────────────────────────────────────────────────────────

export function renderEntry(ctx) {
  const root = h('div.screen-pad');
  const rerender = () => ctx.rerender();

  // Refs to the derived amount fields so typing can patch them without a
  // re-render (which would steal focus mid-edit).
  const refs = {};

  // ── Header ─────────────────────────────────────────────────────────────────
  root.appendChild(h('div.row.mb-12', {},
    h('button.icon-btn', {
      type: 'button', 'aria-label': 'Back',
      onclick: () => requestBack(ctx),
    }, icon('arrowBack')),
    h('div.headline-medium.grow', { style: { marginLeft: '4px' } },
      form.isEditing ? 'Edit expense' : 'Add expense'),
    h('button.icon-btn.filled-tonal', {
      type: 'button', 'aria-label': 'Calculator',
      onclick: () => openCalculator(ctx, { target: null }),
    }, icon('calculate')),
  ));

  // ── Date ───────────────────────────────────────────────────────────────────
  const dateField = textField({
    label: 'Date',
    value: fmtSlash(form.selectedDate),
    readOnly: true,
    disabled: true,
    onclick: () => datePickerDialog({
      initial: form.selectedDate,
      onDateSelected: (iso) => { if (iso) form.selectedDate = iso; },
      onDismiss: rerender,
    }),
  });
  root.appendChild(h('div.mb-8', {}, dateField));

  // ── Store (with history suggestions) ───────────────────────────────────────
  root.appendChild(h('div.mb-8', {}, suggestField({
    label: 'Store',
    value: form.storeName,
    suggestionsFor: (value) => data.storeHistory
      .filter((s) => s.trim() && s.toLowerCase().includes(value.toLowerCase()))
      .slice(0, 5),
    oninput: (value) => { form.storeName = value; form.storeNameTouched = true; },
    onpick: (value) => { form.storeName = value; form.storeNameTouched = true; rerender(); },
  })));

  // ── Location (bonded to the store) ─────────────────────────────────────────
  const matchedStoreLocations = Object.entries(data.storeLocationHistory)
    .find(([store]) => store.toLowerCase() === form.storeName.toLowerCase())?.[1] || [];

  root.appendChild(h('div.mb-8', {}, suggestField({
    label: 'Location',
    value: form.location,
    openOnFocus: matchedStoreLocations.length > 0,
    suggestionsFor: (value) => matchedStoreLocations
      .filter((l) => l.toLowerCase().includes(value.toLowerCase()))
      .slice(0, 5),
    oninput: (value) => { form.location = value; },
    onpick: (value) => { form.location = value; rerender(); },
  })));

  // ── Split toggle ───────────────────────────────────────────────────────────
  root.appendChild(h('div.mb-8', {}, card({}, h('div.row.between', { style: { padding: '8px 16px' } },
    h('span.title-small', {}, 'Split transaction'),
    toggle(form.isSplit, (checked) => {
      form.isSplit = checked;
      if (checked && form.subTransactions.length === 1) {
        form.subTransactions = [{
          ...form.subTransactions[0],
          category: form.mainCategory,
          subcategory: form.mainSubcategory,
          amount: form.totalAmount,
          description: form.description,
          labels: [...form.selectedLabels],
          quantity: form.quantity,
          unit: form.unit,
          notes: form.notes,
          baseAmount: form.baseAmount,
          gstPercentage: form.gstPercentage,
          gstAmount: form.gstAmount,
        }];
      }
      if (checked) form.expandedSplitId = form.subTransactions[0]?.id;
      rerender();
    }, 'Split transaction'),
  ))));

  // ── Non-split fields ───────────────────────────────────────────────────────
  if (!form.isSplit) {
    root.appendChild(h('div.mb-8', {}, textField({
      label: 'Item name',
      value: form.description,
      oninput: (value) => { form.description = value; },
    })));

    refs.baseField = textField({
      label: 'Base amount (₹)',
      value: form.baseAmount,
      numeric: true,
      oninput: (value) => {
        form.baseAmount = value;
        const next = recalcFromBase(form, value);
        form.gstPercentage = next.gstPercentage;
        form.gstAmount = next.gstAmount;
        form.totalAmount = next.total;
        refs.gstPctField.inputEl.value = form.gstPercentage;
        refs.gstAmtField.inputEl.value = form.gstAmount;
        refs.totalField.inputEl.value = form.totalAmount;
      },
      trailing: calcButton(ctx, () => form.baseAmount, 'MAIN_BASE'),
    });

    refs.gstPctField = textField({
      label: 'GST %',
      value: form.gstPercentage,
      numeric: true,
      oninput: (value) => {
        form.gstPercentage = value;
        const next = recalcFromPercent(form, value);
        form.gstAmount = next.gstAmount;
        form.totalAmount = next.total;
        refs.gstAmtField.inputEl.value = form.gstAmount;
        refs.totalField.inputEl.value = form.totalAmount;
      },
      trailing: calcButton(ctx, () => form.gstPercentage, 'MAIN_GST_PCT'),
    });

    refs.gstAmtField = textField({
      label: 'GST (₹)',
      value: form.gstAmount,
      numeric: true,
      oninput: (value) => {
        form.gstAmount = value;
        const next = recalcFromGstAmount(form, value);
        form.gstPercentage = next.gstPercentage;
        form.totalAmount = next.total;
        refs.gstPctField.inputEl.value = form.gstPercentage;
        refs.totalField.inputEl.value = form.totalAmount;
      },
      trailing: calcButton(ctx, () => form.gstAmount, 'MAIN_GST_AMT'),
    });

    refs.totalField = textField({
      label: 'Total amount (₹)',
      value: form.totalAmount,
      readOnly: true,
      disabled: true,
    });

    root.appendChild(h('div.mb-8', {}, refs.baseField));
    root.appendChild(h('div.row.gap-8.mb-8', {},
      h('div.grow', {}, refs.gstPctField),
      h('div.grow', {}, refs.gstAmtField),
    ));
    root.appendChild(h('div.mb-8', {}, refs.totalField));

    root.appendChild(h('div.row.gap-8.mb-8', {},
      h('div.grow', {}, textField({
        label: 'Qty', value: form.quantity, numeric: true,
        oninput: (value) => { form.quantity = value; },
      })),
      h('div.grow', {}, textField({
        label: 'Unit', value: form.unit,
        oninput: (value) => { form.unit = value; },
      })),
    ));

    // Category / subcategory / labels
    root.appendChild(h('div.mb-8', {}, dropdown({
      label: 'Category',
      options: [...data.categories, '+ Add New'],
      selected: form.mainCategory,
      onSelect: (option) => {
        if (option === '+ Add New') {
          promptAddCategory(ctx, (value) => { form.mainCategory = value; form.mainSubcategory = ''; });
        } else {
          form.mainCategory = option;
          form.mainSubcategory = '';
          rerender();
        }
      },
    })));

    root.appendChild(h('div.mb-8', {}, dropdown({
      label: 'Subcategory',
      options: form.mainCategory
        ? [...(data.subcategoriesMap[form.mainCategory] || []), '+ Add New']
        : [],
      selected: form.mainSubcategory,
      onSelect: (option) => {
        if (option === '+ Add New') {
          if (form.mainCategory) {
            promptAddSubcategory(ctx, form.mainCategory, (value) => { form.mainSubcategory = value; });
          }
        } else {
          form.mainSubcategory = option;
          rerender();
        }
      },
    })));

    root.appendChild(h('div.mb-8', {}, multiSelectDropdown({
      label: 'Labels',
      options: [...data.labels, '+ Add New'],
      selected: new Set(form.selectedLabels),
      showSearch: true,
      onToggle: (label) => {
        if (label === '+ Add New') {
          promptAddLabel(ctx, (value) => { form.selectedLabels.push(value); });
        } else {
          form.selectedLabels = form.selectedLabels.includes(label)
            ? form.selectedLabels.filter((l) => l !== label)
            : [...form.selectedLabels, label];
          rerender();
        }
      },
    })));
  }

  // ── Payment mode / paid via (shared) ───────────────────────────────────────
  root.appendChild(h('div.mb-8', {}, dropdown({
    label: 'Payment mode',
    options: [...data.paymentModes, '+ Add New'],
    selected: form.mainPaymentMode,
    onSelect: (option) => {
      if (option === '+ Add New') {
        editDialog({
          title: 'New payment mode', fieldLabel: 'Payment mode name', initialValue: '',
          onConfirm: (value) => {
            if (value.trim()) {
              update((d) => { d.paymentModes.push(value); });
              form.mainPaymentMode = value;
            }
            rerender();
          },
          onDismiss: rerender,
        });
      } else {
        form.mainPaymentMode = option;
        rerender();
      }
    },
  })));

  root.appendChild(h('div.mb-8', {}, dropdown({
    label: 'Paid via',
    options: [...data.paidVia, '+ Add New'],
    selected: form.mainPaidVia,
    onSelect: (option) => {
      if (option === '+ Add New') {
        editDialog({
          title: 'New paid via', fieldLabel: 'Paid via name', initialValue: '',
          onConfirm: (value) => {
            if (value.trim()) {
              update((d) => { d.paidVia.push(value); });
              form.mainPaidVia = value;
            }
            rerender();
          },
          onDismiss: rerender,
        });
      } else {
        form.mainPaidVia = option;
        rerender();
      }
    },
  })));

  if (!form.isSplit) {
    root.appendChild(h('div.mb-8', {}, textField({
      label: 'Notes',
      value: form.notes,
      multiline: true,
      oninput: (value) => { form.notes = value; },
    })));
  }

  // ── Split section ──────────────────────────────────────────────────────────
  if (form.isSplit) {
    root.appendChild(renderSplitSection(ctx, refs));
  }

  // ── Actions ────────────────────────────────────────────────────────────────
  root.appendChild(h('div.row.gap-8', { style: { marginTop: '12px' } },
    h('button.btn.outlined.tall.grow', {
      type: 'button',
      onclick: () => performSave(ctx, true),
    }, 'Save draft'),
    h('button.btn.tall.grow', {
      type: 'button',
      onclick: () => performSave(ctx, false),
    }, form.isEditing ? 'Update' : 'Save'),
  ));
  root.appendChild(h('div', { style: { height: '16px' } }));

  return root;
}

// ── Split section ────────────────────────────────────────────────────────────

function renderSplitSection(ctx, refs) {
  const rerender = () => ctx.rerender();
  const wrap = h('div');

  // Global GST settings
  const globalPctField = textField({
    label: 'Global GST %',
    value: form.globalGstPercent,
    numeric: true,
    oninput: (value) => {
      form.globalGstPercent = value;
      form.totalGstPaid = '';
      form.subTransactions = applyGlobalGstPercent(form.subTransactions, value);
      totalGstField.inputEl.value = '';
      refreshSplitTotals();
    },
  });
  const totalGstField = textField({
    label: 'Total GST (₹)',
    value: form.totalGstPaid,
    numeric: true,
    oninput: (value) => {
      form.totalGstPaid = value;
      if (value !== '') {
        form.subTransactions = applyTotalGstPaid(form.subTransactions, toDoubleOrNull(value) ?? 0);
        globalPctField.inputEl.value = form.globalGstPercent;
      } else {
        form.globalGstPercent = '';
        globalPctField.inputEl.value = '';
        form.subTransactions = form.subTransactions.map((subTx) => ({
          ...subTx, gstPercentage: '', gstAmount: '', amount: subTx.baseAmount,
        }));
      }
      refreshSplitTotals();
    },
  });

  wrap.appendChild(h('div.mb-12', {}, card({}, h('div.pad-14', {},
    h('div.title-small.mb-8', {}, 'Split GST settings'),
    h('div.row.gap-8', {},
      h('div.grow', {}, globalPctField),
      h('div.grow', {}, totalGstField),
    ),
  ))));

  // Split totals card
  const totalsBase = h('div.label-medium');
  const totalsGst = h('div.label-medium');
  const totalsSum = h('div.title-medium');

  function refreshSplitTotals() {
    const totalBase = form.subTransactions.reduce((s, t) => s + (toDoubleOrNull(t.baseAmount) ?? 0), 0);
    const totalGst = form.subTransactions.reduce((s, t) => s + (toDoubleOrNull(t.gstAmount) ?? 0), 0);
    const totalCalc = form.subTransactions.reduce((s, t) => s + (toDoubleOrNull(t.amount) ?? 0), 0);
    totalsBase.textContent = `Base ₹${fmt2Trim(totalBase)}`;
    totalsGst.textContent = `GST ₹${fmt2Trim(totalGst)}`;
    totalsSum.textContent = `Split total ₹${fmt2Trim(totalCalc)}`;
  }
  refreshSplitTotals();

  wrap.appendChild(h('div.mb-12', {}, card({ variant: 'primary-container' },
    h('div.row.between', { style: { padding: '10px 14px' } },
      h('div.col', {}, totalsBase, totalsGst),
      totalsSum,
    ),
  )));

  // Split cards
  form.subTransactions.forEach((subTx, index) => {
    const isExpanded = form.expandedSplitId === subTx.id;

    const patch = (changes) => {
      const updated = [...form.subTransactions];
      updated[index] = { ...updated[index], ...changes };
      form.subTransactions = reapplyGlobalGst(updated);
      refreshSplitTotals();
      return form.subTransactions[index];
    };

    if (!isExpanded) {
      wrap.appendChild(card({
        className: 'mb-8',
        onclick: () => { form.expandedSplitId = subTx.id; rerender(); },
      }, h('div.row.between.pad-14', {},
        h('div.row.grow', {},
          h('span.title-small.muted', { style: { paddingRight: '8px' } }, `${index + 1}.`),
          h('span.body-medium.ellipsis', {
            style: {
              fontWeight: '600',
              color: subTx.description.trim() ? 'var(--on-surface)' : 'var(--outline)',
            },
          }, subTx.description.trim() || 'No item name'),
        ),
        h('span.title-small.primary', {}, subTx.amount.trim() ? `₹${subTx.amount}` : '₹0.00'),
      )));
      return;
    }

    const splitRefs = {};
    splitRefs.gstPct = textField({
      label: 'GST %',
      value: subTx.gstPercentage,
      numeric: true,
      oninput: (value) => {
        const next = recalcFromPercent(form.subTransactions[index], value);
        form.globalGstPercent = '';
        form.totalGstPaid = '';
        globalPctField.inputEl.value = '';
        totalGstField.inputEl.value = '';
        const updated = [...form.subTransactions];
        updated[index] = {
          ...updated[index],
          gstPercentage: value,
          gstAmount: next.gstAmount,
          amount: next.total,
        };
        form.subTransactions = updated;
        splitRefs.gstAmt.inputEl.value = next.gstAmount;
        splitRefs.total.inputEl.value = next.total;
        refreshSplitTotals();
      },
      trailing: calcButton(ctx, () => form.subTransactions[index].gstPercentage, 'SPLIT_GST_PCT', index),
    });

    splitRefs.gstAmt = textField({
      label: 'GST (₹)',
      value: subTx.gstAmount,
      numeric: true,
      oninput: (value) => {
        const next = recalcFromGstAmount(form.subTransactions[index], value);
        form.globalGstPercent = '';
        form.totalGstPaid = '';
        globalPctField.inputEl.value = '';
        totalGstField.inputEl.value = '';
        const updated = [...form.subTransactions];
        updated[index] = {
          ...updated[index],
          gstPercentage: next.gstPercentage,
          gstAmount: value,
          amount: next.total,
        };
        form.subTransactions = updated;
        splitRefs.gstPct.inputEl.value = next.gstPercentage;
        splitRefs.total.inputEl.value = next.total;
        refreshSplitTotals();
      },
      trailing: calcButton(ctx, () => form.subTransactions[index].gstAmount, 'SPLIT_GST_AMT', index),
    });

    splitRefs.total = textField({
      label: 'Total amount (₹)',
      value: subTx.amount,
      readOnly: true,
      disabled: true,
    });

    splitRefs.base = textField({
      label: 'Base amount (₹)',
      value: subTx.baseAmount,
      numeric: true,
      oninput: (value) => {
        const next = recalcFromBase(form.subTransactions[index], value);
        const result = patch({
          baseAmount: value,
          gstPercentage: next.gstPercentage,
          gstAmount: next.gstAmount,
          amount: next.total,
        });
        splitRefs.gstPct.inputEl.value = result.gstPercentage;
        splitRefs.gstAmt.inputEl.value = result.gstAmount;
        splitRefs.total.inputEl.value = result.amount;
      },
      trailing: calcButton(ctx, () => form.subTransactions[index].baseAmount, 'SPLIT_BASE', index),
    });

    const body = h('div.pad-14', {},
      h('div.row.between.mb-8', {},
        h('span.title-small', {}, `Split ${index + 1}`),
        form.subTransactions.length > 1
          ? h('button.btn.text.destructive', {
            type: 'button',
            onclick: () => {
              form.subTransactions = form.subTransactions.filter((s) => s.id !== subTx.id);
              if (form.expandedSplitId === subTx.id) {
                form.expandedSplitId = form.subTransactions[0]?.id;
              }
              rerender();
            },
          }, 'Remove')
          : null,
      ),
      h('div.mb-8', {}, textField({
        label: 'Item name',
        value: subTx.description,
        oninput: (value) => {
          form.subTransactions[index] = { ...form.subTransactions[index], description: value };
        },
      })),
      h('div.mb-8', {}, splitRefs.base),
      h('div.row.gap-8.mb-8', {},
        h('div.grow', {}, splitRefs.gstPct),
        h('div.grow', {}, splitRefs.gstAmt),
      ),
      h('div.mb-8', {}, splitRefs.total),
      h('div.row.gap-8.mb-8', {},
        h('div.grow', {}, textField({
          label: 'Qty', value: subTx.quantity, numeric: true,
          oninput: (value) => {
            form.subTransactions[index] = { ...form.subTransactions[index], quantity: value };
          },
        })),
        h('div.grow', {}, textField({
          label: 'Unit', value: subTx.unit,
          oninput: (value) => {
            form.subTransactions[index] = { ...form.subTransactions[index], unit: value };
          },
        })),
      ),
      h('div.mb-8', {}, dropdown({
        label: 'Category',
        options: [...data.categories, '+ Add New'],
        selected: subTx.category,
        onSelect: (option) => {
          if (option === '+ Add New') {
            promptAddCategory(ctx, (value) => {
              form.subTransactions[index] = {
                ...form.subTransactions[index], category: value, subcategory: '',
              };
            });
          } else {
            form.subTransactions[index] = {
              ...form.subTransactions[index], category: option, subcategory: '',
            };
            rerender();
          }
        },
      })),
      h('div.mb-8', {}, dropdown({
        label: 'Subcategory',
        options: subTx.category
          ? [...(data.subcategoriesMap[subTx.category] || []), '+ Add New']
          : [],
        selected: subTx.subcategory,
        onSelect: (option) => {
          if (option === '+ Add New') {
            if (subTx.category) {
              promptAddSubcategory(ctx, subTx.category, (value) => {
                form.subTransactions[index] = { ...form.subTransactions[index], subcategory: value };
              });
            }
          } else {
            form.subTransactions[index] = { ...form.subTransactions[index], subcategory: option };
            rerender();
          }
        },
      })),
      h('div.mb-8', {}, multiSelectDropdown({
        label: 'Labels',
        options: [...data.labels, '+ Add New'],
        selected: new Set(subTx.labels),
        showSearch: true,
        onToggle: (label) => {
          if (label === '+ Add New') {
            promptAddLabel(ctx, (value) => {
              form.subTransactions[index] = {
                ...form.subTransactions[index],
                labels: [...form.subTransactions[index].labels, value],
              };
            });
          } else {
            const current = form.subTransactions[index].labels;
            form.subTransactions[index] = {
              ...form.subTransactions[index],
              labels: current.includes(label) ? current.filter((l) => l !== label) : [...current, label],
            };
            rerender();
          }
        },
      })),
      textField({
        label: 'Notes',
        value: subTx.notes,
        multiline: true,
        oninput: (value) => {
          form.subTransactions[index] = { ...form.subTransactions[index], notes: value };
        },
      }),
    );

    wrap.appendChild(card({ className: 'mb-8' }, body));
  });

  // Add split — enabled only once the last split has a name and an amount
  const lastSplit = form.subTransactions[form.subTransactions.length - 1];
  const canAddMore = !!lastSplit
    && lastSplit.description.trim() !== ''
    && lastSplit.amount.trim() !== ''
    && (toDoubleOrNull(lastSplit.amount) ?? 0) > 0;

  wrap.appendChild(h('button.btn.outlined.full.mb-16', {
    type: 'button',
    disabled: !canAddMore,
    onclick: () => {
      const nextId = Math.max(0, ...form.subTransactions.map((s) => s.id)) + 1;
      const firstSplit = form.subTransactions[0];
      const inheritedGstPercent = form.globalGstPercent !== ''
        ? form.globalGstPercent
        : (firstSplit?.gstPercentage || '');

      form.subTransactions = [...form.subTransactions, subTransaction(nextId, {
        category: firstSplit?.category ?? form.mainCategory,
        subcategory: firstSplit?.subcategory ?? form.mainSubcategory,
        labels: firstSplit ? [...firstSplit.labels] : [...form.selectedLabels],
        gstPercentage: inheritedGstPercent,
      })];
      form.expandedSplitId = nextId;
      rerender();
    },
  }, '+ Add split'));

  return wrap;
}

// ── Inline "+ Add New" prompts ───────────────────────────────────────────────

function promptAddCategory(ctx, apply) {
  editDialog({
    title: 'New category', fieldLabel: 'Category name', initialValue: '',
    onConfirm: (value) => {
      if (value.trim()) {
        update((d) => {
          d.categories.push(value);
          d.subcategoriesMap[value] = [];
        });
        apply(value);
      }
      ctx.rerender();
    },
    onDismiss: () => ctx.rerender(),
  });
}

function promptAddSubcategory(ctx, category, apply) {
  editDialog({
    title: `New subcategory in ${category}`, fieldLabel: 'Subcategory name', initialValue: '',
    onConfirm: (value) => {
      if (value.trim() && category) {
        update((d) => {
          if (!d.subcategoriesMap[category]) d.subcategoriesMap[category] = [];
          d.subcategoriesMap[category].push(value);
        });
        apply(value);
      }
      ctx.rerender();
    },
    onDismiss: () => ctx.rerender(),
  });
}

function promptAddLabel(ctx, apply) {
  editDialog({
    title: 'New label', fieldLabel: 'Label name', initialValue: '',
    onConfirm: (value) => {
      if (value.trim()) {
        update((d) => { d.labels.push(value); });
        apply(value);
      }
      ctx.rerender();
    },
    onDismiss: () => ctx.rerender(),
  });
}

// ── Store / location suggestion field ────────────────────────────────────────

function suggestField({ label, value, suggestionsFor, oninput, onpick, openOnFocus = false }) {
  const host = h('div', { style: { position: 'relative' } });
  let menu = null;

  const closeMenu = () => { if (menu) { menu.remove(); menu = null; } };

  const openMenu = (current) => {
    closeMenu();
    const suggestions = suggestionsFor(current);
    if (suggestions.length === 0) return;
    menu = h('div.suggest-menu', {}, ...suggestions.map((s) => h('button.suggest-item', {
      type: 'button',
      onmousedown: (e) => e.preventDefault(), // keep focus so blur doesn't race the click
      onclick: () => { closeMenu(); onpick(s); },
    }, s)));
    host.appendChild(menu);
  };

  const field = textField({
    label,
    value,
    oninput: (next) => {
      oninput(next);
      if (next.trim()) openMenu(next); else closeMenu();
    },
  });

  field.inputEl.addEventListener('focus', () => {
    if (openOnFocus) openMenu(field.inputEl.value);
  });
  field.inputEl.addEventListener('blur', () => setTimeout(closeMenu, 120));

  host.appendChild(field);
  return host;
}

// ── Calculator ───────────────────────────────────────────────────────────────

const calc = { display: '0', previous: '', operation: null, newNumber: true };

function calcButton(ctx, getValue, targetField, splitIndex = null) {
  return h('button.icon-btn', {
    type: 'button',
    'aria-label': 'Calculator',
    style: { color: 'var(--primary)' },
    onclick: (e) => {
      e.stopPropagation();
      calc.display = getValue() || '0';
      openCalculator(ctx, { target: targetField, splitIndex });
    },
  }, icon('calculate'));
}

function openCalculator(ctx, { target, splitIndex = null }) {
  customDialog((api) => {
    const display = h('div.headline-small', {
      style: { textAlign: 'right', padding: '0 4px 12px', overflow: 'hidden', whiteSpace: 'nowrap' },
    }, calc.display);

    const refresh = () => { display.textContent = calc.display; };

    const onDigit = (digit) => {
      if (calc.newNumber) { calc.display = digit; calc.newNumber = false; }
      else calc.display = calc.display === '0' ? digit : calc.display + digit;
      refresh();
    };
    const onOperation = (op) => {
      calc.previous = calc.display;
      calc.operation = op;
      calc.newNumber = true;
    };
    const onEqual = () => {
      if (calc.operation && calc.previous !== '') {
        const prev = toDoubleOrNull(calc.previous) ?? 0;
        const curr = toDoubleOrNull(calc.display) ?? 0;
        const result = calc.operation === '+' ? prev + curr
          : calc.operation === '-' ? prev - curr
            : calc.operation === '*' ? prev * curr
              : calc.operation === '/' ? (curr !== 0 ? prev / curr : 0)
                : curr;
        calc.display = fmt2Trim(result);
        calc.operation = null;
        calc.previous = '';
        calc.newNumber = true;
        refresh();
      }
    };
    const onClear = () => {
      calc.display = '0';
      calc.previous = '';
      calc.operation = null;
      calc.newNumber = true;
      refresh();
    };

    const key = (label, { emphasis = false, danger = false, onclick }) => h('button.btn', {
      type: 'button',
      class: danger ? 'destructive' : (emphasis ? '' : 'tonal'),
      style: {
        flex: '1', minHeight: '44px', padding: '0', borderRadius: '14px',
        ...(danger ? { background: 'var(--error-container)', color: 'var(--on-error-container)' } : {}),
        ...(!emphasis && !danger ? { background: 'var(--surface-6)', color: 'var(--on-surface)' } : {}),
      },
      onclick,
    }, label);

    const rows = [['7', '8', '9', '/'], ['4', '5', '6', '*'], ['1', '2', '3', '-']];
    const grid = h('div', {});
    for (const row of rows) {
      grid.appendChild(h('div.row.gap-6', { style: { marginBottom: '6px' } },
        ...row.map((label) => {
          const isOp = ['+', '-', '*', '/'].includes(label);
          return key(label, {
            emphasis: isOp,
            onclick: () => (isOp ? onOperation(label) : onDigit(label)),
          });
        }),
      ));
    }
    grid.appendChild(h('div.row.gap-6', {},
      key('C', { danger: true, onclick: onClear }),
      key('0', { onclick: () => onDigit('0') }),
      key('.', { onclick: () => onDigit('.') }),
      key('+', { emphasis: true, onclick: () => onOperation('+') }),
      key('=', { emphasis: true, onclick: onEqual }),
      key('INS', {
        emphasis: true,
        onclick: () => { api.close(); insertCalcValue(ctx, target, splitIndex); },
      }),
    ));

    return h('div', { style: { width: 'min(320px, 100%)' } }, display, grid);
  }, { onDismiss: () => ctx.rerender() });
}

/** onCalcInsert — writes the calculator result into the targeted field. */
function insertCalcValue(ctx, target, splitIndex) {
  const valueStr = calc.display;

  if (target === 'MAIN_BASE') {
    const next = recalcFromBase(form, valueStr);
    form.baseAmount = valueStr;
    form.gstPercentage = next.gstPercentage;
    form.gstAmount = next.gstAmount;
    form.totalAmount = next.total;
  } else if (target === 'MAIN_GST_PCT') {
    const next = recalcFromPercent(form, valueStr);
    form.gstPercentage = valueStr;
    form.gstAmount = next.gstAmount;
    form.totalAmount = next.total;
  } else if (target === 'MAIN_GST_AMT') {
    const next = recalcFromGstAmount(form, valueStr);
    form.gstAmount = valueStr;
    form.gstPercentage = next.gstPercentage;
    form.totalAmount = next.total;
  } else if (target && target.startsWith('SPLIT_') && splitIndex !== null
      && splitIndex >= 0 && splitIndex < form.subTransactions.length) {
    const subTx = form.subTransactions[splitIndex];
    let changes;
    if (target === 'SPLIT_BASE') {
      const next = recalcFromBase(subTx, valueStr);
      changes = {
        baseAmount: valueStr,
        gstPercentage: next.gstPercentage,
        gstAmount: next.gstAmount,
        amount: next.total,
      };
    } else if (target === 'SPLIT_GST_PCT') {
      const next = recalcFromPercent(subTx, valueStr);
      changes = { gstPercentage: valueStr, gstAmount: next.gstAmount, amount: next.total };
      form.globalGstPercent = '';
      form.totalGstPaid = '';
    } else {
      const next = recalcFromGstAmount(subTx, valueStr);
      changes = { gstPercentage: next.gstPercentage, gstAmount: valueStr, amount: next.total };
      form.globalGstPercent = '';
      form.totalGstPaid = '';
    }
    const updated = [...form.subTransactions];
    updated[splitIndex] = { ...subTx, ...changes };
    form.subTransactions = reapplyGlobalGst(updated);
  } else if (form.isSplit && form.expandedSplitId != null) {
    // No explicit target: fill the expanded split's base amount.
    const index = form.subTransactions.findIndex((s) => s.id === form.expandedSplitId);
    if (index >= 0) {
      const subTx = form.subTransactions[index];
      const baseVal = toDoubleOrNull(valueStr) ?? 0;
      const percentVal = toDoubleOrNull(subTx.gstPercentage) ?? 0;
      const calculatedGst = baseVal * (percentVal / 100);
      const totalVal = baseVal + calculatedGst;
      const updated = [...form.subTransactions];
      updated[index] = {
        ...subTx,
        baseAmount: valueStr,
        gstAmount: calculatedGst > 0 ? fmt2Trim(calculatedGst) : '',
        amount: totalVal > 0 ? fmt2Trim(totalVal) : '',
      };
      form.subTransactions = reapplyGlobalGst(updated);
    }
  } else {
    const baseVal = toDoubleOrNull(valueStr) ?? 0;
    const percentVal = toDoubleOrNull(form.gstPercentage) ?? 0;
    const calculatedGst = baseVal * (percentVal / 100);
    const totalVal = baseVal + calculatedGst;
    form.baseAmount = valueStr;
    form.gstAmount = calculatedGst > 0 ? fmt2Trim(calculatedGst) : '';
    form.totalAmount = totalVal > 0 ? fmt2Trim(totalVal) : '';
  }

  ctx.rerender();
}

// ── Leaving the screen ───────────────────────────────────────────────────────

/** Back / bottom-nav away from an edited form: Save, Save draft or Discard. */
export function requestBack(ctx, proceed) {
  const leave = proceed || (() => ctx.navigate('Dashboard'));
  if (!hasChanges()) { leave(); return; }

  customDialog((api) => h('div', {},
    h('div.headline-small.dialog-title', {}, 'Unsaved changes'),
    h('div.body-medium', {}, 'Do you want to save before leaving?'),
    h('div.dialog-actions', {},
      h('button.btn.text', {
        type: 'button',
        onclick: () => { api.close(); leave(); },
      }, 'Discard'),
      h('button.btn.text', {
        type: 'button',
        onclick: () => { api.close(); performSave(ctx, true); },
      }, 'Save draft'),
      h('button.btn', {
        type: 'button',
        onclick: () => { api.close(); performSave(ctx, false); },
      }, 'Save'),
    ),
  ));
}

export const entryForm = form;
