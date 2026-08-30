// ── Charts ────────────────────────────────────────────────────────────────────
// Canvas ports of ui/modern/components/AuroraCharts.kt — the same geometry,
// rounded bars, trend line, label auto-fit and tap/long-press behaviour.

import { h } from './dom.js';
import { fmt0, sumBy } from './util.js';

function cssVar(name) {
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim();
}

/**
 * Applies an alpha to a color. Canvas 2D can't parse color-mix(), so hex values
 * (which every Aurora palette token is) are expanded to rgba() by hand.
 */
function withAlpha(color, alpha) {
  const hex = color.trim();
  const m = /^#([0-9a-f]{3}|[0-9a-f]{6})$/i.exec(hex);
  if (!m) return hex;
  const raw = m[1].length === 3 ? m[1].split('').map((c) => c + c).join('') : m[1];
  const r = parseInt(raw.slice(0, 2), 16);
  const g = parseInt(raw.slice(2, 4), 16);
  const b = parseInt(raw.slice(4, 6), 16);
  return `rgba(${r}, ${g}, ${b}, ${alpha})`;
}

function setupCanvas(canvas, cssWidth, cssHeight) {
  const dpr = window.devicePixelRatio || 1;
  canvas.width = Math.max(1, Math.round(cssWidth * dpr));
  canvas.height = Math.max(1, Math.round(cssHeight * dpr));
  const ctx = canvas.getContext('2d');
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  ctx.clearRect(0, 0, cssWidth, cssHeight);
  return ctx;
}

function roundRect(ctx, x, y, w, hgt, r) {
  const radius = Math.max(0, Math.min(r, Math.min(w, hgt) / 2));
  ctx.beginPath();
  ctx.moveTo(x + radius, y);
  ctx.lineTo(x + w - radius, y);
  ctx.quadraticCurveTo(x + w, y, x + w, y + radius);
  ctx.lineTo(x + w, y + hgt - radius);
  ctx.quadraticCurveTo(x + w, y + hgt, x + w - radius, y + hgt);
  ctx.lineTo(x + radius, y + hgt);
  ctx.quadraticCurveTo(x, y + hgt, x, y + hgt - radius);
  ctx.lineTo(x, y + radius);
  ctx.quadraticCurveTo(x, y, x + radius, y);
  ctx.closePath();
  ctx.fill();
}

/** Shrinks `size` until `label` fits `maxWidth`. */
function fitTextSize(ctx, label, size, maxWidth, weight = '') {
  ctx.font = `${weight} ${size}px system-ui, sans-serif`.trim();
  const width = ctx.measureText(label).width;
  return width > maxWidth && width > 0 ? size * (maxWidth / width) : size;
}

/**
 * AuroraBarChart — rounded bars, amount labels above, period labels below,
 * a trend line, tap to select (and drill through via onBarClicked).
 * @param {object[]} chartData [{ label, value, period, endDate }]
 */
export function barChart(chartData, { height = 220, onBarClicked } = {}) {
  const box = h('div.chart-box', { style: { height: `${height}px` } });

  if (!chartData || chartData.length === 0) {
    box.classList.add('chart-empty');
    box.appendChild(h('span', {}, 'No data'));
    return box;
  }

  const canvas = h('canvas');
  box.appendChild(canvas);

  let selectedIndex = null;
  const maxSpent = Math.max(...chartData.map((p) => p.value), 100);

  function layout(width) {
    const itemCount = Math.max(chartData.length, 1);
    const barWidth = width / (itemCount * 1.5);
    const spacing = (width - barWidth * itemCount) / (itemCount + 1);
    return { itemCount, barWidth, spacing };
  }

  function draw() {
    const width = box.clientWidth;
    const heightPx = box.clientHeight;
    if (width === 0 || heightPx === 0) return;

    const ctx = setupCanvas(canvas, width, heightPx);
    const pad = 8;
    const w = width - pad * 2;
    const hgt = heightPx - pad * 2;
    ctx.translate(pad, pad);

    const barColor = cssVar('--primary');
    const selectedColor = cssVar('--tertiary');
    const trendLineColor = cssVar('--outline');
    const labelColor = cssVar('--on-surface-variant');

    const { barWidth, spacing } = layout(w);

    // Unified label size: shrink until the widest label fits a bar.
    ctx.font = '100px system-ui, sans-serif';
    let maxLabelWidth = 1;
    for (const point of chartData) {
      maxLabelWidth = Math.max(maxLabelWidth, ctx.measureText(point.label).width);
    }
    const unifiedSize = Math.min(100 * (barWidth / maxLabelWidth), 14);
    const labelPadding = 4;
    const topTextHeight = unifiedSize * 1.5 + labelPadding;
    const bottomTextHeight = unifiedSize * 1.5 + labelPadding;
    const usableHeight = Math.max(hgt - topTextHeight - bottomTextHeight, 0);

    // Bars
    chartData.forEach((point, index) => {
      const x = spacing + index * (barWidth + spacing);
      const barH = (point.value / maxSpent) * usableHeight;
      ctx.fillStyle = index === selectedIndex ? selectedColor : barColor;
      if (barH > 0) {
        roundRect(ctx, x, hgt - bottomTextHeight - barH, barWidth, barH, barWidth / 3);
      } else {
        ctx.fillStyle = withAlpha(barColor, 0.15);
        roundRect(ctx, x, hgt - bottomTextHeight - 2, barWidth, 2, 1);
      }
    });

    // Trend line
    if (chartData.length > 1) {
      ctx.beginPath();
      chartData.forEach((point, index) => {
        const x = spacing + index * (barWidth + spacing) + barWidth / 2;
        const y = hgt - bottomTextHeight - (point.value / maxSpent) * usableHeight;
        if (index === 0) ctx.moveTo(x, y); else ctx.lineTo(x, y);
      });
      ctx.strokeStyle = withAlpha(trendLineColor, 0.6);
      ctx.lineWidth = 1.5;
      ctx.stroke();
    }

    // Labels
    ctx.textAlign = 'center';
    ctx.textBaseline = 'alphabetic';
    ctx.fillStyle = labelColor;
    chartData.forEach((point, index) => {
      const x = spacing + index * (barWidth + spacing);
      const barH = (point.value / maxSpent) * usableHeight;
      const barTop = hgt - bottomTextHeight - barH;
      const centerX = x + barWidth / 2;
      const weight = index === selectedIndex ? '700' : '';

      if (point.value > 0) {
        const amountText = `₹${Math.trunc(point.value)}`;
        const size = fitTextSize(ctx, amountText, unifiedSize, barWidth, weight);
        ctx.font = `${weight} ${size}px system-ui, sans-serif`.trim();
        ctx.fillText(amountText, centerX, barTop - 3);
      }

      const rangeText = point.endDate && point.periodIsDate
        ? (point.period === point.endDate
          ? String(Number(point.period.slice(8, 10)))
          : `${Number(point.period.slice(8, 10))}-${Number(point.endDate.slice(8, 10))}`)
        : point.label;
      const size = fitTextSize(ctx, rangeText, unifiedSize, barWidth, weight);
      ctx.font = `${weight} ${size}px system-ui, sans-serif`.trim();
      ctx.fillText(rangeText, centerX, hgt - 2);
    });
  }

  canvas.addEventListener('click', (e) => {
    const rect = canvas.getBoundingClientRect();
    const x = e.clientX - rect.left - 8;
    const { barWidth, spacing } = layout(rect.width - 16);

    let hit = -1;
    chartData.forEach((_, i) => {
      const barX = spacing + i * (barWidth + spacing);
      if (x >= barX && x <= barX + barWidth) hit = i;
    });

    if (hit === -1 || hit === selectedIndex) {
      selectedIndex = null;
      draw();
    } else {
      selectedIndex = hit;
      draw();
      onBarClicked && onBarClicked(chartData[hit]);
    }
  });

  observeResize(box, draw);
  return box;
}

/**
 * AuroraHorizontalBarChart — category breakdown; tap drills into subcategories
 * (or opens the expense list when already drilled in), long-press always opens.
 */
export function horizontalBarChart({
  expenses, categories, subcategoriesMap, selectedCategory,
  onCategorySelected, onBarLongPressed, height = 300,
}) {
  const chartData = selectedCategory
    ? (subcategoriesMap[selectedCategory] || [])
      .map((sub) => ({
        label: sub,
        value: sumBy(expenses.filter((e) => e.category === selectedCategory && e.subcategory === sub), (e) => e.amount),
      }))
      .filter((d) => d.value > 0)
      .sort((a, b) => b.value - a.value)
    : categories
      .map((cat) => ({
        label: cat,
        value: sumBy(expenses.filter((e) => e.category === cat), (e) => e.amount),
      }))
      .filter((d) => d.value > 0)
      .sort((a, b) => b.value - a.value);

  const box = h('div.chart-box', { style: { height: `${height}px` } });

  if (chartData.length === 0) {
    box.classList.add('chart-empty');
    box.appendChild(h('span', {}, 'No spending in this period'));
    return box;
  }

  const canvas = h('canvas');
  box.appendChild(canvas);

  let selectedIndex = null;
  const maxValue = Math.max(...chartData.map((d) => d.value), 100);
  const BAR_H = 26;
  const SPACING = 8;

  function draw() {
    const width = box.clientWidth;
    const heightPx = box.clientHeight;
    if (width === 0 || heightPx === 0) return;

    const ctx = setupCanvas(canvas, width, heightPx);
    const pad = 8;
    ctx.translate(pad, pad);
    const w = width - pad * 2;

    const palette = [1, 2, 3, 4, 5, 6, 7, 8].map((n) => cssVar(`--chart-${n}`));
    const labelColor = cssVar('--on-surface');
    const valueColor = cssVar('--on-surface-variant');
    const selectedTint = cssVar('--tertiary');

    ctx.font = '600 13px system-ui, sans-serif';
    let maxLabelWidth = 0;
    for (const point of chartData) {
      maxLabelWidth = Math.max(maxLabelWidth, ctx.measureText(point.label).width);
    }
    const labelWidth = Math.min(Math.max(maxLabelWidth, 60), Math.min(140, w * 0.4));
    const valueTextWidth = 62;
    const usableWidth = Math.max(w - labelWidth - valueTextWidth - 16, 0);

    chartData.forEach((point, index) => {
      const y = SPACING + index * (BAR_H + SPACING);
      const barW = Math.max((point.value / maxValue) * usableWidth, 4);
      const base = palette[index % palette.length];

      ctx.fillStyle = withAlpha(base, 0.14);
      roundRect(ctx, labelWidth + 8, y, usableWidth, BAR_H, BAR_H / 2);
      ctx.fillStyle = index === selectedIndex ? selectedTint : base;
      roundRect(ctx, labelWidth + 8, y, barW, BAR_H, BAR_H / 2);
    });

    ctx.textBaseline = 'middle';
    chartData.forEach((point, index) => {
      const y = SPACING + index * (BAR_H + SPACING);
      const weight = index === selectedIndex ? '700' : '600';

      ctx.fillStyle = labelColor;
      ctx.textAlign = 'left';
      ctx.font = `${weight} 13px system-ui, sans-serif`;
      let display = point.label;
      while (ctx.measureText(display).width > labelWidth && display.length > 3) {
        display = display.slice(0, -2);
      }
      if (display !== point.label) display = `${display.slice(0, -1)}…`;
      ctx.fillText(display, 2, y + BAR_H / 2);

      ctx.fillStyle = valueColor;
      ctx.textAlign = 'right';
      ctx.fillText(`₹${fmt0(point.value)}`, w - 2, y + BAR_H / 2);
    });
  }

  function hitIndex(clientY) {
    const rect = canvas.getBoundingClientRect();
    const y = clientY - rect.top - 8;
    let hit = -1;
    chartData.forEach((_, i) => {
      const barY = SPACING + i * (BAR_H + SPACING);
      if (y >= barY && y <= barY + BAR_H) hit = i;
    });
    return hit;
  }

  let longPressed = false;
  let pressTimer = null;

  canvas.addEventListener('pointerdown', (e) => {
    longPressed = false;
    const clientY = e.clientY;
    pressTimer = setTimeout(() => {
      longPressed = true;
      const hit = hitIndex(clientY);
      if (hit !== -1) {
        const category = selectedCategory || chartData[hit].label;
        const subcategory = selectedCategory ? chartData[hit].label : null;
        onBarLongPressed && onBarLongPressed(category, subcategory);
      }
    }, 500);
  });
  const cancelPress = () => { if (pressTimer) { clearTimeout(pressTimer); pressTimer = null; } };
  canvas.addEventListener('pointerup', cancelPress);
  canvas.addEventListener('pointerleave', cancelPress);
  canvas.addEventListener('pointercancel', cancelPress);
  canvas.addEventListener('contextmenu', (e) => e.preventDefault());

  canvas.addEventListener('click', (e) => {
    if (longPressed) { longPressed = false; return; }
    const hit = hitIndex(e.clientY);
    if (hit === -1 || hit === selectedIndex) {
      selectedIndex = null;
      draw();
      onCategorySelected && onCategorySelected(null);
    } else {
      selectedIndex = hit;
      draw();
      if (selectedCategory) onBarLongPressed && onBarLongPressed(selectedCategory, chartData[hit].label);
      else onCategorySelected && onCategorySelected(chartData[hit].label);
    }
  });

  observeResize(box, draw);
  return box;
}

/** Redraws on size or theme changes; cleans itself up when detached. */
function observeResize(box, draw) {
  const ro = new ResizeObserver(() => draw());
  ro.observe(box);
  requestAnimationFrame(draw);

  const mo = new MutationObserver(() => {
    if (!box.isConnected) { ro.disconnect(); mo.disconnect(); themeObserver.disconnect(); }
  });
  mo.observe(document.body, { childList: true, subtree: true });

  const themeObserver = new MutationObserver(() => draw());
  themeObserver.observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme'] });
}
