// ── CSV import / export ───────────────────────────────────────────────────────
// Port of AppCommon.kt (parseCsvLine, cleanCsvField, parseFlexibleDate,
// CSV_TEMPLATE_CONTENT) plus the import pipeline from ModernMainActivity.

import { RecurrenceFrequency, createExpense, createRecurringExpense } from './model.js';
import { generate } from './recurring.js';
import { uuid, today, dayOfMonth, dayOfWeek, toDoubleOrNull } from './util.js';

export function cleanCsvField(field) {
  let s = field.trim();
  if (s.startsWith('"') && s.endsWith('"') && s.length >= 2) {
    s = s.slice(1, -1).trim();
  }
  return s;
}

export function parseCsvLine(line) {
  const result = [];
  let inQuotes = false;
  let current = '';
  for (const c of line) {
    if (c === '"') {
      inQuotes = !inQuotes;
    } else if (c === ',' && !inQuotes) {
      result.push(cleanCsvField(current));
      current = '';
    } else {
      current += c;
    }
  }
  result.push(cleanCsvField(current));
  return result;
}

export function parseFlexibleDate(dateStr) {
  const trimmed = String(dateStr || '').trim();
  try {
    if (trimmed.includes('-')) {
      const [y, m, d] = trimmed.split('-').map(Number);
      if (!y || !m || !d) return today();
      return `${String(y).padStart(4, '0')}-${String(m).padStart(2, '0')}-${String(d).padStart(2, '0')}`;
    }
    if (trimmed.includes('/')) {
      const parts = trimmed.split('/');
      const [a, b, c] = parts;
      // YYYY/MM/DD when the first field is a 4-digit year, else DD/MM/YYYY
      const [y, m, d] = a.length === 4
        ? [Number(a), Number(b), Number(c)]
        : [Number(c), Number(b), Number(a)];
      if (!y || !m || !d) return today();
      return `${String(y).padStart(4, '0')}-${String(m).padStart(2, '0')}-${String(d).padStart(2, '0')}`;
    }
    return today();
  } catch {
    return today();
  }
}

export const CSV_TEMPLATE_CONTENT = `Date (YYYY-MM-DD),Store Name,Amount,Category,Subcategory,Item Description,Labels (comma-separated),Quantity,Unit,Notes,Payment Mode,Paid Via,Split ID,Is Recurring (Yes/No),Recurring Frequency (Daily/Weekly/Monthly/Yearly),Recurring End Date (YYYY-MM-DD)
2026-06-01,Groceries - Target,45.50,Food,Groceries,Weekly grocery shopping,"Personal, Urgent",1,Bag,Weekly milk and eggs,Credit Card,Google Pay,,No,,
2026-06-02,Costco,100.00,Food,Groceries,Food supplies,Personal,,,,Credit Card,Google Pay,SplitA,No,,
2026-06-02,Costco,50.00,Shopping,Clothing,New shirt,Personal,,,,Credit Card,Google Pay,SplitA,No,,
2026-06-03,Gym Membership,30.00,Health,Gym,Monthly Gym fee,Personal,,,,Net Banking,Other,,Yes,Weekly,2026-12-31`;

/** Rows exported from the current data set, in the template's column order. */
export function expensesToCsv(expenses) {
  const escape = (v) => {
    const s = v === null || v === undefined ? '' : String(v);
    return /[",\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
  };
  const header = CSV_TEMPLATE_CONTENT.split('\n')[0];
  const rows = expenses.map((e) => [
    e.date, e.storeName, e.amount, e.category, e.subcategory, e.itemDescription,
    (e.labels || []).join(', '), e.quantity ?? '', e.unit ?? '', e.notes,
    e.paymentMode, e.paidVia, e.groupId, 'No', '', '',
  ].map(escape).join(','));
  return [header, ...rows].join('\n');
}

/**
 * Imports a CSV file into `data`, mutating it exactly the way
 * ModernMainActivity's importLauncher does.
 * @returns {{ importCount: number, recurringCount: number }}
 */
export function importCsv(csvText, data) {
  const lines = csvText.split(/\r?\n/);
  if (lines.length === 0) throw new Error('CSV file is empty');

  const rows = lines.slice(1);
  let importCount = 0;
  let recurringCount = 0;

  const newExpenses = [];
  const newRecurringConfigs = [];
  const splitGroupMap = new Map();

  for (const line of rows) {
    if (!line.trim()) continue;
    const fields = parseCsvLine(line);
    if (fields.length < 3) continue;

    const dateStr = fields[0];
    const storeName = fields[1];
    const amountStr = fields[2];
    if (!dateStr.trim() || !storeName.trim() || !amountStr.trim()) continue;

    const date = parseFlexibleDate(dateStr);
    const amount = toDoubleOrNull(amountStr) ?? 0;

    const category = fields[3] ?? '';
    const subcategory = fields[4] ?? '';
    const itemDesc = fields[5] ?? '';
    const labelsStr = fields[6] ?? '';
    const quantityStr = fields[7] ?? '';
    const unitStr = fields[8] ?? '';
    const notesStr = fields[9] ?? '';
    const paymentMode = fields[10] ?? '';
    const paidViaStr = fields[11] ?? '';
    const splitId = fields[12] ?? '';
    const isRecurringStr = fields[13] ?? '';
    const recurringFreq = fields[14] ?? '';
    const recurringEndDateStr = fields[15] ?? '';

    const rowLabels = labelsStr.trim()
      ? labelsStr.split(',').map((s) => s.trim()).filter(Boolean)
      : [];

    const quantity = toDoubleOrNull(quantityStr);
    const unit = unitStr.trim() ? unitStr : null;

    // Grow the taxonomy from the imported rows, like the Android importer.
    if (category.trim() && !data.categories.includes(category)) {
      data.categories.push(category);
      data.subcategoriesMap[category] = [];
    }
    if (category.trim() && subcategory.trim()) {
      const subs = data.subcategoriesMap[category];
      if (subs && !subs.includes(subcategory)) subs.push(subcategory);
    }
    for (const label of rowLabels) {
      if (label.trim() && !data.labels.includes(label)) data.labels.push(label);
    }
    if (paymentMode.trim() && !data.paymentModes.includes(paymentMode)) {
      data.paymentModes.push(paymentMode);
    }
    if (paidViaStr.trim() && !data.paidVia.includes(paidViaStr)) {
      data.paidVia.push(paidViaStr);
    }

    const flag = isRecurringStr.trim().toLowerCase();
    const isRecurring = flag === 'yes' || flag === 'true';

    if (isRecurring) {
      const freqKey = recurringFreq.trim().toLowerCase();
      const frequency = {
        daily: RecurrenceFrequency.DAILY,
        weekly: RecurrenceFrequency.WEEKLY,
        monthly: RecurrenceFrequency.MONTHLY,
        yearly: RecurrenceFrequency.YEARLY,
      }[freqKey] || RecurrenceFrequency.MONTHLY;

      const dayOfPeriod = frequency === RecurrenceFrequency.WEEKLY
        ? dayOfWeek(date)
        : dayOfMonth(date);
      const endDate = recurringEndDateStr.trim() ? parseFlexibleDate(recurringEndDateStr) : null;

      newRecurringConfigs.push(createRecurringExpense({
        name: storeName,
        storeName,
        amount,
        category,
        subcategory,
        itemDescription: itemDesc,
        labels: rowLabels,
        notes: notesStr,
        paymentMode,
        paidVia: paidViaStr,
        frequency,
        dayOfPeriod,
        startDate: date,
        endDate,
      }));
      recurringCount++;
    } else {
      let groupId;
      if (splitId.trim()) {
        const key = `${splitId.trim()}_${dateStr.trim()}_${storeName.trim()}`;
        if (!splitGroupMap.has(key)) splitGroupMap.set(key, uuid());
        groupId = splitGroupMap.get(key);
      } else {
        groupId = uuid();
      }

      newExpenses.push(createExpense({
        groupId,
        date,
        storeName,
        amount,
        category,
        subcategory,
        itemDescription: itemDesc,
        labels: rowLabels,
        quantity,
        unit,
        notes: notesStr,
        paymentMode,
        paidVia: paidViaStr,
      }));
      importCount++;
    }
  }

  if (newExpenses.length > 0) data.expenses.push(...newExpenses);

  const { newExpenses: occurrences, updatedTemplates } = generate(newRecurringConfigs, today());
  if (occurrences.length > 0) data.expenses.push(...occurrences);
  data.recurringExpenses.push(...updatedTemplates);

  return { importCount, recurringCount };
}

// ── File helpers ─────────────────────────────────────────────────────────────

export function downloadText(filename, content, mime = 'text/plain') {
  const blob = new Blob([content], { type: `${mime};charset=utf-8` });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

export function pickFile(accept) {
  return new Promise((resolve) => {
    const input = document.createElement('input');
    input.type = 'file';
    if (accept) input.accept = accept;
    input.style.display = 'none';
    document.body.appendChild(input);
    input.addEventListener('change', () => {
      const file = input.files && input.files[0];
      input.remove();
      if (!file) { resolve(null); return; }
      const reader = new FileReader();
      reader.onload = () => resolve({ name: file.name, text: String(reader.result || '') });
      reader.onerror = () => resolve(null);
      reader.readAsText(file);
    });
    input.addEventListener('cancel', () => { input.remove(); resolve(null); });
    input.click();
  });
}
