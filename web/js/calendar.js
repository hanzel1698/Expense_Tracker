// ── Aurora calendar ───────────────────────────────────────────────────────────
// Port of ui/modern/components/AuroraCalendar.kt — month navigation, per-day
// spend badges, day selection and a long-press action dialog.

import { h, icon, onLongPress } from './dom.js';
import { card, customDialog } from './components.js';
import {
  today, monthFull, ymYear, ymMonth, ymAtDay, ymAtEnd, ymPlusMonths,
  dayOfWeek, plusDays, minusDays, fmtDate, monthValue,
} from './util.js';

/**
 * @param {object} opts
 *  - expensesMap: { [isoDate]: totalAmount }
 *  - viewedMonth: "YYYY-MM"
 *  - onMonthChanged(ym), onViewExpensesForDate(iso), onNewExpenseForDate(iso)
 *  - selectedDate: iso|null, onSelectDate(iso)
 */
export function auroraCalendar({
  expensesMap = {}, viewedMonth, onMonthChanged,
  onViewExpensesForDate, onNewExpenseForDate,
  selectedDate = null, onSelectDate,
}) {
  const todayIso = today();

  const head = h('div.cal-head', {},
    h('button.icon-btn', {
      type: 'button', 'aria-label': 'Previous month',
      style: { color: 'var(--primary)' },
      onclick: () => onMonthChanged(ymPlusMonths(viewedMonth, -1)),
    }, icon('arrowLeft')),
    h('div.title-medium', {}, `${monthFull(ymMonth(viewedMonth))} ${ymYear(viewedMonth)}`),
    h('button.icon-btn', {
      type: 'button', 'aria-label': 'Next month',
      style: { color: 'var(--primary)' },
      onclick: () => onMonthChanged(ymPlusMonths(viewedMonth, 1)),
    }, icon('arrowRight')),
  );

  const weekdays = h('div.cal-weekdays', {},
    ...['S', 'M', 'T', 'W', 'T', 'F', 'S'].map((d) => h('span', {}, d)));

  const grid = h('div.cal-grid');

  // The grid runs from the Sunday on/before the 1st to the Saturday on/after the last.
  const first = ymAtDay(viewedMonth, 1);
  const last = ymAtEnd(viewedMonth);
  const startOfGrid = minusDays(first, dayOfWeek(first) % 7);
  const endOfGrid = plusDays(last, (6 - (dayOfWeek(last) % 7)));

  const month = ymMonth(viewedMonth);
  let cursor = startOfGrid;
  while (cursor <= endOfGrid) {
    const iso = cursor;
    const spend = expensesMap[iso];
    const hasSpend = spend !== undefined && spend > 0;
    const isToday = iso === todayIso;
    const isSelected = iso === selectedDate;
    const isCurrentMonth = monthValue(iso) === month;

    const cell = h('button.cal-day', {
      type: 'button',
      class: [
        isSelected ? 'selected' : '',
        isToday ? 'today' : '',
        isCurrentMonth ? '' : 'other-month',
      ].filter(Boolean).join(' '),
      onclick: () => onSelectDate && onSelectDate(isSelected ? null : iso),
    },
      h('span.cal-num', {}, String(Number(iso.slice(8, 10)))),
      hasSpend
        ? h('span.cal-amt', {}, `₹${Math.round(spend)}`)
        : h('span.cal-spacer'),
      h('span.cal-dot', { class: hasSpend && !isSelected ? '' : 'hidden-dot' }),
    );

    onLongPress(cell, () => openDayActions(iso, onViewExpensesForDate, onNewExpenseForDate));
    grid.appendChild(cell);
    cursor = plusDays(cursor, 1);
  }

  return card({}, h('div', { style: { paddingBottom: '8px' } }, head, weekdays, grid));
}

/** Long-press action sheet: View expenses / New expense. */
function openDayActions(iso, onView, onNew) {
  customDialog((api) => h('div', {},
    h('div.headline-small.dialog-title', {}, fmtDate(iso)),
    h('div.col.gap-10', {},
      h('button.btn.full', {
        type: 'button',
        onclick: () => { api.close(); onView && onView(iso); },
      }, 'View expenses'),
      h('button.btn.tonal.full', {
        type: 'button',
        onclick: () => { api.close(); onNew && onNew(iso); },
      }, 'New expense'),
    ),
    h('div.dialog-actions', {},
      h('button.btn.text', { type: 'button', onclick: () => api.close() }, 'Cancel'),
    ),
  ));
}
