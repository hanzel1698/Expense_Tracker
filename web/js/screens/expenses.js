// ── Expense list ──────────────────────────────────────────────────────────────
// Port of ui/modern/screens/ModernExpenseListScreen.kt — search, date filter
// (presets / exact / range), attribute filters, by-store & by-item views,
// filtered totals, group & single detail views, edit and delete.

import { h, icon } from '../dom.js';
import {
  card, searchField, multiSelectDropdown, chip, segmented, stat, tag,
  confirmDialog, customDialog, datePickerDialog, dateRangePickerDialog,
} from '../components.js';
import { data, update } from '../store.js';
import {
  today, fmtDate, fmt0, fmt2, sumBy, inRange, minusMonths, minusYears, withDayOfYear,
} from '../util.js';

/** Screen-local state — survives re-renders, like Compose's `remember`. */
const state = {
  viewByStore: true,
  selectedGroupId: null,
  selectedExpenseId: null,
  searchQuery: '',
  filterCategories: new Set(),
  filterSubcategories: new Set(),
  filterLabels: new Set(),
  filterPaymentModes: new Set(),
  filterPaidVia: new Set(),
  exactDateFilter: null,
  startDateFilter: null,
  endDateFilter: null,
  filtersExpanded: false,
};

/**
 * Applies the filters an incoming navigation carries (the LaunchedEffect that
 * syncs `initial*` params in the Compose screen).
 */
export function applyIncomingFilters(nav, expenses) {
  state.filterCategories = new Set(nav.categories || []);
  state.filterSubcategories = new Set(nav.subcategories || []);
  state.filterLabels = new Set(nav.labels || []);
  state.filterPaymentModes = new Set();
  state.filterPaidVia = new Set();

  if (nav.startDate) {
    state.startDateFilter = nav.startDate;
    state.exactDateFilter = null;
  }
  if (nav.endDate) state.endDateFilter = nav.endDate;

  state.selectedExpenseId = null;
  state.selectedGroupId = null;

  if (nav.expenseId) {
    const found = expenses.find((e) => e.id === nav.expenseId);
    if (found) {
      clearAllDateFilters();
      clearAttributeFilters();
      state.viewByStore = false;
      state.selectedExpenseId = found.id;
    }
  }
  if (nav.groupId) {
    const group = expenses.filter((e) => e.groupId === nav.groupId);
    if (group.length > 0) {
      clearAllDateFilters();
      clearAttributeFilters();
      state.viewByStore = true;
      state.selectedGroupId = nav.groupId;
    }
  }
}

function clearAllDateFilters() {
  state.exactDateFilter = null;
  state.startDateFilter = null;
  state.endDateFilter = null;
}

function clearAttributeFilters() {
  state.filterCategories = new Set();
  state.filterSubcategories = new Set();
  state.filterLabels = new Set();
  state.filterPaymentModes = new Set();
  state.filterPaidVia = new Set();
}

export function resetExpenseListState() {
  state.selectedGroupId = null;
  state.selectedExpenseId = null;
  state.searchQuery = '';
  clearAttributeFilters();
  clearAllDateFilters();
}

// ── Screen ───────────────────────────────────────────────────────────────────

export function renderExpenseList(ctx, { showOnlyDrafts = false } = {}) {
  const nav = ctx.expenseFilters;
  const expenses = showOnlyDrafts
    ? data.expenses.filter((e) => e.isDraft)
    : data.expenses.filter((e) => !e.isDraft);

  const viewingDate = nav.date || null;

  const filtered = expenses.filter((expense) => {
    const matchesDate = viewingDate
      ? expense.date === viewingDate
      : state.exactDateFilter
        ? expense.date === state.exactDateFilter
        : (state.startDateFilter && state.endDateFilter)
          ? inRange(expense.date, state.startDateFilter, state.endDateFilter)
          : true;
    if (!matchesDate) return false;

    if (state.filterCategories.size && !state.filterCategories.has(expense.category)) return false;
    if (state.filterSubcategories.size && !state.filterSubcategories.has(expense.subcategory)) return false;
    if (state.filterLabels.size && !expense.labels.some((l) => state.filterLabels.has(l))) return false;
    if (state.filterPaymentModes.size && !state.filterPaymentModes.has(expense.paymentMode)) return false;
    if (state.filterPaidVia.size && !state.filterPaidVia.has(expense.paidVia)) return false;

    const q = state.searchQuery.toLowerCase();
    if (q.trim()) {
      const matchesSearch = expense.storeName.toLowerCase().includes(q)
        || expense.itemDescription.toLowerCase().includes(q)
        || expense.category.toLowerCase().includes(q)
        || expense.subcategory.toLowerCase().includes(q)
        || String(expense.amount).includes(q)
        || expense.labels.some((l) => l.toLowerCase().includes(q))
        || expense.notes.toLowerCase().includes(q);
      if (!matchesSearch) return false;
    }
    return true;
  });

  const displayExpenses = [...filtered].sort((a, b) => (a.date < b.date ? 1 : a.date > b.date ? -1 : 0));

  const groupMap = new Map();
  for (const expense of displayExpenses) {
    if (!groupMap.has(expense.groupId)) groupMap.set(expense.groupId, []);
    groupMap.get(expense.groupId).push(expense);
  }
  const groupedExpenses = [...groupMap.values()]
    .sort((a, b) => (a[0].date < b[0].date ? 1 : a[0].date > b[0].date ? -1 : 0));

  const selectedGroup = state.selectedGroupId
    ? data.expenses.filter((e) => e.groupId === state.selectedGroupId)
    : null;
  const selectedExpense = state.selectedExpenseId
    ? data.expenses.find((e) => e.id === state.selectedExpenseId)
    : null;

  if ((selectedGroup && selectedGroup.length > 0) || selectedExpense) {
    return renderDetail(ctx, selectedGroup, selectedExpense);
  }

  const root = h('div.screen-pad');

  // Title
  const titleText = showOnlyDrafts ? 'Drafts'
    : viewingDate ? `Expenses · ${fmtDate(viewingDate)}`
      : nav.subcategories && nav.subcategories.size === 1 ? `Expenses · ${[...nav.subcategories][0]}`
        : nav.subcategories && nav.subcategories.size > 1 ? 'Expenses · multiple'
          : nav.categories && nav.categories.size === 1 ? `Expenses · ${[...nav.categories][0]}`
            : nav.categories && nav.categories.size > 1 ? 'Expenses · multiple'
              : 'Expenses';
  root.appendChild(h('div.headline-medium.mb-12.ellipsis', {}, titleText));

  // Search row
  const hasIncomingFilter = viewingDate
    || (nav.categories && nav.categories.size)
    || (nav.subcategories && nav.subcategories.size)
    || (nav.labels && nav.labels.size);

  const search = searchField({
    value: state.searchQuery,
    oninput: (value) => {
      state.searchQuery = value;
      ctx.rerender({ focus: 'search' });
    },
  });

  root.appendChild(h('div.row.gap-8', {},
    hasIncomingFilter
      ? chip('Clear', { className: 'assist', onclick: () => { ctx.clearExpenseFilters(); resetExpenseListState(); ctx.rerender(); } })
      : null,
    search,
    h('button.icon-btn.filled', {
      type: 'button', 'aria-label': 'Add expense',
      onclick: () => ctx.newExpense(null),
    }, icon('add')),
  ));

  // Date filter + filters toggle
  const dateFilterActive = !!(state.exactDateFilter || state.startDateFilter);
  const dateFilterText = state.exactDateFilter
    ? fmtDate(state.exactDateFilter)
    : (state.startDateFilter && state.endDateFilter)
      ? `${fmtDate(state.startDateFilter)} – ${fmtDate(state.endDateFilter)}`
      : 'Date filter';

  root.appendChild(h('div.row.gap-8.mt-8', {},
    chip(dateFilterText, {
      selected: dateFilterActive,
      leading: icon('dateRange'),
      onclick: () => openDateFilterDialog(ctx),
      className: 'grow',
    }),
    chip(state.filtersExpanded ? 'Hide filters' : 'Filters', {
      selected: state.filtersExpanded,
      onclick: () => { state.filtersExpanded = !state.filtersExpanded; ctx.rerender(); },
    }),
  ));

  if (state.filtersExpanded) {
    const sorted = (list) => [...new Set(list)].sort();
    const categoriesInData = sorted(expenses.map((e) => e.category));
    const subcats = sorted(expenses.map((e) => e.subcategory));
    const availableLabels = sorted(expenses.flatMap((e) => e.labels));
    const paymentModesInData = sorted(expenses.map((e) => e.paymentMode).filter(Boolean));
    const paidViasInData = sorted(expenses.map((e) => e.paidVia).filter(Boolean));

    const toggleIn = (set, option) => {
      if (set.has(option)) set.delete(option); else set.add(option);
      ctx.rerender();
    };

    const panel = h('div.col.gap-8.mt-8', {},
      multiSelectDropdown({
        label: 'Categories', options: categoriesInData, selected: state.filterCategories,
        onToggle: (o) => toggleIn(state.filterCategories, o),
      }),
      multiSelectDropdown({
        label: 'Subcategories', options: subcats, selected: state.filterSubcategories,
        onToggle: (o) => toggleIn(state.filterSubcategories, o),
      }),
      multiSelectDropdown({
        label: 'Labels', options: availableLabels, selected: state.filterLabels,
        onToggle: (o) => toggleIn(state.filterLabels, o),
      }),
      multiSelectDropdown({
        label: 'Payment modes', options: paymentModesInData, selected: state.filterPaymentModes,
        onToggle: (o) => toggleIn(state.filterPaymentModes, o),
      }),
      multiSelectDropdown({
        label: 'Paid via', options: paidViasInData, selected: state.filterPaidVia,
        onToggle: (o) => toggleIn(state.filterPaidVia, o),
      }),
    );

    const anyActive = state.filterCategories.size || state.filterSubcategories.size
      || state.filterLabels.size || state.filterPaymentModes.size
      || state.filterPaidVia.size || state.searchQuery.trim();
    if (anyActive) {
      panel.appendChild(h('button.btn.text.full', {
        type: 'button',
        onclick: () => {
          clearAttributeFilters();
          state.searchQuery = '';
          ctx.clearExpenseFilters();
          ctx.rerender();
        },
      }, 'Clear all filters'));
    }
    root.appendChild(panel);
  }

  // View mode toggle
  root.appendChild(h('div.mt-8', {}, segmented(['By store', 'By item'], state.viewByStore ? 0 : 1, (index) => {
    state.viewByStore = index === 0;
    ctx.rerender();
  })));

  // Filtered total
  const filteredTotal = sumBy(displayExpenses, (e) => e.amount);
  const filteredCount = state.viewByStore ? groupedExpenses.length : displayExpenses.length;
  root.appendChild(h('div.mt-8', {}, card({ variant: 'filled-primary' },
    h('div.row.between', { style: { padding: '12px 16px' } },
      h('span.label-large', { style: { opacity: '.85' } }, `${filteredCount} ${state.viewByStore ? 'groups' : 'items'}`),
      h('span.title-medium', {}, `Total ₹${fmt2(filteredTotal)}`),
    ),
  )));

  // List
  const list = h('div.col.gap-8.mt-8');
  if (state.viewByStore) {
    for (const group of groupedExpenses) {
      const groupTotal = sumBy(group, (e) => e.amount);
      list.appendChild(card({
        onclick: () => { state.selectedGroupId = group[0].groupId; ctx.rerender(); },
      }, h('div.pad-14', {},
        h('div.row.between', {},
          h('div.grow', { style: { paddingRight: '8px' } },
            h('div.title-medium.ellipsis', {}, group[0].storeName),
            h('div.label-small.muted', {}, fmtDate(group[0].date)),
          ),
          h('div.title-medium.primary', {}, `₹${fmt0(groupTotal)}`),
        ),
        ...group.map((expense) => h('div.row.between', { style: { marginTop: '6px' } },
          h('div.body-medium.muted.grow.ellipsis', {}, expense.itemDescription || expense.storeName),
          h('div.body-medium', {}, `₹${fmt0(expense.amount)}`),
        )),
      )));
    }
  } else {
    for (const expense of displayExpenses) {
      const title = expense.itemDescription || expense.storeName;
      const subtitle = fmtDate(expense.date)
        + (expense.storeName && expense.itemDescription ? `  ·  ${expense.storeName}` : '');
      list.appendChild(card({
        onclick: () => { state.selectedExpenseId = expense.id; ctx.rerender(); },
      }, h('div.row.between.pad-14', {},
        h('div.grow', { style: { paddingRight: '8px' } },
          h('div.title-medium.ellipsis', {}, title),
          h('div.label-small.muted.ellipsis', {}, subtitle),
        ),
        h('div.title-medium.primary', {}, `₹${fmt0(expense.amount)}`),
      )));
    }
  }

  if (displayExpenses.length === 0) {
    list.appendChild(h('div.empty-note', {}, showOnlyDrafts ? 'No drafts' : 'No expenses match these filters'));
  }

  root.appendChild(list);
  root.appendChild(h('div', { style: { height: '8px' } }));
  root.searchInput = search.inputEl;
  return root;
}

// ── Detail views ─────────────────────────────────────────────────────────────

function backToList(ctx) {
  state.selectedGroupId = null;
  state.selectedExpenseId = null;
  ctx.rerender();
}

function deleteExpense(ctx, expense) {
  confirmDialog({
    title: 'Delete expense?',
    message: `Store: ${expense.storeName}\n`
      + `Item: ${expense.itemDescription || expense.category}\n`
      + `Amount: ₹${fmt2(expense.amount)}\n\n`
      + 'This cannot be undone.',
    confirmText: 'Delete',
    destructive: true,
    onConfirm: () => {
      update((d) => { d.expenses = d.expenses.filter((e) => e.id !== expense.id); });
      state.selectedGroupId = null;
      state.selectedExpenseId = null;
      ctx.rerender();
    },
  });
}

function renderDetail(ctx, group, expense) {
  const root = h('div.screen-pad');

  root.appendChild(h('button.btn.tonal.mb-12', {
    type: 'button',
    onclick: () => backToList(ctx),
  }, icon('arrowBack'), 'Back to list'));

  if (group && group.length > 0) {
    root.appendChild(renderGroupDetail(ctx, group));
  } else if (expense) {
    root.appendChild(renderSingleDetail(ctx, expense));
  }
  return root;
}

function renderGroupDetail(ctx, group) {
  const first = group[0];
  const total = sumBy(group, (e) => e.amount);

  const body = h('div.pad-16');
  body.appendChild(h('div.row.between.mb-8', { style: { alignItems: 'flex-start' } },
    h('div.grow', {},
      h('div.label-medium.muted', {}, fmtDate(first.date)),
      h('div.headline-small.clamp-2', {}, first.storeName),
    ),
    stat('Total', `₹${fmt0(total)}`, { valueColor: 'var(--primary)', alignEnd: true }),
  ));

  if (first.location.trim()) {
    body.appendChild(h('div.mb-8', {}, tag(first.location)));
  }

  if (first.paymentMode.trim() || first.paidVia.trim()) {
    body.appendChild(h('div.row.gap-24.mb-12', {},
      first.paymentMode.trim() ? h('div.col', {},
        h('div.label-small.muted', {}, 'Payment'),
        h('div.body-medium', {}, first.paymentMode),
      ) : null,
      first.paidVia.trim() ? h('div.col', {},
        h('div.label-small.muted', {}, 'Paid via'),
        h('div.body-medium', {}, first.paidVia),
      ) : null,
    ));
  }

  body.appendChild(h('hr.divider'));
  body.appendChild(h('div.label-medium.muted', { style: { padding: '8px 0' } }, `Items (${group.length})`));

  group.forEach((subTx, i) => {
    const itemCard = card({ variant: 'surface-3', className: 'mb-8' }, h('div.pad-12', {},
      h('div.row.between', {},
        h('div.title-small.grow.ellipsis', {}, `${i + 1}.  ${subTx.itemDescription || subTx.category}`),
        h('div.title-small.primary', {}, `₹${fmt2(subTx.amount)}`),
      ),
      h('div.row.between', { style: { marginTop: '2px' } },
        h('div.body-small.muted', {}, `${subTx.category} / ${subTx.subcategory}`),
        subTx.quantity && subTx.quantity > 0
          ? h('div.body-small.muted', {}, quantityText(subTx))
          : null,
      ),
      subTx.labels.length > 0
        ? h('div.row.gap-6.wrap', { style: { marginTop: '6px' } },
          ...subTx.labels.slice(0, 3).map((l) => tag(l)),
          subTx.labels.length > 3 ? tag(`+${subTx.labels.length - 3}`) : null,
        )
        : null,
      subTx.notes.trim()
        ? h('div.body-small.muted.clamp-2', { style: { marginTop: '4px', whiteSpace: 'pre-wrap' } }, subTx.notes)
        : null,
      h('div.row.gap-8', { style: { marginTop: '8px' } },
        h('button.btn.tonal.grow', {
          type: 'button',
          style: { minHeight: '34px', padding: '4px 12px' },
          onclick: () => ctx.editExpense(subTx),
        }, 'Edit'),
        h('button.btn.outlined.destructive.grow', {
          type: 'button',
          style: { minHeight: '34px', padding: '4px 12px' },
          onclick: () => deleteExpense(ctx, subTx),
        }, 'Delete'),
      ),
    ));
    body.appendChild(itemCard);
  });

  return card({}, body);
}

function quantityText(expense) {
  const qty = expense.unit ? `${expense.quantity} ${expense.unit}` : String(expense.quantity);
  const unitRate = expense.amount / expense.quantity;
  const rate = expense.unit ? ` • ₹${fmt2(unitRate)}/${expense.unit}` : '';
  return qty + rate;
}

function renderSingleDetail(ctx, expense) {
  const title = expense.itemDescription || expense.storeName;
  const body = h('div.pad-16');

  body.appendChild(h('div.row.between.mb-12', { style: { alignItems: 'flex-start' } },
    h('div.grow', {},
      h('div.label-small.muted', {}, 'Item'),
      h('div.title-large.clamp-2', {}, title || 'No description'),
    ),
    stat('Amount', `₹${fmt2(expense.amount)}`, { valueColor: 'var(--primary)', alignEnd: true }),
  ));

  body.appendChild(h('div.row.gap-24.mb-8', {},
    h('div.col.grow', {},
      h('div.label-small.muted', {}, 'Store'),
      h('div.body-medium', {}, expense.storeName),
    ),
    h('div.col', {},
      h('div.label-small.muted', {}, 'Date'),
      h('div.body-medium', {}, fmtDate(expense.date)),
    ),
  ));

  if (expense.location.trim()) body.appendChild(h('div.mb-8', {}, tag(expense.location)));

  const hasQty = expense.quantity && expense.quantity > 0;
  body.appendChild(h('div.row.gap-24.mb-8', {},
    h('div.col.grow', {},
      h('div.label-small.muted', {}, 'Category'),
      h('div.body-medium', {}, `${expense.category} / ${expense.subcategory}`),
    ),
    hasQty ? h('div.col', {},
      h('div.label-small.muted', {}, 'Qty'),
      h('div.body-medium', {}, expense.unit ? `${expense.quantity} ${expense.unit}` : String(expense.quantity)),
    ) : null,
    hasQty ? h('div.col', {},
      h('div.label-small.muted', {}, 'Rate'),
      h('div.body-medium', {}, `₹${fmt2(expense.amount / expense.quantity)}/${expense.unit || 'unit'}`),
    ) : null,
  ));

  if (expense.paymentMode.trim() || expense.paidVia.trim()) {
    body.appendChild(h('div.row.gap-24.mb-8', {},
      expense.paymentMode.trim() ? h('div.col', {},
        h('div.label-small.muted', {}, 'Payment'),
        h('div.body-medium', {}, expense.paymentMode),
      ) : null,
      expense.paidVia.trim() ? h('div.col', {},
        h('div.label-small.muted', {}, 'Paid via'),
        h('div.body-medium', {}, expense.paidVia),
      ) : null,
    ));
  }

  if (expense.labels.length > 0) {
    body.appendChild(h('div.label-small.muted', {}, 'Labels'));
    body.appendChild(h('div.row.gap-6.wrap.mb-8', { style: { marginTop: '4px' } },
      ...expense.labels.slice(0, 4).map((l) => tag(l)),
      expense.labels.length > 4 ? tag(`+${expense.labels.length - 4}`) : null,
    ));
  }

  if (expense.notes.trim()) {
    body.appendChild(h('div.label-small.muted', {}, 'Notes'));
    body.appendChild(h('div.body-medium.mb-8', { style: { whiteSpace: 'pre-wrap' } }, expense.notes));
  }

  body.appendChild(h('div', { style: { height: '8px' } }));
  body.appendChild(h('hr.divider'));
  body.appendChild(h('div.row.gap-8', { style: { marginTop: '12px' } },
    h('button.btn.tonal.grow', {
      type: 'button',
      onclick: () => ctx.editExpense(expense),
    }, icon('edit'), 'Edit'),
    h('button.btn.outlined.destructive.grow', {
      type: 'button',
      onclick: () => deleteExpense(ctx, expense),
    }, icon('del'), 'Delete'),
  ));

  return card({}, body);
}

// ── Date filter dialog ───────────────────────────────────────────────────────

function openDateFilterDialog(ctx) {
  customDialog((api) => {
    const preset = (label, apply) => h('button.btn.tonal.full', {
      type: 'button',
      onclick: () => {
        apply();
        state.exactDateFilter = null;
        api.close();
        ctx.rerender();
      },
    }, label);

    const now = today();
    const body = h('div.col.gap-8', {},
      h('div.headline-small', {}, 'Date filter'),
      h('div.label-medium.muted.mt-8', {}, 'Presets'),
      preset('Last 6 months', () => {
        state.startDateFilter = minusMonths(now, 6);
        state.endDateFilter = now;
      }),
      preset('This year', () => {
        state.startDateFilter = withDayOfYear(now, 1);
        state.endDateFilter = now;
      }),
      preset('Last 12 months', () => {
        state.startDateFilter = minusYears(now, 1);
        state.endDateFilter = now;
      }),
      h('div.label-medium.muted.mt-8', {}, 'Custom'),
      h('button.btn.outlined.full', {
        type: 'button',
        onclick: () => {
          api.close();
          datePickerDialog({
            initial: state.exactDateFilter,
            onDateSelected: (iso) => {
              if (iso) {
                state.exactDateFilter = iso;
                state.startDateFilter = null;
                state.endDateFilter = null;
              }
            },
            onDismiss: () => ctx.rerender(),
          });
        },
      }, 'Exact date'),
      h('button.btn.outlined.full', {
        type: 'button',
        onclick: () => {
          api.close();
          dateRangePickerDialog({
            initialStart: state.startDateFilter,
            initialEnd: state.endDateFilter,
            onDateRangeSelected: (start, end) => {
              if (start && end) {
                state.startDateFilter = start;
                state.endDateFilter = end;
                state.exactDateFilter = null;
              }
            },
            onDismiss: () => ctx.rerender(),
          });
        },
      }, 'Custom range'),
    );

    if (state.exactDateFilter || state.startDateFilter) {
      body.appendChild(h('button.btn.text.destructive.full', {
        type: 'button',
        onclick: () => {
          clearAllDateFilters();
          api.close();
          ctx.rerender();
        },
      }, 'Clear date filter'));
    }
    return body;
  });
}

export const expenseListState = state;
