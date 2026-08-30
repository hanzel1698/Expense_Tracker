// ── App shell ─────────────────────────────────────────────────────────────────
// Web port of ui/modern/ModernMainActivity.kt — owns navigation, the data
// state, auto-save, Google Drive sync, CSV import/export and the
// recurring-expense engine.

import { h, icon, clear } from './dom.js';
import { Screen, TrendDimension } from './nav.js';
import { data, load, update, replaceAll, subscribe, saveNow, readPrefs, writePref, serialize } from './store.js';
import { defaultAppData } from './model.js';
import { generate, applyGeneratedResult } from './recurring.js';
import { populateSampleData } from './sample.js';
import { CSV_TEMPLATE_CONTENT, expensesToCsv, importCsv, downloadText, pickFile } from './csv.js';
import { confirmDialog } from './components.js';
import * as sync from './sync.js';
import { today, debounce } from './util.js';

import { renderSignInGate } from './screens/signin.js';
import { renderDashboard } from './screens/dashboard.js';
import { renderExpenseList, applyIncomingFilters, resetExpenseListState } from './screens/expenses.js';
import { renderBudget } from './screens/budget.js';
import { renderSettings } from './screens/settings.js';
import { renderEntry, initEntry, hasChanges, requestBack } from './screens/entry.js';

// ── App state ────────────────────────────────────────────────────────────────

const app = {
  currentScreen: Screen.Dashboard,
  expenseFilters: {
    date: null, startDate: null, endDate: null,
    categories: new Set(), subcategories: new Set(), labels: new Set(),
    expenseId: null, groupId: null,
  },
  pendingEdit: { expenseToEdit: null, groupToEdit: null, selectedExpenseId: null, initialDate: null },
  busy: false,
  snackTimer: null,
};

const screenEl = document.getElementById('screen');
const navEl = document.getElementById('nav');

function clearExpenseFilters() {
  app.expenseFilters = {
    date: null, startDate: null, endDate: null,
    categories: new Set(), subcategories: new Set(), labels: new Set(),
    expenseId: null, groupId: null,
  };
}

// ── Messages & busy overlay ──────────────────────────────────────────────────

function showMessage(message, isError = false) {
  const existing = document.querySelector('.snack');
  if (existing) existing.remove();
  if (app.snackTimer) clearTimeout(app.snackTimer);
  if (!message) return;

  const snack = h('div.snack', { class: isError ? 'error' : '' }, message);
  document.body.appendChild(snack);
  app.snackTimer = setTimeout(() => snack.remove(), 3000);
}

function setBusy(busy) {
  app.busy = busy;
  const existing = document.querySelector('.sync-overlay');
  if (busy && !existing) {
    document.body.appendChild(h('div.sync-overlay', {},
      h('div.sync-panel', {},
        h('div.spinner'),
        h('div.title-medium', {}, 'Syncing data…'),
        h('div.body-small.muted', {}, 'Please wait'),
      ),
    ));
  } else if (!busy && existing) {
    existing.remove();
  }
}

// ── Theme ────────────────────────────────────────────────────────────────────

function applyTheme() {
  document.documentElement.setAttribute('data-theme', data.isDarkTheme ? 'dark' : 'light');
  const meta = document.querySelector('meta[name="theme-color"]');
  if (meta) meta.setAttribute('content', data.isDarkTheme ? '#0E1513' : '#F5FBF8');
}

// ── Navigation ───────────────────────────────────────────────────────────────

const MAIN_SCREENS = [Screen.Dashboard, Screen.ExpenseList, Screen.Budget, Screen.Settings];

function navigate(screen, { skipGuard = false } = {}) {
  // Leaving a dirty entry form prompts to save first (Compose's BackHandler).
  if (!skipGuard && app.currentScreen === Screen.AddExpense && hasChanges()) {
    requestBack(ctx, () => navigate(screen, { skipGuard: true }));
    return;
  }

  if (screen !== Screen.ExpenseList && screen !== Screen.DraftList) {
    clearExpenseFilters();
    resetExpenseListState();
  }
  if (screen !== Screen.AddExpense) {
    app.pendingEdit = { expenseToEdit: null, groupToEdit: null, selectedExpenseId: null, initialDate: null };
  }
  app.currentScreen = screen;
  history.replaceState({ screen }, '');
  render();
}

function navigateToExpenses({ date }) {
  clearExpenseFilters();
  app.expenseFilters.date = date;
  app.currentScreen = Screen.ExpenseList;
  applyIncomingFilters(app.expenseFilters, data.expenses);
  render();
}

function navigateToMonthExpenses(ym, dimension, item) {
  clearExpenseFilters();
  app.expenseFilters.startDate = `${ym}-01`;
  app.expenseFilters.endDate = endOfMonth(ym);
  if (dimension === TrendDimension.CATEGORY && item) app.expenseFilters.categories = new Set([item]);
  if (dimension === TrendDimension.SUBCATEGORY && item) app.expenseFilters.subcategories = new Set([item]);
  if (dimension === TrendDimension.LABEL && item) app.expenseFilters.labels = new Set([item]);
  app.currentScreen = Screen.ExpenseList;
  applyIncomingFilters(app.expenseFilters, data.expenses);
  render();
}

function navigateToFilteredExpenses({ startDate, endDate, categories, subcategories, labels }) {
  clearExpenseFilters();
  app.expenseFilters.startDate = startDate;
  app.expenseFilters.endDate = endDate;
  app.expenseFilters.categories = categories || new Set();
  app.expenseFilters.subcategories = subcategories || new Set();
  app.expenseFilters.labels = labels || new Set();
  app.currentScreen = Screen.ExpenseList;
  applyIncomingFilters(app.expenseFilters, data.expenses);
  render();
}

function endOfMonth(ym) {
  const [y, m] = ym.split('-').map(Number);
  const lastDay = new Date(Date.UTC(y, m, 0)).getUTCDate();
  return `${ym}-${String(lastDay).padStart(2, '0')}`;
}

function newExpense(date) {
  app.pendingEdit = {
    expenseToEdit: null, groupToEdit: null, selectedExpenseId: null,
    initialDate: date || null,
  };
  initEntry(app.pendingEdit);
  app.currentScreen = Screen.AddExpense;
  render();
}

function editExpense(expense) {
  const group = data.expenses.filter((e) => e.groupId === expense.groupId);
  app.pendingEdit = group.length > 1
    ? { expenseToEdit: null, groupToEdit: group, selectedExpenseId: expense.id, initialDate: null }
    : { expenseToEdit: expense, groupToEdit: null, selectedExpenseId: null, initialDate: null };
  initEntry(app.pendingEdit);
  app.currentScreen = Screen.AddExpense;
  render();
}

/** onSave — replaces the whole group being edited, then returns to the dashboard. */
function saveExpenses(newExpenses, groupId, editedOriginal) {
  update((d) => {
    if (editedOriginal) {
      const gid = editedOriginal.groupId;
      d.expenses = d.expenses.filter((e) => e.groupId !== gid);
    }
    d.expenses.push(...newExpenses);
  }, { rerender: false });

  app.pendingEdit = { expenseToEdit: null, groupToEdit: null, selectedExpenseId: null, initialDate: null };
  navigate(Screen.Dashboard, { skipGuard: true });
}

// ── Google Drive ─────────────────────────────────────────────────────────────

async function signIn() {
  try {
    setBusy(true);
    await sync.signIn();
    setBusy(false);
    showMessage('Signed in to Google Drive');
    if (app.currentScreen === Screen.SignInGate) navigate(Screen.Dashboard);
    else render();
  } catch (err) {
    setBusy(false);
    showMessage(`Google sign-in failed: ${err.message}`, true);
    render();
  }
}

function driveSignOut() {
  sync.signOut();
  showMessage('Signed out of Google Drive');
  render();
}

async function uploadBackup() {
  setBusy(true);
  try {
    const result = await sync.uploadToDrive();
    showMessage(result.message);
  } catch (err) {
    showMessage(`Upload failed: ${err.message}`, true);
  }
  setBusy(false);
  render();
}

async function downloadLatestBackup() {
  setBusy(true);
  try {
    const backups = await sync.listBackups();
    if (backups.length === 0) {
      showMessage('No backups found', true);
    } else {
      const jsonData = await sync.downloadBackup(backups[0].fileId);
      const result = sync.validateAndMerge(jsonData);
      if (result.success) {
        applyTheme();
        const removed = result.expensesRemoved > 0 ? `, ${result.expensesRemoved} removed` : '';
        showMessage(`Backup restored (${result.expensesAdded} added${removed})`);
      } else {
        showMessage(`Restore failed: ${result.message}`, true);
      }
    }
  } catch (err) {
    showMessage(`Download failed: ${err.message}`, true);
  }
  setBusy(false);
  render();
}

async function restoreFromFile() {
  const file = await pickFile('.json,application/json');
  if (!file) return;
  setBusy(true);
  try {
    if (!file.text.trim()) {
      showMessage('Restore failed: could not read file', true);
    } else {
      const result = sync.validateAndMerge(file.text);
      if (result.success) {
        applyTheme();
        const removed = result.expensesRemoved > 0 ? `, ${result.expensesRemoved} removed` : '';
        showMessage(`Backup restored (${result.expensesAdded} added${removed})`);
      } else {
        showMessage(`Restore failed: ${result.message}`, true);
      }
    }
  } catch (err) {
    showMessage(`Restore failed: ${err.message}`, true);
  }
  setBusy(false);
  render();
}

function downloadBackupFile() {
  downloadText(sync.localBackupFileName(), serialize(), 'application/json');
  showMessage('Backup file saved');
}

// Auto-sync: one debounced upload after a burst of edits, silent on failure —
// the manual Upload button in Settings stays the fallback.
let autoSyncInFlight = false;
const autoSync = debounce(async () => {
  if (!sync.isSignedIn() || autoSyncInFlight) return;
  autoSyncInFlight = true;
  try {
    await sync.uploadToDrive();
  } catch (err) {
    console.warn('[sync] auto-sync upload failed:', err.message);
  } finally {
    autoSyncInFlight = false;
  }
}, 3000);

// ── CSV ──────────────────────────────────────────────────────────────────────

function exportTemplate() {
  downloadText('expense_import_template.csv', CSV_TEMPLATE_CONTENT, 'text/csv');
  showMessage('Template exported successfully!');
}

function exportExpensesCsv() {
  downloadText(`expenses_${today()}.csv`, expensesToCsv(data.expenses), 'text/csv');
  showMessage('Expenses exported');
}

async function runImportCsv() {
  const file = await pickFile('.csv,text/csv');
  if (!file) return;
  try {
    if (!file.text.trim()) {
      showMessage('CSV file is empty', true);
      return;
    }
    let result;
    update((d) => { result = importCsv(file.text, d); }, { rerender: false });
    showMessage(`Import complete! Added ${result.importCount} expenses and ${result.recurringCount} recurring rules.`);
    render();
  } catch (err) {
    showMessage(`Import failed: ${err.message}`, true);
  }
}

// ── Dev mode actions ─────────────────────────────────────────────────────────

function doPopulateSampleData() {
  const sample = populateSampleData();
  replaceAll(sample);
  applyTheme();
  showMessage(`Sample data populated: ${sample.expenses.length} expenses`);
  render();
}

function clearAllData() {
  confirmDialog({
    title: 'Clear all data',
    message: 'This will delete ALL expenses, categories, and settings. This cannot be undone. Are you sure?',
    confirmText: 'Clear everything',
    destructive: true,
    onConfirm: () => {
      replaceAll({
        ...defaultAppData(),
        categories: [],
        subcategoriesMap: {},
        labels: [],
        categoryBudgets: {},
        subcategoryBudgets: {},
        isDarkTheme: false,
      });
      applyTheme();
      showMessage('All data cleared');
      render();
    },
  });
}

// ── Store history (onUpdateStoreHistory / onUpdateStoreLocation) ─────────────

function updateStoreHistory(newStore) {
  if (!newStore.trim()) return;
  update((d) => {
    d.storeHistory = [newStore, ...d.storeHistory.filter((s) => s !== newStore)].slice(0, 50);
  }, { rerender: false });
}

function updateStoreLocation(store, location) {
  if (!store.trim() || !location.trim()) return;
  update((d) => {
    const existing = d.storeLocationHistory[store] || [];
    d.storeLocationHistory[store] = [location, ...existing.filter((l) => l !== location)].slice(0, 10);
  }, { rerender: false });
}

// ── Recurring expense actions ────────────────────────────────────────────────

function addRecurringExpense(re) {
  update((d) => {
    const { newExpenses, updatedTemplates } = generate([re]);
    if (newExpenses.length > 0) {
      d.expenses.push(...newExpenses);
      d.recurringExpenses.push(updatedTemplates[0]);
    } else {
      d.recurringExpenses.push(re);
    }
  }, { rerender: false });
  render();
}

function editRecurringExpense(re) {
  update((d) => {
    const index = d.recurringExpenses.findIndex((r) => r.id === re.id);
    if (index >= 0) d.recurringExpenses[index] = re;
  }, { rerender: false });
  render();
}

// ── Render ───────────────────────────────────────────────────────────────────

const ctx = {
  get expenseFilters() { return app.expenseFilters; },
  navigate,
  navigateToExpenses,
  navigateToMonthExpenses,
  navigateToFilteredExpenses,
  clearExpenseFilters,
  newExpense,
  editExpense,
  saveExpenses,
  updateStoreHistory,
  updateStoreLocation,
  addRecurringExpense,
  editRecurringExpense,
  populateSampleData: doPopulateSampleData,
  clearAllData,
  exportTemplate,
  exportExpensesCsv,
  importCsv: runImportCsv,
  restoreFromFile,
  downloadBackupFile,
  uploadBackup,
  downloadLatestBackup,
  signIn,
  driveSignOut,
  isSignedIn: () => sync.isSignedIn(),
  showMessage,
  setBusy,
  toggleTheme: () => {
    update((d) => { d.isDarkTheme = !d.isDarkTheme; }, { rerender: false });
    applyTheme();
    render();
  },
  rerender: (opts) => render(opts),
};

let lastRenderedScreen = null;

function render(opts = {}) {
  // Keep the scroll offset across re-renders of the same screen; start a
  // newly-opened screen at the top.
  const sameScreen = lastRenderedScreen === app.currentScreen;
  const scrollTop = sameScreen ? screenEl.scrollTop : 0;
  lastRenderedScreen = app.currentScreen;
  clear(screenEl);

  let node;
  switch (app.currentScreen) {
    case Screen.SignInGate:
      node = renderSignInGate(ctx);
      break;
    case Screen.ExpenseList:
      node = renderExpenseList(ctx, { showOnlyDrafts: false });
      break;
    case Screen.DraftList:
      node = renderExpenseList(ctx, { showOnlyDrafts: true });
      break;
    case Screen.Budget:
      node = renderBudget(ctx);
      break;
    case Screen.Settings:
      node = renderSettings(ctx);
      break;
    case Screen.AddExpense:
      node = renderEntry(ctx);
      break;
    case Screen.Dashboard:
    default:
      node = renderDashboard(ctx);
  }

  screenEl.appendChild(node);
  renderNav();

  // Restore focus where a screen asked for it (the search field re-creates
  // itself on every keystroke).
  screenEl.scrollTop = scrollTop;
  if (opts.focus === 'search' && node.searchInput) {
    const input = node.searchInput;
    input.focus();
    const end = input.value.length;
    input.setSelectionRange(end, end);
  }
}

const NAV_ITEMS = [
  [Screen.Dashboard, 'home', 'Home'],
  [Screen.ExpenseList, 'list', 'Expenses'],
  [Screen.Budget, 'pieChart', 'Budget'],
  [Screen.Settings, 'settings', 'Settings'],
];

function renderNav() {
  clear(navEl);
  if (app.currentScreen === Screen.SignInGate) {
    navEl.classList.add('hidden');
    return;
  }
  navEl.classList.remove('hidden');

  const isMainScreen = MAIN_SCREENS.includes(app.currentScreen);
  for (const [screen, iconName, label] of NAV_ITEMS) {
    const selected = isMainScreen && app.currentScreen === screen;
    navEl.appendChild(h('button.nav-item', {
      type: 'button',
      'aria-selected': String(selected),
      onclick: () => navigate(screen),
    },
      h('span.nav-icon', {}, icon(iconName)),
      h('span.nav-label', {}, label),
    ));
  }
}

// ── Browser back (BackHandler equivalent) ────────────────────────────────────

window.addEventListener('popstate', () => {
  history.pushState({ screen: app.currentScreen }, '');

  if (app.currentScreen === Screen.ExpenseList || app.currentScreen === Screen.DraftList) {
    const f = app.expenseFilters;
    if (f.expenseId || f.groupId) {
      f.expenseId = null;
      f.groupId = null;
      clearExpenseFilters();
      resetExpenseListState();
      render();
      return;
    }
    navigate(Screen.Dashboard);
    return;
  }
  if (app.currentScreen !== Screen.Dashboard && app.currentScreen !== Screen.SignInGate) {
    navigate(Screen.Dashboard);
  }
});

// ── Startup ──────────────────────────────────────────────────────────────────

function start() {
  load();
  applyTheme();

  // Generate any recurring occurrences due since the last visit.
  update((d) => {
    const { newExpenses, updatedTemplates } = generate(d.recurringExpenses, today());
    applyGeneratedResult(d, newExpenses, updatedTemplates);
  }, { rerender: false });

  subscribe(({ rerender }) => {
    autoSync();
    if (rerender) render();
  });

  // First run shows the sign-in gate; afterwards go straight to the dashboard.
  const prefs = readPrefs();
  app.currentScreen = prefs.seenGate ? Screen.Dashboard : Screen.SignInGate;
  writePref('seenGate', true);

  history.replaceState({ screen: app.currentScreen }, '');
  history.pushState({ screen: app.currentScreen }, '');

  render();

  // Restore a Drive session silently when this browser has consented before.
  if (sync.hasSignedInBefore() && sync.isDriveConfigured()) {
    sync.signIn({ silent: true })
      .then(() => render())
      .catch(() => { /* stay signed out; Settings offers a manual sign-in */ });
  }

  window.addEventListener('beforeunload', saveNow);
}

start();
