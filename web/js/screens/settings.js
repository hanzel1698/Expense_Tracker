// ── Settings ──────────────────────────────────────────────────────────────────
// Port of ui/modern/screens/ModernSettingsScreen.kt — Google Drive sync, backup
// file restore, CSV import/export, categories / subcategories / labels / payment
// modes / paid-via management, recurring expenses, and the hidden developer mode
// (long-press the version label) with sample data and clear-all.

import { h, icon, onLongPress } from '../dom.js';
import {
  card, textField, dropdown, multiSelectDropdown, segmented, toggle,
  confirmDialog, editDialog, customDialog, datePickerDialog,
} from '../components.js';
import { data, update } from '../store.js';
import { RecurrenceFrequency, createRecurringExpense } from '../model.js';
import {
  today, fmtSlash, fmtSlashShort, fmt2, monthNameShort, monthValue,
  toDoubleOrNull, toIntOrNull, WEEKDAY_NAMES, WEEKDAY_ABBR, uuid,
} from '../util.js';
import { isDriveConfigured, getClientId, setClientId } from '../sync.js';

const state = {
  selectedCategory: null,
  isDeveloperMode: false,
};

export function renderSettings(ctx) {
  const categories = data.categories;
  if (!state.selectedCategory || !categories.includes(state.selectedCategory)) {
    state.selectedCategory = categories[0] || null;
  }
  const currentSubcategories = state.selectedCategory
    ? (data.subcategoriesMap[state.selectedCategory] || [])
    : [];

  const root = h('div.screen-pad');
  root.appendChild(h('div.headline-large.mb-16', {}, 'Settings'));

  // ── Google Drive sync ──────────────────────────────────────────────────────
  root.appendChild(renderSyncCard(ctx));

  // ── CSV import / export ────────────────────────────────────────────────────
  root.appendChild(h('div.mt-12', {}, card({}, h('div.pad-16', {},
    h('div.title-medium', {}, 'Data import / export (CSV)'),
    h('div', { style: { height: '12px' } }),
    h('div.row.gap-8', {},
      h('button.btn.outlined.grow', { type: 'button', onclick: () => ctx.exportTemplate() }, 'Export template'),
      h('button.btn.tonal.grow', { type: 'button', onclick: () => ctx.importCsv() }, 'Import CSV'),
    ),
    h('div', { style: { height: '8px' } }),
    h('button.btn.text.full', { type: 'button', onclick: () => ctx.exportExpensesCsv() }, 'Export my expenses as CSV'),
    h('div', { style: { height: '8px' } }),
    h('div.label-small.muted', { style: { whiteSpace: 'pre-line' } },
      '• Export a blank template with sample entries\n• Import CSV to add multiple expenses and settings'),
  ))));

  // ── Taxonomy management ────────────────────────────────────────────────────
  root.appendChild(h('div.mt-20', {}, manageableList(ctx, {
    title: 'Categories',
    items: categories,
    selectedIndex: categories.indexOf(state.selectedCategory),
    onItemSelected: (index) => { state.selectedCategory = categories[index]; ctx.rerender(); },
    onAdd: (name) => update((d) => {
      d.categories.push(name);
      d.subcategoriesMap[name] = [];
    }),
    onEdit: (index, newValue) => update((d) => {
      const oldName = d.categories[index];
      const subs = d.subcategoriesMap[oldName];
      delete d.subcategoriesMap[oldName];
      d.categories[index] = newValue;
      if (subs) d.subcategoriesMap[newValue] = subs;

      if (oldName in d.categoryBudgets) {
        d.categoryBudgets[newValue] = d.categoryBudgets[oldName];
        delete d.categoryBudgets[oldName];
      }
      for (const key of Object.keys(d.subcategoryBudgets)) {
        if (key.startsWith(`${oldName}/`)) {
          d.subcategoryBudgets[key.replace(`${oldName}/`, `${newValue}/`)] = d.subcategoryBudgets[key];
          delete d.subcategoryBudgets[key];
        }
      }
      if (state.selectedCategory === oldName) state.selectedCategory = newValue;
    }),
    onDelete: (index) => update((d) => {
      const name = d.categories[index];
      delete d.subcategoriesMap[name];
      d.categories.splice(index, 1);
      delete d.categoryBudgets[name];
      for (const key of Object.keys(d.subcategoryBudgets)) {
        if (key.startsWith(`${name}/`)) delete d.subcategoryBudgets[key];
      }
    }),
  })));

  root.appendChild(h('div.mt-16', {}, manageableList(ctx, {
    title: 'Subcategories',
    items: currentSubcategories,
    parentLabel: state.selectedCategory,
    onAdd: (name) => update((d) => {
      const cat = state.selectedCategory;
      if (!cat) return;
      if (!d.subcategoriesMap[cat]) d.subcategoriesMap[cat] = [];
      d.subcategoriesMap[cat].push(name);
    }),
    onEdit: (index, newValue) => update((d) => {
      const cat = state.selectedCategory;
      const list = cat && d.subcategoriesMap[cat];
      if (!list) return;
      const oldSubName = list[index];
      list[index] = newValue;
      const oldKey = `${cat}/${oldSubName}`;
      if (oldKey in d.subcategoryBudgets) {
        d.subcategoryBudgets[`${cat}/${newValue}`] = d.subcategoryBudgets[oldKey];
        delete d.subcategoryBudgets[oldKey];
      }
    }),
    onDelete: (index) => update((d) => {
      const cat = state.selectedCategory;
      const list = cat && d.subcategoriesMap[cat];
      if (!list) return;
      const subName = list[index];
      list.splice(index, 1);
      delete d.subcategoryBudgets[`${cat}/${subName}`];
    }),
  })));

  root.appendChild(h('div.mt-16', {}, manageableList(ctx, {
    title: 'Labels',
    items: data.labels,
    onAdd: (name) => update((d) => { d.labels.push(name); }),
    onEdit: (index, newValue) => update((d) => { d.labels[index] = newValue; }),
    onDelete: (index) => update((d) => { d.labels.splice(index, 1); }),
  })));

  root.appendChild(h('div.mt-16', {}, manageableList(ctx, {
    title: 'Payment modes',
    items: data.paymentModes,
    onAdd: (name) => update((d) => { d.paymentModes.push(name); }),
    onEdit: (index, newValue) => update((d) => { d.paymentModes[index] = newValue; }),
    onDelete: (index) => update((d) => { d.paymentModes.splice(index, 1); }),
  })));

  root.appendChild(h('div.mt-16', {}, manageableList(ctx, {
    title: 'Paid via',
    items: data.paidVia,
    onAdd: (name) => update((d) => { d.paidVia.push(name); }),
    onEdit: (index, newValue) => update((d) => { d.paidVia[index] = newValue; }),
    onDelete: (index) => update((d) => { d.paidVia.splice(index, 1); }),
  })));

  // ── Recurring expenses ─────────────────────────────────────────────────────
  root.appendChild(h('div.mt-16', {}, renderRecurringSection(ctx)));

  // ── Version (long-press for dev mode) ──────────────────────────────────────
  const version = h('div.label-medium.muted', {
    style: { textAlign: 'center', padding: '8px', cursor: 'default', userSelect: 'none' },
  }, 'Version 1.0.0 · Aurora');
  onLongPress(version, () => {
    state.isDeveloperMode = !state.isDeveloperMode;
    ctx.rerender();
  }, 3000);
  root.appendChild(h('div.mt-20', {}, version));

  if (state.isDeveloperMode) {
    root.appendChild(h('div.mt-8', {}, card({ variant: 'error-container' }, h('div.pad-16', {},
      h('div.title-small', { style: { textAlign: 'center' } }, 'Dev mode active'),
      h('div', { style: { height: '10px' } }),
      h('div.row.gap-8', {},
        h('button.btn.tonal.grow', {
          type: 'button',
          onclick: () => confirmDialog({
            title: 'Populate sample data',
            message: 'This will generate approximately 500 sample expenses spanning 18 months. Existing data will be replaced. Continue?',
            onConfirm: () => ctx.populateSampleData(),
          }),
        }, 'Populate data'),
        h('button.btn.destructive.grow', {
          type: 'button',
          onclick: () => ctx.clearAllData(),
        }, 'Clear all'),
      ),
    ))));
  }

  root.appendChild(h('div', { style: { height: '24px' } }));
  return root;
}

// ── Google Drive card ────────────────────────────────────────────────────────

function renderSyncCard(ctx) {
  const configured = isDriveConfigured();
  const signedIn = ctx.isSignedIn();

  const body = h('div.pad-16');

  body.appendChild(h('div.row.between', {},
    h('div.col', {},
      h('div.title-medium', {}, 'Google Drive sync'),
      h('div.body-small', {
        class: signedIn ? 'success' : 'danger',
      }, signedIn ? 'Connected' : (configured ? 'Not connected' : 'Not configured')),
    ),
    signedIn
      ? h('button.btn.outlined.destructive', { type: 'button', onclick: () => ctx.driveSignOut() }, 'Sign out')
      : h('button.btn', {
        type: 'button',
        disabled: !configured,
        onclick: () => confirmDialog({
          title: 'Sign in',
          message: 'Sign in to enable backup and sync functionality for your expense data across devices.',
          confirmText: 'Sign in',
          onConfirm: () => ctx.signIn(),
        }),
      }, 'Sign in'),
  ));

  if (signedIn) {
    body.appendChild(h('div', { style: { height: '12px' } }));
    body.appendChild(h('div.row.gap-8', {},
      h('button.btn.tonal.grow', { type: 'button', onclick: () => ctx.uploadBackup() }, 'Upload'),
      h('button.btn.outlined.grow', {
        type: 'button',
        onclick: () => confirmDialog({
          title: 'Download backup',
          message: 'This will restore the most recent backup from Google Drive.',
          confirmText: 'Download',
          onConfirm: () => ctx.downloadLatestBackup(),
        }),
      }, 'Download'),
    ));
    body.appendChild(h('div', { style: { height: '8px' } }));
    body.appendChild(h('div.label-small.muted', { style: { whiteSpace: 'pre-line' } },
      '• Upload saves your data to Google Drive\n• Download retrieves backups from Drive'));
  }

  // OAuth client configuration — the web equivalent of the Android app's
  // package-name + SHA-1 registration.
  body.appendChild(h('div', { style: { height: '12px' } }));
  body.appendChild(h('hr.divider.faint'));
  body.appendChild(h('div', { style: { height: '12px' } }));
  body.appendChild(h('div.title-small', {}, 'Google OAuth client ID'));
  body.appendChild(h('div', { style: { height: '4px' } }));
  body.appendChild(h('div.label-small.muted', {},
    'Drive sync needs a Web OAuth client ID from Google Cloud Console with this site listed as an authorised JavaScript origin. Stored only in this browser.'));
  body.appendChild(h('div', { style: { height: '8px' } }));
  body.appendChild(h('button.btn.outlined.full', {
    type: 'button',
    onclick: () => editDialog({
      title: 'Google OAuth client ID',
      fieldLabel: 'Client ID',
      initialValue: getClientId(),
      onConfirm: (value) => {
        setClientId(value);
        ctx.showMessage(value.trim() ? 'Client ID saved' : 'Client ID cleared');
        ctx.rerender();
      },
    }),
  }, configured ? 'Change client ID…' : 'Set client ID…'));

  // Backup file restore / download — works with no sign-in at all.
  body.appendChild(h('div', { style: { height: '12px' } }));
  body.appendChild(h('hr.divider.faint'));
  body.appendChild(h('div', { style: { height: '12px' } }));
  body.appendChild(h('div.title-small', {}, 'Backup file'));
  body.appendChild(h('div', { style: { height: '4px' } }));
  body.appendChild(h('div.label-small.muted', {},
    'Save a backup JSON file, or pick one from your device (e.g. downloaded from Drive or shared to you) and merge it in — no sign-in required.'));
  body.appendChild(h('div', { style: { height: '8px' } }));
  body.appendChild(h('div.row.gap-8', {},
    h('button.btn.tonal.grow', { type: 'button', onclick: () => ctx.downloadBackupFile() }, 'Save backup'),
    h('button.btn.outlined.grow', {
      type: 'button',
      onclick: () => confirmDialog({
        title: 'Restore from file',
        message: 'Pick a backup JSON file to merge into your data. Existing categories, labels, and expenses are kept — only missing items are added.',
        confirmText: 'Choose file',
        onConfirm: () => ctx.restoreFromFile(),
      }),
    }, 'Restore…'),
  ));

  return card({}, body);
}

// ── Manageable list (ModernManageableList) ───────────────────────────────────

function manageableList(ctx, {
  title, items, onAdd, onEdit, onDelete, selectedIndex = -1, onItemSelected, parentLabel,
}) {
  const body = h('div');

  body.appendChild(h('div.row.between', { style: { padding: '12px 16px' } },
    h('div.grow', {},
      h('div.title-medium', {}, title),
      parentLabel ? h('div.label-small.muted.ellipsis', {}, `in ${parentLabel}`) : null,
    ),
    h('button.icon-btn.filled-tonal.sized-36', {
      type: 'button', 'aria-label': `Add ${title.toLowerCase()}`,
      onclick: () => editDialog({
        title: `Add ${title.toLowerCase()}`,
        initialValue: '',
        onConfirm: (value) => { if (value.trim()) onAdd(value.trim()); ctx.rerender(); },
        onDismiss: () => ctx.rerender(),
      }),
    }, icon('add')),
  ));

  if (parentLabel && items.length === 0) {
    body.appendChild(h('div.empty-note', {}, 'No items'));
  } else {
    const list = h('div', { style: { paddingBottom: '8px' } });
    items.forEach((item, index) => {
      const isSelected = index === selectedIndex;
      list.appendChild(h('div.list-row', {
        class: [isSelected ? 'selected' : '', onItemSelected ? 'selectable' : ''].filter(Boolean).join(' '),
        onclick: onItemSelected ? () => onItemSelected(index) : null,
      },
        h('div.body-medium.grow.clamp-2', {
          style: isSelected ? { fontWeight: '600' } : null,
        }, item),
        h('button.icon-btn.small', {
          type: 'button', 'aria-label': 'Edit',
          onclick: (e) => {
            e.stopPropagation();
            editDialog({
              title: `Edit ${title.toLowerCase()}`,
              initialValue: item,
              onConfirm: (value) => { if (value.trim()) onEdit(index, value.trim()); ctx.rerender(); },
              onDismiss: () => ctx.rerender(),
            });
          },
        }, icon('edit')),
        h('button.icon-btn.small.danger', {
          type: 'button', 'aria-label': 'Delete',
          onclick: (e) => {
            e.stopPropagation();
            confirmDialog({
              title: 'Delete',
              message: `Remove "${item}"?`,
              confirmText: 'Delete',
              destructive: true,
              onConfirm: () => { onDelete(index); ctx.rerender(); },
            });
          },
        }, icon('del')),
      ));
    });
    body.appendChild(list);
  }

  return card({}, body);
}

// ── Recurring expenses (ModernRecurringExpensesSection) ─────────────────────

function scheduleLabel(re, { long = false } = {}) {
  let out;
  switch (re.frequency) {
    case RecurrenceFrequency.DAILY:
      out = 'Every day';
      break;
    case RecurrenceFrequency.WEEKLY: {
      const names = long ? WEEKDAY_NAMES : WEEKDAY_ABBR;
      out = `Every ${names[re.dayOfPeriod - 1] || '?'}`;
      break;
    }
    case RecurrenceFrequency.YEARLY: {
      const m = re.monthOfPeriod > 0 ? re.monthOfPeriod : monthValue(re.startDate);
      out = `Yearly on ${monthNameShort(Math.min(Math.max(m, 1), 12))} ${re.dayOfPeriod}`;
      break;
    }
    case RecurrenceFrequency.MONTHLY:
    default:
      out = long ? `Day ${re.dayOfPeriod} of each month` : `Day ${re.dayOfPeriod} monthly`;
  }
  if (long) return out;
  out += ` · from ${fmtSlashShort(re.startDate)}`;
  if (re.endDate) out += ` to ${fmtSlashShort(re.endDate)}`;
  return out;
}

function renderRecurringSection(ctx) {
  const list = data.recurringExpenses;
  const active = list.filter((re) => re.isActive);
  const sumFor = (freqs) => active
    .filter((re) => freqs.includes(re.frequency))
    .reduce((sum, re) => sum + re.amount, 0);
  const monthlySum = sumFor([RecurrenceFrequency.MONTHLY]);
  const yearlySum = sumFor([RecurrenceFrequency.YEARLY]);
  const otherSum = sumFor([RecurrenceFrequency.DAILY, RecurrenceFrequency.WEEKLY]);

  const body = h('div');
  body.appendChild(h('div.row.between', { style: { padding: '12px 16px' } },
    h('div.grow', {},
      h('div.title-medium', {}, 'Recurring expenses'),
      h('div.label-small.muted', {}, `${active.length} active · ${list.length} total`),
      h('div.label-small.muted', { style: { opacity: '.8' } },
        `Monthly ₹${fmt2(monthlySum)} · Yearly ₹${fmt2(yearlySum)}`
        + (otherSum > 0 ? ` · Other ₹${fmt2(otherSum)}` : '')),
    ),
    h('button.icon-btn.filled-tonal.sized-36', {
      type: 'button', 'aria-label': 'Add recurring expense',
      onclick: () => openRecurringDialog(ctx, { title: 'Add recurring expense', initial: null }),
    }, icon('add')),
  ));

  if (list.length === 0) {
    body.appendChild(h('div.empty-note', {}, 'No recurring expenses'));
  } else {
    const rows = h('div', { style: { paddingBottom: '8px' } });
    list.forEach((re, index) => {
      rows.appendChild(h('div.row', {
        style: { padding: '8px 16px', cursor: 'pointer' },
        onclick: () => openRecurringDetail(re),
      },
        h('span.dot', {
          style: { background: re.isActive ? 'var(--success)' : 'var(--outline-variant)' },
        }),
        h('div.grow', { style: { marginLeft: '10px', minWidth: '0' } },
          h('div.title-small.ellipsis', {
            style: re.isActive ? null : { opacity: '.45' },
          }, re.name),
          h('div.label-small.muted.ellipsis', {}, `₹${fmt2(re.amount)} · ${scheduleLabel(re)}`),
          re.category
            ? h('div.label-small.muted.ellipsis', { style: { opacity: '.7' } },
              re.category + (re.subcategory ? ` › ${re.subcategory}` : ''))
            : null,
        ),
        h('div', {
          style: { padding: '0 4px' },
          onclick: (e) => e.stopPropagation(),
        }, toggle(re.isActive, () => {
          update((d) => { d.recurringExpenses[index] = { ...d.recurringExpenses[index], isActive: !re.isActive }; });
          ctx.rerender();
        }, 'Active')),
        h('button.icon-btn.small', {
          type: 'button', 'aria-label': 'Edit',
          onclick: (e) => {
            e.stopPropagation();
            openRecurringDialog(ctx, { title: 'Edit recurring expense', initial: re });
          },
        }, icon('edit')),
        h('button.icon-btn.small.danger', {
          type: 'button', 'aria-label': 'Delete',
          onclick: (e) => {
            e.stopPropagation();
            confirmDialog({
              title: 'Delete recurring expense',
              message: `Remove "${re.name}"? This will not delete any expenses already generated.`,
              confirmText: 'Delete',
              destructive: true,
              onConfirm: () => {
                update((d) => { d.recurringExpenses.splice(index, 1); });
                ctx.rerender();
              },
            });
          },
        }, icon('del')),
      ));
      if (index < list.length - 1) {
        rows.appendChild(h('hr.divider.faint', { style: { margin: '0 16px' } }));
      }
    });
    body.appendChild(rows);
  }

  return card({}, body);
}

function openRecurringDetail(re) {
  const line = (label, value) => h('div.body-medium', {}, `${label}: ${value}`);
  customDialog(() => h('div', {},
    h('div.headline-small.dialog-title', {}, 'Recurring expense'),
    h('div.col.gap-6', {},
      h('div.body-medium', { style: { fontWeight: '600' } }, `Name: ${re.name}`),
      re.storeName ? line('Store', re.storeName) : null,
      h('div.body-medium', { style: { fontWeight: '600' } }, `Amount: ₹${fmt2(re.amount)}`),
      re.category ? line('Category', re.category + (re.subcategory ? ` › ${re.subcategory}` : '')) : null,
      re.labels.length ? line('Labels', re.labels.join(', ')) : null,
      re.paymentMode ? line('Payment mode', re.paymentMode) : null,
      re.paidVia ? line('Paid via', re.paidVia) : null,
      line('Frequency', re.frequency.charAt(0) + re.frequency.slice(1).toLowerCase()),
      line('Schedule', scheduleLabel(re, { long: true })),
      line('Start date', fmtSlash(re.startDate)),
      line('End date', re.endDate ? fmtSlash(re.endDate) : 'None'),
      re.notes ? line('Notes', re.notes) : null,
      h('div.body-medium', {
        class: re.isActive ? 'success' : 'muted',
        style: { fontWeight: '600' },
      }, `Status: ${re.isActive ? 'Active' : 'Inactive'}`),
      re.lastGeneratedDate
        ? h('div.body-small.muted', {}, `Last generated: ${fmtSlash(re.lastGeneratedDate)}`)
        : null,
    ),
  ));
}

// ── Recurring add/edit dialog (ModernRecurringExpenseDialog) ────────────────

const FREQUENCY_OPTIONS = [
  [RecurrenceFrequency.DAILY, 'Daily'],
  [RecurrenceFrequency.WEEKLY, 'Weekly'],
  [RecurrenceFrequency.MONTHLY, 'Monthly'],
  [RecurrenceFrequency.YEARLY, 'Yearly'],
];

const YEARLY_MONTHS = ['Current Month (Default)', 'January', 'February', 'March', 'April', 'May',
  'June', 'July', 'August', 'September', 'October', 'November', 'December'];

function openRecurringDialog(ctx, { title, initial }) {
  const draft = {
    name: initial?.name ?? '',
    storeName: initial?.storeName ?? '',
    amountStr: initial ? fmt2(initial.amount) : '',
    category: initial?.category ?? data.categories[0] ?? '',
    subcategory: initial?.subcategory ?? '',
    itemDescription: initial?.itemDescription ?? '',
    paymentMode: initial?.paymentMode ?? '',
    paidVia: initial?.paidVia ?? '',
    frequency: initial?.frequency ?? RecurrenceFrequency.MONTHLY,
    dayOfPeriod: initial?.dayOfPeriod ?? 1,
    monthOfPeriod: initial?.monthOfPeriod ?? 0,
    startDate: initial?.startDate ?? today(),
    endDate: initial?.endDate ?? null,
    notes: initial?.notes ?? '',
    isActive: initial?.isActive ?? true,
    labels: [...(initial?.labels ?? [])],
    nameError: false,
    amountError: false,
  };

  const dialog = customDialog((api) => buildBody(api), { wide: true, onDismiss: () => ctx.rerender() });

  function rebuild() {
    const next = buildBody(dialog);
    dialog.body.replaceChildren(next);
  }

  function buildBody(api) {
    const body = h('div');

    body.appendChild(h('div.row.between', {},
      h('div.headline-small', {}, title),
      h('button.icon-btn', { type: 'button', onclick: () => api.close() }, icon('close')),
    ));
    body.appendChild(h('div', { style: { height: '12px' } }));

    body.appendChild(textField({
      label: 'Name *',
      value: draft.name,
      error: draft.nameError,
      supportingText: draft.nameError ? 'Required' : null,
      oninput: (v) => { draft.name = v; draft.nameError = false; },
    }));
    body.appendChild(h('div', { style: { height: '10px' } }));

    body.appendChild(textField({
      label: 'Amount (₹) *',
      value: draft.amountStr,
      numeric: true,
      error: draft.amountError,
      supportingText: draft.amountError ? 'Enter a valid amount' : null,
      oninput: (v) => { draft.amountStr = v; draft.amountError = false; },
    }));
    body.appendChild(h('div', { style: { height: '10px' } }));

    body.appendChild(textField({
      label: 'Store / merchant',
      value: draft.storeName,
      oninput: (v) => { draft.storeName = v; },
    }));
    body.appendChild(h('div', { style: { height: '10px' } }));

    body.appendChild(textField({
      label: 'Item description',
      value: draft.itemDescription,
      oninput: (v) => { draft.itemDescription = v; },
    }));
    body.appendChild(h('div', { style: { height: '10px' } }));

    body.appendChild(dropdown({
      label: 'Category',
      options: [...data.categories, '+ Add New'],
      selected: draft.category,
      onSelect: (option) => {
        if (option === '+ Add New') {
          editDialog({
            title: 'New category', fieldLabel: 'Category name', initialValue: '',
            onConfirm: (value) => {
              if (value.trim()) {
                update((d) => { d.categories.push(value); d.subcategoriesMap[value] = []; });
                draft.category = value;
                draft.subcategory = '';
              }
              rebuild();
            },
            onDismiss: rebuild,
          });
        } else {
          draft.category = option;
          draft.subcategory = '';
          rebuild();
        }
      },
    }));
    body.appendChild(h('div', { style: { height: '10px' } }));

    if (draft.category) {
      body.appendChild(dropdown({
        label: 'Subcategory',
        options: ['', ...(data.subcategoriesMap[draft.category] || []), '+ Add New'],
        selected: draft.subcategory,
        onSelect: (option) => {
          if (option === '+ Add New') {
            editDialog({
              title: `New subcategory in ${draft.category}`, fieldLabel: 'Subcategory name', initialValue: '',
              onConfirm: (value) => {
                if (value.trim()) {
                  update((d) => {
                    if (!d.subcategoriesMap[draft.category]) d.subcategoriesMap[draft.category] = [];
                    d.subcategoriesMap[draft.category].push(value);
                  });
                  draft.subcategory = value;
                }
                rebuild();
              },
              onDismiss: rebuild,
            });
          } else {
            draft.subcategory = option;
            rebuild();
          }
        },
      }));
      body.appendChild(h('div', { style: { height: '10px' } }));
    }

    body.appendChild(multiSelectDropdown({
      label: 'Labels',
      options: [...data.labels, '+ Add New'],
      selected: new Set(draft.labels),
      onToggle: (label) => {
        if (label === '+ Add New') {
          editDialog({
            title: 'New label', fieldLabel: 'Label name', initialValue: '',
            onConfirm: (value) => {
              if (value.trim()) {
                update((d) => { d.labels.push(value); });
                draft.labels.push(value);
              }
              rebuild();
            },
            onDismiss: rebuild,
          });
        } else {
          draft.labels = draft.labels.includes(label)
            ? draft.labels.filter((l) => l !== label)
            : [...draft.labels, label];
          rebuild();
        }
      },
    }));
    body.appendChild(h('div', { style: { height: '14px' } }));

    body.appendChild(h('div.label-medium.muted', {}, 'Frequency'));
    body.appendChild(h('div', { style: { height: '6px' } }));
    body.appendChild(segmented(
      FREQUENCY_OPTIONS.map(([, label]) => label),
      FREQUENCY_OPTIONS.findIndex(([freq]) => freq === draft.frequency),
      (index) => { draft.frequency = FREQUENCY_OPTIONS[index][0]; rebuild(); },
    ));
    body.appendChild(h('div', { style: { height: '10px' } }));

    if (draft.frequency === RecurrenceFrequency.MONTHLY) {
      body.appendChild(dropdown({
        label: 'Day of month',
        options: Array.from({ length: 31 }, (_, i) => String(i + 1)),
        selected: String(draft.dayOfPeriod),
        onSelect: (v) => { draft.dayOfPeriod = toIntOrNull(v) ?? 1; rebuild(); },
      }));
    } else if (draft.frequency === RecurrenceFrequency.WEEKLY) {
      body.appendChild(dropdown({
        label: 'Day of week',
        options: WEEKDAY_NAMES,
        selected: WEEKDAY_NAMES[draft.dayOfPeriod - 1] || 'Monday',
        onSelect: (v) => { draft.dayOfPeriod = WEEKDAY_NAMES.indexOf(v) + 1; rebuild(); },
      }));
    } else if (draft.frequency === RecurrenceFrequency.YEARLY) {
      body.appendChild(dropdown({
        label: 'Month of year',
        options: YEARLY_MONTHS,
        selected: draft.monthOfPeriod >= 1 && draft.monthOfPeriod <= 12
          ? YEARLY_MONTHS[draft.monthOfPeriod] : YEARLY_MONTHS[0],
        onSelect: (v) => { draft.monthOfPeriod = YEARLY_MONTHS.indexOf(v); rebuild(); },
      }));
      body.appendChild(h('div', { style: { height: '10px' } }));
      body.appendChild(dropdown({
        label: 'Day of month (yearly)',
        options: Array.from({ length: 31 }, (_, i) => String(i + 1)),
        selected: String(draft.dayOfPeriod),
        onSelect: (v) => { draft.dayOfPeriod = toIntOrNull(v) ?? 1; rebuild(); },
      }));
    }
    body.appendChild(h('div', { style: { height: '10px' } }));

    body.appendChild(h('div.row.gap-10', {},
      h('div.grow', {}, textField({
        label: 'Start date',
        value: fmtSlash(draft.startDate),
        readOnly: true,
        disabled: true,
        onclick: () => datePickerDialog({
          initial: draft.startDate,
          onDateSelected: (iso) => { if (iso) draft.startDate = iso; },
          onDismiss: rebuild,
        }),
      })),
      h('div.grow', {}, textField({
        label: 'End date',
        value: draft.endDate ? fmtSlash(draft.endDate) : 'None',
        readOnly: true,
        disabled: true,
        onclick: () => datePickerDialog({
          initial: draft.endDate || draft.startDate,
          onDateSelected: (iso) => { if (iso) draft.endDate = iso; },
          onDismiss: rebuild,
        }),
      })),
    ));
    if (draft.endDate) {
      body.appendChild(h('div.row.end', {},
        h('button.btn.text.destructive', {
          type: 'button',
          onclick: () => { draft.endDate = null; rebuild(); },
        }, 'Clear end date'),
      ));
    }
    body.appendChild(h('div', { style: { height: '10px' } }));

    if (data.paymentModes.length > 0) {
      body.appendChild(dropdown({
        label: 'Payment mode',
        options: ['', ...data.paymentModes],
        selected: draft.paymentMode,
        onSelect: (v) => { draft.paymentMode = v; rebuild(); },
      }));
      body.appendChild(h('div', { style: { height: '10px' } }));
    }
    if (data.paidVia.length > 0) {
      body.appendChild(dropdown({
        label: 'Paid via',
        options: ['', ...data.paidVia],
        selected: draft.paidVia,
        onSelect: (v) => { draft.paidVia = v; rebuild(); },
      }));
      body.appendChild(h('div', { style: { height: '10px' } }));
    }

    body.appendChild(textField({
      label: 'Notes',
      value: draft.notes,
      multiline: true,
      oninput: (v) => { draft.notes = v; },
    }));
    body.appendChild(h('div', { style: { height: '10px' } }));

    body.appendChild(h('div.row.between', {},
      h('span.title-small', {}, 'Active'),
      toggle(draft.isActive, (checked) => { draft.isActive = checked; rebuild(); }, 'Active'),
    ));

    body.appendChild(h('div.dialog-actions', {},
      h('button.btn.text', { type: 'button', onclick: () => api.close() }, 'Cancel'),
      h('button.btn', {
        type: 'button',
        onclick: () => {
          const amount = toDoubleOrNull(draft.amountStr.trim());
          draft.nameError = draft.name.trim() === '';
          draft.amountError = amount === null || amount <= 0;
          if (draft.nameError || draft.amountError) { rebuild(); return; }

          const re = createRecurringExpense({
            id: initial?.id ?? uuid(),
            name: draft.name.trim(),
            storeName: draft.storeName.trim(),
            amount,
            category: draft.category,
            subcategory: draft.subcategory,
            itemDescription: draft.itemDescription.trim(),
            labels: [...draft.labels],
            paymentMode: draft.paymentMode,
            paidVia: draft.paidVia,
            frequency: draft.frequency,
            dayOfPeriod: draft.dayOfPeriod,
            monthOfPeriod: draft.monthOfPeriod,
            startDate: draft.startDate,
            endDate: draft.endDate,
            notes: draft.notes.trim(),
            isActive: draft.isActive,
            lastGeneratedDate: initial?.lastGeneratedDate ?? null,
          });

          api.close();
          if (initial) ctx.editRecurringExpense(re);
          else ctx.addRecurringExpense(re);
        },
      }, 'Save'),
    ));

    return body;
  }
}

export const settingsState = state;
