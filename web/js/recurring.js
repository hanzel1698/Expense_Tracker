// ── Recurring expense engine ──────────────────────────────────────────────────
// Direct port of data/RecurringExpenseEngine.kt. Runs once on load: for every
// active template it generates the occurrences due since the template was last
// generated (or its start date) up to today, and stamps lastGeneratedDate.

import { RecurrenceFrequency, createExpense } from './model.js';
import {
  today as todayIso, plusDays, plusWeeks, isAfter, isBefore, clamp,
  dayOfWeek, monthValue, year, lengthOfMonth, ymOf, ymAtDay, ymPlusMonths, ymIsAfter,
} from './util.js';

/** Every day from `from` to `to` inclusive. */
function dailyDates(from, to) {
  const out = [];
  let cur = from;
  while (!isAfter(cur, to)) {
    out.push(cur);
    cur = plusDays(cur, 1);
  }
  return out;
}

/** Every occurrence of `dow` (1=Mon … 7=Sun) from `from` to `to` inclusive. */
function weeklyDates(dow, from, to) {
  const target = clamp(dow, 1, 7);
  let cur = from;
  while (dayOfWeek(cur) !== target) cur = plusDays(cur, 1);
  const out = [];
  while (!isAfter(cur, to)) {
    out.push(cur);
    cur = plusWeeks(cur, 1);
  }
  return out;
}

/** The nth day of every month in range, clamped to each month's length. */
function monthlyDates(dom, from, to) {
  const out = [];
  let ym = ymOf(from);
  const endYm = ymOf(to);
  while (!ymIsAfter(ym, endYm)) {
    const day = clamp(dom, 1, lengthOfMonth(Number(ym.slice(0, 4)), Number(ym.slice(5, 7))));
    const dateIso = ymAtDay(ym, day);
    if (!isBefore(dateIso, from) && !isAfter(dateIso, to)) out.push(dateIso);
    ym = ymPlusMonths(ym, 1);
  }
  return out;
}

/** The nth day of the target month, once per year in range. */
function yearlyDates(dom, targetMonth, from, to) {
  const out = [];
  const month = clamp(targetMonth, 1, 12);
  for (let y = year(from); y <= year(to); y++) {
    const day = clamp(dom, 1, lengthOfMonth(y, month));
    const dateIso = `${String(y).padStart(4, '0')}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
    if (!isBefore(dateIso, from) && !isAfter(dateIso, to)) out.push(dateIso);
  }
  return out;
}

function dueDates(re, from, to) {
  switch (re.frequency) {
    case RecurrenceFrequency.DAILY: return dailyDates(from, to);
    case RecurrenceFrequency.WEEKLY: return weeklyDates(re.dayOfPeriod, from, to);
    case RecurrenceFrequency.YEARLY:
      return yearlyDates(
        re.dayOfPeriod,
        re.monthOfPeriod > 0 ? re.monthOfPeriod : monthValue(re.startDate),
        from, to,
      );
    case RecurrenceFrequency.MONTHLY:
    default:
      return monthlyDates(re.dayOfPeriod, from, to);
  }
}

/**
 * @returns {{ newExpenses: object[], updatedTemplates: object[] }}
 *   newExpenses — occurrences to add to the expense list
 *   updatedTemplates — templates with lastGeneratedDate stamped
 */
export function generate(recurringExpenses, today = todayIso()) {
  const newExpenses = [];
  const updatedTemplates = [];

  for (const re of recurringExpenses) {
    if (!re.isActive) {
      updatedTemplates.push(re);
      continue;
    }

    const generateFrom = re.lastGeneratedDate ? plusDays(re.lastGeneratedDate, 1) : re.startDate;
    const effectiveTo = re.endDate && isBefore(re.endDate, today) ? re.endDate : today;

    if (isAfter(generateFrom, effectiveTo)) {
      updatedTemplates.push(re);
      continue;
    }

    const dates = dueDates(re, generateFrom, effectiveTo);

    for (const date of dates) {
      newExpenses.push(createExpense({
        date,
        storeName: re.storeName,
        amount: re.amount,
        category: re.category,
        subcategory: re.subcategory,
        itemDescription: re.itemDescription || re.name,
        labels: re.labels,
        paymentMode: re.paymentMode,
        paidVia: re.paidVia,
        notes: `[Recurring: ${re.name}]${re.notes ? `\n${re.notes}` : ''}`,
      }));
    }

    updatedTemplates.push(dates.length > 0 ? { ...re, lastGeneratedDate: today } : re);
  }

  return { newExpenses, updatedTemplates };
}

/** Convenience wrapper matching ModernMainActivity.applyRecurringEngineResult. */
export function applyGeneratedResult(data, newExpenses, updatedTemplates) {
  if (newExpenses.length > 0) data.expenses.push(...newExpenses);
  for (const updated of updatedTemplates) {
    const index = data.recurringExpenses.findIndex((r) => r.id === updated.id);
    if (index >= 0 && data.recurringExpenses[index].lastGeneratedDate !== updated.lastGeneratedDate) {
      data.recurringExpenses[index] = updated;
    }
  }
}
