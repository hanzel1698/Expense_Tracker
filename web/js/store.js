// ── Persistent app state ──────────────────────────────────────────────────────
// Web port of data/DataRepository.kt. The Android app keeps one
// `expense_data.json` in its files dir; the web app keeps the same JSON under a
// localStorage key, so backups round-trip between the two.

import { defaultAppData, parseAppData } from './model.js';
import { debounce } from './util.js';

const STORAGE_KEY = 'expense_data.json';

/** Live app data — screens read this directly, like the Compose snapshot state. */
export let data = defaultAppData();

const listeners = new Set();

export function subscribe(fn) {
  listeners.add(fn);
  return () => listeners.delete(fn);
}

export function load() {
  try {
    const json = localStorage.getItem(STORAGE_KEY);
    if (!json) {
      data = defaultAppData();
      return data;
    }
    data = parseAppData(JSON.parse(json));
  } catch (err) {
    console.error('[store] load failed:', err);
    data = defaultAppData();
  }
  return data;
}

export function serialize(appData = data) {
  return JSON.stringify(appData, null, 2);
}

export function saveNow() {
  try {
    localStorage.setItem(STORAGE_KEY, serialize(data));
  } catch (err) {
    console.error('[store] save failed:', err);
  }
}

const saveDebounced = debounce(saveNow, 250);

/**
 * Apply a mutation, persist it, and notify listeners — the equivalent of
 * mutating snapshot state in the Compose UI (which auto-saves and auto-syncs).
 */
export function update(mutator, { rerender = true } = {}) {
  if (typeof mutator === 'function') mutator(data);
  saveDebounced();
  for (const fn of listeners) fn({ rerender });
  return data;
}

/** Replace the whole dataset (restore, sample data, clear-all). */
export function replaceAll(next) {
  data = parseAppData(next);
  saveNow();
  for (const fn of listeners) fn({ rerender: true });
  return data;
}

// ── Non-persistent UI preferences ────────────────────────────────────────────

const PREF_KEY = 'expense_tracker.prefs';

export function readPrefs() {
  try {
    return JSON.parse(localStorage.getItem(PREF_KEY) || '{}') || {};
  } catch {
    return {};
  }
}

export function writePref(key, value) {
  const prefs = readPrefs();
  if (value === null || value === undefined) delete prefs[key];
  else prefs[key] = value;
  try {
    localStorage.setItem(PREF_KEY, JSON.stringify(prefs));
  } catch (err) {
    console.error('[store] pref save failed:', err);
  }
}
