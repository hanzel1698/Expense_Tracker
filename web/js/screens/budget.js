// ── Budget ────────────────────────────────────────────────────────────────────
// Port of ui/modern/screens/ModernBudgetScreen.kt — total allocation card,
// per-category budgets, allocated/unallocated summary for the selected
// category, and subcategory budgets. Editing opens the quick-set dialog.

import { h, icon } from '../dom.js';
import { card, monthSwitcher, progressBar, textField, customDialog } from '../components.js';
import { data, update } from '../store.js';
import {
  ymNow, ymOf, ymMonth, ymYear, ymPlusMonths, ymMinusMonths,
  monthShort, fmt0, fmt2, sumBy, toDoubleOrNull,
} from '../util.js';

const state = {
  currentMonth: ymNow(),
  selectedCategory: null,
};

export function renderBudget(ctx) {
  const categories = data.categories;
  if (!state.selectedCategory || !categories.includes(state.selectedCategory)) {
    state.selectedCategory = categories[0] || null;
  }

  const monthExpenses = data.expenses.filter((e) => ymOf(e.date) === state.currentMonth);
  const totalSpent = sumBy(monthExpenses, (e) => e.amount);
  const overallBudget = sumBy(categories, (c) => data.categoryBudgets[c] || 0);

  const categorySpending = {};
  const subcategorySpending = {};
  for (const e of monthExpenses) {
    categorySpending[e.category] = (categorySpending[e.category] || 0) + e.amount;
    const key = `${e.category}/${e.subcategory}`;
    subcategorySpending[key] = (subcategorySpending[key] || 0) + e.amount;
  }

  const root = h('div.screen-pad');

  root.appendChild(h('div.row.between.mb-16', {},
    h('div.headline-large', {}, 'Budget'),
    monthSwitcher({
      monthLabel: monthShort(ymMonth(state.currentMonth)),
      yearLabel: String(ymYear(state.currentMonth)),
      onPrevious: () => { state.currentMonth = ymMinusMonths(state.currentMonth, 1); ctx.rerender(); },
      onNext: () => { state.currentMonth = ymPlusMonths(state.currentMonth, 1); ctx.rerender(); },
    }),
  ));

  // Total allocation
  const isOverBudget = totalSpent > overallBudget && overallBudget > 0;
  root.appendChild(card({ variant: 'primary-container' }, h('div.pad-16', {},
    h('div.label-medium', { style: { opacity: '.75' } }, 'Total budget allocation'),
    h('div.row.between', { style: { alignItems: 'flex-end' } },
      h('div.headline-medium', {}, `₹${fmt0(overallBudget)}`),
      h('div.title-small', {
        style: { color: isOverBudget ? 'var(--error)' : 'inherit' },
      }, `₹${fmt0(totalSpent)} spent`),
    ),
    h('div', { style: { height: '10px' } }),
    progressBar(totalSpent, overallBudget, {
      trackColor: 'color-mix(in srgb, var(--on-primary-container) 15%, transparent)',
    }),
  )));

  // Categories
  root.appendChild(h('div.title-medium.muted.mt-20.mb-8', {}, 'Categories'));

  const categoryList = h('div.col.gap-8');
  for (const category of categories) {
    const isSelected = category === state.selectedCategory;
    const catBudget = data.categoryBudgets[category] || 0;
    const catSpent = categorySpending[category] || 0;

    categoryList.appendChild(card({
      variant: isSelected ? 'secondary-container' : '',
      className: isSelected ? 'selected' : '',
      onclick: () => { state.selectedCategory = category; ctx.rerender(); },
    }, h('div.pad-14', {},
      h('div.row.between', {},
        h('div.title-small.grow.ellipsis', {}, category),
        h('button.icon-btn.small', {
          type: 'button', 'aria-label': 'Edit budget',
          style: { color: 'var(--primary)' },
          onclick: (e) => {
            e.stopPropagation();
            openBudgetEditDialog(ctx, {
              title: `${category} budget`,
              currentBudget: catBudget,
              onConfirm: (value) => update((d) => { d.categoryBudgets[category] = value; }),
            });
          },
        }, icon('edit')),
      ),
      h('div.row.between', { style: { alignItems: 'flex-end' } },
        h('div.title-large', {}, `₹${fmt0(catBudget)}`),
        h('div.label-small', {
          class: catBudget > 0 && catSpent > catBudget ? 'danger' : 'muted',
        }, `₹${fmt0(catSpent)} spent`),
      ),
      h('div', { style: { height: '6px' } }),
      progressBar(catSpent, catBudget, { thin: true }),
    )));
  }
  root.appendChild(categoryList);

  // Selected category: allocation summary + subcategories
  const category = state.selectedCategory;
  if (category) {
    const categoryTotalBudget = data.categoryBudgets[category] || 0;
    const subs = data.subcategoriesMap[category] || [];
    const categoryAllocated = sumBy(subs, (sub) => data.subcategoryBudgets[`${category}/${sub}`] || 0);
    const categoryUnallocated = categoryTotalBudget - categoryAllocated;
    const isOverAllocated = categoryUnallocated < 0;

    root.appendChild(h('div.row.gap-10.mt-24.mb-16', {},
      card({ variant: 'tertiary-container', className: 'grow' }, h('div.pad-12', {},
        h('div.label-small.ellipsis', { style: { opacity: '.75' } }, `${category} allocated`),
        h('div.title-large', {}, `₹${fmt0(categoryAllocated)}`),
      )),
      card({ variant: isOverAllocated ? 'error-container' : '', className: 'grow' }, h('div.pad-12', {},
        h('div.label-small.ellipsis', {
          class: isOverAllocated ? '' : 'muted',
          style: isOverAllocated ? { opacity: '.75' } : null,
        }, isOverAllocated ? 'Over-allocated' : `${category} unallocated`),
        h('div.title-large', {}, `₹${fmt0(Math.abs(categoryUnallocated))}`),
      )),
    ));

    root.appendChild(h('div.title-medium.muted.mb-8', {}, 'Subcategories'));

    const subList = h('div.col.gap-8');
    for (const sub of subs) {
      const key = `${category}/${sub}`;
      const subBudget = data.subcategoryBudgets[key] || 0;
      const subSpent = subcategorySpending[key] || 0;

      subList.appendChild(card({}, h('div.pad-14', {},
        h('div.row.between', {},
          h('div.title-small.grow.ellipsis', {}, sub || 'Uncategorized'),
          h('button.icon-btn.small', {
            type: 'button', 'aria-label': 'Edit budget',
            style: { color: 'var(--primary)' },
            onclick: () => openBudgetEditDialog(ctx, {
              title: `${sub} budget`,
              subtitle: `in ${category}`,
              currentBudget: subBudget,
              onConfirm: (value) => update((d) => { d.subcategoryBudgets[key] = value; }),
            }),
          }, icon('edit')),
        ),
        h('div.row.between', { style: { alignItems: 'flex-end' } },
          h('div.title-medium', {}, `₹${fmt0(subBudget)}`),
          h('div.label-small', {
            class: subBudget > 0 && subSpent > subBudget ? 'danger' : 'muted',
          }, `₹${fmt0(subSpent)} spent`),
        ),
        h('div', { style: { height: '6px' } }),
        progressBar(subSpent, subBudget, { thin: true }),
      )));
    }
    if (subs.length === 0) {
      subList.appendChild(h('div.empty-note', {}, 'No subcategories in this category'));
    }
    root.appendChild(subList);
  }

  root.appendChild(h('div', { style: { height: '24px' } }));
  return root;
}

/** ModernBudgetEditDialog — amount field, quick-set chips, Clear / Save. */
function openBudgetEditDialog(ctx, { title, subtitle, currentBudget, onConfirm }) {
  customDialog((api) => {
    const field = textField({
      label: 'Budget amount (₹)',
      value: currentBudget > 0 ? fmt2(currentBudget) : '',
      numeric: true,
    });

    const quickSet = h('div.row.gap-6', {}, ...[50, 100, 200, 500].map((amount) => h('button.chip.suggestion', {
      type: 'button',
      onclick: () => { field.inputEl.value = String(amount); },
    }, `₹${amount}`)));

    return h('div', {},
      h('div.row.between', {},
        h('div.grow', {},
          h('div.headline-small.clamp-2', {}, title),
          subtitle ? h('div.body-small.muted', {}, subtitle) : null,
        ),
        h('button.icon-btn', { type: 'button', onclick: () => api.close() }, icon('close')),
      ),
      h('div.mt-16', {}, field),
      h('div.label-medium.muted', { style: { margin: '12px 0 8px' } }, 'Quick set'),
      quickSet,
      h('div.dialog-actions', {},
        h('button.btn.text.destructive', {
          type: 'button',
          onclick: () => { api.close(); onConfirm(0); ctx.rerender(); },
        }, 'Clear'),
        h('button.btn', {
          type: 'button',
          onclick: () => {
            const value = toDoubleOrNull(field.inputEl.value) ?? 0;
            api.close();
            onConfirm(value);
            ctx.rerender();
          },
        }, 'Save'),
      ),
    );
  });
}

export const budgetState = state;
