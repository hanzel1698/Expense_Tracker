// ── Sign-in gate ──────────────────────────────────────────────────────────────
// Port of ui/modern/screens/ModernSignInGateScreen.kt.

import { h, icon } from '../dom.js';
import { isDriveConfigured } from '../sync.js';

export function renderSignInGate(ctx) {
  const configured = isDriveConfigured();

  return h('div', {
    style: {
      minHeight: '100%',
      display: 'flex',
      flexDirection: 'column',
      alignItems: 'center',
      justifyContent: 'center',
      padding: '32px 24px',
      textAlign: 'center',
    },
  },
    h('div', { style: { color: 'var(--primary)' } }, icon('sync', 'gate-icon')),
    h('div', { style: { height: '24px' } }),
    h('div.headline-medium', {}, 'Expense Tracker'),
    h('div', { style: { height: '12px' } }),
    h('div.body-large.muted', {},
      configured
        ? 'Sign in with Google to automatically back up and sync your expenses to Google Drive.'
        : 'Track daily spending, budgets and recurring expenses. Your data stays in this browser — connect Google Drive later from Settings to back it up.'),
    h('div', { style: { height: '32px', width: '100%' } }),
    configured
      ? h('button.btn.full', {
        type: 'button',
        onclick: () => ctx.signIn(),
      }, icon('google'), 'Sign in with Google')
      : null,
    configured ? h('div', { style: { height: '8px' } }) : null,
    h('button.btn.text', {
      type: 'button',
      onclick: () => ctx.navigate('Dashboard'),
    }, configured ? 'Continue without signing in' : 'Get started'),
  );
}
