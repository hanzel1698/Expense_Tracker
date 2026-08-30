// ── Aurora components ─────────────────────────────────────────────────────────
// Web ports of ui/modern/components/AuroraComponents.kt — card, section header,
// text field, search field, single/multi-select dropdowns, confirm/edit dialogs,
// date & date-range pickers, month switcher, progress bar, stat and chip.

import { h, icon, clear, onLongPress } from './dom.js';
import {
  today, year, monthValue, dayOfMonth, monthFull, lengthOfMonth,
  ymOf, ymAtDay, ymPlusMonths, ymYear, ymMonth, isBefore, isAfter, dayOfWeek,
} from './util.js';

// ── Card ─────────────────────────────────────────────────────────────────────

export function card(props = {}, ...children) {
  const { variant, className, onclick, longPress, ...rest } = props;
  const el = h('div.card', {
    class: [variant || '', className || '', onclick ? 'clickable' : ''].filter(Boolean).join(' '),
    onclick,
    ...rest,
  }, ...children);
  if (longPress) onLongPress(el, longPress);
  return el;
}

// ── Section header (AuroraSectionHeader) ─────────────────────────────────────

export function sectionHeader(title, { subtitle, trailing } = {}) {
  return h('div.section-header', {},
    h('div.grow', {},
      h('div.title-large', {}, title),
      subtitle && h('div.body-small.muted', {}, subtitle),
    ),
    trailing || null,
  );
}

// ── Text field (AuroraTextField) ─────────────────────────────────────────────

/**
 * @param {object} opts label, value, oninput(value, event), multiline, readOnly,
 *   disabled, error, supportingText, trailing (node), numeric, onclick
 */
export function textField(opts = {}) {
  const {
    label, value = '', oninput, multiline = false, readOnly = false,
    disabled = false, error = false, supportingText, trailing, numeric = false,
    onclick, placeholder = ' ', name,
  } = opts;

  const input = h(multiline ? 'textarea.field-input' : 'input.field-input', {
    value: value ?? '',
    placeholder,
    readonly: readOnly || disabled ? true : null,
    inputmode: numeric ? 'decimal' : null,
    name: name || null,
    oninput: oninput ? (e) => oninput(e.target.value, e) : null,
    onclick: onclick || null,
  });
  if (multiline) input.value = value ?? '';

  const wrap = h('div.field', {
    class: [error ? 'error' : '', disabled ? 'disabled' : '', trailing ? 'has-trailing' : ''].filter(Boolean).join(' '),
  },
    input,
    h('label.field-label', {}, label),
    trailing ? h('div.field-trailing', {}, trailing) : null,
    supportingText ? h('div.field-support', {}, supportingText) : null,
  );
  wrap.inputEl = input;
  if (onclick) wrap.addEventListener('click', onclick);
  return wrap;
}

// ── Search field (AuroraSearchField) ─────────────────────────────────────────

export function searchField({ value = '', placeholder = 'Search…', oninput } = {}) {
  const input = h('input', {
    value,
    placeholder,
    type: 'search',
    oninput: oninput ? (e) => oninput(e.target.value) : null,
  });
  const el = h('div.search-field', {}, icon('search'), input);
  el.inputEl = input;
  return el;
}

// ── Dropdown menus ───────────────────────────────────────────────────────────

let openMenuCloser = null;

function closeOpenMenu() {
  if (openMenuCloser) {
    const fn = openMenuCloser;
    openMenuCloser = null;
    fn();
  }
}

document.addEventListener('pointerdown', (e) => {
  if (openMenuCloser && !e.target.closest('.dropdown, .suggest-menu')) closeOpenMenu();
}, true);

function mountMenu(host, menu) {
  host.appendChild(menu);
  host.classList.add('open');
  // Flip above the anchor when there isn't room below.
  const rect = menu.getBoundingClientRect();
  if (rect.bottom > window.innerHeight - 8) menu.classList.add('drop-up');

  const close = () => {
    menu.remove();
    host.classList.remove('open');
  };
  closeOpenMenu();
  openMenuCloser = close;
  return close;
}

/** AuroraDropdown — read-only outlined field that opens a single-select menu. */
export function dropdown({ label, options = [], selected = '', onSelect, disabled = false }) {
  const host = h('div.dropdown');

  const valueEl = h('span.dd-value', {}, selected === '' ? '' : selected);
  const box = h('button.field-box', { type: 'button', disabled: disabled || null },
    valueEl,
    icon('arrowRight', 'dd-arrow'),
  );
  // Rotate the arrow to point down (M3 uses a caret; reuse the chevron asset).
  box.querySelector('.dd-arrow').style.transform = 'translateY(-50%) rotate(90deg)';

  const labelEl = h('label.field-label', {}, label);
  if (selected === '' ) {
    labelEl.style.top = '17px';
    labelEl.style.fontSize = '16px';
    labelEl.style.lineHeight = '22px';
  }

  host.appendChild(box);
  host.appendChild(labelEl);

  box.addEventListener('click', (e) => {
    e.stopPropagation();
    if (host.classList.contains('open')) { closeOpenMenu(); return; }

    const menu = h('div.dd-menu');
    for (const option of options) {
      const isSelected = option === selected && option !== '';
      menu.appendChild(h('button.dd-item', {
        type: 'button',
        class: [isSelected ? 'selected' : '', option.startsWith('+ ') ? 'add-new' : ''].filter(Boolean).join(' '),
        onclick: (ev) => {
          ev.stopPropagation();
          closeOpenMenu();
          onSelect && onSelect(option);
        },
      },
        h('span.dd-text', {}, option === '' ? '—' : option),
        isSelected ? icon('check', 'dd-check') : null,
      ));
    }
    const close = mountMenu(host, menu);
    host._close = close;
  });

  return host;
}

/** AuroraMultiSelectDropdown — "All" / "a, b" / "n selected" summary + checks. */
export function multiSelectDropdown({
  label, options = [], selected = new Set(), onToggle, showSearch = false,
}) {
  const selectedSet = selected instanceof Set ? selected : new Set(selected);
  const host = h('div.dropdown');

  const display = selectedSet.size === 0
    ? 'All'
    : selectedSet.size <= 2 ? [...selectedSet].join(', ') : `${selectedSet.size} selected`;

  const box = h('button.field-box', { type: 'button' },
    h('span.dd-value', {}, display),
    icon('arrowRight', 'dd-arrow'),
  );
  box.querySelector('.dd-arrow').style.transform = 'translateY(-50%) rotate(90deg)';

  host.appendChild(box);
  if (label) host.appendChild(h('label.field-label', {}, label));

  box.addEventListener('click', (e) => {
    e.stopPropagation();
    if (host.classList.contains('open')) { closeOpenMenu(); return; }

    const menu = h('div.dd-menu');
    let query = '';

    const renderItems = () => {
      // Keep the search box; replace only the option rows.
      [...menu.querySelectorAll('.dd-item')].forEach((n) => n.remove());
      const hasAddNew = options.includes('+ Add New');
      const core = options.filter((o) => o !== '+ Add New');
      const filtered = !showSearch || query === ''
        ? core
        : core.filter((o) => o.toLowerCase().includes(query.toLowerCase()));
      const list = hasAddNew ? [...filtered, '+ Add New'] : filtered;

      for (const option of list) {
        const isSelected = selectedSet.has(option);
        menu.appendChild(h('button.dd-item', {
          type: 'button',
          class: [isSelected ? 'selected' : '', option.startsWith('+ ') ? 'add-new' : ''].filter(Boolean).join(' '),
          onclick: (ev) => {
            ev.stopPropagation();
            if (option.startsWith('+ ')) closeOpenMenu();
            onToggle && onToggle(option);
          },
        },
          icon('check', `dd-check${isSelected ? '' : ' empty'}`),
          h('span.dd-text', {}, option),
        ));
      }
    };

    if (showSearch) {
      const search = searchField({
        placeholder: 'Search…',
        oninput: (v) => { query = v; renderItems(); },
      });
      menu.appendChild(h('div.dd-search', {}, search));
      setTimeout(() => search.inputEl.focus(), 0);
    }
    renderItems();
    mountMenu(host, menu);
  });

  return host;
}

// ── Dialogs ──────────────────────────────────────────────────────────────────

function openScrim(content, { onDismiss, dismissable = true } = {}) {
  const scrim = h('div.scrim', {
    onclick: (e) => {
      if (dismissable && e.target === scrim) { close(); onDismiss && onDismiss(); }
    },
  }, content);

  function close() {
    scrim.remove();
    document.removeEventListener('keydown', onKey);
  }
  function onKey(e) {
    if (e.key === 'Escape' && dismissable) { close(); onDismiss && onDismiss(); }
  }

  document.addEventListener('keydown', onKey);
  document.body.appendChild(scrim);
  scrim.closeDialog = close;
  return close;
}

/** AuroraConfirmDialog */
export function confirmDialog({
  title, message, onConfirm, onDismiss,
  confirmText = 'Confirm', dismissText = 'Cancel', destructive = false,
}) {
  let close;
  const body = h('div.dialog-card', {},
    h('div.headline-small.dialog-title', {}, title),
    h('div.body-medium', { style: { whiteSpace: 'pre-wrap' } }, message),
    h('div.dialog-actions', {},
      h('button.btn.text', { type: 'button', onclick: () => { close(); onDismiss && onDismiss(); } }, dismissText),
      h('button.btn', {
        type: 'button',
        class: destructive ? 'destructive' : '',
        onclick: () => { close(); onConfirm && onConfirm(); },
      }, confirmText),
    ),
  );
  close = openScrim(body, { onDismiss });
  return close;
}

/** AuroraEditDialog — a single text field with Cancel / Save. */
export function editDialog({ title, initialValue = '', fieldLabel = 'Name', onConfirm, onDismiss }) {
  let close;
  const field = textField({ label: fieldLabel, value: initialValue });

  const submit = () => {
    const value = field.inputEl.value;
    close();
    onConfirm && onConfirm(value);
  };

  field.inputEl.addEventListener('keydown', (e) => { if (e.key === 'Enter') submit(); });

  const body = h('div.dialog-card', {},
    h('div.row.between', {},
      h('div.headline-small.grow.clamp-2', {}, title),
      h('button.icon-btn', { type: 'button', onclick: () => { close(); onDismiss && onDismiss(); } }, icon('close')),
    ),
    h('div.mt-16', {}, field),
    h('div.dialog-actions', {},
      h('button.btn.text', { type: 'button', onclick: () => { close(); onDismiss && onDismiss(); } }, 'Cancel'),
      h('button.btn', { type: 'button', onclick: submit }, 'Save'),
    ),
  );
  close = openScrim(body, { onDismiss });
  setTimeout(() => field.inputEl.focus(), 0);
  return close;
}

/** A dialog whose body is supplied by the caller (used by entry/settings screens). */
export function customDialog(buildBody, { onDismiss, wide = false, dismissable = true } = {}) {
  const cardEl = h('div.dialog-card', { class: wide ? 'wide' : '' });
  let close;
  const api = { close: () => close() };
  cardEl.appendChild(buildBody(api));
  close = openScrim(cardEl, { onDismiss, dismissable });
  api.close = close;
  api.body = cardEl;
  return api;
}

// ── Date pickers ─────────────────────────────────────────────────────────────

function monthGrid(ym, { isSelected, isInRange, isRangeStart, isRangeEnd, onPick }) {
  const y = ymYear(ym);
  const m = ymMonth(ym);
  const grid = h('div.picker-grid');

  for (const wd of ['S', 'M', 'T', 'W', 'T', 'F', 'S']) {
    grid.appendChild(h('div.pk-wd', {}, wd));
  }

  const first = ymAtDay(ym, 1);
  // Grid starts on the Sunday on/before the 1st (matches AuroraCalendar).
  const lead = dayOfWeek(first) % 7;
  for (let i = 0; i < lead; i++) grid.appendChild(h('div'));

  const todayIso = today();
  for (let d = 1; d <= lengthOfMonth(y, m); d++) {
    const iso = ymAtDay(ym, d);
    const classes = ['pk-day'];
    if (iso === todayIso) classes.push('today');
    if (isInRange && isInRange(iso)) classes.push('in-range');
    if (isRangeStart && isRangeStart(iso)) classes.push('range-start');
    if (isRangeEnd && isRangeEnd(iso)) classes.push('range-end');
    if (isSelected && isSelected(iso)) classes.push('selected');
    grid.appendChild(h('button', {
      type: 'button',
      class: classes.join(' '),
      onclick: () => onPick(iso),
    }, String(d)));
  }
  return grid;
}

function pickerHeader(ym, onChange) {
  return h('div.row.between.mb-8', {},
    h('button.icon-btn', { type: 'button', onclick: () => onChange(ymPlusMonths(ym, -1)) }, icon('arrowLeft')),
    h('div.title-medium', {}, `${monthFull(ymMonth(ym))} ${ymYear(ym)}`),
    h('button.icon-btn', { type: 'button', onclick: () => onChange(ymPlusMonths(ym, 1)) }, icon('arrowRight')),
  );
}

/** AuroraDatePickerDialog — single date; calls onDateSelected(iso|null). */
export function datePickerDialog({ initial, onDateSelected, onDismiss }) {
  let selected = initial || today();
  let ym = ymOf(selected);
  let close;

  const bodyEl = h('div');

  function render() {
    clear(bodyEl);
    bodyEl.appendChild(h('div.headline-small.dialog-title', {}, 'Select date'));
    bodyEl.appendChild(h('div.body-medium.muted.mb-12', {}, selected ? fmtLong(selected) : ''));
    bodyEl.appendChild(pickerHeader(ym, (next) => { ym = next; render(); }));
    bodyEl.appendChild(monthGrid(ym, {
      isSelected: (iso) => iso === selected,
      onPick: (iso) => { selected = iso; render(); },
    }));
    bodyEl.appendChild(h('div.dialog-actions', {},
      h('button.btn.text', { type: 'button', onclick: () => { close(); onDismiss && onDismiss(); } }, 'Cancel'),
      h('button.btn.text', {
        type: 'button',
        style: { fontWeight: '600' },
        onclick: () => { close(); onDateSelected && onDateSelected(selected); onDismiss && onDismiss(); },
      }, 'OK'),
    ));
  }

  render();
  close = openScrim(h('div.dialog-card', {}, bodyEl), { onDismiss });
  return close;
}

/** AuroraDateRangePickerDialog — start/end; calls onDateRangeSelected(a, b). */
export function dateRangePickerDialog({ initialStart, initialEnd, onDateRangeSelected, onDismiss }) {
  let start = initialStart || null;
  let end = initialEnd || null;
  let ym = ymOf(start || today());
  let close;

  const bodyEl = h('div');

  function pick(iso) {
    if (!start || (start && end)) { start = iso; end = null; }
    else if (isBefore(iso, start)) { start = iso; }
    else { end = iso; }
    render();
  }

  function render() {
    clear(bodyEl);
    bodyEl.appendChild(h('div.headline-small.dialog-title', {}, 'Select range'));
    bodyEl.appendChild(h('div.body-medium.muted.mb-12', {},
      start ? `${fmtLong(start)}${end ? `  –  ${fmtLong(end)}` : '  –  …'}` : 'Pick a start date'));
    bodyEl.appendChild(pickerHeader(ym, (next) => { ym = next; render(); }));
    bodyEl.appendChild(monthGrid(ym, {
      isSelected: (iso) => iso === start || iso === end,
      isInRange: (iso) => !!(start && end && !isBefore(iso, start) && !isAfter(iso, end)),
      isRangeStart: (iso) => iso === start,
      isRangeEnd: (iso) => iso === end,
      onPick: pick,
    }));
    bodyEl.appendChild(h('div.dialog-actions', {},
      h('button.btn.text', { type: 'button', onclick: () => { close(); onDismiss && onDismiss(); } }, 'Cancel'),
      h('button.btn.text', {
        type: 'button',
        style: { fontWeight: '600' },
        onclick: () => {
          close();
          onDateRangeSelected && onDateRangeSelected(start, end || start);
          onDismiss && onDismiss();
        },
      }, 'OK'),
    ));
  }

  render();
  close = openScrim(h('div.dialog-card', {}, bodyEl), { onDismiss });
  return close;
}

function fmtLong(iso) {
  return `${monthFull(monthValue(iso))} ${dayOfMonth(iso)}, ${year(iso)}`;
}

// ── Small helpers ────────────────────────────────────────────────────────────

/** AuroraMonthSwitcher — ◀ MONTH YEAR ▶ inside a pill. */
export function monthSwitcher({ monthLabel, yearLabel, onPrevious, onNext }) {
  return h('div.month-switch', {},
    h('button.icon-btn', { type: 'button', 'aria-label': 'Previous', onclick: onPrevious }, icon('arrowLeft')),
    h('div.col.ms-label', {},
      h('div.label-large', {}, monthLabel),
      h('div.label-small.ms-year', {}, yearLabel),
    ),
    h('button.icon-btn', { type: 'button', 'aria-label': 'Next', onclick: onNext }, icon('arrowRight')),
  );
}

/** AuroraProgressBar — rounded bar with over-budget tinting. */
export function progressBar(spent, budget, { thin = false, trackColor, barColor } = {}) {
  const fraction = budget > 0 ? Math.min(Math.max(spent / budget, 0), 1) : 0;
  const isOver = budget > 0 && spent > budget;
  const bar = h('i', { style: { width: `${fraction * 100}%` } });
  if (barColor && !isOver) bar.style.background = barColor;
  const el = h('div.progress', { class: [thin ? 'thin' : '', isOver ? 'over' : ''].filter(Boolean).join(' ') }, bar);
  if (trackColor) el.style.background = trackColor;
  return el;
}

/** AuroraStat — small label over a large value. */
export function stat(label, value, { valueColor, alignEnd = false } = {}) {
  return h('div.col', { style: alignEnd ? { alignItems: 'flex-end' } : null },
    h('div.label-medium.muted', {}, label),
    h('div.title-large.ellipsis', { style: valueColor ? { color: valueColor } : null }, value),
  );
}

/** AuroraChip — small static label chip. */
export function tag(textValue, { variant } = {}) {
  return h('span.tag', { class: variant || '' }, textValue);
}

/** M3 FilterChip / AssistChip. */
export function chip(label, { selected = false, onclick, leading, className = '' } = {}) {
  return h('button.chip', {
    type: 'button',
    class: className,
    'aria-pressed': String(!!selected),
    onclick,
  }, leading || null, h('span.ellipsis', {}, label));
}

/** M3 Switch. */
export function toggle(checked, onChange, label) {
  return h('button.switch', {
    type: 'button',
    role: 'switch',
    'aria-checked': String(!!checked),
    'aria-label': label || 'Toggle',
    onclick: () => onChange(!checked),
  });
}

/** SingleChoiceSegmentedButtonRow. */
export function segmented(options, selectedIndex, onSelect) {
  return h('div.segmented', {}, ...options.map((label, index) => h('button', {
    type: 'button',
    'aria-pressed': String(index === selectedIndex),
    onclick: () => onSelect(index),
  }, index === selectedIndex ? icon('check') : null, label)));
}
