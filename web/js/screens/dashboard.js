// ── Dashboard ─────────────────────────────────────────────────────────────────
// Port of ui/modern/screens/ModernDashboardScreen.kt — calendar, budget/balance/
// spent stats, trends (period + filters + summary + bar chart with drill-through),
// category breakdown, drafts shortcut with badge, quick-add and theme toggle.

import { h, icon } from '../dom.js';
import {
  card, sectionHeader, monthSwitcher, dropdown, multiSelectDropdown,
  chip, confirmDialog, dateRangePickerDialog,
} from '../components.js';
import { auroraCalendar } from '../calendar.js';
import { barChart, horizontalBarChart } from '../charts.js';
import { data } from '../store.js';
import { TrendDimension } from '../nav.js';
import {
  today, ymNow, ymOf, ymAtDay, ymAtEnd, ymPlusMonths, ymMinusMonths,
  ymMinusYears, ymWithMonth, ymMonth, ymYear, monthFull, monthShort, monthNameShort,
  dowShort, dayOfWeek, dayOfMonth, plusDays, minusDays, daysBetween, inRange,
  fmt0, sumBy, minDate, fmtDayMonth, fmtYearMonth, fmtDate,
} from '../util.js';

const TIME_PERIODS = [
  'Selected Month', 'This Week', 'Last Week', 'This Quarter',
  'Last Quarter', 'This Year', 'Last Year', 'Custom',
];

/** Screen-local state — the Compose `remember { }` equivalents. */
const state = {
  viewedMonth: ymNow(),
  trendsMonth: ymNow(),
  selectedTimePeriod: 'Selected Month',
  customStartDate: null,
  customEndDate: null,
  selectedCategories: new Set(),
  selectedSubcategories: new Set(),
  selectedLabels: new Set(),
  showFilterOptions: false,
  selectedCategoryForHorizontalChart: null,
  calendarSelectedDate: null,
};

// ── Date-range helpers (identical logic to the Compose dashboard) ────────────

function getDateRangeForPeriod(period) {
  const now = today();
  const tm = state.trendsMonth;

  switch (period) {
    case 'This Week': {
      const offset = dayOfWeek(now) % 7; // weeks start on Sunday
      return [minusDays(now, offset), plusDays(now, 6 - offset)];
    }
    case 'Last Week': {
      const offset = dayOfWeek(now) % 7;
      return [minusDays(now, offset + 7), minusDays(now, offset + 1)];
    }
    case 'Selected Month':
      return [ymAtDay(tm, 1), ymAtEnd(tm)];
    case 'This Quarter': {
      const startMonth = Math.floor((ymMonth(tm) - 1) / 3) * 3 + 1;
      return [ymAtDay(ymWithMonth(tm, startMonth), 1), ymAtEnd(ymWithMonth(tm, startMonth + 2))];
    }
    case 'Last Quarter': {
      const anchor = ymMinusMonths(tm, 3);
      const startMonth = Math.floor((ymMonth(anchor) - 1) / 3) * 3 + 1;
      return [ymAtDay(ymWithMonth(anchor, startMonth), 1), ymAtEnd(ymWithMonth(anchor, startMonth + 2))];
    }
    case 'This Year':
      return [ymAtDay(ymWithMonth(tm, 1), 1), ymAtEnd(ymWithMonth(tm, 12))];
    case 'Last Year': {
      const anchor = ymMinusYears(tm, 1);
      return [ymAtDay(ymWithMonth(anchor, 1), 1), ymAtEnd(ymWithMonth(anchor, 12))];
    }
    case 'Custom':
      return [state.customStartDate || now, state.customEndDate || now];
    default:
      return [ymAtDay(tm, 1), ymAtEnd(tm)];
  }
}

function point(label, value, period, endDate = null, periodIsDate = false) {
  return { label, value, period, endDate, periodIsDate };
}

function generateChartData(source, period) {
  const now = today();
  const tm = state.trendsMonth;
  const totalOn = (iso) => sumBy(source.filter((e) => e.date === iso), (e) => e.amount);
  const totalBetween = (a, b) => sumBy(source.filter((e) => inRange(e.date, a, b)), (e) => e.amount);
  const totalInMonth = (ym) => sumBy(source.filter((e) => ymOf(e.date) === ym), (e) => e.amount);

  switch (period) {
    case 'This Week':
    case 'Last Week': {
      const offset = dayOfWeek(now) % 7;
      const startOfWeek = period === 'This Week' ? minusDays(now, offset) : minusDays(now, offset + 7);
      return Array.from({ length: 7 }, (_, i) => {
        const date = plusDays(startOfWeek, i);
        return point(dowShort(date), totalOn(date), date, null, true);
      });
    }
    case 'Selected Month': {
      const startOfMonth = ymAtDay(tm, 1);
      const endOfMonth = ymAtEnd(tm);
      const weeks = [];
      let currentStart = startOfMonth;
      let weekIdx = 1;
      while (currentStart <= endOfMonth) {
        // Each bucket ends on the coming Saturday (or the month end).
        const daysToSaturday = (6 - (dayOfWeek(currentStart) % 7));
        const weekEnd = minDate(plusDays(currentStart, daysToSaturday), endOfMonth);
        weeks.push([currentStart, weekEnd, `W${weekIdx}`]);
        currentStart = plusDays(weekEnd, 1);
        weekIdx++;
      }
      return weeks.map(([start, end, label]) => point(label, totalBetween(start, end), start, end, true));
    }
    case 'This Quarter':
    case 'Last Quarter': {
      const anchor = period === 'This Quarter' ? tm : ymMinusMonths(tm, 3);
      const startMonth = Math.floor((ymMonth(anchor) - 1) / 3) * 3 + 1;
      return [0, 1, 2].map((offset) => {
        const targetMonth = ymWithMonth(anchor, startMonth + offset);
        return point(monthNameShort(ymMonth(targetMonth)), totalInMonth(targetMonth), targetMonth);
      });
    }
    case 'This Year':
    case 'Last Year': {
      const anchor = period === 'This Year' ? tm : ymMinusYears(tm, 1);
      return Array.from({ length: 12 }, (_, i) => {
        const targetMonth = ymWithMonth(anchor, i + 1);
        return point(monthNameShort(i + 1), totalInMonth(targetMonth), targetMonth);
      });
    }
    case 'Custom': {
      const startDate = state.customStartDate || now;
      const endDate = state.customEndDate || now;
      const days = daysBetween(startDate, endDate);
      if (days <= 7) {
        return Array.from({ length: days + 1 }, (_, i) => {
          const date = plusDays(startDate, i);
          return point(String(dayOfMonth(date)), totalOn(date), date, null, true);
        });
      }
      if (days < 30) {
        const weeks = Math.floor(days / 7) + 1;
        return Array.from({ length: weeks }, (_, i) => {
          const weekStart = plusDays(startDate, i * 7);
          const weekEnd = minDate(plusDays(weekStart, 6), endDate);
          return point(`W${i + 1}`, totalBetween(weekStart, weekEnd), weekStart, weekEnd, true);
        });
      }
      const monthsBetween = (ymYear(ymOf(endDate)) - ymYear(ymOf(startDate))) * 12
        + (ymMonth(ymOf(endDate)) - ymMonth(ymOf(startDate))) + 1;
      return Array.from({ length: monthsBetween }, (_, i) => {
        const targetMonth = ymPlusMonths(ymOf(startDate), i);
        return point(monthNameShort(ymMonth(targetMonth)), totalInMonth(targetMonth), targetMonth);
      });
    }
    default: {
      const currentMonth = ymNow();
      return Array.from({ length: 12 }, (_, i) => {
        const targetMonth = ymMinusMonths(currentMonth, i);
        return point(monthNameShort(ymMonth(targetMonth)), totalInMonth(targetMonth), targetMonth);
      }).reverse();
    }
  }
}

// ── Screen ───────────────────────────────────────────────────────────────────

export function renderDashboard(ctx) {
  const expenses = data.expenses;
  const categories = data.categories;
  const subcategoriesMap = data.subcategoriesMap;
  const labels = data.labels;
  const budget = sumBy(categories, (c) => data.categoryBudgets[c] || 0);
  const draftCount = expenses.filter((e) => e.isDraft).length;

  const [rangeStart, rangeEnd] = getDateRangeForPeriod(state.selectedTimePeriod);
  const trendTimeFiltered = expenses.filter((e) => inRange(e.date, rangeStart, rangeEnd));
  const trendFiltered = trendTimeFiltered.filter((e) => {
    const categoryMatch = state.selectedCategories.size === 0 || state.selectedCategories.has(e.category);
    const subMatch = state.selectedSubcategories.size === 0 || state.selectedSubcategories.has(e.subcategory);
    const labelMatch = state.selectedLabels.size === 0 || e.labels.some((l) => state.selectedLabels.has(l));
    return categoryMatch && subMatch && labelMatch;
  });

  const monthlyExpenses = expenses.filter((e) => ymOf(e.date) === state.viewedMonth);
  const totalSpent = sumBy(monthlyExpenses, (e) => e.amount);

  const expensesMap = {};
  for (const e of expenses) {
    if (e.isDraft) continue;
    expensesMap[e.date] = (expensesMap[e.date] || 0) + e.amount;
  }

  const root = h('div.screen-pad');

  // ── Header ─────────────────────────────────────────────────────────────────
  root.appendChild(h('div.row.between.mb-12', {},
    h('div', {},
      h('div.headline-large', {}, 'Dashboard'),
      h('div.body-small.muted', {}, `${monthFull(ymMonth(state.viewedMonth))} overview`),
    ),
    h('div.row', { style: { gap: '4px' } },
      h('div', { style: { position: 'relative' } },
        h('button.icon-btn', {
          type: 'button', 'aria-label': 'Drafts',
          onclick: () => ctx.navigate('DraftList'),
        }, icon('drafts')),
        draftCount > 0 ? h('span.badge', {}, String(draftCount)) : null,
      ),
      h('button.icon-btn', {
        type: 'button', 'aria-label': 'Toggle theme',
        onclick: () => ctx.toggleTheme(),
      }, icon(data.isDarkTheme ? 'lightMode' : 'darkMode')),
      h('button.icon-btn.filled', {
        type: 'button', 'aria-label': 'Add expense',
        onclick: () => ctx.newExpense(today()),
      }, icon('add')),
    ),
  ));

  // ── Calendar ───────────────────────────────────────────────────────────────
  root.appendChild(auroraCalendar({
    expensesMap,
    viewedMonth: state.viewedMonth,
    selectedDate: state.calendarSelectedDate,
    onSelectDate: (iso) => { state.calendarSelectedDate = iso; ctx.rerender(); },
    onMonthChanged: (ym) => { state.viewedMonth = ym; ctx.rerender(); },
    onViewExpensesForDate: (iso) => ctx.navigateToExpenses({ date: iso }),
    onNewExpenseForDate: (iso) => ctx.newExpense(iso),
  }));

  // ── Budget / Balance / Spent ───────────────────────────────────────────────
  root.appendChild(h('div.row.gap-10', { style: { marginTop: '14px' } },
    card({ className: 'grow' }, h('div.pad-12', {},
      h('div.label-medium.muted', {}, 'Budget'),
      h('div.title-medium.ellipsis', {}, `₹${fmt0(budget)}`),
    )),
    card({ variant: 'primary-container', className: 'grow' }, h('div.pad-12', {},
      h('div.label-medium', { style: { opacity: '.75' } }, 'Balance'),
      h('div.title-medium.ellipsis', {}, `₹${fmt0(budget - totalSpent)}`),
    )),
    card({
      className: 'grow',
      onclick: () => ctx.navigateToMonthExpenses(state.viewedMonth, TrendDimension.TOTAL, null),
    }, h('div.pad-12', {},
      h('div.label-medium.muted', {}, 'Spent'),
      h('div.title-medium.ellipsis', {}, `₹${fmt0(totalSpent)}`),
    )),
  ));

  // ── Trends ─────────────────────────────────────────────────────────────────
  root.appendChild(h('div.mt-20', {}, sectionHeader('Trends', {
    trailing: monthSwitcher({
      monthLabel: monthShort(ymMonth(state.trendsMonth)),
      yearLabel: String(ymYear(state.trendsMonth)),
      onPrevious: () => {
        state.trendsMonth = ymMinusMonths(state.trendsMonth, 1);
        state.selectedTimePeriod = 'Selected Month';
        ctx.rerender();
      },
      onNext: () => {
        state.trendsMonth = ymPlusMonths(state.trendsMonth, 1);
        state.selectedTimePeriod = 'Selected Month';
        ctx.rerender();
      },
    }),
  })));

  root.appendChild(h('div.row.gap-8.mt-12', {},
    h('div.grow', {}, dropdown({
      label: 'Period',
      options: TIME_PERIODS,
      selected: state.selectedTimePeriod,
      onSelect: (period) => {
        state.selectedTimePeriod = period;
        if (period === 'Custom') {
          dateRangePickerDialog({
            initialStart: state.customStartDate,
            initialEnd: state.customEndDate,
            onDateRangeSelected: (start, end) => {
              state.customStartDate = start;
              state.customEndDate = end;
              ctx.rerender();
            },
            onDismiss: () => ctx.rerender(),
          });
        } else {
          ctx.rerender();
        }
      },
    })),
    chip('Filters', {
      selected: state.showFilterOptions,
      leading: icon('filterList'),
      onclick: () => { state.showFilterOptions = !state.showFilterOptions; ctx.rerender(); },
    }),
  ));

  const anyFilter = state.selectedCategories.size || state.selectedSubcategories.size || state.selectedLabels.size;
  if (state.showFilterOptions && anyFilter) {
    root.appendChild(h('div.row.end', {},
      h('button.btn.text', {
        type: 'button',
        onclick: () => {
          state.selectedCategories = new Set();
          state.selectedSubcategories = new Set();
          state.selectedLabels = new Set();
          state.selectedTimePeriod = 'Selected Month';
          ctx.rerender();
        },
      }, 'Clear filters'),
    ));
  }

  if (state.showFilterOptions) {
    const filters = h('div.col.gap-8.mt-8');
    const toggleIn = (set, option) => {
      if (set.has(option)) set.delete(option); else set.add(option);
      ctx.rerender();
    };

    if (categories.length > 0) {
      filters.appendChild(multiSelectDropdown({
        label: 'Categories',
        options: categories,
        selected: state.selectedCategories,
        onToggle: (option) => toggleIn(state.selectedCategories, option),
      }));
    }
    if (state.selectedCategories.size > 0) {
      const availableSubs = [...new Set(
        [...state.selectedCategories].flatMap((cat) => subcategoriesMap[cat] || []),
      )];
      if (availableSubs.length > 0) {
        filters.appendChild(multiSelectDropdown({
          label: 'Subcategories',
          options: availableSubs,
          selected: state.selectedSubcategories,
          onToggle: (option) => toggleIn(state.selectedSubcategories, option),
        }));
      }
    }
    if (labels.length > 0) {
      filters.appendChild(multiSelectDropdown({
        label: 'Labels',
        options: labels,
        selected: state.selectedLabels,
        onToggle: (option) => toggleIn(state.selectedLabels, option),
      }));
    }
    root.appendChild(filters);
  }

  // Trends summary
  const totalAmount = sumBy(trendFiltered, (e) => e.amount);
  root.appendChild(h('div.mt-12', {}, card({ variant: 'filled-primary' },
    h('div.row.between.pad-16', {},
      h('div.col', {},
        h('div.label-medium', { style: { opacity: '.8' } }, 'Total spent'),
        h('div.headline-small', {}, `₹${fmt0(totalAmount)}`),
      ),
      h('div.col', { style: { alignItems: 'flex-end' } },
        h('div.label-medium', { style: { opacity: '.8' } }, 'Transactions'),
        h('div.headline-small', {}, String(trendFiltered.length)),
      ),
    ),
  )));

  // Trend chart
  const trendChartData = generateChartData(trendFiltered, state.selectedTimePeriod);
  root.appendChild(h('div.mt-12', {}, card({}, h('div', { style: { padding: '8px' } },
    barChart(trendChartData, {
      height: 220,
      onBarClicked: (p) => openTrendDrillThrough(ctx, p),
    }),
  ))));

  // ── Category breakdown ─────────────────────────────────────────────────────
  const drilled = state.selectedCategoryForHorizontalChart;
  root.appendChild(h('div.mt-20', {}, sectionHeader('Category breakdown', {
    subtitle: drilled
      ? `Subcategories of ${drilled}`
      : 'Tap a bar to view subcategories, long-press to open expenses',
    trailing: drilled
      ? h('button.btn.text', {
        type: 'button',
        onclick: () => { state.selectedCategoryForHorizontalChart = null; ctx.rerender(); },
      }, 'Back')
      : null,
  })));

  root.appendChild(h('div.mt-8', {}, card({}, h('div', { style: { padding: '8px' } },
    horizontalBarChart({
      expenses: trendTimeFiltered,
      categories,
      subcategoriesMap,
      selectedCategory: drilled,
      onCategorySelected: (category) => {
        state.selectedCategoryForHorizontalChart = category === drilled ? null : category;
        ctx.rerender();
      },
      onBarLongPressed: (category, subcategory) => openCategoryDrillThrough(ctx, category, subcategory),
      height: 300,
    }),
  ))));

  root.appendChild(h('div', { style: { height: '24px' } }));
  return root;
}

// ── Drill-through dialogs ────────────────────────────────────────────────────

function openCategoryDrillThrough(ctx, category, subcategory) {
  const itemText = subcategory ? `${category} / ${subcategory}` : (category || '');
  confirmDialog({
    title: 'Show expenses?',
    message: `View expenses for ${itemText}?`,
    confirmText: 'Show',
    onConfirm: () => {
      const [startDate, endDate] = getDateRangeForPeriod(state.selectedTimePeriod);
      ctx.navigateToFilteredExpenses({
        startDate,
        endDate,
        categories: category ? new Set([category]) : new Set(),
        subcategories: subcategory ? new Set([subcategory]) : new Set(),
        labels: new Set(state.selectedLabels),
      });
    },
  });
}

function openTrendDrillThrough(ctx, p) {
  const periodText = p.periodIsDate
    ? (p.endDate ? `${fmtDayMonth(p.period)} – ${fmtDayMonth(p.endDate)}` : fmtDate(p.period))
    : (p.period && p.period.length === 7 ? fmtYearMonth(p.period) : p.label);

  confirmDialog({
    title: 'Show expenses?',
    message: `View expenses for ${periodText}?`,
    confirmText: 'Show',
    onConfirm: () => {
      let startDate;
      let endDate;
      if (p.periodIsDate) {
        startDate = p.period;
        endDate = p.endDate || p.period;
      } else if (p.period && p.period.length === 7) {
        startDate = ymAtDay(p.period, 1);
        endDate = ymAtEnd(p.period);
      } else {
        [startDate, endDate] = getDateRangeForPeriod(state.selectedTimePeriod);
      }
      ctx.navigateToFilteredExpenses({
        startDate,
        endDate,
        categories: new Set(state.selectedCategories),
        subcategories: new Set(state.selectedSubcategories),
        labels: new Set(state.selectedLabels),
      });
    },
  });
}

export const dashboardState = state;
