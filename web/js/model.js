// ── Data model ────────────────────────────────────────────────────────────────
// Web port of model/Expense.kt, model/RecurringExpense.kt and data/AppData.
// Field names and JSON shape match the Android app exactly, so a backup written
// by either side loads in the other.

import { uuid, today, isValidDate, toDoubleOrNull } from './util.js';

export const RecurrenceFrequency = {
  DAILY: 'DAILY',
  WEEKLY: 'WEEKLY',
  MONTHLY: 'MONTHLY',
  YEARLY: 'YEARLY',
};

export function createExpense(props = {}) {
  return {
    id: props.id || uuid(),
    groupId: props.groupId || uuid(),
    date: props.date || today(),
    storeName: props.storeName ?? '',
    location: props.location ?? '',
    amount: props.amount ?? 0,
    category: props.category ?? '',
    subcategory: props.subcategory ?? '',
    itemDescription: props.itemDescription ?? '',
    labels: props.labels ?? [],
    quantity: props.quantity ?? null,
    unit: props.unit ?? null,
    notes: props.notes ?? '',
    paymentMode: props.paymentMode ?? '',
    paidVia: props.paidVia ?? '',
    isDraft: props.isDraft ?? false,
    baseAmount: props.baseAmount ?? null,
    gstPercentage: props.gstPercentage ?? null,
    gstAmount: props.gstAmount ?? null,
  };
}

export function createRecurringExpense(props = {}) {
  return {
    id: props.id || uuid(),
    name: props.name ?? '',
    storeName: props.storeName ?? '',
    amount: props.amount ?? 0,
    category: props.category ?? '',
    subcategory: props.subcategory ?? '',
    itemDescription: props.itemDescription ?? '',
    labels: props.labels ?? [],
    paymentMode: props.paymentMode ?? '',
    paidVia: props.paidVia ?? '',
    frequency: props.frequency ?? RecurrenceFrequency.MONTHLY,
    dayOfPeriod: props.dayOfPeriod ?? 1,
    startDate: props.startDate || today(),
    notes: props.notes ?? '',
    isActive: props.isActive ?? true,
    lastGeneratedDate: props.lastGeneratedDate ?? null,
    monthOfPeriod: props.monthOfPeriod ?? 0,
    endDate: props.endDate ?? null,
  };
}

/** Defaults from data/AppData in DataRepository.kt. */
export function defaultAppData() {
  return {
    expenses: [],
    categories: ['Food', 'Utilities', 'Entertainment', 'Transport', 'Health', 'Shopping'],
    subcategoriesMap: {
      Food: ['Groceries', 'Coffee', 'Snacks', 'Dining Out'],
      Utilities: ['Internet', 'Electricity', 'Gas'],
      Entertainment: ['Movies', 'Music', 'Games'],
      Transport: ['Public Transit', 'Fuel', 'Parking'],
      Health: ['Medicine', 'Doctor', 'Gym'],
      Shopping: ['Clothing', 'Electronics', 'Home'],
    },
    labels: ['Personal', 'Business', 'Urgent', 'Recurring', 'One-time'],
    paymentModes: ['Cash', 'Credit Card', 'Debit Card', 'UPI', 'Net Banking', 'Wallet'],
    paidVia: ['Google Pay', 'PhonePe', 'Paytm', 'Amazon Pay', 'BHIM', 'Other'],
    categoryBudgets: {
      Food: 200, Utilities: 150, Entertainment: 100,
      Transport: 100, Health: 100, Shopping: 150,
    },
    subcategoryBudgets: {},
    storeHistory: [],
    storeLocationHistory: {},
    isDarkTheme: false,
    recurringExpenses: [],
  };
}

// ── Lenient parsing (mirrors the Gson TypeAdapters' tolerance) ───────────────

function str(v, fallback = '') {
  return typeof v === 'string' ? v : (v === null || v === undefined ? fallback : String(v));
}

function num(v) {
  if (typeof v === 'number') return Number.isFinite(v) ? v : null;
  return toDoubleOrNull(v);
}

function date(v, fallback) {
  const s = str(v, '');
  if (!s || s === '0000-00-00' || !isValidDate(s)) return fallback;
  return s;
}

function strList(v) {
  return Array.isArray(v) ? v.filter((x) => typeof x === 'string') : [];
}

export function parseExpense(raw) {
  if (!raw || typeof raw !== 'object') return null;
  return createExpense({
    id: str(raw.id) || uuid(),
    groupId: str(raw.groupId) || uuid(),
    date: date(raw.date, today()),
    storeName: str(raw.storeName),
    location: str(raw.location),
    amount: num(raw.amount) ?? 0,
    category: str(raw.category),
    subcategory: str(raw.subcategory),
    itemDescription: str(raw.itemDescription),
    labels: strList(raw.labels),
    quantity: num(raw.quantity),
    unit: raw.unit === null || raw.unit === undefined ? null : str(raw.unit),
    notes: str(raw.notes),
    paymentMode: str(raw.paymentMode),
    paidVia: str(raw.paidVia),
    isDraft: raw.isDraft === true,
    baseAmount: num(raw.baseAmount),
    gstPercentage: num(raw.gstPercentage),
    gstAmount: num(raw.gstAmount),
  });
}

export function parseRecurringExpense(raw) {
  if (!raw || typeof raw !== 'object') return null;
  const freq = str(raw.frequency).toUpperCase();
  return createRecurringExpense({
    id: str(raw.id) || uuid(),
    name: str(raw.name),
    storeName: str(raw.storeName),
    amount: num(raw.amount) ?? 0,
    category: str(raw.category),
    subcategory: str(raw.subcategory),
    itemDescription: str(raw.itemDescription),
    labels: strList(raw.labels),
    paymentMode: str(raw.paymentMode),
    paidVia: str(raw.paidVia),
    frequency: RecurrenceFrequency[freq] || RecurrenceFrequency.MONTHLY,
    dayOfPeriod: Math.trunc(num(raw.dayOfPeriod) ?? 1) || 1,
    startDate: date(raw.startDate, today()),
    notes: str(raw.notes),
    isActive: raw.isActive !== false,
    lastGeneratedDate: raw.lastGeneratedDate ? date(raw.lastGeneratedDate, null) : null,
    monthOfPeriod: Math.trunc(num(raw.monthOfPeriod) ?? 0) || 0,
    endDate: raw.endDate ? date(raw.endDate, null) : null,
  });
}

function plainStringMap(v, mapValue) {
  const out = {};
  if (v && typeof v === 'object' && !Array.isArray(v)) {
    for (const [key, value] of Object.entries(v)) {
      const mapped = mapValue(value);
      if (mapped !== null) out[key] = mapped;
    }
  }
  return out;
}

/** Parse an AppData JSON payload (from localStorage, a backup file, or Drive). */
export function parseAppData(raw) {
  const defaults = defaultAppData();
  if (!raw || typeof raw !== 'object') return defaults;

  const expenses = Array.isArray(raw.expenses)
    ? raw.expenses.map(parseExpense).filter(Boolean)
    : defaults.expenses;
  const recurringExpenses = Array.isArray(raw.recurringExpenses)
    ? raw.recurringExpenses.map(parseRecurringExpense).filter(Boolean)
    : defaults.recurringExpenses;

  return {
    expenses,
    categories: Array.isArray(raw.categories) ? strList(raw.categories) : defaults.categories,
    subcategoriesMap: raw.subcategoriesMap
      ? plainStringMap(raw.subcategoriesMap, (v) => strList(v))
      : defaults.subcategoriesMap,
    labels: Array.isArray(raw.labels) ? strList(raw.labels) : defaults.labels,
    paymentModes: Array.isArray(raw.paymentModes) ? strList(raw.paymentModes) : defaults.paymentModes,
    paidVia: Array.isArray(raw.paidVia) ? strList(raw.paidVia) : defaults.paidVia,
    categoryBudgets: raw.categoryBudgets
      ? plainStringMap(raw.categoryBudgets, (v) => num(v))
      : defaults.categoryBudgets,
    subcategoryBudgets: raw.subcategoryBudgets
      ? plainStringMap(raw.subcategoryBudgets, (v) => num(v))
      : defaults.subcategoryBudgets,
    storeHistory: Array.isArray(raw.storeHistory) ? strList(raw.storeHistory) : defaults.storeHistory,
    storeLocationHistory: raw.storeLocationHistory
      ? plainStringMap(raw.storeLocationHistory, (v) => strList(v))
      : defaults.storeLocationHistory,
    isDarkTheme: raw.isDarkTheme === true,
    recurringExpenses,
  };
}
