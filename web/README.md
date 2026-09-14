# Expense Tracker — web app

A browser port of the Android app, matching its Aurora (Material 3) UI, screens
and features. No build step, no dependencies: plain ES modules, CSS and one
HTML file, deployed to Netlify as static files.

## Run locally

```bash
cd web
python3 -m http.server 8000
```

Then open <http://localhost:8000>. Any static server works (`npx serve`,
`php -S`, …) — the app must be served over HTTP rather than opened as a
`file://` URL, because it uses ES modules.

## Deploy to GitHub Pages

`.github/workflows/pages.yml` publishes `web/` on every push to `master`, and
can also be run by hand from the Actions tab. One-time setup:

1. **Settings → Pages → Build and deployment → Source** = **GitHub Actions**.
2. Push to `master` (or run the workflow manually) and the site goes live at
   `https://<owner>.github.io/<repo>/`.
3. Add that URL to **Authorised JavaScript origins** on the OAuth client, or
   Drive sign-in fails with an origin error. The origin is the scheme + host
   only — `https://<owner>.github.io`, with no repo path.

Free on public repositories. Two things differ from Netlify, neither of which
this app needs: Pages serves from a **subpath**, which is fine because every
asset path, the manifest's `start_url`/`scope` and the service-worker
registration are relative; and it has no redirect rules, which costs nothing
because navigation uses `pushState` without a URL, so the path never changes
and there are no deep links to rewrite. Pages also ignores `netlify.toml`'s
cache headers, but the service worker is network-first, so a deploy is still
picked up on the next load.

Because `localStorage` is per-origin, a new host starts with no local data.
Sign in to Drive there and the startup pull fills it from the newest backup.

## Deploy to Netlify

`netlify.toml` in the repo root already points Netlify at this directory:

```toml
[build]
  publish = "web"
  command = ""
```

Either way of connecting works:

- **Git-based** — in Netlify, *Add new site → Import an existing project*,
  pick this repository, and accept the settings from `netlify.toml`
  (build command empty, publish directory `web`). Every push to the connected
  branch redeploys.
- **CLI** — `npx netlify-cli deploy --prod` from the repo root.
- **Drag and drop** — drop the `web/` folder onto the Netlify dashboard.

There is nothing to install or compile, so builds take seconds.

## Feature parity with the Android app

| Android | Web |
| --- | --- |
| Dashboard: calendar, budget/balance/spent, trends, category breakdown | ✅ same layout, same drill-through dialogs |
| Expense list: search, date/attribute filters, by-store & by-item, detail views | ✅ |
| Budget: category & subcategory budgets, allocated/unallocated | ✅ |
| Add/edit expense: GST auto-calc, splits, calculator, store & location suggestions, drafts | ✅ |
| Settings: taxonomy management, recurring expenses, dev mode, sample data | ✅ |
| Recurring expense engine | ✅ same due-date maths (`js/recurring.js`) |
| CSV import / template export | ✅ same parser and template |
| Aurora light & dark themes | ✅ same palette, type scale and shapes |
| Local persistence | `expense_data.json` in app storage → the same JSON in `localStorage` |
| Google Drive backup | ✅ same folder and file naming, see below |
| Back button | Browser back maps to the same navigation rules |

The app is also installable (PWA) and works offline; all data lives in the
browser, so nothing leaves the device unless Drive sync is switched on.

## Data compatibility

The JSON written by the web app is byte-for-byte the same shape as the Android
app's `expense_data.json`, so a backup from either side restores into the other:

- **Android → web**: download the backup from Drive, then
  *Settings → Backup file → Restore…*
- **Web → Android**: *Settings → Backup file → Save backup*, put the file on the
  device, then *Restore from a backup file* in the Android app.

Restores **merge** rather than overwrite, using the same `MERGE_BY_DATE`
strategy as `SyncService.kt`.

## Google Drive sync (optional)

The Android app resolves its OAuth client from the APK's package name + SHA-1
signature. A web page can't do that, so the web app needs its own **Web
application** OAuth client ID:

1. In [Google Cloud Console](https://console.cloud.google.com), open the project
   that has the **Google Drive API** enabled (or enable it in a new one).
2. **APIs & Services → Credentials → Create Credentials → OAuth client ID**,
   application type **Web application**.
3. Under **Authorised JavaScript origins**, add the deployed origin
   (e.g. `https://your-site.netlify.app`) and, for local development,
   `http://localhost:8000`. No redirect URI is needed — the app uses the
   Google Identity Services token flow.
4. If the OAuth consent screen is in **Testing** mode, add your Google account
   as a test user.
5. Put the client ID in one of two places:
   - `web/config.js` — ships with the deploy, so the app signs in on its own and
     nobody has to paste anything. **Use this one**; or
   - *Settings → Google OAuth client ID* — stored in that browser's
     `localStorage` only, so it has to be re-entered on every new browser and
     any time site data is cleared.

   A shipped ID takes precedence over a stored one, and Settings then shows it
   as read-only rather than offering a field that would be ignored. That way a
   browser which pasted an ID back when `config.js` was empty can't go on
   shadowing the deploy's with a stale value.

Everything except Drive sync works with no client ID configured.

The client ID is **not a secret**. The browser sends it in the clear to Google on
every sign-in, and the deploy serves `config.js` to every visitor — a private
GitHub repo hides it from the repo, not from the site. The *Authorised JavaScript
origins* allowlist is what actually restricts its use, so keep that list tight.

## How sync behaves

| When | What happens |
| --- | --- |
| Page load, previously signed in | Silent token refresh, then an **automatic pull** of the newest Drive backup, merged into local data |
| Interactive sign-in | Same pull, so a fresh browser fills itself from Drive |
| Any edit | **Upload, debounced 3 s** after the last change — one upload per burst, not per keystroke |
| *Settings → Upload backup* | Timestamped snapshot, kept as a point-in-time restore point |
| *Settings → Download latest backup* | Explicit restore of the newest backup |

Auto-sync overwrites a single rolling file, `expense_data_latest.json`, so the
Drive folder no longer grows by one file per edit burst. Manual uploads still
write `expense_data_<timestamp>.json`, and both show up in the restore list.

The two pull paths differ deliberately, and the difference matters:

- The **automatic** pull is additive — it adds and updates from remote but never
  drops a local-only expense, since a silent background merge must not destroy
  an expense entered offline whose upload never landed.
- The **explicit** restore keeps full `MERGE_BY_DATE` semantics, dropping
  expenses missing from the backup, and reports the count it removed.

So deletions propagate between devices on an explicit restore, not on page load.

Two things differ from Android by necessity:

- The scope is `drive.file`, so the web app only sees files **it** created. It
  will not list backups uploaded by the Android app (a different OAuth client).
  Move backups between the two with the file restore path above.
- Google's token flow needs a real origin, so Drive sync does not work from a
  `file://` page.

## Layout

```
web/
├── index.html               # App shell
├── config.js                # Optional Google OAuth client ID
├── manifest.webmanifest     # PWA manifest
├── sw.js                    # Service worker (network-first, offline fallback)
├── css/aurora.css           # Aurora palette, type scale, shapes, components
└── js/
    ├── app.js               # ModernMainActivity: navigation, sync, CSV, autosave
    ├── nav.js               # Screen / TrendDimension enums
    ├── model.js             # Expense, RecurringExpense, AppData + lenient parsing
    ├── store.js             # DataRepository: localStorage load/save
    ├── recurring.js         # RecurringExpenseEngine
    ├── csv.js               # CSV helpers, template, import pipeline
    ├── sync.js              # SyncService + Drive REST (Google Identity Services)
    ├── sample.js            # SampleDataManager
    ├── components.js        # AuroraComponents: fields, dropdowns, dialogs, pickers
    ├── calendar.js          # AuroraCalendar
    ├── charts.js            # AuroraCharts (canvas)
    ├── dom.js               # DOM builder + Material icon paths
    ├── util.js              # LocalDate/YearMonth maths, formatting
    └── screens/             # Dashboard, ExpenseList, Budget, Settings, Entry, SignIn
```

Each module names the Kotlin file it ports at the top.
