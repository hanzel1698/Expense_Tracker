// ── Deployment configuration ──────────────────────────────────────────────────
// Optional. Google Drive sync needs a *Web application* OAuth client ID from
// Google Cloud Console (APIs & Services → Credentials), with the Drive API
// enabled and this site's origin listed under "Authorised JavaScript origins" —
// e.g. https://your-site.netlify.app and http://localhost:8000 for local runs.
//
// Set it here to ship it with the deploy, or leave it empty and let each user
// paste their own in Settings → Google OAuth client ID (stored in their browser
// only). Everything except Drive sync works with no client ID at all.

window.EXPENSE_TRACKER_CONFIG = {
  googleClientId: '',
};
