// ── Deployment configuration ──────────────────────────────────────────────────
// Google Drive sync needs a *Web application* OAuth client ID from Google Cloud
// Console (APIs & Services → Credentials), with the Drive API enabled and this
// site's origin listed under "Authorised JavaScript origins" — e.g.
// https://your-site.netlify.app and http://localhost:8000 for local runs.
//
// Set `googleClientId` below and it ships with the Netlify deploy, so the app
// signs in on its own and no one has to paste anything into Settings. Leaving it
// empty falls back to Settings → Google OAuth client ID, which is stored in that
// one browser only and is lost whenever site data is cleared.
//
// This value is NOT a secret. The browser sends it in the clear to Google on
// every sign-in, and Netlify serves this file to every visitor — a private
// GitHub repo hides it from the repo, not from the deployed site. What actually
// restricts it is the "Authorised JavaScript origins" allowlist, so keep that
// list tight and treat it as the real control.

window.EXPENSE_TRACKER_CONFIG = {
  googleClientId: '',
};
