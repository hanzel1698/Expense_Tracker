// ── Backup & Google Drive sync ────────────────────────────────────────────────
// Port of sync/SyncService.kt + sync/SimpleGoogleDriveManager.kt. The merge
// semantics (MERGE_BY_DATE), the Drive folder name and the backup file naming
// all match the Android app, so the two stay interchangeable.
//
// The Android app resolves its OAuth client from the APK's package + signature.
// On the web that isn't possible, so the Client ID is supplied by the deployer
// (config.js) or pasted into Settings, and stored locally.

import { parseAppData } from './model.js';
import { data as localData, replaceAll, serialize, readPrefs, writePref } from './store.js';
import { today, isAfter, distinct } from './util.js';

const APP_FOLDER = 'ExpenseTracker_Backups';
const DRIVE_SCOPE = 'https://www.googleapis.com/auth/drive.file';
const GIS_SRC = 'https://accounts.google.com/gsi/client';

// Auto-sync overwrites this single rolling file instead of adding a timestamped
// one per edit burst, which used to grow the folder without bound. The name
// still starts with `expense_data_` so listBackups() picks it up, and because
// it is the most recently modified file it sorts first for the restore path.
const ROLLING_BACKUP_NAME = 'expense_data_latest.json';

// ── Client ID configuration ──────────────────────────────────────────────────

export function getClientId() {
  const stored = readPrefs().googleClientId;
  if (stored) return stored;
  return (window.EXPENSE_TRACKER_CONFIG && window.EXPENSE_TRACKER_CONFIG.googleClientId) || '';
}

export function setClientId(id) {
  writePref('googleClientId', id ? id.trim() : null);
  accessToken = null;
  tokenExpiry = 0;
  tokenClient = null;
}

export function isDriveConfigured() {
  return !!getClientId();
}

// ── Google Identity Services token flow ──────────────────────────────────────

let gisReady = null;
let tokenClient = null;
let accessToken = null;
let tokenExpiry = 0;

function loadGis() {
  if (gisReady) return gisReady;
  gisReady = new Promise((resolve, reject) => {
    if (window.google && window.google.accounts && window.google.accounts.oauth2) {
      resolve();
      return;
    }
    const script = document.createElement('script');
    script.src = GIS_SRC;
    script.async = true;
    script.defer = true;
    script.onload = () => resolve();
    script.onerror = () => reject(new Error('Could not load Google Identity Services (offline?)'));
    document.head.appendChild(script);
  });
  return gisReady;
}

export function isSignedIn() {
  return !!accessToken && Date.now() < tokenExpiry;
}

/**
 * Requests a Drive access token. `prompt: ''` attempts a silent refresh for an
 * already-consented session; the interactive consent screen is used otherwise.
 */
export async function signIn({ silent = false } = {}) {
  const clientId = getClientId();
  if (!clientId) throw new Error('No Google Client ID configured — add one in Settings.');

  await loadGis();

  return new Promise((resolve, reject) => {
    tokenClient = window.google.accounts.oauth2.initTokenClient({
      client_id: clientId,
      scope: DRIVE_SCOPE,
      prompt: silent ? '' : 'consent',
      callback: (response) => {
        if (response.error) {
          reject(new Error(response.error_description || response.error));
          return;
        }
        accessToken = response.access_token;
        tokenExpiry = Date.now() + (Number(response.expires_in || 3000) - 60) * 1000;
        writePref('driveSignedInOnce', true);
        resolve(true);
      },
      error_callback: (err) => reject(new Error(err && err.message ? err.message : 'Sign-in cancelled')),
    });
    tokenClient.requestAccessToken({ prompt: silent ? '' : 'consent' });
  });
}

export function signOut() {
  if (accessToken && window.google && window.google.accounts && window.google.accounts.oauth2) {
    try { window.google.accounts.oauth2.revoke(accessToken, () => {}); } catch { /* ignore */ }
  }
  accessToken = null;
  tokenExpiry = 0;
  writePref('driveSignedInOnce', null);
}

/** True when this browser has consented before — used to try a silent restore. */
export function hasSignedInBefore() {
  return readPrefs().driveSignedInOnce === true;
}

async function driveFetch(url, options = {}) {
  if (!isSignedIn()) throw new Error('Not signed in to Google Drive');
  const response = await fetch(url, {
    ...options,
    headers: { Authorization: `Bearer ${accessToken}`, ...(options.headers || {}) },
  });
  if (response.status === 401) {
    accessToken = null;
    throw new Error('Google session expired — sign in again');
  }
  if (!response.ok) {
    const body = await response.text().catch(() => '');
    throw new Error(`Drive request failed (${response.status}) ${body.slice(0, 160)}`);
  }
  return response;
}

async function getOrCreateAppFolder() {
  const query = encodeURIComponent(
    `name='${APP_FOLDER}' and mimeType='application/vnd.google-apps.folder' and trashed=false`,
  );
  const listRes = await driveFetch(
    `https://www.googleapis.com/drive/v3/files?q=${query}&fields=files(id,name)`,
  );
  const list = await listRes.json();
  if (list.files && list.files.length > 0) return list.files[0];

  const createRes = await driveFetch('https://www.googleapis.com/drive/v3/files?fields=id,name', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name: APP_FOLDER, mimeType: 'application/vnd.google-apps.folder' }),
  });
  return createRes.json();
}

/** yyyy-MM-dd_HH-mm-ss, matching SimpleGoogleDriveManager's file naming. */
function backupTimestamp() {
  const d = new Date();
  const p = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}_` +
    `${p(d.getHours())}-${p(d.getMinutes())}-${p(d.getSeconds())}`;
}

export function backupFileName() {
  return `expense_data_${backupTimestamp()}.json`;
}

/** Finds a file by exact name inside the app folder, or null. */
async function findInFolder(folderId, name) {
  const query = encodeURIComponent(`name='${name}' and '${folderId}' in parents and trashed=false`);
  const res = await driveFetch(
    `https://www.googleapis.com/drive/v3/files?q=${query}&fields=files(id,name)`,
  );
  const json = await res.json();
  return json.files && json.files.length > 0 ? json.files[0] : null;
}

/**
 * Uploads the current dataset to Drive.
 *
 * `rolling: true` (auto-sync) overwrites ROLLING_BACKUP_NAME in place, so
 * repeated syncs keep one file rather than accumulating one per edit burst.
 * `rolling: false` (the manual Upload button) writes a timestamped snapshot,
 * since an explicit upload is usually meant as a point-in-time restore point.
 */
export async function uploadToDrive({ rolling = false } = {}) {
  const folder = await getOrCreateAppFolder();
  const fileName = rolling ? ROLLING_BACKUP_NAME : backupFileName();
  const existing = rolling ? await findInFolder(folder.id, fileName) : null;

  if (existing) {
    const res = await driveFetch(
      `https://www.googleapis.com/upload/drive/v3/files/${existing.id}` +
        '?uploadType=media&fields=id,name,size,modifiedTime',
      {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: serialize(),
      },
    );
    const file = await res.json();
    return {
      fileId: file.id,
      fileName: file.name || fileName,
      message: `Successfully updated backup: ${file.name || fileName}`,
    };
  }

  const metadata = { name: fileName, parents: [folder.id], mimeType: 'application/json' };
  const boundary = `-------expense${Date.now()}`;
  const body =
    `--${boundary}\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n${JSON.stringify(metadata)}\r\n` +
    `--${boundary}\r\nContent-Type: application/json\r\n\r\n${serialize()}\r\n` +
    `--${boundary}--`;

  const res = await driveFetch(
    'https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id,name,size,modifiedTime',
    {
      method: 'POST',
      headers: { 'Content-Type': `multipart/related; boundary=${boundary}` },
      body,
    },
  );
  const file = await res.json();
  return {
    fileId: file.id,
    fileName: file.name || fileName,
    message: `Successfully uploaded backup: ${file.name || fileName}`,
  };
}

export async function listBackups() {
  const folder = await getOrCreateAppFolder();
  const query = encodeURIComponent(
    `name contains 'expense_data_' and '${folder.id}' in parents and trashed=false`,
  );
  const res = await driveFetch(
    `https://www.googleapis.com/drive/v3/files?q=${query}&orderBy=modifiedTime desc&fields=files(id,name,modifiedTime,size)`,
  );
  const json = await res.json();
  return (json.files || []).map((f) => ({
    fileId: f.id,
    fileName: f.name || '',
    modifiedTime: f.modifiedTime || '',
    size: `${f.size || 0} bytes`,
  }));
}

export async function downloadBackup(fileId) {
  const res = await driveFetch(`https://www.googleapis.com/drive/v3/files/${fileId}?alt=media`);
  return res.text();
}

export async function deleteBackup(fileId) {
  await driveFetch(`https://www.googleapis.com/drive/v3/files/${fileId}`, { method: 'DELETE' });
  return true;
}

// ── Validation + merge (SyncService.validateAndMergeData) ────────────────────

function validateDataIntegrity(appData) {
  for (const expense of appData.expenses) {
    if (!expense.id) return { valid: false, message: 'Invalid expense: missing ID' };
    if (expense.amount < 0) return { valid: false, message: 'Invalid expense: negative amount' };
  }
  if (appData.categories.length === 0) return { valid: false, message: 'No categories found' };
  for (const [category, budget] of Object.entries(appData.categoryBudgets)) {
    if (budget < 0) {
      return { valid: false, message: `Invalid budget for category '${category}': negative amount` };
    }
  }
  return { valid: true, message: '' };
}

function mergeLists(local, remote) {
  return distinct([...local, ...remote]);
}

function mergeBudgets(local, remote) {
  const merged = { ...local };
  for (const [key, value] of Object.entries(remote)) {
    if (!(key in merged) || value > merged[key]) merged[key] = value;
  }
  return merged;
}

function mergeMapOfLists(local, remote, cap) {
  const merged = {};
  for (const key of distinct([...Object.keys(local), ...Object.keys(remote)])) {
    const combined = distinct([...(local[key] || []), ...(remote[key] || [])]);
    merged[key] = cap ? combined.slice(0, cap) : combined;
  }
  return merged;
}

/**
 * MERGE_BY_DATE: remote is the source of truth for which expenses exist; when
 * both sides have an id, the newer date wins.
 *
 * With `dropMissing: true` (an explicit, user-initiated restore) expenses absent
 * from remote are treated as deleted elsewhere and dropped. With `false` — used
 * by the silent pull on startup — they are kept instead: a background merge must
 * never destroy local-only expenses, e.g. ones entered while offline whose
 * upload never landed. Deletions then propagate only via an explicit restore.
 */
function mergeExpenses(local, remote, { dropMissing = true } = {}) {
  const localById = new Map(local.expenses.map((e) => [e.id, e]));
  const remoteIds = new Set(remote.expenses.map((e) => e.id));

  const mergedExpenses = [];
  let expensesAdded = 0;
  let expensesUpdated = 0;
  let expensesRemoved = 0;
  let expensesKept = 0;
  let conflictsResolved = 0;

  for (const remoteExpense of remote.expenses) {
    const localExpense = localById.get(remoteExpense.id);
    if (!localExpense) {
      mergedExpenses.push(remoteExpense);
      expensesAdded++;
    } else if (isAfter(remoteExpense.date, localExpense.date)) {
      mergedExpenses.push(remoteExpense);
      expensesUpdated++;
      conflictsResolved++;
    } else {
      mergedExpenses.push(localExpense);
    }
  }

  for (const localExpense of local.expenses) {
    if (remoteIds.has(localExpense.id)) continue;
    if (dropMissing) {
      expensesRemoved++;
    } else {
      mergedExpenses.push(localExpense);
      expensesKept++;
    }
  }

  return {
    mergedExpenses,
    expensesAdded,
    expensesUpdated,
    expensesRemoved,
    expensesKept,
    conflictsResolved,
  };
}

/**
 * Validates a backup payload and merges it into the local data set.
 *
 * @param {string} jsonText Backup JSON.
 * @param {{ dropMissing?: boolean }} [options] `dropMissing: false` keeps
 *   local-only expenses — see mergeExpenses.
 * @returns {{ success: boolean, message: string, expensesAdded: number,
 *   expensesUpdated: number, expensesRemoved: number, expensesKept: number }}
 */
export function validateAndMerge(jsonText, { dropMissing = true } = {}) {
  let remote;
  try {
    remote = parseAppData(JSON.parse(jsonText));
  } catch {
    return { success: false, message: 'Corrupted backup file' };
  }

  const validation = validateDataIntegrity(remote);
  if (!validation.valid) return { success: false, message: validation.message };

  const result = mergeExpenses(localData, remote, { dropMissing });

  replaceAll({
    expenses: result.mergedExpenses,
    categories: mergeLists(localData.categories, remote.categories),
    subcategoriesMap: mergeMapOfLists(localData.subcategoriesMap, remote.subcategoriesMap),
    labels: mergeLists(localData.labels, remote.labels),
    paymentModes: mergeLists(localData.paymentModes, remote.paymentModes),
    paidVia: mergeLists(localData.paidVia, remote.paidVia),
    storeHistory: mergeLists(localData.storeHistory, remote.storeHistory),
    storeLocationHistory: mergeMapOfLists(localData.storeLocationHistory, remote.storeLocationHistory, 10),
    categoryBudgets: mergeBudgets(localData.categoryBudgets, remote.categoryBudgets),
    subcategoryBudgets: mergeBudgets(localData.subcategoryBudgets, remote.subcategoryBudgets),
    isDarkTheme: remote.isDarkTheme,
    recurringExpenses: mergeById(localData.recurringExpenses, remote.recurringExpenses),
  });

  return {
    success: true,
    message: 'Successfully merged data from backup.',
    expensesAdded: result.expensesAdded,
    expensesUpdated: result.expensesUpdated,
    expensesRemoved: result.expensesRemoved,
    expensesKept: result.expensesKept,
  };
}

/**
 * Startup pull: merges the newest Drive backup into local data without dropping
 * anything. Returns null when there is nothing to pull.
 */
export async function pullLatestBackup() {
  const backups = await listBackups();
  if (backups.length === 0) return null;
  const jsonText = await downloadBackup(backups[0].fileId);
  return validateAndMerge(jsonText, { dropMissing: false });
}

function mergeById(local, remote) {
  const byId = new Map(local.map((item) => [item.id, item]));
  for (const item of remote) if (!byId.has(item.id)) byId.set(item.id, item);
  return [...byId.values()];
}

/** Local JSON backup filename, used by the download-a-backup button. */
export function localBackupFileName() {
  return `expense_data_${today()}.json`;
}
