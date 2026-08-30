// ── Shared helpers ────────────────────────────────────────────────────────────
// Date arithmetic mirroring java.time.LocalDate / YearMonth (dates are ISO
// "YYYY-MM-DD" strings, year-months are "YYYY-MM"), plus number formatting that
// matches Kotlin's String.format usage in the Android app.

export function uuid() {
  if (crypto.randomUUID) return crypto.randomUUID();
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

// ── LocalDate ────────────────────────────────────────────────────────────────

const MS_DAY = 86400000;

/** Today as an ISO date string, in the viewer's local time zone. */
export function today() {
  return toISO(new Date());
}

/** Date object (local calendar day) → "YYYY-MM-DD". */
export function toISO(d) {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

/** "YYYY-MM-DD" → epoch millis at UTC midnight (safe for day arithmetic). */
function utc(iso) {
  const [y, m, d] = iso.split('-').map(Number);
  return Date.UTC(y, m - 1, d);
}

function fromUtc(ms) {
  const d = new Date(ms);
  const y = d.getUTCFullYear();
  const m = String(d.getUTCMonth() + 1).padStart(2, '0');
  const day = String(d.getUTCDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

export function isValidDate(iso) {
  return typeof iso === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(iso) && !Number.isNaN(utc(iso));
}

export function year(iso) { return Number(iso.slice(0, 4)); }
export function monthValue(iso) { return Number(iso.slice(5, 7)); }
export function dayOfMonth(iso) { return Number(iso.slice(8, 10)); }

/** ISO-8601 day of week: 1 = Monday … 7 = Sunday (matches java.time). */
export function dayOfWeek(iso) {
  const js = new Date(utc(iso)).getUTCDay(); // 0 = Sunday
  return js === 0 ? 7 : js;
}

export function plusDays(iso, n) { return fromUtc(utc(iso) + n * MS_DAY); }
export function minusDays(iso, n) { return plusDays(iso, -n); }
export function plusWeeks(iso, n) { return plusDays(iso, n * 7); }

export function plusMonths(iso, n) {
  const y = year(iso), m = monthValue(iso), d = dayOfMonth(iso);
  const total = y * 12 + (m - 1) + n;
  const ny = Math.floor(total / 12);
  const nm = (total % 12 + 12) % 12 + 1;
  const nd = Math.min(d, lengthOfMonth(ny, nm));
  return `${String(ny).padStart(4, '0')}-${String(nm).padStart(2, '0')}-${String(nd).padStart(2, '0')}`;
}
export function minusMonths(iso, n) { return plusMonths(iso, -n); }
export function plusYears(iso, n) { return plusMonths(iso, n * 12); }
export function minusYears(iso, n) { return plusMonths(iso, -n * 12); }

export function lengthOfMonth(y, m) { return new Date(Date.UTC(y, m, 0)).getUTCDate(); }

export function withDayOfMonth(iso, d) {
  const y = year(iso), m = monthValue(iso);
  const nd = Math.min(Math.max(d, 1), lengthOfMonth(y, m));
  return `${String(y).padStart(4, '0')}-${String(m).padStart(2, '0')}-${String(nd).padStart(2, '0')}`;
}

export function withDayOfYear(iso, n) { return plusDays(`${year(iso)}-01-01`, n - 1); }

export function daysBetween(a, b) { return Math.round((utc(b) - utc(a)) / MS_DAY); }

export function isBefore(a, b) { return utc(a) < utc(b); }
export function isAfter(a, b) { return utc(a) > utc(b); }
export function minDate(a, b) { return isBefore(a, b) ? a : b; }
export function maxDate(a, b) { return isAfter(a, b) ? a : b; }
export function inRange(iso, start, end) { return !isBefore(iso, start) && !isAfter(iso, end); }

// ── YearMonth ("YYYY-MM") ────────────────────────────────────────────────────

export function ymNow() { return today().slice(0, 7); }
export function ymOf(iso) { return iso.slice(0, 7); }
export function ymYear(ym) { return Number(ym.slice(0, 4)); }
export function ymMonth(ym) { return Number(ym.slice(5, 7)); }
export function ymAtDay(ym, d) {
  return `${ym}-${String(Math.min(d, lengthOfMonth(ymYear(ym), ymMonth(ym)))).padStart(2, '0')}`;
}
export function ymAtEnd(ym) { return ymAtDay(ym, lengthOfMonth(ymYear(ym), ymMonth(ym))); }
export function ymPlusMonths(ym, n) { return ymOf(plusMonths(ymAtDay(ym, 1), n)); }
export function ymMinusMonths(ym, n) { return ymPlusMonths(ym, -n); }
export function ymPlusYears(ym, n) { return ymPlusMonths(ym, n * 12); }
export function ymMinusYears(ym, n) { return ymPlusMonths(ym, -n * 12); }
export function ymWithMonth(ym, m) { return `${ymYear(ym)}-${String(m).padStart(2, '0')}`; }
export function ymIsAfter(a, b) { return a > b; }

// ── Display formatting ───────────────────────────────────────────────────────

const MONTHS_FULL = ['January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December'];
const MONTHS_SHORT = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun',
  'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
const DOW_SHORT = ['MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN'];
export const WEEKDAY_NAMES = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'];
export const WEEKDAY_ABBR = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];

export function monthFull(m) { return MONTHS_FULL[m - 1]; }
export function monthShort(m) { return MONTHS_SHORT[m - 1]; }
/** DayOfWeek.name.take(3) equivalent — "MON", "TUE", … */
export function dowShort(iso) { return DOW_SHORT[dayOfWeek(iso) - 1]; }
/** Month.name.take(3) equivalent — "JAN", "FEB", … (uppercase, like Kotlin) */
export function monthNameShort(m) { return MONTHS_SHORT[m - 1].toUpperCase(); }

/** "dd MMM yyyy" */
export function fmtDate(iso) {
  return `${String(dayOfMonth(iso)).padStart(2, '0')} ${monthShort(monthValue(iso))} ${year(iso)}`;
}
/** "dd MMM" */
export function fmtDayMonth(iso) {
  return `${String(dayOfMonth(iso)).padStart(2, '0')} ${monthShort(monthValue(iso))}`;
}
/** "dd/MM/yyyy" */
export function fmtSlash(iso) {
  return `${String(dayOfMonth(iso)).padStart(2, '0')}/${String(monthValue(iso)).padStart(2, '0')}/${year(iso)}`;
}
/** "dd/MM/yy" */
export function fmtSlashShort(iso) {
  return `${String(dayOfMonth(iso)).padStart(2, '0')}/${String(monthValue(iso)).padStart(2, '0')}/${String(year(iso)).slice(2)}`;
}
/** "MMM yyyy" */
export function fmtYearMonth(ym) { return `${monthShort(ymMonth(ym))} ${ymYear(ym)}`; }

// ── Numbers ──────────────────────────────────────────────────────────────────

function safe(n) { return Number.isFinite(n) ? n : 0; }
/** String.format("%.0f", x) */
export function fmt0(n) { return safe(n).toFixed(0); }
/** String.format("%.2f", x) */
export function fmt2(n) { return safe(n).toFixed(2); }
/** String.format("%.2f", x).replace(".00", "") — the app's compact amount form. */
export function fmt2Trim(n) { return fmt2(n).replace('.00', ''); }
/** "₹" + %.0f */
export function rupees0(n) { return `₹${fmt0(n)}`; }
/** "₹" + %.2f */
export function rupees2(n) { return `₹${fmt2(n)}`; }

/** Kotlin's String.toDoubleOrNull() */
export function toDoubleOrNull(s) {
  if (typeof s === 'number') return Number.isFinite(s) ? s : null;
  if (typeof s !== 'string') return null;
  const t = s.trim();
  if (t === '') return null;
  const n = Number(t);
  return Number.isFinite(n) ? n : null;
}

export function toIntOrNull(s) {
  const n = toDoubleOrNull(s);
  return n === null ? null : Math.trunc(n);
}

export function clamp(n, lo, hi) { return Math.min(Math.max(n, lo), hi); }

export function sumBy(list, fn) {
  let total = 0;
  for (const item of list) total += fn(item) || 0;
  return total;
}

export function distinct(list) { return [...new Set(list)]; }

export function groupBy(list, keyFn) {
  const map = new Map();
  for (const item of list) {
    const k = keyFn(item);
    if (!map.has(k)) map.set(k, []);
    map.get(k).push(item);
  }
  return map;
}

export function debounce(fn, ms) {
  let t = null;
  return (...args) => {
    if (t) clearTimeout(t);
    t = setTimeout(() => { t = null; fn(...args); }, ms);
  };
}
